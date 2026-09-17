package com.example.fastorder.auth.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.fastorder.R
import com.example.fastorder.auth.domain.AuthError
import com.example.fastorder.auth.ui.AuthOperation
import com.example.fastorder.ui.theme.ButtonShape

/**
 * Third pane: OTP entry.
 *
 * A single wide field with letter-spaced digits rather than six separate boxes.
 * Six boxes look the part but each one is its own focus target, which breaks
 * paste, fights the SMS autofill suggestion, and behaves unpredictably with
 * backspace. One field gets all of that right for free.
 *
 * Note that on most devices the user may never see this pane at all: Play
 * services auto-retrieval reads the incoming SMS and completes verification
 * on its own, which the repository handles and surfaces through the auth state.
 */
@Composable
internal fun OtpPane(
    phoneE164: String,
    otp: String,
    inFlight: AuthOperation?,
    error: AuthError?,
    canSubmit: Boolean,
    canResend: Boolean,
    resendCooldownSeconds: Int,
    onOtpChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onResend: () -> Unit,
    onChangeNumber: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isBusy = inFlight != null
    val otpFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) { otpFocus.requestFocus() }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.sign_in_otp_title),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        VSpace(8)
        Text(
            text = stringResource(R.string.sign_in_otp_subtitle, phoneE164),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        VSpace(24)

        if (error != null) {
            AuthErrorBanner(error)
            VSpace(16)
        }

        OutlinedTextField(
            value = otp,
            onValueChange = onOtpChange,
            label = { Text(stringResource(R.string.sign_in_otp_label)) },
            singleLine = true,
            enabled = !isBusy,
            // NumberPassword, not Number: it guarantees a digits-only keypad on
            // every IME. Plain Number still offers a decimal point and locale
            // separators on some keyboards, which can only produce input this
            // field will strip anyway.
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.NumberPassword,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { if (canSubmit) onSubmit() }),
            textStyle = TextStyle(
                textAlign = TextAlign.Center,
                fontSize = 24.sp,
                // Spacing the digits out makes a 6-digit code scannable at a
                // glance, so the user can check what they typed against the SMS
                // without counting characters.
                letterSpacing = 8.sp,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(otpFocus),
        )

        VSpace(24)

        Button(
            onClick = onSubmit,
            enabled = canSubmit,
            shape = ButtonShape,
            colors = loadingButtonColors(inFlight == AuthOperation.VerifyingOtp),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = BUTTON_MIN_HEIGHT_DP.dp),
        ) {
            if (inFlight == AuthOperation.VerifyingOtp) {
                ButtonSpinner()
            } else {
                Text(
                    text = stringResource(R.string.sign_in_otp_verify),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }

        VSpace(8)

        TextButton(onClick = onResend, enabled = canResend) {
            Text(
                // The countdown replaces the label rather than sitting beside a
                // greyed-out button, so "why can't I tap this?" is answered
                // without the user having to work it out.
                text = if (resendCooldownSeconds > 0) {
                    stringResource(R.string.sign_in_otp_resend_in, resendCooldownSeconds)
                } else {
                    stringResource(R.string.sign_in_otp_resend)
                },
            )
        }

        TextButton(onClick = onChangeNumber, enabled = !isBusy) {
            Text(stringResource(R.string.sign_in_otp_wrong_number))
        }
    }
}

private const val BUTTON_MIN_HEIGHT_DP = 52

