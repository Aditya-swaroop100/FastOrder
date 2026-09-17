package com.example.fastorder.auth.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.fastorder.R
import com.example.fastorder.auth.domain.AuthError
import com.example.fastorder.auth.ui.AuthOperation
import com.example.fastorder.auth.ui.SignInUiState
import com.example.fastorder.ui.theme.ButtonShape

/**
 * Second pane: phone number entry.
 *
 * ### Why one field with a fixed `+91`
 *
 * This was two fields - an editable dial code and a national number - which is
 * the right shape for an app that ships in many countries. This one does not.
 * FastOrder delivers in India, so every number is `+91`, and an editable field
 * asking the user to confirm that is a question with one correct answer.
 *
 * Making it a non-editable `prefix` rather than a disabled field is deliberate:
 * a greyed-out input reads as "broken", while a prefix reads as part of the
 * number - which is what it is. The `+` also stops being something the user can
 * forget, and Firebase silently rejects a number without it.
 *
 * When a second country arrives this goes back to being a picker, and
 * [SignInUiState.dialCode] is still there to hold its value.
 */
@Composable
internal fun PhoneNumberPane(
    dialCode: String,
    nationalNumber: String,
    inFlight: AuthOperation?,
    error: AuthError?,
    canSubmit: Boolean,
    onNationalNumberChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isBusy = inFlight != null
    val numberFocus = remember { FocusRequester() }

    // Derived from length, not from `canSubmit`: `canSubmit` also goes false
    // while the request is in flight, and a button that vanished the moment it
    // was tapped would be worse than one that never appeared.
    val isNumberComplete = nationalNumber.length == SignInUiState.NATIONAL_NUMBER_LENGTH

    LaunchedEffect(Unit) { numberFocus.requestFocus() }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.sign_in_phone_title),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        VSpace(8)
        Text(
            text = stringResource(R.string.sign_in_phone_subtitle),
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
            value = nationalNumber,
            onValueChange = onNationalNumberChange,
            label = { Text(stringResource(R.string.sign_in_phone_number)) },
            prefix = {
                Text(
                    text = dialCode,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            // The count is the only rule, so showing progress against it is
            // more useful than an error after the fact. It also explains why
            // the button below has not appeared yet.
            supportingText = {
                Text(
                    stringResource(
                        R.string.sign_in_phone_digits,
                        nationalNumber.length,
                        SignInUiState.NATIONAL_NUMBER_LENGTH,
                    ),
                )
            },
            singleLine = true,
            enabled = !isBusy,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Phone,
                imeAction = ImeAction.Done,
            ),
            // Submitting from the keyboard is the natural gesture once the
            // number is complete; without this the user has to dismiss the
            // IME to reach the button hidden behind it.
            keyboardActions = KeyboardActions(onDone = { if (canSubmit) onSubmit() }),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(numberFocus),
        )

        // Absent, not disabled, until the number is complete. A greyed-out
        // button invites tapping to find out what is wrong; nothing at all,
        // next to a "4 / 10" counter, says what to do without being asked.
        AnimatedVisibility(
            visible = isNumberComplete,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                VSpace(16)
                Button(
                    onClick = onSubmit,
                    enabled = canSubmit,
                    shape = ButtonShape,
                    colors = loadingButtonColors(inFlight == AuthOperation.SendingOtp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = BUTTON_MIN_HEIGHT_DP.dp),
                ) {
                    if (inFlight == AuthOperation.SendingOtp) {
                        ButtonSpinner()
                    } else {
                        Text(
                            text = stringResource(R.string.sign_in_phone_send_code),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }
        }

        VSpace(8)

        TextButton(onClick = onBack, enabled = !isBusy) {
            Text(stringResource(R.string.sign_in_back))
        }
    }
}

private const val BUTTON_MIN_HEIGHT_DP = 56

