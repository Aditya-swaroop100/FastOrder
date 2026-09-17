package com.example.fastorder.auth.data

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.credentials.CredentialManager
import com.example.fastorder.BuildConfig
import com.example.fastorder.MainActivity
import com.example.fastorder.R
import com.example.fastorder.auth.domain.AuthError
import com.example.fastorder.auth.domain.AuthResult
import com.example.fastorder.auth.domain.OtpChallenge
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import android.app.Activity

/**
 * Integration tests for [FirebaseAuthRepository] against a real Firebase project.
 *
 * ### Why these cannot be unit tests
 *
 * `SignInViewModelTest` covers the ViewModel against a hand-written fake, and
 * that fake answers every `sendOtp` with `CodeSent`. It therefore passed
 * happily through a bug where the *second* send for a number returned nothing
 * at all - because the bug does not live in the ViewModel. It lives in the one
 * place a fake cannot model: what the Firebase SDK does when asked the same
 * question twice.
 *
 * ### Why real time, not a TestDispatcher
 *
 * The failure mode is "no callback ever arrives". Virtual time would either
 * skip straight past the wait or fast-forward the 60s backstop, and in both
 * cases a hang would look like a pass. These deliberately measure the wall
 * clock.
 *
 * ### Requirements
 *
 * Runs against the numbers registered under Authentication -> Sign-in method ->
 * Phone -> "Phone numbers for testing". No SMS is sent and no quota is spent.
 * Skipped (not failed) when the build has app verification enabled, since real
 * verification cannot complete unattended.
 */
@RunWith(AndroidJUnit4::class)
class FirebaseAuthRepositoryTest {

    private lateinit var scenario: ActivityScenario<MainActivity>
    private lateinit var scope: CoroutineScope
    private lateinit var repository: FirebaseAuthRepository

