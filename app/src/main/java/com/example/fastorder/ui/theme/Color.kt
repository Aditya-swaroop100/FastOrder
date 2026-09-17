package com.example.fastorder.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * The FastOrder palette.
 *
 * Anchored on the two colours already used by the launch screen
 * (`res/values/colors.xml`): deep aubergine `#2E0F4F` and brand amber
 * `#FFC531`. Before this file existed the app dropped from that branded splash
 * straight into the Compose template's default purple, which is the single most
 * obvious "this is a demo app" tell.
 *
 * Aubergine carries the primary actions; amber is kept as a *tertiary* accent
 * for badges, offers and highlights. That split is deliberate: amber is a poor
 * surface for white text (it fails contrast), so promoting it to `primary`
 * would force every filled button to use dark-on-amber and lose the brand's
 * contrast against the catalogue imagery it will eventually sit on.
 *
 * Values are hand-picked rather than generated so the tonal steps stay legible
 * against `surface` in both modes — every `on*` pairing below clears WCAG AA.
 */

// --- Light ------------------------------------------------------------------

val BrandPurple = Color(0xFF4C1D7A)
val BrandPurpleOn = Color(0xFFFFFFFF)
val BrandPurpleContainer = Color(0xFFEDE0FF)
val BrandPurpleOnContainer = Color(0xFF21003D)

val BrandMutedPurple = Color(0xFF63577A)
val BrandMutedPurpleOn = Color(0xFFFFFFFF)
val BrandMutedPurpleContainer = Color(0xFFF0E7FA)
val BrandMutedPurpleOnContainer = Color(0xFF1F1533)

/** Amber is readable only as a container; the solid role is its dark shade. */
val BrandAmberDark = Color(0xFF7A5400)
val BrandAmberOn = Color(0xFFFFFFFF)
val BrandAmber = Color(0xFFFFC531)
val BrandAmberOnContainer = Color(0xFF2A1C00)

val SurfaceLight = Color(0xFFFDFBFF)
val OnSurfaceLight = Color(0xFF1C1B20)
val SurfaceVariantLight = Color(0xFFEAE1EE)
val OnSurfaceVariantLight = Color(0xFF4B4356)
val OutlineLight = Color(0xFF7C7387)
val OutlineVariantLight = Color(0xFFCDC4D6)

// --- Dark -------------------------------------------------------------------

val BrandPurpleDarkMode = Color(0xFFD7BBFF)
val BrandPurpleOnDarkMode = Color(0xFF3A0F66)

/** Identical to the splash background, so dark mode and launch agree. */
val BrandPurpleContainerDarkMode = Color(0xFF2E0F4F)
val BrandPurpleOnContainerDarkMode = Color(0xFFEDE0FF)

val BrandMutedPurpleDarkMode = Color(0xFFCFC0DC)
val BrandMutedPurpleOnDarkMode = Color(0xFF342942)
val BrandMutedPurpleContainerDarkMode = Color(0xFF4A3F59)
val BrandMutedPurpleOnContainerDarkMode = Color(0xFFEBDDF8)

/** On a dark surface amber finally has the contrast to be a solid role. */
val BrandAmberDarkMode = Color(0xFFFFC531)
val BrandAmberOnDarkMode = Color(0xFF422D00)
val BrandAmberContainerDarkMode = Color(0xFF5F4300)
val BrandAmberOnContainerDarkMode = Color(0xFFFFDF9E)

val SurfaceDark = Color(0xFF151218)
val OnSurfaceDark = Color(0xFFE7E0EA)
val SurfaceVariantDark = Color(0xFF4B4356)
val OnSurfaceVariantDark = Color(0xFFCDC4D6)
val OutlineDark = Color(0xFF968D9F)
val OutlineVariantDark = Color(0xFF4B4356)

// --- Errors -----------------------------------------------------------------
// Kept close to the Material baseline. Error colour is a safety signal, and a
// brand-tinted red reads as decoration rather than a warning.

val ErrorLight = Color(0xFFB3261E)
val ErrorContainerLight = Color(0xFFF9DEDC)
val OnErrorContainerLight = Color(0xFF410E0B)

val ErrorDark = Color(0xFFF2B8B5)
val OnErrorDark = Color(0xFF601410)
val ErrorContainerDark = Color(0xFF8C1D18)
val OnErrorContainerDark = Color(0xFFF9DEDC)

