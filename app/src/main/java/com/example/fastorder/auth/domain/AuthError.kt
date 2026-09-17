package com.example.fastorder.auth.domain

/**
 * Every way sign-in can fail, expressed in terms this app cares about.
 *
 * Firebase and Credential Manager between them throw several dozen exception
 * types and error codes. Passing those upward would put `FirebaseAuthException`
 * string-code comparisons inside Composables; collapsing them to a single
 * "something went wrong" would lose the distinctions that actually change what
 * the UI should do. This sits in between: one case per *distinct user-visible
 * outcome*.
 *
 * The UI maps these to strings (see `AuthError.messageRes`). Keeping the text
 * out of here is what lets the same error be phrased differently on the phone
 * step and the OTP step.
 */
sealed interface AuthError {

    /**
     * The user dismissed the sheet, or backed out. Not a failure.
     *
     * Separated from the rest because it is the one case that must **not** show
     * an error message - the user knows what they did, and telling them
     * "sign-in failed" for a deliberate dismissal is noise.
     */
    data object Cancelled : AuthError

    /**
     * Credential Manager found nothing to offer: no Google account on the
     * device, or none the user is willing to share.
     *
     * Distinct from [Cancelled] because the fix differs - this one warrants
     * pointing the user at the phone-number path instead.
     */
    data object NoCredentialAvailable : AuthError

    /** Offline, or the request timed out. Retry is meaningful. */
    data object Network : AuthError

    /**
     * A verification request was accepted and then never answered.
     *
     * Deliberately not folded into [Network], which is what it used to report:
     * the connection is usually fine, and "check your connection" sends the
     * user off to fix something that was never broken. This means the SDK
     * swallowed the request - so the honest message is "that took too long,
     * try again", and the log line beside it is what points at the real cause.
     */
    data object VerificationTimedOut : AuthError

    /** Not a dialable number in E.164 form, or rejected by Firebase as malformed. */
    data object InvalidPhoneNumber : AuthError

    /** Wrong OTP. The user can simply retype it. */
    data object InvalidOtp : AuthError

    /**
     * The verification session expired before the code was submitted.
     *
     * Recovery is different from [InvalidOtp]: the `verificationId` is dead, so
     * the UI has to go back and request a fresh SMS rather than let the user
     * retype.
     */
    data object OtpExpired : AuthError

    /**
     * Firebase's anti-abuse quota tripped - too many SMS to this number or from
     * this device/project.
     *
     * Also what an unfunded project hits once the free daily SMS allowance is
     * gone, so during development this usually means "use a test number", not
     * "you have a bug".
     */
    data object TooManyRequests : AuthError

    /** The Firebase project has Phone (or Google) sign-in switched off. */
    data object ProviderDisabled : AuthError

    /** The account exists but an administrator disabled it. */
    data object AccountDisabled : AuthError

    /**
     * The credential is already attached to a *different* account.
     *
     * Reached when linking rather than signing in - e.g. adding a phone number
     * to a Google account when that number already belongs to someone else.
     */
    data object CredentialAlreadyInUse : AuthError

    /**
     * Play Integrity / reCAPTCHA could not vouch for the app.
     *
     * In practice on a fresh project this is almost always a missing SHA-1 or
     * SHA-256 fingerprint in the Firebase console rather than anything in the
     * code, so it gets its own case to make that diagnosable from a log line.
     */
    data object AppVerificationFailed : AuthError

    /**
     * Anything unmapped.
     *
     * [cause] is kept so the crash reporters get a real stack trace; it is
     * never shown to the user.
     */
    data class Unknown(val cause: Throwable? = null) : AuthError
}


