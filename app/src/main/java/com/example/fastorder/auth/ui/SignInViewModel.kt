package com.example.fastorder.auth.ui

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fastorder.auth.domain.AuthError
import com.example.fastorder.auth.domain.AuthRepository
import com.example.fastorder.auth.domain.AuthResult
import com.example.fastorder.auth.domain.OtpChallenge
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * Drops a country code or trunk prefix the user typed in front of the number.
 *
 * `919876543210` and `09876543210` are both what someone reaches for out of
 * habit. Without this the leading digits would be taken as part of the ten, and
 * the real last digits would be silently truncated away - producing a valid
 * looking number belonging to nobody.
 */
private fun String.removeIndianPrefixes(): String = when {
    length > SignInUiState.NATIONAL_NUMBER_LENGTH && startsWith("91") -> drop(2)
    length > SignInUiState.NATIONAL_NUMBER_LENGTH && startsWith("0") -> drop(1)
    else -> this
}

/**
 * Drives the sign-in screen.
 *
 * Talks only to [AuthRepository], so it never sees a Firebase type. That is
 * what makes it unit-testable with a fake repository and no Robolectric, and
 * what would let the whole `auth` package move into its own Gradle module
 * untouched.
 */
@HiltViewModel
class SignInViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SignInUiState())
    val uiState: StateFlow<SignInUiState> = _uiState.asStateFlow()

    /**
     * Whether a session now exists.
     *
     * Derived from the repository's auth state rather than set by the sign-in
     * calls, which is what makes the screen handle the awkward paths for free:
     * phone auto-verification that completes after the OTP pane is already up,
     * and a session restored while the screen happens to be open. Both mean
     * "you are signed in" arrives with no call awaiting a return value.
     */
    val isSignedIn: StateFlow<Boolean> = authRepository.currentUser
        .map { it != null }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS),
            initialValue = authRepository.currentUser.value != null,
        )

    private val _events = MutableSharedFlow<SignInEvent>(extraBufferCapacity = 1)

    /** One-shot things for the screen to react to. See [SignInEvent]. */
    val events: SharedFlow<SignInEvent> = _events.asSharedFlow()

    /** Cancelled and restarted whenever a new code is sent. */
    private var resendCooldownJob: Job? = null

    /**
     * The send/resend/verify call currently in flight.
     *
     * Held so stepping backwards can cancel it. Without that, backing out of
     * the code pane left a request running that still owned the spinner, and
     * re-submitting the number appeared to hang forever.
     */
    private var requestJob: Job? = null

    init {
        // Auto-retrieval lands with nobody awaiting it - `sendOtp` returned
        // `CodeSent` long ago. Filling the field here is what turns the wait
        // for the SMS into something the user can watch succeed, instead of a
        // screen that sits still and then suddenly navigates.
        viewModelScope.launch {
            authRepository.autoRetrievedCode.collect { code ->
                Timber.i("Auth: SMS code auto-retrieved")
                _uiState.update {
                    it.copy(
                        step = SignInStep.EnterOtp,
                        otp = code.take(SignInUiState.OTP_LENGTH),
                        error = null,
                    )
                }
                _events.emit(SignInEvent.OtpAutoFilled)
            }
        }
    }

    // ------------------------------------------------------------------
    // Input
    // ------------------------------------------------------------------

    fun onNationalNumberChange(value: String) {
        // Two jobs. Stripping non-digits means a number pasted from a contacts
        // app in any national format - "+91 98765 43210", "(98765) 43210" -
        // still reaches Firebase as E.164. Dropping a leading "91" or "0"
        // handles the two things people habitually type in front of an Indian
        // mobile number, which would otherwise silently push a real digit past
        // the cap below.
        val digits = value.filter(Char::isDigit).removeIndianPrefixes()

        // Truncating rather than rejecting is what makes the field refuse an
        // eleventh digit outright: the keystroke lands, the state does not
        // change, and the cursor stays put.
        _uiState.update {
            it.copy(
                nationalNumber = digits.take(SignInUiState.NATIONAL_NUMBER_LENGTH),
                error = null,
            )
        }
    }

    fun onOtpChange(value: String) {
        val digits = value.filter(Char::isDigit).take(SignInUiState.OTP_LENGTH)
        _uiState.update { it.copy(otp = digits, error = null) }
    }

    /** Clears a shown error without changing step - for a "dismiss" affordance. */
    fun onErrorDismissed() {
        _uiState.update { it.copy(error = null) }
    }

    // ------------------------------------------------------------------
    // Navigation between steps
    // ------------------------------------------------------------------

    fun onPhoneMethodSelected() {
        _uiState.update { it.copy(step = SignInStep.EnterPhone, error = null) }
    }

    /**
     * Steps backwards, returning false when there is nowhere left to go.
     *
     * The screen forwards the system back gesture here so that backing out of
     * the OTP pane returns to the number - the user's actual intent - instead
     * of leaving the sign-in flow entirely. The `false` return lets the caller
     * fall through to normal back handling at the first step.
     */
    fun onBack(): Boolean {
        val current = _uiState.value
        val previous = when (current.step) {
            SignInStep.ChooseMethod -> return false
            SignInStep.EnterPhone -> SignInStep.ChooseMethod
            SignInStep.EnterOtp -> SignInStep.EnterPhone
        }

        // Abandon anything still running. A request that outlives the step
        // that started it keeps the spinner alive on a pane the user has left,
        // and the repository's verificationId keeps pointing at a number they
        // are in the middle of correcting - which is what made "wrong number"
        // followed by a re-submit spin forever.
        requestJob?.cancel()
        requestJob = null
        if (current.step == SignInStep.EnterOtp) {
            authRepository.abandonVerification()
        }

        // The code is dropped on the way back: the verification it belonged to
        // is abandoned, so keeping the digits on screen would invite the user to
        // submit them against a session that no longer accepts them.
        _uiState.update {
            it.copy(step = previous, otp = "", error = null, inFlight = null)
        }
        return true
    }

    // ------------------------------------------------------------------
    // Google
    // ------------------------------------------------------------------

    /**
     * @param activity required by Credential Manager to host its bottom sheet.
     *   Passed per call rather than held, so the ViewModel never outlives a
     *   window reference. See [AuthRepository] for the full rationale.
     */
    fun signInWithGoogle(activity: Activity) {
        if (_uiState.value.isBusy) return

        viewModelScope.launch {
            _uiState.update { it.copy(inFlight = AuthOperation.GoogleSignIn, error = null) }

            when (val result = authRepository.signInWithGoogle(activity)) {
                is AuthResult.Success -> {
                    // No navigation here. `isSignedIn` fires from the repository's
                    // auth state, giving one exit path shared with every other
                    // way a session can appear.
                    Timber.i("Auth: Google sign-in succeeded")
                    _uiState.update { it.copy(inFlight = null) }
                }

                is AuthResult.Failure -> showError(result.error)
            }
        }
    }

    // ------------------------------------------------------------------
    // Phone / OTP
    // ------------------------------------------------------------------

    fun sendOtp(activity: Activity) {
        val state = _uiState.value
        if (!state.canSubmitPhone) return

        requestCode(
            operation = AuthOperation.SendingOtp,
            request = { authRepository.sendOtp(activity, state.phoneE164) },
        )
    }

    fun resendOtp(activity: Activity) {
        if (!_uiState.value.canResendOtp) return

        requestCode(
            operation = AuthOperation.ResendingOtp,
            request = { authRepository.resendOtp(activity) },
        )
    }

    fun verifyOtp() {
        val state = _uiState.value
        if (!state.canSubmitOtp) return

        viewModelScope.launch {
            _uiState.update { it.copy(inFlight = AuthOperation.VerifyingOtp, error = null) }

            when (val result = authRepository.verifyOtp(state.otp)) {
                is AuthResult.Success -> _uiState.update { it.copy(inFlight = null) }

                is AuthResult.Failure -> {
                    // An expired session cannot be recovered by retyping, so send
                    // the user back to the number rather than leaving them
                    // guessing at a code that can never be accepted.
                    if (result.error is AuthError.OtpExpired) {
                        _uiState.update {
                            it.copy(
                                step = SignInStep.EnterPhone,
                                otp = "",
                                inFlight = null,
                                error = result.error,
                            )
                        }
                    } else {
                        showError(result.error)
                    }
                }
            }
        }
    }

    /** Shared body of [sendOtp] and [resendOtp] - they differ only in the call. */
    private fun requestCode(
        operation: AuthOperation,
        request: suspend () -> AuthResult<OtpChallenge>,
    ) {
        requestJob?.cancel()
        requestJob = viewModelScope.launch {
            _uiState.update { it.copy(inFlight = operation, error = null) }

            when (val result = request()) {
                is AuthResult.Failure -> showError(result.error)

                is AuthResult.Success -> when (result.value) {
                    is OtpChallenge.CodeSent -> {
                        _uiState.update {
                            it.copy(step = SignInStep.EnterOtp, otp = "", inFlight = null)
                        }
                        startResendCooldown()
                    }

                    // Play services verified the number with no SMS. Stay put
                    // and let `isSignedIn` navigate: advancing to an OTP pane
                    // for a code that will never arrive is the classic bug this
                    // branch exists to avoid.
                    is OtpChallenge.AutoVerified -> {
                        Timber.i("Auth: phone verified without code entry")
                        _uiState.update { it.copy(inFlight = null) }
                    }
                }
            }
        }
    }

    private fun startResendCooldown() {
        resendCooldownJob?.cancel()
        resendCooldownJob = viewModelScope.launch {
            var remaining = SignInUiState.RESEND_COOLDOWN_SECONDS
            while (remaining > 0) {
                _uiState.update { it.copy(resendCooldownSeconds = remaining) }
                delay(ONE_SECOND_MS)
                remaining--
            }
            _uiState.update { it.copy(resendCooldownSeconds = 0) }
        }
    }

    private fun showError(error: AuthError) {
        // A dismissal is the user's own doing. Surfacing "sign-in failed"
        // because they tapped outside the sheet trains people to ignore error
        // messages, so it only clears the spinner.
        val visibleError = error.takeUnless { it is AuthError.Cancelled }
        _uiState.update { it.copy(inFlight = null, error = visibleError) }
    }

    private companion object {

        private const val ONE_SECOND_MS = 1_000L

        /**
         * Keeps the auth-state subscription alive briefly across configuration
         * changes, so a rotation mid-sign-in does not drop and re-create it.
         */
        private const val SUBSCRIPTION_TIMEOUT_MS = 5_000L
    }
}



