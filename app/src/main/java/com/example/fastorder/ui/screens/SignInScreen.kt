package com.example.fastorder.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * Authentication screen.
 *
 * Note this is a *destination*, not an Activity - it shares MainActivity's
 * NavController, so a forced sign-out mid-session can simply navigate here.
 *
 * Placeholder - Firebase Auth wiring comes later.
 */
@Composable
fun SignInScreen(
    onSignedIn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "Sign in",
            style = MaterialTheme.typography.titleMedium,
        )
        Button(onClick = onSignedIn) {
            Text("Continue")
        }
    }
}

