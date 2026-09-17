package com.example.fastorder.auth.data

import android.app.Activity
import com.example.fastorder.auth.domain.AuthError
import com.example.fastorder.auth.domain.AuthProvider
import com.example.fastorder.auth.domain.AuthRepository
import com.example.fastorder.auth.domain.AuthResult
import com.example.fastorder.auth.domain.AuthUser
import com.example.fastorder.auth.domain.OtpChallenge
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

/**
 * [AuthRepository] backed by Firebase Authentication.
 *
 * The only file in the app that imports `com.google.firebase.auth`. That is the
 * point: everything above it speaks [AuthUser] / [AuthError], so replacing the
 * identity provider, or faking it in a test, means substituting this class and
 * nothing else.
 *
 * @param externalScope an application-lifetime scope. It backs [currentUser]
 *   and completes out-of-band phone auto-verification. It must outlive any
 *   screen: a `viewModelScope` would cancel the auth-state subscription the
 *   moment the sign-in screen left, exactly when the resulting session matters
 *   most.
 */
internal class FirebaseAuthRepository(
    private val firebaseAuth: FirebaseAuth,
    private val googleCredentialClient: GoogleCredentialClient,
    private val externalScope: CoroutineScope,
) : AuthRepository {

    /**
     * The most recent phone verification, live or abandoned.
     *
     * Held here rather than passed back through the ViewModel so no Firebase
     * token has to survive in a `SavedStateHandle`. [AtomicBoolean]-style
     * publication is unnecessary, but `@Volatile` is not: the Firebase
     * callbacks run on the main thread while `verifyOtp` may resume on another,
     * so without it the write is not guaranteed to be visible to the read.
     */
    @Volatile
    private var phoneVerification: PhoneVerification? = null

    /**
     * Auto-retrieved SMS codes.
     *
     * `tryEmit` onto a buffered [MutableSharedFlow] rather than `emit`, because
     * this is published from a Firebase callback thread - which must never be
     * parked waiting for a slow collector.
     */
    private val _autoRetrievedCode = MutableSharedFlow<String>(extraBufferCapacity = 1)
    override val autoRetrievedCode: SharedFlow<String> = _autoRetrievedCode.asSharedFlow()

    override fun abandonVerification() {
        // The verificationId goes: a code typed after the user walked away
        // would be submitted against a session that no longer matches what is
        // on screen.
        //
        // The resend token deliberately stays. It is the *only* way to ask
        // Firebase for another code for the number it is already verifying -
        // discarding it here is what made "wrong number" followed by
        // re-submitting the same number hang, since the resulting token-less
        // request was swallowed in silence. See [sendOtp].
        phoneVerification = phoneVerification?.copy(verificationId = null)
    }

    override val currentUser: StateFlow<AuthUser?> = callbackFlow {
        // Firebase's own listener is the source of truth rather than the return
        // value of the sign-in calls, because three things produce a session
        // with nobody awaiting one: restoring a persisted session on cold
        // start, phone auto-verification landing after the OTP screen moved on,
        // and a server-side token revocation. Funnelling all of them through
        // one listener is what keeps "am I signed in?" single-valued.
        val listener = FirebaseAuth.AuthStateListener { auth ->
            trySend(auth.currentUser?.toAuthUser())
        }
        firebaseAuth.addAuthStateListener(listener)
        awaitClose { firebaseAuth.removeAuthStateListener(listener) }
    }.stateIn(
        scope = externalScope,
        // Eagerly, not WhileSubscribed: MainViewModel reads `.value` during
        // cold start to pick a start destination, before any collector exists.
        // Lazily started, that read would always return the initial value and
        // send a signed-in user to the sign-in screen on every launch.
        started = SharingStarted.Eagerly,
        initialValue = firebaseAuth.currentUser?.toAuthUser(),
    )

    // ------------------------------------------------------------------
    // Google
    // ------------------------------------------------------------------

    override suspend fun signInWithGoogle(activity: Activity): AuthResult<AuthUser> =
        runAuth("signInWithGoogle") {
            val idToken = googleCredentialClient.requestIdToken(activity)

            // The second leg of the exchange. Credential Manager proves *to the
            // app* who the user is; this proves it to Firebase, which verifies
            // the token's signature server-side and mints the session. Skipping
            // it would leave a Google identity that no Firebase Security Rule
            // can see.
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            firebaseAuth.signInWithCredential(credential).await().requireUser()
        }

    // ------------------------------------------------------------------
    // Phone / OTP
    // ------------------------------------------------------------------

    override suspend fun sendOtp(
        activity: Activity,
        phoneE164: String,
    ): AuthResult<OtpChallenge> {
        // Checked before the network call so an obvious typo costs nothing and,
        // more importantly, does not burn one of the project's SMS quota units
        // to be told the same thing by the server.
        if (!phoneE164.isPlausibleE164()) {
            Timber.w("Auth: rejected malformed phone number locally")
            return AuthResult.Failure(AuthError.InvalidPhoneNumber)
        }

        // Replay the token when this is the number already being verified.
        //
        // Firebase drops a repeat `verifyPhoneNumber` for the number of the
        // most recent verification unless that verification's resend token
        // comes with it - and drops it *silently*: no callback, no error,
        // nothing in the log, so the request is simply never answered. Passing
        // the token is the SDK's supported way of saying "yes, I really do want
        // another code for this same number", and it also marks the request as
        // the same attempt rather than a fresh one for quota purposes.
        //
        // Only that most recent verification is compared against, which is why
        // one slot is enough: leaving for another number and coming back is
        // already accepted on its own.
        val resendToken = phoneVerification
            ?.takeIf { it.phoneE164 == phoneE164 }
            ?.resendToken

        return startVerification(activity, phoneE164, resendToken)
    }

    override suspend fun resendOtp(activity: Activity): AuthResult<OtpChallenge> {
        val previous = phoneVerification
            ?: return AuthResult.Failure(AuthError.OtpExpired)

        // Replaying Firebase's resend token is what marks this as the *same*
        // attempt. Calling sendOtp again instead would open a second
        // verification, invalidating the first and counting twice against the
        // per-number abuse throttle - which is how a user tapping "resend"
        // twice ends up locked out.
        return startVerification(activity, previous.phoneE164, previous.resendToken)
    }

    override suspend fun verifyOtp(code: String): AuthResult<AuthUser> {
        // Null once the user has stepped back off the code pane, so a stale
        // code can never be submitted against an abandoned session even though
        // the record itself is kept for its resend token.
        val verificationId = phoneVerification?.verificationId
            ?: return AuthResult.Failure(AuthError.OtpExpired)

        return runAuth("verifyOtp") {
            val credential = PhoneAuthProvider.getCredential(verificationId, code)
            firebaseAuth.signInWithCredential(credential).await().requireUser()
                .also { phoneVerification = null }
        }
    }

    /**
     * Drives one `verifyPhoneNumber` round-trip.
     *
     * Firebase reports through a callback object that may fire more than once,
     * in an order that varies by device. Bridging it to a single suspend
     * function therefore needs an explicit resume-once guard: without it, a
     * device where auto-retrieval succeeds after the SMS was sent would resume
     * the same continuation twice and crash with `IllegalStateException:
     * Already resumed`.
     */
    private suspend fun startVerification(
        activity: Activity,
        phoneE164: String,
        resendToken: PhoneAuthProvider.ForceResendingToken?,
    ): AuthResult<OtpChallenge> {
        val start = try {
            // Firebase promises *a* callback, not that one will ever arrive.
            // A bound turns a hang into something the user can act on.
            withTimeoutOrNull(VERIFICATION_START_TIMEOUT_MS) {
                awaitVerificationStart(activity, phoneE164, resendToken)
            } ?: run {
                Timber.w(
                    "Auth: no verification callback within %dms (resendToken=%b)",
                    VERIFICATION_START_TIMEOUT_MS,
                    resendToken != null,
                )
                return AuthResult.Failure(AuthError.VerificationTimedOut)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            return AuthResult.Failure(e.toAuthError().also(::logFailure))
        }

        return when (start) {
            is VerificationStart.Failed ->
                AuthResult.Failure(start.cause.toAuthError().also(::logFailure))

            is VerificationStart.Sent -> {
                phoneVerification = PhoneVerification(
                    phoneE164 = phoneE164,
                    verificationId = start.verificationId,
                    resendToken = start.resendToken,
                )
                Timber.i("Auth: OTP sent")
                AuthResult.Success(OtpChallenge.CodeSent(canResend = true))
            }

            // Play services resolved the number with no SMS round-trip
            // (instant verification, or auto-retrieval winning the race). The
            // user is about to be signed in without typing anything.
            is VerificationStart.AutoCompleted -> {
                Timber.i("Auth: phone auto-verified, no code entry needed")
                runAuth("autoVerify") {
                    firebaseAuth.signInWithCredential(start.credential).await().requireUser()
                }.let { result ->
                    when (result) {
                        is AuthResult.Success -> {
                            phoneVerification = null
                            AuthResult.Success(OtpChallenge.AutoVerified(result.value))
                        }

                        is AuthResult.Failure -> result
                    }
                }
            }
        }
    }

    private suspend fun awaitVerificationStart(
        activity: Activity,
        phoneE164: String,
        resendToken: PhoneAuthProvider.ForceResendingToken?,
    ): VerificationStart = suspendCancellableCoroutine { continuation ->
        val resumed = AtomicBoolean(false)

        val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {

            override fun onCodeSent(
                verificationId: String,
                token: PhoneAuthProvider.ForceResendingToken,
            ) {
                if (resumed.compareAndSet(false, true)) {
                    continuation.resume(VerificationStart.Sent(verificationId, token))
                }
            }

            override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                if (resumed.compareAndSet(false, true)) {
                    // Instant verification: beat onCodeSent entirely.
                    continuation.resume(VerificationStart.AutoCompleted(credential))
                    return
                }

                // Auto-retrieval arriving *after* the OTP screen was already
                // shown - the common case on a device that can read its own
                // SMS. The continuation is spent, so finish the sign-in on the
                // application scope instead. `currentUser` emits when it lands
                // and the UI navigates off the OTP screen on its own, which is
                // precisely why sign-in state is observed rather than returned.
                Timber.i("Auth: late auto-retrieval, completing out of band")

                // Surface the digits first. The sign-in below will navigate the
                // user off this screen within a moment either way, but filling
                // the field they are staring at - and saying why - is the
                // difference between "it worked" and "it did something".
                credential.smsCode?.let(_autoRetrievedCode::tryEmit)

                externalScope.launch {
                    runAuth("lateAutoVerify") {
                        firebaseAuth.signInWithCredential(credential).await().requireUser()
                    }
                    phoneVerification = null
                }
            }

            override fun onVerificationFailed(e: FirebaseException) {
                if (resumed.compareAndSet(false, true)) {
                    continuation.resume(VerificationStart.Failed(e))
                }
            }
        }

        val options = PhoneAuthOptions.newBuilder(firebaseAuth)
            .setPhoneNumber(phoneE164)
            .setTimeout(AUTO_RETRIEVAL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            // Required. Firebase hosts a reCAPTCHA WebView here when Play
            // Integrity cannot attest the app - which is every emulator without
            // Play services, and any build whose signing fingerprint is not
            // registered in the Firebase console.
            .setActivity(activity)
            .setCallbacks(callbacks)
            .apply { resendToken?.let(::setForceResendingToken) }
            .build()

        PhoneAuthProvider.verifyPhoneNumber(options)
    }

    // ------------------------------------------------------------------
    // Sign-out
    // ------------------------------------------------------------------

    override suspend fun signOut() {
        phoneVerification = null
        firebaseAuth.signOut()

        // Both halves are required. Firebase forgets the session; the credential
        // provider still remembers the account and, with auto-select, would
        // silently sign the same user straight back in on the next attempt.
        googleCredentialClient.clearCredentialState()
        Timber.i("Auth: signed out")
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /**
     * Runs [block], converting any throw into a typed [AuthResult.Failure].
     *
     * `CancellationException` is re-thrown rather than mapped. Swallowing it
     * would break structured concurrency - the parent scope would believe a
     * cancelled child finished normally - and would let a screen that is
     * already gone report an error.
     */
    private suspend inline fun <T> runAuth(
        operation: String,
        block: () -> T,
    ): AuthResult<T> = try {
        AuthResult.Success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        val error = e.toAuthError()
        Timber.w("Auth: %s failed with %s", operation, error.telemetryTag())
        AuthResult.Failure(error)
    }

    private fun logFailure(error: AuthError) {
        // Tag only - never the exception message. Firebase embeds the phone
        // number and email in those, which would defeat Sentry's
        // isSendDefaultPii = false setting in FastOrderApplication.
        Timber.w("Auth: phone verification failed with %s", error.telemetryTag())
    }

    private companion object {
        /**
         * How long Play services may keep trying to read the SMS itself before
         * giving up and leaving it to the user.
         *
         * 60s is Firebase's documented maximum. Shorter values only make the
         * auto-fill give up early on a slow carrier, which costs the user a
         * manual retype for no benefit.
         */
        const val AUTO_RETRIEVAL_TIMEOUT_SECONDS = 60L

        /**
         * Backstop for the *first* callback after `verifyPhoneNumber`.
         *
         * Deliberately longer than a good network round-trip: when Play
         * Integrity cannot attest the app, Firebase puts a reCAPTCHA in front
         * of the user, and timing out while they are solving it would be a bug
         * of our own making. This is a floor under "never", not a latency
         * budget - reaching it means the SDK accepted a request and then said
         * nothing at all, which should no longer be reachable now that repeat
         * sends replay the resend token.
         */
        const val VERIFICATION_START_TIMEOUT_MS = 60_000L
    }
}

/**
 * The most recent phone verification for a number.
 *
 * [verificationId] is null once the user has stepped back off the code pane:
 * the session can no longer accept a code, but [resendToken] is deliberately
 * retained, because Firebase silently ignores a second `verifyPhoneNumber` for
 * the same number unless that token comes with it.
 */
private data class PhoneVerification(
    val phoneE164: String,
    val verificationId: String?,
    val resendToken: PhoneAuthProvider.ForceResendingToken,
)

/** The three ways `verifyPhoneNumber` can report back on its first signal. */
private sealed interface VerificationStart {
    data class Sent(
        val verificationId: String,
        val resendToken: PhoneAuthProvider.ForceResendingToken,
    ) : VerificationStart

    data class AutoCompleted(val credential: PhoneAuthCredential) : VerificationStart

    data class Failed(val cause: FirebaseException) : VerificationStart
}

/**
 * Unwraps the user from a successful `AuthResult`.
 *
 * Firebase types `user` as nullable even on success. It should never be null
 * there, so this fails loudly rather than propagating a null that would later
 * surface as an unexplained "signed out" three screens away.
 */
private fun com.google.firebase.auth.AuthResult.requireUser(): AuthUser =
    checkNotNull(user) { "Firebase reported a successful sign-in with no user" }.toAuthUser()

/** Maps Firebase's live user handle onto the app's immutable [AuthUser]. */
private fun FirebaseUser.toAuthUser(): AuthUser = AuthUser(
    uid = uid,
    displayName = displayName?.takeIf { it.isNotBlank() },
    email = email?.takeIf { it.isNotBlank() },
    phoneNumber = phoneNumber?.takeIf { it.isNotBlank() },
    photoUrl = photoUrl?.toString(),
    isAnonymous = isAnonymous,
    // providerData includes a synthetic "firebase" entry alongside the real
    // ones; AuthProvider.fromProviderId folds it to Anonymous rather than
    // dropping it, so an unknown provider can never make a linked account look
    // unlinked.
    providers = providerData.map { AuthProvider.fromProviderId(it.providerId) }.toSet(),
)

/**
 * Cheap structural check for E.164: `+`, then 8-15 digits.
 *
 * Deliberately not a full validity test - that needs libphonenumber and a
 * per-country numbering plan. This only catches the mistakes worth catching
 * offline (missing `+`, national format, obvious typos) before spending an SMS
 * to learn the same thing. Firebase remains the authority.
 */
private fun String.isPlausibleE164(): Boolean =
    matches(Regex("^\\+[1-9]\\d{7,14}$"))

