package com.example.fastorder.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fastorder.auth.domain.AuthRepository
import com.example.fastorder.auth.domain.AuthUser
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Backs [HomeScreen].
 *
 * Exists mainly so the screen stops reaching for a dependency itself. Under the
 * old ServiceLocator, `HomeScreen` read the repository straight out of a global
 * object inside its composable body - which made the composable untestable and
 * gave a preview no way to supply a fake. With Hilt the dependency arrives
 * through a constructor, and the composable only ever sees state and lambdas.
 *
 * It will grow into the real catalog ViewModel; the sign-out control below is
 * temporary and belongs on an account screen once one exists.
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    /** Re-exposed as-is: the repository already publishes process-wide state. */
    val currentUser: StateFlow<AuthUser?> = authRepository.currentUser

    /**
     * Ends the session, then invokes [onSignedOut].
     *
     * The callback fires *after* the suspend call returns rather than the
     * screen navigating optimistically, because `signOut` also clears Credential
     * Manager's saved account hint - skip that and the next sign-in silently
     * re-uses the account just left. Waiting for both halves also means Home is
     * never torn down mid-way through.
     *
     * Run on [viewModelScope] rather than a `rememberCoroutineScope`, so the
     * work is not cancelled by the recomposition that navigation triggers.
     */
    fun signOut(onSignedOut: () -> Unit) {
        viewModelScope.launch {
            authRepository.signOut()
            onSignedOut()
        }
    }
}

