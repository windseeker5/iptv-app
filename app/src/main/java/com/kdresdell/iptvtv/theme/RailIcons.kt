package com.kdresdell.iptvtv.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

// Exact reproductions of the outline icon set from the approved side-nav
// mockup (STYLE_GUIDE.md §6.2, https://claude.ai/artifact/EX4zdqX4ZBDXWqs8oAGzy2)
// - same 24x24 viewport, 2dp round-cap/round-join stroke paths transcribed
// node-for-node from that mockup's SVGs. Color is irrelevant here: Icon()
// applies its own `tint` as a full ColorFilter over whatever is drawn.
private fun strokeIcon(name: String, build: ImageVector.Builder.() -> Unit): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply(build).build()

private fun ImageVector.Builder.strokePath(pathData: PathBuilder.() -> Unit) {
    path(
        fill = null,
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
        pathFillType = PathFillType.NonZero,
        pathBuilder = pathData
    )
}

// Player transport controls (§6.1) are solid glyphs, not outline strokes -
// same as every OS/TV player's play/pause - so this is a filled path rather
// than strokePath above.
private fun ImageVector.Builder.fillPath(pathData: PathBuilder.() -> Unit) {
    path(
        fill = SolidColor(Color.Black),
        pathFillType = PathFillType.NonZero,
        pathBuilder = pathData
    )
}

object PlayerIcons {
    // Standard play-triangle glyph (Material "play_arrow"), transcribed by
    // hand like the rest of this file's icons - see RailIcons for why (no
    // material-icons-extended dependency, and the user dislikes anything
    // that reads as a stock emoji glyph rather than a real vector icon).
    val Play: ImageVector = strokeIcon("PlayerPlay") {
        fillPath {
            moveTo(8f, 5f)
            verticalLineToRelative(14f)
            lineToRelative(11f, -7f)
            close()
        }
    }

    val Pause: ImageVector = strokeIcon("PlayerPause") {
        fillPath {
            moveTo(6f, 5f)
            horizontalLineToRelative(4f)
            verticalLineToRelative(14f)
            horizontalLineToRelative(-4f)
            close()
        }
        fillPath {
            moveTo(14f, 5f)
            horizontalLineToRelative(4f)
            verticalLineToRelative(14f)
            horizontalLineToRelative(-4f)
            close()
        }
    }
}

object RailIcons {
    // <circle cx="11" cy="11" r="7"/><line x1="21" y1="21" x2="16.65" y2="16.65"/>
    val Search: ImageVector = strokeIcon("RailSearch") {
        strokePath {
            moveTo(18f, 11f)
            arcToRelative(7f, 7f, 0f, true, true, -14f, 0f)
            arcToRelative(7f, 7f, 0f, true, true, 14f, 0f)
        }
        strokePath {
            moveTo(21f, 21f)
            lineTo(16.65f, 16.65f)
        }
    }

    // <rect x="3" y="4" width="18" height="13" rx="2"/>
    // <line x1="8" y1="21" x2="16" y2="21"/><line x1="12" y1="17" x2="12" y2="21"/>
    val MyTv: ImageVector = strokeIcon("RailMyTv") {
        strokePath {
            moveTo(5f, 4f)
            horizontalLineToRelative(14f)
            arcToRelative(2f, 2f, 0f, false, true, 2f, 2f)
            verticalLineToRelative(9f)
            arcToRelative(2f, 2f, 0f, false, true, -2f, 2f)
            horizontalLineTo(5f)
            arcToRelative(2f, 2f, 0f, false, true, -2f, -2f)
            verticalLineTo(6f)
            arcToRelative(2f, 2f, 0f, false, true, 2f, -2f)
        }
        strokePath {
            moveTo(8f, 21f)
            lineTo(16f, 21f)
        }
        strokePath {
            moveTo(12f, 17f)
            lineTo(12f, 21f)
        }
    }

    // M4 19.5A2.5 2.5 0 0 1 6.5 17H20
    // M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z
    val MyLibrairie: ImageVector = strokeIcon("RailMyLibrairie") {
        strokePath {
            moveTo(4f, 19.5f)
            arcTo(2.5f, 2.5f, 0f, false, true, 6.5f, 17f)
            horizontalLineTo(20f)
        }
        strokePath {
            moveTo(6.5f, 2f)
            horizontalLineTo(20f)
            verticalLineToRelative(20f)
            horizontalLineTo(6.5f)
            arcTo(2.5f, 2.5f, 0f, false, true, 4f, 19.5f)
            verticalLineToRelative(-15f)
            arcTo(2.5f, 2.5f, 0f, false, true, 6.5f, 2f)
            close()
        }
    }

