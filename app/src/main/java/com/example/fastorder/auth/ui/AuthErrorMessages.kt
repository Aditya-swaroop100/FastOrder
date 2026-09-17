package com.example.fastorder.auth.ui

import androidx.annotation.StringRes
import com.example.fastorder.R
import com.example.fastorder.auth.domain.AuthError

/**
 * Maps a domain [AuthError] onto the string the user sees.
 *
 * Lives in the UI layer, not beside [AuthError], so the domain model stays free
 * of `R` and Android resources - which is what keeps it unit-testable on the
 * JVM and portable into a shared module later.
 *
 * The `when` is exhaustive with no `else` on purpose: adding a case to
 * [AuthError] then produces a compile error here rather than silently falling
 * through to a generic message, which is how untranslated errors accumulate.
 */
@StringRes
internal fun AuthError.messageRes(): Int = when (this) {
    // Should never reach the UI - SignInViewModel.showError filters it out,
    // since telling users "sign-in failed" for their own dismissal is noise.
    // Mapped anyway so the `when` stays exhaustive without an else branch.
    AuthError.Cancelled -> R.string.auth_error_unknown

    AuthError.NoCredentialAvailable -> R.string.auth_error_no_credential
    AuthError.Network -> R.string.auth_error_network
    AuthError.VerificationTimedOut -> R.string.auth_error_verification_timeout
    AuthError.InvalidPhoneNumber -> R.string.auth_error_invalid_phone
    AuthError.InvalidOtp -> R.string.auth_error_invalid_otp
    AuthError.OtpExpired -> R.string.auth_error_otp_expired
    AuthError.TooManyRequests -> R.string.auth_error_too_many_requests
    AuthError.ProviderDisabled -> R.string.auth_error_provider_disabled
    AuthError.AccountDisabled -> R.string.auth_error_account_disabled
    AuthError.CredentialAlreadyInUse -> R.string.auth_error_credential_in_use
    AuthError.AppVerificationFailed -> R.string.auth_error_app_verification
    is AuthError.Unknown -> R.string.auth_error_unknown
}


