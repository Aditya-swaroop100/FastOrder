package com.example.fastorder

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Where the first screen should land once startup work is done.
 *
 * Deciding this *before* the splash is dismissed is the whole point of holding
 * it - it avoids showing Home for a frame and then yanking the user to sign-in.
 */
enum class StartDestination {
    Home,
    Onboarding,
    SignIn,
}

/**
 * Immutable snapshot of app startup.
 *
 * @param isReady false while startup work is in flight; the splash stays up.
 * @param startDestination where to navigate once [isReady] is true.
 */
data class MainUiState(
    val isReady: Boolean = false,
    val startDestination: StartDestination = StartDestination.Home,
)

/**
 * Owns app-startup state.
 *
 * Lives in a ViewModel rather than the Activity so it survives configuration
 * changes - rotating the device mid-startup must not restart the work or
 * re-show the splash.
 */
class MainViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val startedAt = SystemClock.uptimeMillis()

            // TODO: real startup work - restore the Firebase Auth session, warm
            //  the Firestore catalog cache, fetch Remote Config. The result
            //  decides startDestination (Home vs Onboarding vs SignIn).

            // The system splash no longer needs padding to be seen - the in-app
            // BrandSplashScreen provides the branded moment now, and it runs in
            // parallel with this work. Keep this at 0 so the system splash
            // lasts exactly as long as startup actually takes.
            val elapsed = SystemClock.uptimeMillis() - startedAt
            if (elapsed < MIN_SPLASH_VISIBLE_MS) {
                delay(MIN_SPLASH_VISIBLE_MS - elapsed)
            }

            _uiState.value = MainUiState(
                isReady = true,
                startDestination = StartDestination.Home,
            )
        }
    }

    private companion object {
        const val MIN_SPLASH_VISIBLE_MS = 0L
    }
}





