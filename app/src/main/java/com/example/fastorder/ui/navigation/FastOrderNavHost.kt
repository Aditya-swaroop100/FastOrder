package com.example.fastorder.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.fastorder.StartDestination
import com.example.fastorder.auth.ui.SignInScreen
import com.example.fastorder.ui.screens.HomeScreen
import com.example.fastorder.ui.screens.OnboardingScreen
import io.sentry.compose.withSentryObservableEffect
import timber.log.Timber

/**
 * The app's single navigation graph, hosted by MainActivity.
 *
 * [startDestination] is resolved by MainViewModel *before* the splash is
 * dismissed, so the user never sees Home flash before being sent to sign-in.
 *
 * The default [navController] is wrapped in Sentry's observable effect, which
 * opens a transaction per destination. In a single-Activity Compose app that is
 * what makes tracing useful at all: Activity-level instrumentation alone would
 * report one transaction for the entire session, with every screen collapsed
 * inside it.
 */
@Composable
fun FastOrderNavHost(
    startDestination: StartDestination,
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController().withSentryObservableEffect(
        enableNavigationBreadcrumbs = true,
        enableNavigationTracing = true,
    ),
) {
    // Records where the user actually went. "Which screen was this on?" is
    // the first question asked of any crash report, and is otherwise pure
    // guesswork.
    //
    // Observing the NavController is deliberate: logging inside each screen
    // would mean every new destination has to remember to opt in, and would
    // re-fire on recomposition. This emits once per real navigation and
    // picks up back-stack pops for free.
    //
    // Sentry now also records its own navigation breadcrumb via
    // withSentryObservableEffect, so within Sentry the two overlap. This is
    // kept because it is the only one that reaches logcat and Crashlytics -
    // Sentry's integration feeds Sentry alone.
    LaunchedEffect(navController) {
        navController.currentBackStackEntryFlow.collect { entry ->
            Timber.i("Navigation: %s", entry.destination.route ?: "unknown")
        }
    }

    NavHost(
        navController = navController,
        startDestination = startDestination.toRoute(),
        modifier = modifier,
    ) {
        composable<Route.Onboarding> {
            OnboardingScreen(
                onFinished = {
                    Timber.i("Onboarding: completed")
                    navController.replaceGraphWith(Route.SignIn)
                },
            )
        }
        composable<Route.SignIn> {
            SignInScreen(
                onSignedIn = {
                    // The screen has already logged the event (without any
                    // identifier - isSendDefaultPii is false in the Sentry
                    // config, so an email or uid here would route around it).
                    // This callback exists purely to own the navigation, which
                    // is the NavHost's job rather than the screen's.
                    navController.replaceGraphWith(Route.Home)
                },
            )
        }
        composable<Route.Home> {
            HomeScreen(
                onSignedOut = {
                    Timber.i("Auth: signed out, returning to sign-in")
                    // replaceGraphWith, not popBackStack: the session is gone,
                    // so leaving Home on the back stack would let the system
                    // back gesture return to a screen rendering data the user
                    // is no longer entitled to.
                    navController.replaceGraphWith(Route.SignIn)
                },
            )
        }
    }
}

private fun StartDestination.toRoute(): Route = when (this) {
    StartDestination.Home -> Route.Home
    StartDestination.Onboarding -> Route.Onboarding
    StartDestination.SignIn -> Route.SignIn
}

/**
 * Navigates to [route] and clears everything behind it.
 *
 * Used for one-way transitions (onboarding -> sign-in -> home) so pressing back
 * from Home exits the app instead of returning to the sign-in screen.
 */
private fun NavHostController.replaceGraphWith(route: Route) {
    navigate(route) {
        popUpTo(graph.id) { inclusive = true }
        launchSingleTop = true
    }
}


