package com.example.fastorder.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Corner radii for the app.
 *
 * Rounder than the Material baseline (4/8/12/16/28) on purpose. Quick-commerce
 * UIs are dense grids of product tiles, and generous rounding is what stops
 * that reading as a spreadsheet — it is the most recognisable shared trait of
 * Zepto, Blinkit and Instamart.
 *
 * `extraLarge` is the one to reach for on bottom sheets and hero cards;
 * [medium] is the default for the inline error banner and product tiles.
 */
val Shapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/**
 * Corner radius for the full-width action buttons on the sign-in flow.
 *
 * Material's default `Button` shape is a full pill. At full width that reads as
 * a banner rather than a control, so the sign-in buttons pin themselves to this
 * instead — the same radius the eventual "Add to cart" button will use.
 */
val ButtonShape = RoundedCornerShape(16.dp)

