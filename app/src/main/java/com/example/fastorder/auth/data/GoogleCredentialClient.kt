package com.example.fastorder.auth.data

import android.app.Activity
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import timber.log.Timber
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Obtains a Google ID token through Credential Manager.
 *
 * Wraps the platform API so [FirebaseAuthRepository] deals with one suspend
 * function returning a token string, and so the retry cascade below lives in
 * exactly one place.
 *
 * Credential Manager - not the old `GoogleSignInClient` / One Tap - because
 * those are deprecated, and because this same API is what serves passkeys. If
 * passkey support is added later it becomes an extra option on the request
 * built here, not a parallel sign-in stack.
 */
internal class GoogleCredentialClient(
    private val credentialManager: CredentialManager,
    /**
     * The OAuth **web** client ID (`client_type: 3` in `google-services.json`),
     * read from the `default_web_client_id` string the Google Services Gradle
     * plugin generates.
     *
     * Counter-intuitively this is the web client, not the Android one: it
     * identifies the *backend* the ID token is minted for, and for a Firebase
     * app that backend is Firebase itself. Passing the Android client ID is the
     * classic cause of a sign-in that fails with no useful message.
     */
    private val serverClientId: String,
) {

    /**
     * Returns a Google ID token, showing whatever UI is needed to get one.
     *
     * Escalates through three requests, each one more intrusive, so a returning
     * user gets the quietest possible experience and a first-time user still
     * reaches a working flow:
     *
     *  1. **Authorized accounts only** - accounts that have already used this
     *     app. Renders as a one-tap sheet with no account chooser.
     *  2. **Any account on the device** - for a reinstall or a new device,
     *     where step 1 correctly finds nothing.
     *  3. **Sign in with Google** - the branded flow, and the only one that
     *     offers "add another account", which matters on a device with no
     *     Google account signed in at all.
     *
     * Only [NoCredentialException] advances the cascade. A user who dismisses
     * the sheet is answered, not re-prompted with a different dialog - retrying
     * on cancellation would trap them in a loop of sheets they cannot escape.
     */
    suspend fun requestIdToken(activity: Activity): String {
        val attempts = listOf(
            "authorized-accounts" to googleIdOption(filterByAuthorizedAccounts = true),
            "all-accounts" to googleIdOption(filterByAuthorizedAccounts = false),
            "sign-in-with-google" to GetSignInWithGoogleOption.Builder(serverClientId)
                .setNonce(newNonce())
                .build(),
        )

        attempts.forEachIndexed { index, (label, option) ->
            try {
                val response = credentialManager.getCredential(
                    context = activity,
                    request = GetCredentialRequest.Builder()
                        .addCredentialOption(option)
                        .build(),
                )
                Timber.d("Auth: Google credential obtained via %s", label)
                return response.credential.extractGoogleIdToken()
            } catch (e: NoCredentialException) {
                val isLastAttempt = index == attempts.lastIndex
                Timber.d("Auth: no credential for %s, escalating=%b", label, !isLastAttempt)
                if (isLastAttempt) throw e
            }
        }

        // Unreachable: the final attempt either returns or re-throws above.
        error("Credential cascade completed without a result")
    }

    /**
     * Clears the "sign in as <name>" hint held by the credential provider.
     *
     * Required on sign-out. Without it the provider keeps offering - and with
     * auto-select, silently re-using - the account the user just left, so
     * switching accounts becomes impossible from inside the app.
     */
    suspend fun clearCredentialState() {
        runCatching {
            credentialManager.clearCredentialState(ClearCredentialStateRequest())
        }.onFailure {
            // Best-effort. The Firebase session is already gone by this point,
            // so failing here degrades the next sign-in's UX but does not leave
            // the user authenticated. Not worth failing sign-out over.
            Timber.w("Auth: could not clear credential state")
        }
    }

    private fun googleIdOption(filterByAuthorizedAccounts: Boolean) =
        GetGoogleIdOption.Builder()
            .setServerClientId(serverClientId)
            .setFilterByAuthorizedAccounts(filterByAuthorizedAccounts)
            // Auto-select only once the account set is unambiguous. Enabling it
            // on the unfiltered pass would sign the user into whichever account
            // happens to be first on a multi-account device, with no prompt.
            .setAutoSelectEnabled(filterByAuthorizedAccounts)
            .setNonce(newNonce())
            .build()

    /**
     * A single-use, hashed nonce bound into the ID token's `nonce` claim.
     *
     * Honest scope note: Firebase's `signInWithCredential` does not expose the
     * raw nonce for comparison, so this is not doing replay validation *here*.
     * It is set because Google's Identity guidance treats it as part of a
     * correctly-formed request, it costs nothing, and the day this app gains a
     * backend that verifies ID tokens itself, the claim is already present.
     *
     * Hashed rather than raw so the value that travels to Google's servers and
     * lands in the token cannot be replayed against anything else.
     */
    private fun newNonce(): String {
        val raw = ByteArray(NONCE_BYTES).also(SecureRandom()::nextBytes)
        return MessageDigest.getInstance("SHA-256")
            .digest(raw)
            .joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val NONCE_BYTES = 32
    }
}

/**
 * Pulls the ID token out of the credential Credential Manager handed back.
 *
 * The response is intentionally loosely typed - a provider is free to return
 * anything - so the type is checked rather than assumed. A blind cast here
 * would turn "a password manager answered instead of Google" into a
 * `ClassCastException` in production.
 */
private fun androidx.credentials.Credential.extractGoogleIdToken(): String {
    val isGoogleIdToken = this is CustomCredential &&
        type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL

    check(isGoogleIdToken) {
        "Expected a Google ID token credential but received type=$type"
    }

    // Smart-cast: `check` narrows the type via the boolean above, so no
    // explicit cast is needed here.
    return GoogleIdTokenCredential.createFrom(data).idToken
}


