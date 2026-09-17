package com.example.fastorder

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.fastorder.ui.navigation.FastOrderNavHost
import com.example.fastorder.ui.screens.BrandSplashScreen
import com.example.fastorder.ui.theme.FastOrderTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * The app's single Activity.
 *
 * Everything else - splash, onboarding, sign-in, catalog - is a destination in
 * [FastOrderNavHost]. There is deliberately no SplashActivity or LoginActivity:
 * since Android 12 the system already draws a splash on every cold start, so a
 * second splash Activity would show two in a row, and extra Activities
 * fragment the back stack and complicate deep links.
 *
 * `@AndroidEntryPoint` is what makes `by viewModels()` below able to resolve a
 * `@HiltViewModel` - the annotation generates a base class that installs Hilt's
 * ViewModel factory as the Activity's default.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    /**
     * Startup state lives here rather than in a field on the Activity so it
     * survives configuration changes - rotating during startup must not
     * restart the work or re-show the splash.
     */
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        // Must run before super.onCreate() so the splash is installed
        // before the activity's window content is created.
        val splashScreen = installSplashScreen()

        super.onCreate(savedInstanceState)

        // Hold the splash until the ViewModel reports startup is complete.
        splashScreen.setKeepOnScreenCondition { !viewModel.uiState.value.isReady }

        // Fade out rather than hard-cutting into the app UI.
        splashScreen.setOnExitAnimationListener { splashProvider ->
            splashProvider.view
                .animate()
                .alpha(0f)
                .setDuration(SPLASH_EXIT_DURATION_MS)
                .withEndAction { splashProvider.remove() }
                .start()
        }

        enableEdgeToEdge()
        setContent {
            FastOrderTheme {
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()

                // Survives rotation so the brand animation does not replay.
                var brandAnimationDone by rememberSaveable { mutableStateOf(false) }

                // Hand off to the app only once startup work AND the brand
                // animation have both finished - whichever is slower wins.
                val showApp = uiState.isReady && brandAnimationDone

                // BrandSplashScreen is composed immediately (not gated on
                // isReady) so its background paints the very first frame.
                // Otherwise the window background flashes between the system
                // splash exiting and Compose drawing.
                Crossfade(targetState = showApp, label = "brandSplash") { ready ->
                    if (ready) {
                        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                            FastOrderNavHost(
                                startDestination = uiState.startDestination,
                                modifier = Modifier.padding(innerPadding),
                            )
                        }
                    } else {
                        // started=isReady so the animation begins only once the
                        // system splash has actually been dismissed - otherwise
                        // the whole sequence plays behind it, unseen.
                        BrandSplashScreen(
                            started = uiState.isReady,
                            onFinished = { brandAnimationDone = true },
                        )
                    }
                }
            }
        }
    }

    private companion object {
        /** Fade duration when handing off from splash to app UI. */
        const val SPLASH_EXIT_DURATION_MS = 250L
    }
}