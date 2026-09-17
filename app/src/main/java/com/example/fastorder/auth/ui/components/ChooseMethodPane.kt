package com.example.fastorder.auth.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.fastorder.R
import com.example.fastorder.auth.domain.AuthError
import com.example.fastorder.auth.ui.AuthOperation
import com.example.fastorder.ui.theme.ButtonShape

/**
 * First pane: pick a sign-in method.
 *
 * Google is the primary offer and phone is the fallback, but the *visual*
 * hierarchy is inverted - Google is an outlined button, phone is filled. That
 * is deliberate. Google's branding guidelines constrain its button's colours
 * and shape, so it cannot be themed as the app's primary action; making the
 * phone button the filled one keeps a single obvious primary on screen instead
 * of two buttons competing.
 */
@Composable
internal fun ChooseMethodPane(
    inFlight: AuthOperation?,
    error: AuthError?,
    onGoogleClick: () -> Unit,
    onPhoneClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isBusy = inFlight != null
    val isGoogleLoading = inFlight == AuthOperation.GoogleSignIn

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.sign_in_title),
            // headlineMedium, not Small: this is the only headline on screen,
            // and at Small it competed with the brand lockup above rather than
            // reading as the thing to look at first.
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        VSpace(10)
        Text(
            text = stringResource(R.string.sign_in_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        VSpace(36)

        if (error != null) {
            AuthErrorBanner(error)
            VSpace(16)
        }

        OutlinedButton(
            onClick = onGoogleClick,
            // Disabled by *any* operation, not just this one. Two credential
            // sheets cannot be on screen at once, and letting the second tap
            // through would dismiss the first with a cancellation the user
            // never asked for.
            enabled = !isBusy,
            shape = ButtonShape,
            // A 1dp hairline in `outline` is nearly invisible against the
            // off-white surface. Stepping it up keeps the outlined button
            // looking like a control rather than a label.
            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outlineVariant),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = BUTTON_MIN_HEIGHT_DP.dp),
        ) {
            GoogleMark(isLoading = isGoogleLoading)
            HSpace(12)
            Text(
                text = stringResource(R.string.sign_in_with_google),
                style = MaterialTheme.typography.labelLarge,
            )
        }

        VSpace(20)
        LabelledDivider(stringResource(R.string.sign_in_divider))
        VSpace(20)

        Button(
            onClick = onPhoneClick,
            enabled = !isBusy,
            shape = ButtonShape,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = BUTTON_MIN_HEIGHT_DP.dp),
        ) {
            Text(
                text = stringResource(R.string.sign_in_with_phone),
                style = MaterialTheme.typography.labelLarge,
            )
        }

        VSpace(24)
        Text(
            text = stringResource(R.string.sign_in_legal),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Comfortably above the 48dp touch-target minimum. These are the only two
 * actions on the screen, so they can afford the space.
 */
private const val BUTTON_MIN_HEIGHT_DP = 56

