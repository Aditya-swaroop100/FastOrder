package com.example.fastorder.auth.data

import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.GetCredentialInterruptedException
import androidx.credentials.exceptions.GetCredentialProviderConfigurationException
import androidx.credentials.exceptions.GetCredentialUnsupportedException
import androidx.credentials.exceptions.NoCredentialException
import com.example.fastorder.auth.domain.AuthError
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthMissingActivityForRecaptchaException
import com.google.firebase.auth.FirebaseAuthUserCollisionException

/**
 * Translates Firebase and Credential Manager failures into [AuthError].
 *
 * Kept in one file on purpose. Error mapping is the code most likely to be
 * quietly duplicated - a `catch` here, an `if (e.errorCode == ...)` there - and
 * once that happens the same underlying failure starts showing the user two
 * different messages depending on which call site it came through.
 *
 * Firebase reports the interesting distinctions through `errorCode` strings
 * rather than the exception class (a single `FirebaseAuthInvalidCredentials`
 * covers both a mistyped OTP and an expired session), so the code is checked
 * first and the class used only as a fallback.
 *
 * Coroutine `CancellationException` is deliberately absent: callers re-throw it
 * before reaching here, because turning a cancelled scope into a `Failure` would
 * let a torn-down screen still report an error.
 */
internal fun Throwable.toAuthError(): AuthError = when (this) {

    // --- Credential Manager ------------------------------------------------
    is GetCredentialCancellationException -> AuthError.Cancelled

    // Nothing to offer: no Google account on the device, or the user declined
    // to share one. Actionable - the UI steers them to the phone path.
    is NoCredentialException -> AuthError.NoCredentialAvailable

    // Transient UI interruption (e.g. a call arriving over the sheet).
    is GetCredentialInterruptedException -> AuthError.Network

    // No credential provider: Play services missing, disabled, or too old.
    // Common on emulators without Google APIs and on de-Googled devices.
    is GetCredentialProviderConfigurationException,
    is GetCredentialUnsupportedException,
        -> AuthError.NoCredentialAvailable

    // --- Firebase ----------------------------------------------------------
    is FirebaseNetworkException -> AuthError.Network

    // Project-level SMS quota, or Firebase's per-number abuse throttle.
    is FirebaseTooManyRequestsException -> AuthError.TooManyRequests

    // Thrown when Play Integrity could not attest the app and Firebase wanted
    // to fall back to a reCAPTCHA WebView but was given no Activity to host it.
    is FirebaseAuthMissingActivityForRecaptchaException -> AuthError.AppVerificationFailed

    is FirebaseAuthException -> fromErrorCode(errorCode, this)

    // Credential Manager wraps provider-side failures; unwrap once before
    // giving up, otherwise every provider error collapses into Unknown.
    is GetCredentialException -> AuthError.Unknown(this)

    else -> AuthError.Unknown(this)
}

/**
 * Maps Firebase's `errorCode` constants.
 *
 * These are the stable, documented identifiers; the human-readable `message`
 * beside them is localised and reworded between SDK releases, so matching on it
 * would break silently on a version bump.
 */
private fun fromErrorCode(code: String, source: FirebaseAuthException): AuthError = when (code) {

    "ERROR_INVALID_PHONE_NUMBER",
    "ERROR_MISSING_PHONE_NUMBER",
        -> AuthError.InvalidPhoneNumber

    "ERROR_INVALID_VERIFICATION_CODE",
    "ERROR_MISSING_VERIFICATION_CODE",
        -> AuthError.InvalidOtp

    // The verificationId is dead, so retyping the code cannot work - the UI has
    // to go back and request a fresh SMS. That is why this is not InvalidOtp.
    "ERROR_SESSION_EXPIRED",
    "ERROR_INVALID_VERIFICATION_ID",
    "ERROR_MISSING_VERIFICATION_ID",
        -> AuthError.OtpExpired

    "ERROR_TOO_MANY_REQUESTS",
    "ERROR_QUOTA_EXCEEDED",
        -> AuthError.TooManyRequests

    // The provider is switched off in the Firebase console. On a fresh project
    // this is the single most likely cause of a first-run failure.
    "ERROR_OPERATION_NOT_ALLOWED" -> AuthError.ProviderDisabled

    "ERROR_USER_DISABLED" -> AuthError.AccountDisabled

    "ERROR_CREDENTIAL_ALREADY_IN_USE",
    "ERROR_ACCOUNT_EXISTS_WITH_DIFFERENT_CREDENTIAL",
    "ERROR_EMAIL_ALREADY_IN_USE",
        -> AuthError.CredentialAlreadyInUse

    "ERROR_APP_NOT_AUTHORIZED",
    "ERROR_CAPTCHA_CHECK_FAILED",
    "ERROR_MISSING_CLIENT_IDENTIFIER",
        -> AuthError.AppVerificationFailed

    "ERROR_NETWORK_REQUEST_FAILED" -> AuthError.Network

    else -> when (source) {
        // Fall back to the exception hierarchy for codes not listed above, so
        // a newly introduced code still lands in a sensible bucket.
        is FirebaseAuthInvalidCredentialsException -> AuthError.InvalidOtp
        is FirebaseAuthInvalidUserException -> AuthError.AccountDisabled
        is FirebaseAuthUserCollisionException -> AuthError.CredentialAlreadyInUse
        else -> AuthError.Unknown(source)
    }
}

/**
 * A short, non-identifying tag for logs and crash-report breadcrumbs.
 *
 * `FastOrderApplication` sets Sentry's `isSendDefaultPii = false`; logging a raw
 * exception message here would route straight around that, because Firebase
 * puts the phone number and email into those messages. The class name alone is
 * enough to tell one failure mode from another.
 */
internal fun AuthError.telemetryTag(): String = when (this) {
    is AuthError.Unknown -> "Unknown(${cause?.javaClass?.simpleName ?: "null"})"
    else -> this::class.simpleName ?: "AuthError"
}


