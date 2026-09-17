package com.example.fastorder.auth.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.fastorder.R
import com.example.fastorder.auth.ui.components.BrandLockup
import com.example.fastorder.auth.ui.components.ChooseMethodPane
import com.example.fastorder.auth.ui.components.OtpPane
import com.example.fastorder.auth.ui.components.PhoneNumberPane
import com.example.fastorder.auth.ui.components.VSpace
import timber.log.Timber

/**
 * Authentication screen: Google sign-in and phone/OTP.
 *
 * A navigation *destination*, not an Activity, so it shares `MainActivity`'s
 * NavController - which is what lets a forced sign-out mid-session simply
 * navigate here.
 *
 * The three steps (method choice, number, code) are panes within this one
 * destination rather than three destinations. They share a single transient
 * draft and an in-flight verification session, neither of which survives
 * process death, so separate back-stack entries would promise a restorability
 * the flow cannot deliver.
 *
 * @param onSignedIn invoked once a session exists, however it was obtained.
 * @param viewModel defaulted to [hiltViewModel], which scopes it to this
 *   NavBackStackEntry rather than the Activity - so leaving the destination
 *   discards the half-typed number and the in-flight verification with it.
 */
@Composable
fun SignInScreen(
    onSignedIn: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SignInViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isSignedIn by viewModel.isSignedIn.collectAsStateWithLifecycle()

    // Credential Manager renders a system bottom sheet and Firebase may host a
    // reCAPTCHA WebView; both need the Activity, not an application Context.
    // Read here and passed down per call, so nothing retains the window.
    val activity = LocalActivity.current
    val context = LocalContext.current

    // The single exit. Every route to a session - Google, a typed code, phone
    // auto-verification landing after this screen moved on, or a session
    // restored underneath it - lands on the repository's auth state, so they
    // all leave through here rather than each call site navigating for itself.
    LaunchedEffect(isSignedIn) {
        if (isSignedIn) {
            // Deliberately carries no identifier: FastOrderApplication sets
            // Sentry's isSendDefaultPii = false, and putting a uid or phone
            // number in a log line would route straight around that.
            Timber.i("Auth: sign-in succeeded")
            onSignedIn()
        }
    }

    // A toast, not an inline message: the field filling itself is already the
    // primary feedback, and this only has to explain *why* it happened. Keyed
    // on Unit so the collector survives recomposition rather than restarting.
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                SignInEvent.OtpAutoFilled -> Toast.makeText(
                    context,
                    context.getString(R.string.sign_in_otp_auto_filled),
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }

    // Back steps within the flow (code -> number -> method choice) before
    // falling through to the navigation back stack. Without this, backing out
    // of the OTP pane would leave sign-in entirely, which is never what someone
    // correcting a typo in their number intends.
    BackHandler(enabled = uiState.step != SignInStep.ChooseMethod) {
        viewModel.onBack()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            // Keeps the focused field above the keyboard. The OTP and number
            // fields sit low enough to be covered otherwise.
            .imePadding()
            // Small screens in landscape cannot fit a pane plus an open IME;
            // scrolling is what stops the submit button becoming unreachable.
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // Outside the AnimatedContent on purpose: it stays anchored while the
        // panes slide underneath it, which is what makes the three steps read
        // as one branded screen rather than three unrelated forms.
        BrandLockup()

        VSpace(28)

        AnimatedContent(
            targetState = uiState.step,
            transitionSpec = {
                // Direction encodes progress: forward slides in from the right,
                // back from the left, matching the platform's spatial model so
                // the animation tells the user which way they just moved.
                val forward = targetState.ordinal > initialState.ordinal
                val offset = { width: Int -> if (forward) width else -width }

                (slideInHorizontally(initialOffsetX = offset) + fadeIn())
                    .togetherWith(
                        slideOutHorizontally(targetOffsetX = { -offset(it) }) + fadeOut(),
                    )
                    // Panes differ in height; without this the container snaps
                    // to the new size before the content has finished moving.
                    .using(SizeTransform(clip = false))
            },
            label = "signInStep",
        ) { step ->
            // Caps line length on tablets and foldables. Full-width form fields
            // on a 10" screen are unpleasant to read and to aim at.
            val paneModifier = Modifier.widthIn(max = MAX_PANE_WIDTH_DP.dp)

            when (step) {
                SignInStep.ChooseMethod -> ChooseMethodPane(
                    inFlight = uiState.inFlight,
                    error = uiState.error,
                    onGoogleClick = { activity?.let(viewModel::signInWithGoogle) },
                    onPhoneClick = viewModel::onPhoneMethodSelected,
                    modifier = paneModifier,
                )

                SignInStep.EnterPhone -> PhoneNumberPane(
                    dialCode = uiState.dialCode,
                    nationalNumber = uiState.nationalNumber,
                    inFlight = uiState.inFlight,
                    error = uiState.error,
                    canSubmit = uiState.canSubmitPhone,
                    onNationalNumberChange = viewModel::onNationalNumberChange,
                    onSubmit = { activity?.let(viewModel::sendOtp) },
                    onBack = { viewModel.onBack() },
                    modifier = paneModifier,
                )

                SignInStep.EnterOtp -> OtpPane(
                    phoneE164 = uiState.phoneE164,
                    otp = uiState.otp,
                    inFlight = uiState.inFlight,
                    error = uiState.error,
                    canSubmit = uiState.canSubmitOtp,
                    canResend = uiState.canResendOtp,
                    resendCooldownSeconds = uiState.resendCooldownSeconds,
                    onOtpChange = viewModel::onOtpChange,
                    onSubmit = viewModel::verifyOtp,
                    onResend = { activity?.let(viewModel::resendOtp) },
                    onChangeNumber = { viewModel.onBack() },
                    modifier = paneModifier,
                )
            }
        }
    }
}

private const val MAX_PANE_WIDTH_DP = 420





