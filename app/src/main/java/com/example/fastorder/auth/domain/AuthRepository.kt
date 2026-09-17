package com.example.fastorder.auth.domain

import android.app.Activity
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * The seam between the auth feature and whoever provides identity.
 *
 * This interface is the reason auth can stay in `:app` for now without painting
 * the project into a corner: everything above it (ViewModels, screens,
 * `MainViewModel`) depends only on this file and the types beside it. Lifting
 * the feature into a `:feature:auth` Gradle module later is a directory move
 * plus a build file - no call-site changes - and swapping Firebase for
 * something else means writing one new implementation.
 *
 * ### Why [Activity] appears in a "domain" interface
 *
 * It is a deliberate, documented leak. Both underlying mechanisms genuinely
 * require an `Activity`, not an application `Context`:
 *
 *  - Credential Manager renders a system bottom sheet over the calling window.
 *  - Firebase Phone Auth falls back to a reCAPTCHA `WebView` hosted in an
 *    Activity when Play Integrity cannot vouch for the app, and throws
 *    `FirebaseAuthMissingActivityForRecaptchaException` if it has none.
 *
 * The alternatives are worse: holding an `Activity` in a repository field leaks
 * the window on rotation, and hiding it behind an `ActivityProvider` indirection
 * would obscure a hard platform constraint rather than remove it. Passing it per
 * call keeps the reference scoped to exactly the duration of the UI it drives.
 */
interface AuthRepository {

    /**
     * The signed-in user, or `null`.
     *
     * A hot [StateFlow] fed by Firebase's own auth-state listener rather than a
     * value returned from the sign-in calls, which matters for three cases that
     * produce a session with nobody awaiting one:
     *
     *  - restoring a persisted session on cold start,
     *  - phone auto-verification completing after the OTP screen already moved
     *    on (see [OtpChallenge.AutoVerified]),
     *  - the token being revoked server-side mid-session.
     *
     * Because every one of those funnels through here, the UI has exactly one
     * place to observe and "am I signed in?" can never disagree with itself.
     */
    val currentUser: StateFlow<AuthUser?>

    /**
     * SMS codes the platform read on the user's behalf, as they arrive.
     *
     * Auto-retrieval completes *after* [sendOtp] has already returned
     * `CodeSent` and the code pane is on screen, so there is no call left to
     * answer with it - hence a hot stream rather than a return value. The UI
     * fills the field from this and tells the user why it filled itself;
     * sign-in still completes on its own through [currentUser].
     *
     * Hot and buffered, not a `StateFlow`: a code is an event, and replaying
     * the last one to a new collector would re-fill the field on the *next*
     * verification with the *previous* code.
     */
    val autoRetrievedCode: SharedFlow<String>

    /**
     * Shows the system credential sheet and exchanges the resulting Google ID
     * token for a Firebase session.
     *
     * @param activity the window to host the sheet over.
     */
    suspend fun signInWithGoogle(activity: Activity): AuthResult<AuthUser>

    /**
     * Starts phone verification for [phoneE164] and sends an SMS code.
     *
     * @param phoneE164 must be full E.164, including the `+` and country code
     *   (`+919876543210`). Firebase rejects national-format numbers.
     * @return [OtpChallenge.CodeSent] normally, or [OtpChallenge.AutoVerified]
     *   when Play services resolved the number with no user input.
     */
    suspend fun sendOtp(activity: Activity, phoneE164: String): AuthResult<OtpChallenge>

    /**
     * Re-sends the code for the in-flight verification.
     *
     * Distinct from calling [sendOtp] again: this replays Firebase's resend
     * token, which is what stops a second request from being treated as a fresh
     * attempt and counting twice against the per-number abuse quota.
     *
     * Fails with [AuthError.OtpExpired] if there is no verification in flight.
     */
    suspend fun resendOtp(activity: Activity): AuthResult<OtpChallenge>

    /**
     * Completes phone sign-in with the code the user typed.
     *
     * The `verificationId` is held by the implementation rather than passed
     * back through the UI, so no Firebase-shaped token has to be threaded
     * through `SavedStateHandle` and the ViewModel.
     */
    suspend fun verifyOtp(code: String): AuthResult<AuthUser>

    /**
     * Discards the in-flight verification, if any.
     *
     * Called when the user leaves the code pane to correct their number. A code
     * typed afterwards must not be accepted, because the session behind it
     * belongs to a number they have walked away from.
     *
     * Note what this does *not* promise: that the next [sendOtp] starts from
     * nothing. Implementations are expected to keep whatever they need in order
     * to re-send for the same number - see [sendOtp].
     */
    fun abandonVerification()

    /**
     * Signs out and clears the saved credential state.
     *
     * Suspending because it is two operations, and skipping the second is a
     * real bug: `FirebaseAuth.signOut()` alone leaves Credential Manager's
     * "sign in as <name>" hint in place, so the next attempt silently
     * re-authenticates the account the user just left. That makes account
     * switching impossible - a support issue, not a cosmetic one.
     */

    suspend fun signOut()
}