    @Before
    fun setUp() {
        // Real app verification puts a reCAPTCHA in front of the request, which
        // nothing here can answer. Skip rather than fail: the build is fine,
        // it just is not the build these tests can drive.
        assumeTrue(
            "Needs DISABLE_PHONE_APP_VERIFICATION (the debug default)",
            BuildConfig.DISABLE_PHONE_APP_VERIFICATION,
        )

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val firebaseAuth = FirebaseAuth.getInstance().apply {
            firebaseAuthSettings.setAppVerificationDisabledForTesting(true)
        }
        // Start from no session, so a previous test's sign-in cannot make a
        // later assertion pass for the wrong reason.
        firebaseAuth.signOut()

        scope = CoroutineScope(SupervisorJob())
        repository = FirebaseAuthRepository(
            firebaseAuth = firebaseAuth,
            googleCredentialClient = GoogleCredentialClient(
                credentialManager = CredentialManager.create(context),
                serverClientId = context.getString(R.string.default_web_client_id),
            ),
            externalScope = scope,
        )
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    @After
    fun tearDown() {
        scope.cancel()
        if (::scenario.isInitialized) scenario.close()
    }

    /** Firebase needs a real window to host its reCAPTCHA fallback in. */
    private fun activity(): Activity {
        var activity: Activity? = null
        scenario.onActivity { activity = it }
        return checkNotNull(activity) { "MainActivity was not available" }
    }

    /**
     * The regression this file exists for.
     *
     * Tapping "Wrong number?" and then putting the same number back is an
     * entirely ordinary thing to do - check the digits, decide they were right
     * after all, re-submit. Firebase ignores that second request unless it is
     * told the two belong together, and ignores it *silently*: no callback, no
     * error. The user sat on a spinner until the 60s backstop fired and then
     * got told to check their network connection.
     */
    @Test
    fun resendingTheSameNumberAfterAbandoningIsAnswered() = runBlocking {
        val first = sendWithin(SEND_BUDGET_MS) { repository.sendOtp(activity(), TEST_NUMBER) }
        assertTrue("first send: $first", first is AuthResult.Success)

        // "Wrong number?"
        repository.abandonVerification()

        // ...and then the same number again. Must be answered, not swallowed.
        val second = sendWithin(SEND_BUDGET_MS) { repository.sendOtp(activity(), TEST_NUMBER) }
        assertNotNull(
            "Second send for the same number was never answered - the resend " +
                "token was not replayed, so Firebase dropped the request.",
            second,
        )
        assertTrue("second send: $second", second is AuthResult.Success)
        assertTrue(
            "expected a code to be sent, got $second",
            (second as AuthResult.Success).value is OtpChallenge.CodeSent,
        )
    }

    /**
     * Retaining the resend token must not quietly retain the right to submit a
     * code against the session it came from - that was the point of abandoning.
     */
    @Test
    fun abandoningStillRefusesACodeTypedAfterwards() = runBlocking {
        sendWithin(SEND_BUDGET_MS) { repository.sendOtp(activity(), TEST_NUMBER) }

        repository.abandonVerification()

        val result = repository.verifyOtp(TEST_CODE)
        assertEquals(AuthResult.Failure(AuthError.OtpExpired), result)
    }

    /** A different number was never affected; this pins that it stays that way. */
    @Test
    fun switchingToADifferentNumberIsAnswered() = runBlocking {
        sendWithin(SEND_BUDGET_MS) { repository.sendOtp(activity(), TEST_NUMBER) }
        repository.abandonVerification()

        val other = sendWithin(SEND_BUDGET_MS) {
            repository.sendOtp(activity(), OTHER_TEST_NUMBER)
        }
        assertTrue("send for a different number: $other", other is AuthResult.Success)
    }

    /**
     * Going back to a number already tried earlier in the same sitting: try one
     * number, try another, then decide the first was right after all.
     *
     * This passes with a single retained token, which is worth stating plainly
     * because it looks like it should not. It was written expecting a failure -
     * if the SDK suppressed repeats per number, the second number's token would
     * have displaced the first's and this would hang exactly like
     * [resendingTheSameNumberAfterAbandoningIsAnswered] did. It does not, which
     * is the evidence that suppression is compared only against the most recent
     * verification, and therefore that one slot is the right size. A per-number
     * map was written, measured against this, and deleted.
     *
     * Kept as a guard on that assumption rather than on our own code: if a
     * future SDK version starts tracking numbers individually, this is what
     * notices.
     */
    @Test
    fun returningToAnEarlierNumberIsAnswered() = runBlocking {
        sendWithin(SEND_BUDGET_MS) { repository.sendOtp(activity(), TEST_NUMBER) }
        repository.abandonVerification()

        sendWithin(SEND_BUDGET_MS) { repository.sendOtp(activity(), OTHER_TEST_NUMBER) }
        repository.abandonVerification()

        val backToFirst = sendWithin(SEND_BUDGET_MS) {
            repository.sendOtp(activity(), TEST_NUMBER)
        }
        assertNotNull(
            "Returning to the first number was never answered - its resend " +
                "token was displaced by the second number's.",
            backToFirst,
        )
        assertTrue("send back to the first number: $backToFirst", backToFirst is AuthResult.Success)
    }

    /**
     * Returns null when [block] outlives [budgetMs], which is what a swallowed
     * request looks like from here. Kept well under the repository's own 60s
     * backstop so a hang fails the test promptly rather than being converted
     * into a tidy-looking error result.
     */
    private suspend fun <T> sendWithin(budgetMs: Long, block: suspend () -> T): T? =
        withTimeoutOrNull(budgetMs) { block() }

    private companion object {
        /**
         * Registered as test numbers in the Firebase console. Fictional, and
         * safe to commit: they send no SMS and are meaningless outside this
         * project's auth config.
         */
        const val TEST_NUMBER = "+919999999999"
        const val OTHER_TEST_NUMBER = "+919887889429"
        const val TEST_CODE = "111111"

        /**
         * Generous next to the ~1.5s a send actually takes here, so a slow CI
         * emulator does not fail the build - but far below the 60s backstop, so
         * the hang under test cannot masquerade as a returned error.
         */
        const val SEND_BUDGET_MS = 20_000L
    }
}


