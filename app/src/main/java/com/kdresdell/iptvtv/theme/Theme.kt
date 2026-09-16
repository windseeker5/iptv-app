package com.kdresdell.iptvtv.theme

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ColorScheme
import androidx.tv.material3.MaterialTheme

// STYLE_GUIDE.md §2.8/§5 - roles tv-material3's ColorScheme doesn't have a
// slot for (the surface-container elevation ladder, the vivid accent, the
// tuned/now-line green, the neutral "selected" fill). Reach these via
// LocalAppColors.current instead of hand-rolled hex in screen code.
data class AppExtendedColors(
    val vividAccent: androidx.compose.ui.graphics.Color = Primary50Accent,
    val tunedIndicator: androidx.compose.ui.graphics.Color = Primary60,
    val surfaceContainerLow: androidx.compose.ui.graphics.Color = SurfaceContainerLow,
    val surfaceContainer: androidx.compose.ui.graphics.Color = SurfaceContainer,
    val surfaceContainerHigh: androidx.compose.ui.graphics.Color = SurfaceContainerHigh,
    val surfaceContainerHighest: androidx.compose.ui.graphics.Color = SurfaceContainerHighest,
    val selectedSurface: androidx.compose.ui.graphics.Color = ScreenColors.SelectedSurface,
    val trueBlack: androidx.compose.ui.graphics.Color = SurfaceTrueBlack
)

val LocalAppColors = staticCompositionLocalOf { AppExtendedColors() }

// STYLE_GUIDE.md §5 - the one focus/press/disabled recipe every focusable
// element in the app follows. Defined once here so no screen re-derives its
// own scale/glow numbers.
object FocusDefaults {
    const val FocusedScale = 1.05f
    const val PressedScale = 1.02f
    val OutlineWidth = 2.dp
    val OutlineInset = 2.dp
    val GlowRadius = 8.dp
    const val GlowAlpha = 0.4f
    const val PressedGlowAlpha = 0.6f
    const val DisabledContentAlpha = 0.38f
    const val AnimationDurationMs = 150
    val AnimationEasing = FastOutSlowInEasing
}

// STYLE_GUIDE.md §1 - dark-theme-only, no light scheme ships. §2.2-§2.7 for
// every value below; see Color.kt for the source hex.
private val AppDarkColorScheme = ColorScheme(
    primary = Primary80,
    onPrimary = Primary20,
    primaryContainer = Primary10,
    onPrimaryContainer = Primary90,
    inversePrimary = Primary40,
    secondary = Secondary80,
    onSecondary = Secondary20,
    secondaryContainer = Secondary20,
    onSecondaryContainer = Secondary90,
    tertiary = Tertiary80,
    onTertiary = Tertiary20,
    tertiaryContainer = Tertiary20,
    onTertiaryContainer = Tertiary80,
    background = Surface,
    onBackground = OnSurface,
    surface = Surface,
    onSurface = OnSurface,
    surfaceVariant = SurfaceContainer,
    onSurfaceVariant = OnSurfaceVariant,
    surfaceTint = Primary80,
    inverseSurface = OnSurface,
    inverseOnSurface = Surface,
    error = Error,
    onError = OnError,
    errorContainer = ErrorContainer,
    onErrorContainer = androidx.compose.ui.graphics.Color(0xFFF9DEDC), // M3 baseline dark onErrorContainer
    border = Outline,
    borderVariant = OutlineVariant,
    scrim = SurfaceTrueBlack
)

@Composable
fun IptvTvTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalAppColors provides AppExtendedColors()) {
        MaterialTheme(
            colorScheme = AppDarkColorScheme,
            typography = AppTypography,
            content = content
        )
    }
}
