package com.example.fastorder.auth.ui

import com.example.fastorder.auth.domain.AuthError

/**
 * Which pane of the sign-in flow is showing.
 *
 * An enum rather than three navigation destinations. These steps share one
 * transient draft (the number being typed, the in-flight verification) and are
 * meaningless to deep-link into or restore individually - a back-stack entry
 * for "the OTP screen" is a promise the app cannot keep once the verification
 * session behind it has expired.
 */
enum class SignInStep {
    /** Provider choice: Google, or continue with a phone number. */
    ChooseMethod,

    /** Phone number entry. */
    EnterPhone,

    /** 6-digit code entry for the number from the previous step. */
    EnterOtp,
}

/**
 * The single long-running operation the screen may be waiting on.
 *
 * One nullable field instead of several booleans, because the states are
 * mutually exclusive and independent flags make illegal combinations
 * representable - `isSendingOtp && isVerifyingOtp` has no meaning but would
 * compile. Knowing *which* operation is running also lets the spinner sit on
 * the button that started it rather than blanking the screen.
 */
enum class AuthOperation {
    GoogleSignIn,
    SendingOtp,
    ResendingOtp,
    VerifyingOtp,
}

/**
 * A one-shot thing that happened, as opposed to a state the screen is in.
 *
 * Kept out of [SignInUiState] on purpose: state is re-read on every
 * recomposition, so a "show a toast" flag held there would fire again on the
 * next rotation, and would need clearing afterwards to avoid firing twice.
 */
enum class SignInEvent {

    /**
     * Play services read the SMS and the code field filled itself.
     *
     * Worth announcing. A field that fills on its own looks like a glitch
     * unless the app says it meant to do that.
     */
    OtpAutoFilled,
}

/**
 * Everything the sign-in screen renders from.
 *
 * The phone number is held split - [dialCode] and [nationalNumber] - because
 * that is how it is typed. Storing the joined E.164 string would mean parsing
 * it back apart on every keystroke to decide which field the cursor belongs to.
 * [phoneE164] rejoins it on demand.
 */
data class SignInUiState(
    val step: SignInStep = SignInStep.ChooseMethod,
    /** Fixed at [DEFAULT_DIAL_CODE]; India-only, so there is nothing to pick. */
    val dialCode: String = DEFAULT_DIAL_CODE,
    val nationalNumber: String = "",
    val otp: String = "",
    val inFlight: AuthOperation? = null,
    val error: AuthError? = null,
    /** Seconds until "Resend code" becomes tappable again; 0 when available. */
    val resendCooldownSeconds: Int = 0,
) {

    /** The number in the only format Firebase accepts. */
    val phoneE164: String get() = "$dialCode$nationalNumber"

    /** True while any operation is running - used to disable competing inputs. */
    val isBusy: Boolean get() = inFlight != null

    /**
     * Local plausibility check, mirroring the repository's.
     *
     * Duplicated deliberately: this one greys out the button so the user gets
     * feedback while typing; the repository's is the real guard, because a
     * ViewModel is not a trustworthy place to enforce anything.
     */
    val canSubmitPhone: Boolean
        get() = !isBusy && nationalNumber.length == NATIONAL_NUMBER_LENGTH

    val canSubmitOtp: Boolean get() = !isBusy && otp.length == OTP_LENGTH

    val canResendOtp: Boolean get() = !isBusy && resendCooldownSeconds == 0

    companion object {
        /** Codes are 6 digits for Firebase phone auth. */
        const val OTP_LENGTH = 6

        /**
         * The only dial code the app serves.
         *
         * Rendered as a non-editable prefix on the number field rather than an
         * input, so the `+` cannot be forgotten - Firebase rejects a number
         * without it, and does so silently.
         */
        const val DEFAULT_DIAL_CODE = "+91"

        /**
         * How long "Resend code" stays disabled.
         *
         * Client-side politeness on top of Firebase's server-side throttle. The
         * server's answer to an impatient user is [AuthError.TooManyRequests],
         * which locks the number out for far longer than 30s - so the cheapest
         * fix is to not let them get there.
         */
        const val RESEND_COOLDOWN_SECONDS = 30

        /**
         * Indian mobile numbers are exactly ten digits after the country code.
         *
         * An exact length, not a range, because FastOrder delivers in India and
         * nowhere else - so "somewhere between 6 and 14 digits" is a rule that
         * accepts numbers the app can never deliver to. It is also what lets the
         * field cap input and the submit button stay hidden until the number is
         * actually complete, instead of letting the user submit and be told no.
         *
         * The repository's own check stays generic E.164: it guards "is this
         * dialable at all", which is a different question from "does this app
         * serve it". Market rules belong here, next to the UI that explains them.
         */
        const val NATIONAL_NUMBER_LENGTH = 10
    }
}

