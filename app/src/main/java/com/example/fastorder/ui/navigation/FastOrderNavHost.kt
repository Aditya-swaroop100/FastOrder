package com.example.fastorder.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.fastorder.StartDestination
import com.example.fastorder.ui.screens.HomeScreen
import com.example.fastorder.ui.screens.OnboardingScreen
import com.example.fastorder.ui.screens.SignInScreen

/**
 * The app's single navigation graph, hosted by MainActivity.
 *
 * [startDestination] is resolved by MainViewModel *before* the splash is
 * dismissed, so the user never sees Home flash before being sent to sign-in.
 */
@Composable
fun FastOrderNavHost(
    startDestination: StartDestination,
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    NavHost(
        navController = navController,
        startDestination = startDestination.toRoute(),
        modifier = modifier,
    ) {
        composable<Route.Onboarding> {
            OnboardingScreen(
                onFinished = { navController.replaceGraphWith(Route.SignIn) },
            )
        }
        composable<Route.SignIn> {
            SignInScreen(
                onSignedIn = { navController.replaceGraphWith(Route.Home) },
            )
        }
        composable<Route.Home> {
            HomeScreen()
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

