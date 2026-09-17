package com.example.fastorder.auth.ui

import android.app.Activity
import com.example.fastorder.auth.domain.AuthError
import com.example.fastorder.auth.domain.AuthProvider
import com.example.fastorder.auth.domain.AuthRepository
import com.example.fastorder.auth.domain.AuthResult
import com.example.fastorder.auth.domain.AuthUser
import com.example.fastorder.auth.domain.OtpChallenge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [SignInViewModel].
 *
 * These run on a plain JVM - no Robolectric, no emulator, no Firebase project -
 * which is the concrete payoff of putting [AuthRepository] between the ViewModel
 * and the SDK. Against `FirebaseAuth` directly, none of the cases below could be
 * exercised without a device and a real SMS.
 *
 * The `Activity` handed to the repository is never touched by the fake, so a
 * throwaway instance is enough; only the repository implementation cares.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SignInViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var repository: FakeAuthRepository

    @Before
    fun setUp() {
        // viewModelScope is hard-wired to Dispatchers.Main; swapping in a test
        // dispatcher is what makes its coroutines advanceable by hand rather
        // than something the test has to sleep and hope for.
        Dispatchers.setMain(dispatcher)
        repository = FakeAuthRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /**
     * Starts collecting [SignInViewModel.isSignedIn], the way the screen does.
     *
     * Not boilerplate - it is load-bearing. `isSignedIn` is shared with
     * [kotlinx.coroutines.flow.SharingStarted.WhileSubscribed], so with no
     * collector the upstream never runs and `.value` is stuck on its initial
     * `false` no matter what the repository reports. `collectAsStateWithLifecycle`
     * provides that subscription in the app; a test has to provide its own or it
     * would be asserting against a flow that was never started.
     *
     * `backgroundScope` so the never-ending collection is cancelled when the
     * test ends instead of hanging `runTest`.
     */
    private fun TestScope.collectSignedIn(viewModel: SignInViewModel) {
        backgroundScope.launch(dispatcher) { viewModel.isSignedIn.collect { } }
    }

    // ------------------------------------------------------------------
    // Input normalisation
    // ------------------------------------------------------------------

    @Test
    fun `phone input strips formatting so pasted numbers still validate`() {
        val viewModel = SignInViewModel(repository)

        // What a contacts app or a website typically yields on paste.
        viewModel.onNationalNumberChange("+91 98765 43210")

        // The pasted country code is dropped rather than eating two of the ten
        // digits and truncating the real ones off the end.
        assertEquals("9876543210", viewModel.uiState.value.nationalNumber)
        assertEquals("+91", viewModel.uiState.value.dialCode)
        assertEquals("+919876543210", viewModel.uiState.value.phoneE164)
        assertTrue(viewModel.uiState.value.canSubmitPhone)
    }

    @Test
    fun `phone input is capped at ten digits and gates the submit button`() {
        val viewModel = SignInViewModel(repository)

        viewModel.onNationalNumberChange("987654321")
        assertEquals(9, viewModel.uiState.value.nationalNumber.length)
        // Nine digits is not a number the app can deliver to, so there is
        // nothing to submit yet.
        assertFalse(viewModel.uiState.value.canSubmitPhone)

        viewModel.onNationalNumberChange("9876543210")
        assertTrue(viewModel.uiState.value.canSubmitPhone)

        // An eleventh keystroke is refused outright rather than accepted and
        // rejected later by Firebase.
        viewModel.onNationalNumberChange("98765432100")
        assertEquals("9876543210", viewModel.uiState.value.nationalNumber)
        assertEquals("+919876543210", viewModel.uiState.value.phoneE164)
    }

    @Test
    fun `otp input is capped at the code length`() {
        val viewModel = SignInViewModel(repository)

        viewModel.onOtpChange("1234567890")

        assertEquals("123456", viewModel.uiState.value.otp)
        assertTrue(viewModel.uiState.value.canSubmitOtp)
    }

    // ------------------------------------------------------------------
    // Phone flow
    // ------------------------------------------------------------------

    @Test
    fun `sending a code advances to the otp step and starts the resend cooldown`() = runTest {
        val viewModel = SignInViewModel(repository)
        viewModel.onPhoneMethodSelected()
        viewModel.onNationalNumberChange("9876543210")

        viewModel.sendOtp(Activity())
        // runCurrent, not advanceUntilIdle: the cooldown is a loop of 1-second
        // delays, and advancing virtual time to idle would run all thirty of
        // them, so the cooldown under test would already have expired.
        runCurrent()

        val state = viewModel.uiState.value
        assertEquals(SignInStep.EnterOtp, state.step)
        assertNull(state.inFlight)
        // Cooldown is running, so the user cannot immediately burn a second SMS.
        assertFalse(state.canResendOtp)
        assertEquals(SignInUiState.RESEND_COOLDOWN_SECONDS, state.resendCooldownSeconds)
    }

    @Test
    fun `resend becomes available once the cooldown elapses`() = runTest {
        val viewModel = SignInViewModel(repository)
        viewModel.onPhoneMethodSelected()
        viewModel.onNationalNumberChange("9876543210")
        viewModel.sendOtp(Activity())

        // Now let virtual time run the cooldown out in full.
        advanceUntilIdle()

        assertEquals(0, viewModel.uiState.value.resendCooldownSeconds)
        assertTrue(viewModel.uiState.value.canResendOtp)
    }

    /**
     * The case that motivated modelling [OtpChallenge.AutoVerified] separately:
     * Play services can verify the number with no SMS at all. Advancing to the
     * OTP pane there would leave the user waiting for a code that never arrives.
     */
    @Test
    fun `auto-verified phone never shows the otp step`() = runTest {
        repository.autoVerifyPhone = true
        val viewModel = SignInViewModel(repository)
        collectSignedIn(viewModel)
        viewModel.onPhoneMethodSelected()
        viewModel.onNationalNumberChange("9876543210")

        viewModel.sendOtp(Activity())
        advanceUntilIdle()

        // Stays on the number pane - crucially, never reaches EnterOtp.
        assertEquals(SignInStep.EnterPhone, viewModel.uiState.value.step)
        assertTrue(viewModel.isSignedIn.value)
    }

    /**
     * An expired verification cannot be rescued by retyping, because the
     * verificationId itself is dead. The user has to be sent back for a fresh
     * SMS rather than left guessing at a code that can never be accepted.
     */
    @Test
    fun `expired code sends the user back to the phone step`() = runTest {
        repository.verifyResult = AuthResult.Failure(AuthError.OtpExpired)
        val viewModel = SignInViewModel(repository)
        viewModel.onPhoneMethodSelected()
        viewModel.onNationalNumberChange("9876543210")
        viewModel.sendOtp(Activity())
        runCurrent()

        viewModel.onOtpChange("123456")
        viewModel.verifyOtp()
        runCurrent()

        val state = viewModel.uiState.value
        assertEquals(SignInStep.EnterPhone, state.step)
        assertEquals(AuthError.OtpExpired, state.error)
        assertEquals("", state.otp)
    }

    @Test
    fun `a wrong code keeps the user on the otp step`() = runTest {
        repository.verifyResult = AuthResult.Failure(AuthError.InvalidOtp)
        val viewModel = SignInViewModel(repository)
        viewModel.onPhoneMethodSelected()
        viewModel.onNationalNumberChange("9876543210")
        viewModel.sendOtp(Activity())
        runCurrent()

        viewModel.onOtpChange("000000")
        viewModel.verifyOtp()
        runCurrent()

        assertEquals(SignInStep.EnterOtp, viewModel.uiState.value.step)
        assertEquals(AuthError.InvalidOtp, viewModel.uiState.value.error)
    }

    // ------------------------------------------------------------------
    // Errors
    // ------------------------------------------------------------------

    /**
     * Dismissing the credential sheet is the user's own doing. Reporting
     * "sign-in failed" for it trains people to ignore error messages.
     */
    @Test
    fun `cancellation clears the spinner without showing an error`() = runTest {
        repository.googleResult = AuthResult.Failure(AuthError.Cancelled)
        val viewModel = SignInViewModel(repository)
        collectSignedIn(viewModel)

        viewModel.signInWithGoogle(Activity())
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.error)
        assertNull(viewModel.uiState.value.inFlight)
        assertFalse(viewModel.isSignedIn.value)
    }

    @Test
    fun `a real google failure is surfaced to the user`() = runTest {
        repository.googleResult = AuthResult.Failure(AuthError.NoCredentialAvailable)
        val viewModel = SignInViewModel(repository)

        viewModel.signInWithGoogle(Activity())
        advanceUntilIdle()

        assertEquals(AuthError.NoCredentialAvailable, viewModel.uiState.value.error)
        assertNull(viewModel.uiState.value.inFlight)
    }

    @Test
    fun `successful google sign-in reports signed in`() = runTest {
        val viewModel = SignInViewModel(repository)
        collectSignedIn(viewModel)

        viewModel.signInWithGoogle(Activity())
        advanceUntilIdle()

        assertTrue(viewModel.isSignedIn.value)
        assertNull(viewModel.uiState.value.error)
    }

    // ------------------------------------------------------------------
    // Step navigation
    // ------------------------------------------------------------------

    @Test
    fun `back walks the steps and then defers to the caller`() = runTest {
        val viewModel = SignInViewModel(repository)
        viewModel.onPhoneMethodSelected()
        viewModel.onNationalNumberChange("9876543210")
        viewModel.sendOtp(Activity())
        runCurrent()

        assertTrue(viewModel.onBack())
        assertEquals(SignInStep.EnterPhone, viewModel.uiState.value.step)

        assertTrue(viewModel.onBack())
        assertEquals(SignInStep.ChooseMethod, viewModel.uiState.value.step)

        // Nowhere left to go: false tells the screen to fall through to the
        // navigation back stack instead of swallowing the gesture.
        assertFalse(viewModel.onBack())
    }

    // ------------------------------------------------------------------
    // Auto-retrieval
    // ------------------------------------------------------------------

    @Test
    fun `auto-retrieved code fills the field and announces itself`() = runTest(dispatcher) {
        val viewModel = SignInViewModel(repository)

        // `events` is a hot SharedFlow with no replay - exactly so a rotation
        // cannot re-show the toast - so the subscriber has to exist before the
        // emission. awaiting first() from a coroutine started ahead of time is
        // what makes that ordering explicit rather than a lucky scheduling.
        val firstEvent = async(dispatcher) { viewModel.events.first() }
        advanceUntilIdle()

        // Play services reading the SMS, long after sendOtp returned CodeSent.
        repository.autoRetrievedCodes.tryEmit("123456")
        advanceUntilIdle()

        assertEquals("123456", viewModel.uiState.value.otp)
        // Pinned to the code pane: a field that fills itself has to be somewhere
        // the user can actually see it happen.
        assertEquals(SignInStep.EnterOtp, viewModel.uiState.value.step)
        assertEquals(SignInEvent.OtpAutoFilled, firstEvent.await())
    }

    // ------------------------------------------------------------------
    // Stepping back out of the code pane
    // ------------------------------------------------------------------

    @Test
    fun `leaving the code pane abandons the verification behind it`() = runTest(dispatcher) {
        val viewModel = SignInViewModel(repository)
        viewModel.onPhoneMethodSelected()
        viewModel.onNationalNumberChange("9876543210")
        viewModel.sendOtp(Activity())
        advanceUntilIdle()
        assertEquals(SignInStep.EnterOtp, viewModel.uiState.value.step)

        // "Wrong number?"
        assertTrue(viewModel.onBack())

        assertEquals(SignInStep.EnterPhone, viewModel.uiState.value.step)
        // Left in place, the verificationId kept pointing at a number the user
        // was in the middle of correcting - and the next send hung behind a
        // verification Firebase still considered live.
        assertEquals(1, repository.abandonVerificationCount)
        // And no spinner is left owned by the pane just left.
        assertNull(viewModel.uiState.value.inFlight)
    }

    @Test
    fun `resubmitting after correcting the number starts a fresh request`() = runTest(dispatcher) {
        val viewModel = SignInViewModel(repository)
        viewModel.onPhoneMethodSelected()
        viewModel.onNationalNumberChange("9876543210")
        viewModel.sendOtp(Activity())
        advanceUntilIdle()

        viewModel.onBack()
        viewModel.onNationalNumberChange("9876500000")
        assertTrue(viewModel.uiState.value.canSubmitPhone)

        viewModel.sendOtp(Activity())
        advanceUntilIdle()

        // Reaches the code pane again instead of spinning on a stale request.
        assertEquals(SignInStep.EnterOtp, viewModel.uiState.value.step)
        assertNull(viewModel.uiState.value.inFlight)
        assertNull(viewModel.uiState.value.error)
    }
}

