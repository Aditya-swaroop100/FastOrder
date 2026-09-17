package com.example.fastorder

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fastorder.auth.domain.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

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
 *
 * @param authRepository injected by Hilt. A constructor parameter with no
 *   default, so production wiring and a test's fake go through exactly the same
 *   door - nothing here can reach a global singleton even by accident.
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val startedAt = SystemClock.uptimeMillis()

            // Firebase restores any persisted session from disk during its own
            // ContentProvider initialisation, which runs before
            // Application.onCreate - so by the time this executes the answer is
            // already available synchronously. No await, no network round-trip.
            //
            // Resolving it here is the entire reason the splash is held: a
            // returning user goes straight to Home instead of seeing the
            // sign-in screen for a frame and being yanked off it.
            val isSignedIn = authRepository.currentUser.value != null

            // TODO: remaining startup work - warm the Firestore catalog cache
            //  and fetch Remote Config. Onboarding also needs a persisted
            //  "has seen it" flag before StartDestination.Onboarding can ever
            //  be chosen; until that exists, a first-run user goes to SignIn.
            val destination = if (isSignedIn) {
                StartDestination.Home
            } else {
                StartDestination.SignIn
            }

            // No identifier in the log line: FastOrderApplication sets Sentry's
            // isSendDefaultPii = false, and a uid here would route around it.
            Timber.i("Startup: start destination resolved to %s", destination)

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
                startDestination = destination,
            )
        }
    }

    private companion object {
        const val MIN_SPLASH_VISIBLE_MS = 0L
    }
}



