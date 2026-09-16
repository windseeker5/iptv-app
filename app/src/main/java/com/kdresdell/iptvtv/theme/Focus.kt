package com.kdresdell.iptvtv.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ButtonBorder
import androidx.tv.material3.ButtonGlow
import androidx.tv.material3.ButtonScale
import androidx.tv.material3.CardBorder
import androidx.tv.material3.CardColors
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.CardGlow
import androidx.tv.material3.CardScale
import androidx.tv.material3.Glow
import androidx.tv.material3.IconButtonDefaults

// STYLE_GUIDE.md §5 - the one focus/press recipe every Card-based focusable
// in the app uses: scale 1.05x/1.02x, 2dp outline inset 2dp in vivid accent,
// 8dp glow at 40%/60% opacity. Screens reach for these instead of each
// hand-rolling its own CardDefaults.scale()/border()/glow().
fun appCardScale(): CardScale = CardDefaults.scale(
    scale = 1f,
    focusedScale = FocusDefaults.FocusedScale,
    pressedScale = FocusDefaults.PressedScale
)

@Composable
fun appCardBorder(shape: Shape = RoundedCornerShape(8.dp)): CardBorder {
    val accent = LocalAppColors.current.vividAccent
    val focused = Border(
        border = BorderStroke(FocusDefaults.OutlineWidth, accent),
        inset = FocusDefaults.OutlineInset,
        shape = shape
    )
    return CardDefaults.border(focusedBorder = focused, pressedBorder = focused)
}

@Composable
fun appCardGlow(): CardGlow {
    val accent = LocalAppColors.current.vividAccent
    val focused = Glow(elevationColor = accent.copy(alpha = FocusDefaults.GlowAlpha), elevation = FocusDefaults.GlowRadius)
    val pressed = Glow(elevationColor = accent.copy(alpha = FocusDefaults.PressedGlowAlpha), elevation = FocusDefaults.GlowRadius)
    return CardDefaults.glow(focusedGlow = focused, pressedGlow = pressed)
}

// §6.4 search-results / §6 list rows - explicitly NO scale-up on focus,
// matching the approved mockup's row-focus CSS (background lift + outline +
// glow only, no transform). A full-width row growing 1.05-1.1x on focus (the
// Card-default or appCardScale() behavior) reads as a jarring, oversized
// jump - fine for a small chip/tile, wrong for something already
// near-screen-width. Pair with appRowCardColors() for the background lift.
fun appRowCardScale(): CardScale = CardDefaults.scale(scale = 1f, focusedScale = 1f, pressedScale = 1f)

@Composable
fun appRowCardColors(): CardColors = CardDefaults.colors(
    containerColor = LocalAppColors.current.surfaceContainer,
    focusedContainerColor = LocalAppColors.current.surfaceContainerLow,
    pressedContainerColor = LocalAppColors.current.surfaceContainerLow
)

// Same §5 recipe, for the circular icon buttons introduced alongside search
// history (§6.4) - tv.material3.IconButton takes its own Button-prefixed
// scale/border/glow types rather than Card's, hence the separate helpers.
fun appIconButtonScale(): ButtonScale = IconButtonDefaults.scale(
    scale = 1f,
    focusedScale = FocusDefaults.FocusedScale,
    pressedScale = FocusDefaults.PressedScale
)

@Composable
fun appIconButtonBorder(shape: Shape = CircleShape): ButtonBorder {
    val accent = LocalAppColors.current.vividAccent
    val focused = Border(
        border = BorderStroke(FocusDefaults.OutlineWidth, accent),
        inset = FocusDefaults.OutlineInset,
        shape = shape
    )
    return IconButtonDefaults.border(focusedBorder = focused, pressedBorder = focused)
}

@Composable
fun appIconButtonGlow(): ButtonGlow {
    val accent = LocalAppColors.current.vividAccent
    val focused = Glow(elevationColor = accent.copy(alpha = FocusDefaults.GlowAlpha), elevation = FocusDefaults.GlowRadius)
    val pressed = Glow(elevationColor = accent.copy(alpha = FocusDefaults.PressedGlowAlpha), elevation = FocusDefaults.GlowRadius)
    return IconButtonDefaults.glow(focusedGlow = focused, pressedGlow = pressed)
}
