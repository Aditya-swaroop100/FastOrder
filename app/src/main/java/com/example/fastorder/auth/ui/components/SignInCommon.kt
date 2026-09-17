package com.example.fastorder.auth.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.example.fastorder.R
import com.example.fastorder.auth.domain.AuthError
import com.example.fastorder.auth.ui.messageRes

/**
 * The brand mark, shown above every pane in the sign-in flow.
 *
 * Reuses `ic_splash_logo` on a `tertiaryContainer` tile - which is the brand
 * amber - so the user arrives from the launch animation into a screen wearing
 * the same two colours rather than a generic form. Keeping it *outside* the
 * animated pane container means it stays put while the panes slide underneath,
 * which is what makes the three steps read as one screen.
 *
 * Marked decorative: the tagline beneath it already carries the meaning, and a
 * screen reader announcing "FastOrder logo" before it would be pure repetition.
 */
@Composable
internal fun BrandLockup(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.clearAndSetSemantics { },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            color = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            shape = MaterialTheme.shapes.medium,
        ) {
            Box(
                modifier = Modifier.size(LOCKUP_TILE_DP.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_splash_logo),
                    contentDescription = null,
                    modifier = Modifier.size(LOCKUP_ICON_DP.dp),
                )
            }
        }
        HSpace(12)
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * Inline error message.
 *
 * A banner in the layout rather than a `Snackbar`: these errors are tied to the
 * field or button directly above them, and a snackbar floating at the bottom of
 * the screen breaks that association - especially on the OTP step, where the
 * message ("that code isn't right") only means anything next to the input it
 * refers to. It also stays put instead of timing out while the user is still
 * reading.
 *
 * `errorContainer`/`onErrorContainer` rather than raw red, so it inherits
 * contrast handling from the theme in both light and dark mode.
 */
@Composable
internal fun AuthErrorBanner(
    error: AuthError,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = MaterialTheme.shapes.medium,
    ) {
        Text(
            text = stringResource(error.messageRes()),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
    }
}

/**
 * The Google "G" mark, or a spinner while a sign-in is in flight.
 *
 * Swapping in place keeps the button's width stable, so the row does not jump
 * when tapped.
 *
 * `tint = Color.Unspecified` is essential: `Icon` tints its painter with the
 * current content colour by default, which would flatten the four-colour Google
 * mark into a single-colour silhouette and break Google's branding rules.
 */
@Composable
internal fun GoogleMark(
    isLoading: Boolean,
    modifier: Modifier = Modifier,
) {
    if (isLoading) {
        CircularProgressIndicator(
            modifier = modifier.size(20.dp),
            strokeWidth = 2.dp,
        )
    } else {
        Icon(
            painter = painterResource(R.drawable.ic_google_logo),
            // Decorative: the button's own label already says "Continue with
            // Google", so announcing the icon too would just repeat it.
            contentDescription = null,
            tint = Color.Unspecified,
            modifier = modifier.size(20.dp),
        )
    }
}

/** A horizontal rule with a centred label, separating the two sign-in options. */
@Composable
internal fun LabelledDivider(
    label: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.outlineVariant,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.outlineVariant,
        )
    }
}

/**
 * Spinner sized to sit inside a filled button without changing its height.
 *
 * `onPrimary` because it draws on the button's primary-coloured surface; the
 * default `primary` tint would render it invisible there.
 */
@Composable
internal fun ButtonSpinner(modifier: Modifier = Modifier) {
    CircularProgressIndicator(
        modifier = modifier.size(18.dp),
        strokeWidth = 2.dp,
        color = MaterialTheme.colorScheme.onPrimary,
    )
}


/**
 * Button colours that keep the brand fill while an operation runs.
 *
 * Material disables a button to block repeat taps, and a disabled button is
 * painted `onSurface` at 12% - a pale grey. The spinner inside it draws in
 * `onPrimary`, which on that grey is very nearly invisible: the button looked
 * like it had simply stopped responding.
 *
 * Overriding only the *disabled* colours keeps the tap actually blocked while
 * the control still reads as the app's primary action, so the spinner has the
 * contrast it was designed for.
 */
@Composable
internal fun loadingButtonColors(isLoading: Boolean): ButtonColors =
    if (isLoading) {
        ButtonDefaults.buttonColors(
            disabledContainerColor = MaterialTheme.colorScheme.primary,
            disabledContentColor = MaterialTheme.colorScheme.onPrimary,
        )
    } else {
        ButtonDefaults.buttonColors()
    }

/** Fixed vertical gap. Named so the call sites read as layout, not arithmetic. */
@Composable
internal fun VSpace(height: Int) = Spacer(Modifier.height(height.dp))

/** Fixed horizontal gap. */
@Composable
internal fun HSpace(width: Int) = Spacer(Modifier.width(width.dp))

private const val LOCKUP_TILE_DP = 48
private const val LOCKUP_ICON_DP = 28

