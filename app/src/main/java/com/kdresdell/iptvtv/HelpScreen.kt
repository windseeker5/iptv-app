package com.kdresdell.iptvtv

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Approved design (2026-09-17 design canvas, "Help screen - remote guide").
// This app only targets Google TV, so the diagram shows exactly the buttons
// its gestures use - the D-pad, OK and Back - and nothing else on the real
// remote (mic, home, volume, power).
private val RemoteWidth = 120.dp
private val RemoteHeight = 230.dp
private val DPadCenter = Offset(60f, 80f)
private val DPadRadius = 46.dp
private val BackCenter = Offset(60f, 166f)
private val BackRadius = 16.dp

private val OnSurface = Color(0xFFE4E7E4)
private val OnSurfaceVariant = Color(0xFF9AA09A)
private val AccentSoft = Color(0xFFA6F2A6)
private val DividerColor = Color(0xFF2A2D2A)
private val ScreenBackground = Color(0xFF0E100E)

@Composable
private fun RemoteDiagram(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(RemoteWidth, RemoteHeight)) {
        val strokeWidth = 3.dp.toPx()

        drawRoundRect(
            color = OnSurface,
            topLeft = Offset(4.dp.toPx(), 4.dp.toPx()),
            size = androidx.compose.ui.geometry.Size(112.dp.toPx(), 222.dp.toPx()),
            cornerRadius = CornerRadius(56.dp.toPx()),
            style = Stroke(strokeWidth)
        )

        val dpadCenterPx = Offset(DPadCenter.x.dp.toPx(), DPadCenter.y.dp.toPx())
        drawCircle(color = OnSurface, radius = DPadRadius.toPx(), center = dpadCenterPx, style = Stroke(strokeWidth))

        // Direction ticks (up/down/left/right) just inside the ring
        val tickInner = 38.dp.toPx()
        val tickOuter = 48.dp.toPx()
        listOf(
            Offset(0f, -1f), // up
            Offset(0f, 1f),  // down
            Offset(-1f, 0f), // left
            Offset(1f, 0f)   // right
        ).forEach { dir ->
            drawLine(
                color = OnSurfaceVariant,
                start = dpadCenterPx + Offset(dir.x * tickInner, dir.y * tickInner),
                end = dpadCenterPx + Offset(dir.x * tickOuter, dir.y * tickOuter),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )
        }

        // OK center dot
        drawCircle(color = AccentSoft, radius = 15.dp.toPx(), center = dpadCenterPx)

        // Back button + chevron
        val backCenterPx = Offset(BackCenter.x.dp.toPx(), BackCenter.y.dp.toPx())
        drawCircle(color = OnSurface, radius = BackRadius.toPx(), center = backCenterPx, style = Stroke(strokeWidth))
        val chevron = Path().apply {
            moveTo(backCenterPx.x + 5.dp.toPx(), backCenterPx.y - 6.dp.toPx())
            lineTo(backCenterPx.x - 3.dp.toPx(), backCenterPx.y)
            lineTo(backCenterPx.x + 5.dp.toPx(), backCenterPx.y + 6.dp.toPx())
        }
        drawPath(chevron, color = OnSurface, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

@Composable
private fun Callout(title: String, body: String, dotColor: Color = OnSurfaceVariant) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(modifier = Modifier.size(8.dp).background(dotColor, CircleShape))
            Text(text = title, fontSize = 22.sp, fontWeight = FontWeight.Medium, color = OnSurface)
        }
        Text(text = body, fontSize = 17.sp, color = OnSurfaceVariant, modifier = Modifier.padding(start = 18.dp))
    }
}

// Self-contained theming, same reasoning as SettingsScreen: keeps contrast
// correct if this is ever shown outside the rail's TV-themed layout.
@Composable
fun HelpScreen() {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    MaterialTheme(colorScheme = darkColorScheme()) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = ScreenBackground
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 64.dp, vertical = 40.dp)
                // This screen has no Card/text-field to naturally hold
                // focus (plain Text/Canvas only) - WithRail's "return focus
                // to content" call needs a real target to land on, or it
                // silently fails and focus never actually leaves the rail.
                // Confirmed bug: Right did nothing and Back exited the app
                // outright while "on" Help, because focus was still in the
                // rail the whole time.
                .focusRequester(focusRequester)
                .focusable()
        ) {
            // Title - normal flow, reserves its own space so nothing below
            // can ever overlap it (an earlier design draft absolutely
            // positioned both independently and they collided).
            Text(
                text = "HELP",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 2.sp,
                color = OnSurfaceVariant,
                modifier = Modifier.padding(bottom = 6.dp)
            )
            Text(text = "Remote Guide", fontSize = 44.sp, color = OnSurface)

            // Remaining space below the title: this group centers within it
            Column(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Row {
                    RemoteDiagram()
                    Spacer(modifier = Modifier.width(44.dp))
                    Box(modifier = Modifier.height(RemoteHeight).weight(1f)) {
                        Box(modifier = Modifier.padding(top = 38.dp)) {
                            Callout(
                                title = "OK — press and hold",
                                body = "Start or stop recording the channel",
                                dotColor = AccentSoft
                            )
                        }
                        Box(modifier = Modifier.padding(top = 144.dp)) {
                            Callout(
                                title = "Back",
                                body = "Opens the menu while watching - press again to exit"
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))
                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(DividerColor))
                Spacer(modifier = Modifier.height(28.dp))

                Callout(
                    title = "Press and hold OK on an item",
                    body = "Favorite, set as default, or remove it"
                )
            }
        }
    }
    }
}
