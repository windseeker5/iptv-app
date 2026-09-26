package com.kdresdell.iptvtv.phone.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

// Black background + yellow primary, matching the TV app's dark look with
// the launcher icon's colors. Always dark - no light variant.
private val PhoneColorScheme = darkColorScheme(
    primary = IconYellow,
    onPrimary = DeepBrown,
    primaryContainer = HatBrown,
    onPrimaryContainer = OnSurface,
    secondary = HatBrown,
    onSecondary = OnSurface,
    secondaryContainer = SurfaceContainerHigh,
    onSecondaryContainer = OnSurface,
    background = Background,
    onBackground = OnSurface,
    surface = Background,
    onSurface = OnSurface,
    surfaceVariant = SurfaceContainerHigh,
    onSurfaceVariant = OnSurfaceVariant,
    surfaceContainerLowest = Background,
    surfaceContainerLow = SurfaceContainer,
    surfaceContainer = SurfaceContainer,
    surfaceContainerHigh = SurfaceContainerHigh,
    surfaceContainerHighest = SurfaceContainerHighest,
    outline = Outline,
    outlineVariant = OutlineVariant
)

@Composable
fun PhoneTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = PhoneColorScheme, content = content)
}