    // §6.4 search history "clear" icon.
    // <polyline points="3 6 5 6 21 6"/><path d="M19 6l-1 14a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2L5 6"/>
    // <path d="M10 11v6"/><path d="M14 11v6"/><path d="M9 6V4a1 1 0 0 1 1-1h4a1 1 0 0 1 1 1v2"/>
    val Trash: ImageVector = strokeIcon("RailTrash") {
        strokePath {
            moveTo(3f, 6f)
            lineTo(5f, 6f)
            lineTo(21f, 6f)
        }
        strokePath {
            moveTo(19f, 6f)
            lineToRelative(-1f, 14f)
            arcToRelative(2f, 2f, 0f, false, true, -2f, 2f)
            horizontalLineTo(8f)
            arcToRelative(2f, 2f, 0f, false, true, -2f, -2f)
            lineTo(5f, 6f)
        }
        strokePath {
            moveTo(10f, 11f)
            verticalLineToRelative(6f)
        }
        strokePath {
            moveTo(14f, 11f)
            verticalLineToRelative(6f)
        }
        strokePath {
            moveTo(9f, 6f)
            verticalLineTo(4f)
            arcToRelative(1f, 1f, 0f, false, true, 1f, -1f)
            horizontalLineToRelative(4f)
            arcToRelative(1f, 1f, 0f, false, true, 1f, 1f)
            verticalLineTo(6f)
        }
    }

    // <rect .../> x4, 7x7 rx1.5, at (3,3) (14,3) (3,14) (14,14)
    val All: ImageVector = strokeIcon("RailAll") {
        listOf(3f to 3f, 14f to 3f, 3f to 14f, 14f to 14f).forEach { (x, y) ->
            strokePath { roundedSquare(x, y, size = 7f, corner = 1.5f) }
        }
    }

    // <circle cx="12" cy="12" r="3"/> + cog outline
    val Settings: ImageVector = strokeIcon("RailSettings") {
        strokePath {
            moveTo(15f, 12f)
            arcToRelative(3f, 3f, 0f, true, true, -6f, 0f)
            arcToRelative(3f, 3f, 0f, true, true, 6f, 0f)
        }
        strokePath {
            moveTo(19.4f, 15f)
            arcToRelative(1.7f, 1.7f, 0f, false, false, 0.34f, 1.87f)
            lineToRelative(0.06f, 0.06f)
            arcToRelative(2f, 2f, 0f, true, true, -2.83f, 2.83f)
            lineToRelative(-0.06f, -0.06f)
            arcToRelative(1.7f, 1.7f, 0f, false, false, -1.87f, -0.34f)
            arcToRelative(1.7f, 1.7f, 0f, false, false, -1.04f, 1.56f)
            verticalLineTo(21f)
            arcToRelative(2f, 2f, 0f, false, true, -4f, 0f)
            verticalLineToRelative(-0.09f)
            arcTo(1.7f, 1.7f, 0f, false, false, 9f, 19.4f)
            arcToRelative(1.7f, 1.7f, 0f, false, false, -1.87f, 0.34f)
            lineToRelative(-0.06f, 0.06f)
            arcToRelative(2f, 2f, 0f, true, true, -2.83f, -2.83f)
            lineToRelative(0.06f, -0.06f)
            arcTo(1.7f, 1.7f, 0f, false, false, 4.6f, 15f)
            arcToRelative(1.7f, 1.7f, 0f, false, false, -1.56f, -1.04f)
            horizontalLineTo(3f)
            arcToRelative(2f, 2f, 0f, false, true, 0f, -4f)
            horizontalLineToRelative(0.09f)
            arcTo(1.7f, 1.7f, 0f, false, false, 4.6f, 9f)
            arcToRelative(1.7f, 1.7f, 0f, false, false, -0.34f, -1.87f)
            lineToRelative(-0.06f, -0.06f)
            arcToRelative(2f, 2f, 0f, true, true, 2.83f, -2.83f)
            lineToRelative(0.06f, 0.06f)
            arcTo(1.7f, 1.7f, 0f, false, false, 9f, 4.6f)
            arcToRelative(1.7f, 1.7f, 0f, false, false, 1.04f, -1.56f)
            verticalLineTo(3f)
            arcToRelative(2f, 2f, 0f, false, true, 4f, 0f)
            verticalLineToRelative(0.09f)
            arcTo(1.7f, 1.7f, 0f, false, false, 15f, 4.6f)
            arcToRelative(1.7f, 1.7f, 0f, false, false, 1.87f, -0.34f)
            lineToRelative(0.06f, -0.06f)
            arcToRelative(2f, 2f, 0f, true, true, 2.83f, 2.83f)
            lineToRelative(-0.06f, 0.06f)
            arcTo(1.7f, 1.7f, 0f, false, false, 19.4f, 9f)
            arcToRelative(1.7f, 1.7f, 0f, false, false, 1.56f, 1.04f)
            horizontalLineTo(21f)
            arcToRelative(2f, 2f, 0f, false, true, 0f, 4f)
            horizontalLineToRelative(-0.09f)
            arcTo(1.7f, 1.7f, 0f, false, false, 19.4f, 15f)
            close()
        }
    }
}

private fun PathBuilder.roundedSquare(x: Float, y: Float, size: Float, corner: Float) {
    moveTo(x + corner, y)
    horizontalLineTo(x + size - corner)
    arcTo(corner, corner, 0f, false, true, x + size, y + corner)
    verticalLineTo(y + size - corner)
    arcTo(corner, corner, 0f, false, true, x + size - corner, y + size)
    horizontalLineTo(x + corner)
    arcTo(corner, corner, 0f, false, true, x, y + size - corner)
    verticalLineTo(y + corner)
    arcTo(corner, corner, 0f, false, true, x + corner, y)
    close()
}
