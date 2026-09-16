package com.kdresdell.iptvtv.theme

import androidx.compose.ui.graphics.Color

// STYLE_GUIDE.md §2 - every value here is a direct transcription of the
// guide's tables. Don't hand-tune a hex here without updating the guide
// first; this file and §2 must always agree.

// §2.2 Primary tonal ramp (hue 120deg)
val Primary10 = Color(0xFF0A290A)
val Primary20 = Color(0xFF0D590D)
val Primary30 = Color(0xFF0B8E0B)
val Primary40 = Color(0xFF0AC20A)
val Primary50Accent = Color(0xFF06F906) // vivid accent - focus glow, outline, live indicators
val Primary60 = Color(0xFF3DF53D) // "now" line, tuned-channel indicator
val Primary70 = Color(0xFF71F471)
val Primary80 = Color(0xFFA6F2A6) // primary (dark theme) - filled buttons, active nav, selected text
val Primary90 = Color(0xFFD7F4D7)
val Primary95 = Color(0xFFEDF7ED)
val Primary99 = Color(0xFFFCFDFC)

// §2.3 Secondary ramp (muted green-gray, hue 120deg, 20% sat)
val Secondary20 = Color(0xFF293D29)
val Secondary80 = Color(0xFFC2D6C2)
val Secondary90 = Color(0xFFE0EBE0)

// §2.4 Tertiary ramp (cyan accent, hue 190deg, 55% sat)
val Tertiary20 = Color(0xFF17464F)
val Tertiary80 = Color(0xFFB0DFE8)

// §2.5 Neutral / surface ramp (hue 120deg, 6% sat)
val SurfaceTrueBlack = Color(0xFF000000)
val Surface = Color(0xFF0E100E) // tone 6 - base background
val SurfaceContainerLow = Color(0xFF181B18) // tone 10
val SurfaceContainer = Color(0xFF1D201D) // tone 12
val SurfaceContainerHigh = Color(0xFF292E29) // tone 17
val SurfaceContainerHighest = Color(0xFF353B35) // tone 22 - card default state
val OnSurface = Color(0xFFE4E7E4) // tone 90
val OnSurfaceVariant = Color(0xFF939F93) // tone 60

// §2.6 Neutral-variant / outline ramp (hue 120deg, 12% sat)
val Outline = Color(0xFF435643) // tone 30
val OutlineVariant = Color(0xFF8DA58D) // tone 60

// §2.7 Error - standard M3 dark-theme error tones, unbranded
val Error = Color(0xFFF2B8B5)
val OnError = Color(0xFF601410)
val ErrorContainer = Color(0xFF8C1D18)

// Per-screen literal exceptions called out by name in §6 (not part of the
// tonal ramps above, but still fixed values the guide pins down).
object ScreenColors {
    // §5 Selected state: neutral elevated surface, not a green fill.
    val SelectedSurface = Color(0xFF232823)

    // §6.3 My TV guide
    val FavoritesBackground = Color(0xFF0A0C0A)
    val DiscreetChannelNumber = Color(0xFF5B635B)
    val CurrentlyPlayingCell = SurfaceContainerHigh // tone 17, #292E29

    // §6.5 All / channel list
    val MutedQualityTag = Color(0xFF6C766C)
    val UnfavoritedStarOutline = DiscreetChannelNumber // #5B635B

    // §6.4 Search results
    val SynopsisMuted = Color(0xFF7A847A)

    // §6.7 Settings
    val SectionDivider = Color(0xFF2A2F2A)

    // §6.1 player overlay "Hold OK to record" hint - a dedicated recording
    // red, deliberately not the same value as Error (#F2B8B5): recording and
    // error/destructive states are different concepts and must stay visually
    // distinct.
    val RecordAccent = Color(0xFFFF453A)
}
