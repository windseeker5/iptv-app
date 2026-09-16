package com.kdresdell.iptvtv.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Typography

// STYLE_GUIDE.md §3.2 - Roboto (system default), M3 mobile tokens scaled
// ~1.4x for 10-foot legibility. bodySmall has no role in the guide's table,
// so it's left at the tv-material3 default rather than invented here.
val AppTypography = Typography(
    displayLarge = TextStyle(fontSize = 80.sp, lineHeight = 96.sp, fontWeight = FontWeight.Normal),
    displayMedium = TextStyle(fontSize = 64.sp, lineHeight = 72.sp, fontWeight = FontWeight.Normal),
    headlineLarge = TextStyle(fontSize = 44.sp, lineHeight = 52.sp, fontWeight = FontWeight.Normal),
    headlineMedium = TextStyle(fontSize = 40.sp, lineHeight = 48.sp, fontWeight = FontWeight.Normal),
    headlineSmall = TextStyle(fontSize = 34.sp, lineHeight = 42.sp, fontWeight = FontWeight.Normal),
    titleLarge = TextStyle(fontSize = 30.sp, lineHeight = 38.sp, fontWeight = FontWeight.Medium),
    titleMedium = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Medium),
    titleSmall = TextStyle(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 22.sp, lineHeight = 30.sp, fontWeight = FontWeight.Normal),
    bodyMedium = TextStyle(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.Normal),
    labelLarge = TextStyle(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 16.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium)
)
