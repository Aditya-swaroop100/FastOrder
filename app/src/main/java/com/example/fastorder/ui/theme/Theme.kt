package com.example.fastorder.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColorScheme = lightColorScheme(
    primary = BrandPurple,
    onPrimary = BrandPurpleOn,
    primaryContainer = BrandPurpleContainer,
    onPrimaryContainer = BrandPurpleOnContainer,

    secondary = BrandMutedPurple,
    onSecondary = BrandMutedPurpleOn,
    secondaryContainer = BrandMutedPurpleContainer,
    onSecondaryContainer = BrandMutedPurpleOnContainer,

    // Amber lives here: an accent for offers and highlights, not a button fill.
    tertiary = BrandAmberDark,
    onTertiary = BrandAmberOn,
    tertiaryContainer = BrandAmber,
    onTertiaryContainer = BrandAmberOnContainer,

    background = SurfaceLight,
    onBackground = OnSurfaceLight,
    surface = SurfaceLight,
    onSurface = OnSurfaceLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = OnSurfaceVariantLight,
    outline = OutlineLight,
    outlineVariant = OutlineVariantLight,

    error = ErrorLight,
    onError = BrandPurpleOn,
    errorContainer = ErrorContainerLight,
    onErrorContainer = OnErrorContainerLight,
)

private val DarkColorScheme = darkColorScheme(
    primary = BrandPurpleDarkMode,
    onPrimary = BrandPurpleOnDarkMode,
    primaryContainer = BrandPurpleContainerDarkMode,
    onPrimaryContainer = BrandPurpleOnContainerDarkMode,

    secondary = BrandMutedPurpleDarkMode,
    onSecondary = BrandMutedPurpleOnDarkMode,
    secondaryContainer = BrandMutedPurpleContainerDarkMode,
    onSecondaryContainer = BrandMutedPurpleOnContainerDarkMode,

    tertiary = BrandAmberDarkMode,
    onTertiary = BrandAmberOnDarkMode,
    tertiaryContainer = BrandAmberContainerDarkMode,
    onTertiaryContainer = BrandAmberOnContainerDarkMode,

    background = SurfaceDark,
    onBackground = OnSurfaceDark,
    surface = SurfaceDark,
    onSurface = OnSurfaceDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = OnSurfaceVariantDark,
    outline = OutlineDark,
    outlineVariant = OutlineVariantDark,

    error = ErrorDark,
    onError = OnErrorDark,
    errorContainer = ErrorContainerDark,
    onErrorContainer = OnErrorContainerDark,
)

/**
 * The app theme.
 *
 * ### Why dynamic colour is off
 *
 * `dynamicColor` defaulted to `true`, which meant the app painted itself from
 * the user's wallpaper. On a blue wallpaper FastOrder was a blue app — with no
 * relationship to the aubergine-and-amber splash it had just shown, and no two
 * users seeing the same product.
 *
 * For a consumer brand that trade is the wrong way round. Zepto, Blinkit and
 * Zomato all opt out for the same reason: the palette *is* the brand, and
 * recognition is worth more than wallpaper harmony. Material You is a good
 * default for utilities, not for storefronts.
 *
 * It stays a parameter rather than being deleted, so a settings toggle - or a
 * screenshot test that wants a fixed palette - can still force either mode.
 */
@Composable
fun FastOrderTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        // Deliberately unreachable unless a caller opts in. Left wired up so
        // the escape hatch is real rather than decorative.
        dynamicColor -> if (darkTheme) DarkColorScheme else LightColorScheme
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = Shapes,
        content = content,
    )
}

