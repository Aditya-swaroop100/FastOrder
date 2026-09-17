package com.example.fastorder.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.fastorder.R

/**
 * Storefront / catalog home.
 *
 * Placeholder - this is where the product grid, categories, and the
 * ingredient-powered search entry point will live.
 *
 * It carries a sign-out control for now. Not product polish: without one there
 * is no way to leave a session, so the sign-in flow could only ever be
 * exercised once per install (or by clearing app data). It should move into a
 * proper account screen once one exists.
 *
 * @param viewModel injected by Hilt. Previously this composable read the
 *   repository out of the ServiceLocator itself; now the dependency arrives
 *   through [HomeViewModel]'s constructor and the composable sees only state.
 */
@Composable
fun HomeScreen(
    onSignedOut: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "Home - catalog goes here",
            style = MaterialTheme.typography.titleMedium,
        )

        currentUser?.let { user ->
            Text(
                // Falls back through the identifiers a user might have: a
                // Google account has a name, a phone-only account has neither
                // name nor email, and the uid is the only one guaranteed to
                // exist. Rendering a blank line instead would look like a bug.
                text = stringResource(
                    R.string.home_signed_in_as,
                    user.displayName ?: user.phoneNumber ?: user.email ?: user.uid,
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        OutlinedButton(
            // The ViewModel owns the coroutine: sign-out also clears Credential
            // Manager's saved account hint, and that second half must not be
            // cancelled by the recomposition that navigating away causes.
            onClick = { viewModel.signOut(onSignedOut) },
            modifier = Modifier.padding(top = 24.dp),
        ) {
            Text(stringResource(R.string.home_sign_out))
        }
    }
}