/**
 * In-memory [AuthRepository] whose outcomes the test sets up front.
 *
 * Hand-written rather than mocked: the interface is small, and a fake keeps the
 * auth-state flow behaving like the real thing - sign-in pushes a user into
 * [currentUser], which is exactly the behaviour the ViewModel depends on.
 */
private class FakeAuthRepository : AuthRepository {

    var googleResult: AuthResult<AuthUser> = AuthResult.Success(TEST_USER)
    var verifyResult: AuthResult<AuthUser> = AuthResult.Success(TEST_USER)
    var autoVerifyPhone: Boolean = false

    private val _currentUser = MutableStateFlow<AuthUser?>(null)
    override val currentUser: StateFlow<AuthUser?> = _currentUser

    /** Exposed so a test can push a code as though Play services had read it. */
    val autoRetrievedCodes = MutableSharedFlow<String>(extraBufferCapacity = 1)
    override val autoRetrievedCode: SharedFlow<String> = autoRetrievedCodes

    var abandonVerificationCount = 0
        private set

    override fun abandonVerification() {
        abandonVerificationCount++
    }

    override suspend fun signInWithGoogle(activity: Activity): AuthResult<AuthUser> =
        googleResult.also { if (it is AuthResult.Success) _currentUser.value = it.value }

    override suspend fun sendOtp(
        activity: Activity,
        phoneE164: String,
    ): AuthResult<OtpChallenge> = if (autoVerifyPhone) {
        _currentUser.value = TEST_USER
        AuthResult.Success(OtpChallenge.AutoVerified(TEST_USER))
    } else {
        AuthResult.Success(OtpChallenge.CodeSent(canResend = true))
    }

    override suspend fun resendOtp(activity: Activity): AuthResult<OtpChallenge> =
        AuthResult.Success(OtpChallenge.CodeSent(canResend = true))

    override suspend fun verifyOtp(code: String): AuthResult<AuthUser> =
        verifyResult.also { if (it is AuthResult.Success) _currentUser.value = it.value }

    override suspend fun signOut() {
        _currentUser.value = null
    }

    private companion object {
        val TEST_USER = AuthUser(
            uid = "test-uid",
            displayName = "Test User",
            email = "test@example.com",
            phoneNumber = null,
            photoUrl = null,
            isAnonymous = false,
            providers = setOf(AuthProvider.Google),
        )
    }
}






