package com.example.fastorder.ui.navigation

import kotlinx.serialization.Serializable

/**
 * Type-safe navigation routes.
 *
 * These are destinations *inside* the single MainActivity - not Activities.
 * Sign-in is a screen you navigate to and pop off the back stack, exactly like
 * any other screen, which is what lets it share a NavController, survive
 * process death, and participate in deep links.
 *
 * Objects are used for argument-less screens; use @Serializable data classes
 * when a route needs arguments, e.g.
 *
 *     @Serializable data class ProductDetail(val productId: String) : Route
 */
sealed interface Route {

    /** First-run product tour. Shown only when the user has never opened the app. */
    @Serializable
    data object Onboarding : Route

    /** Authentication. Reachable mid-session too, e.g. after a forced sign-out. */
    @Serializable
    data object SignIn : Route

    /** Catalog home - the storefront. */
    @Serializable
    data object Home : Route
}

