package com.example.fastorder.auth.domain

/**
 * Outcome of an auth operation: either a value or a typed [AuthError].
 *
 * Kotlin's own `Result` is not used here because it can only carry a
 * `Throwable`. That would force [AuthError] to extend `Exception` purely to fit
 * the container - inventing a throwable for "user tapped outside the sheet",
 * which is not exceptional and should never surface in a crash report. An
 * explicit type also makes `when` exhaustive at the call site, so adding a new
 * error case produces compiler errors everywhere it needs handling instead of
 * being swallowed by a `catch`.
 */
sealed interface AuthResult<out T> {

    data class Success<out T>(val value: T) : AuthResult<T>

    data class Failure(val error: AuthError) : AuthResult<Nothing>
}

/** The value if this succeeded, else `null`. */
fun <T> AuthResult<T>.getOrNull(): T? = (this as? AuthResult.Success)?.value

/** The error if this failed, else `null`. */
fun <T> AuthResult<T>.errorOrNull(): AuthError? = (this as? AuthResult.Failure)?.error

/**
 * What happened after asking Firebase to send an SMS code.
 *
 * Two outcomes, because on Android "send an OTP" does not always end with the
 * user typing anything:
 *
 *  - [CodeSent] is the normal path: an SMS is on its way, show the OTP field.
 *  - [AutoVerified] is Google Play services resolving the number without an SMS
 *    round-trip (instant verification on a number already known to the device,
 *    or auto-retrieval reading the incoming SMS). The user is *already signed
 *    in* at that point.
 *
 * Modelling [AutoVerified] explicitly is what stops the classic bug where the
 * app shows an OTP box over an already-authenticated session and waits forever
 * for a code that was never needed.
 */
sealed interface OtpChallenge {

    /**
     * SMS dispatched. Collect the code and call [AuthRepository.verifyOtp].
     *
     * @param canResend whether a resend token was issued, i.e. whether
     *   [AuthRepository.resendOtp] can be offered once the cooldown expires.
     */
    data class CodeSent(val canResend: Boolean) : OtpChallenge

    /** Verified without user input; [user] is signed in already. */
    data class AutoVerified(val user: AuthUser) : OtpChallenge
}

