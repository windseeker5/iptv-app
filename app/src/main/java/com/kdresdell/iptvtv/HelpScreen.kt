package com.kdresdell.iptvtv

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Remote diagram traced from Google's own Google TV remote drawing (2026-09-18,
// user-supplied picture). Only the buttons this app's gestures use are kept:
// the D-pad ring with OK in the middle, and Back. Everything else on the real
// remote (Assistant, Home, Mute, app buttons, power, input, mic, volume) is
// deliberately left out.
//
// All shape coordinates below are in the picture's own units (body 225 x 711);
// U converts one unit to dp so the whole drawing scales from one number.
private const val BodyW = 225f
private const val BodyH = 711f
private const val U = 300f / BodyH          // dp per picture unit -> remote is 300dp tall
private val DPadCx = 112.5f
private val DPadCy = 111f
private const val DPadR = 98f
private const val OkR = 35f
private val BackCx = 58f
private val BackCy = 258f
private const val BackR = 33f

private val LeftMargin = 64.dp   // room for the "Left" and "Back" labels
private val RightMargin = 68.dp
private val LineReach = 20.dp  // how far a leader line extends past the remote body  // room for the "Up" / "OK" / "Right" / "Down" labels

private val OnSurface = Color(0xFFE4E7E4)
private val OnSurfaceVariant = Color(0xFF9AA09A)
private val AccentSoft = Color(0xFFA6F2A6)
private val HackerGreen = Color(0xFF06F906) // theme Primary50Accent, same as the My TV title
private val DividerColor = Color(0xFF2A2D2A)
private val ScreenBackground = Color(0xFF0E100E)

private fun dp(units: Float) = (units * U).dp

// One label with a leader line: a dot on the button, a line out to the side,
// and the text at the end of it. side = -1 puts the label on the left of the
// remote, +1 on the right. (px, py) is the target in dp, relative to the body.
private class Callout(val text: String, val px: Dp, val py: Dp, val side: Int)

@Composable
private fun LabeledRemote(modifier: Modifier = Modifier) {
    val bodyW = dp(BodyW)
    val bodyH = dp(BodyH)
    val cx = dp(DPadCx)
    val cy = dp(DPadCy)
    val ringStep = 12.dp  // vertical spacing between the labels on the right

    val callouts = listOf(
        Callout("Up", cx, cy - ringStep * 3, 1),
        Callout("OK", cx, cy - ringStep, 1),
        Callout("Right", cx + ringStep * 3, cy + ringStep, 1),
        Callout("Down", cx, cy + ringStep * 3, 1),
        Callout("Left", cx - ringStep * 3, cy, -1),
        Callout("Back", dp(BackCx - BackR - 3f), dp(BackCy), -1)
    )

    Box(modifier = modifier.size(LeftMargin + bodyW + RightMargin, bodyH)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = 2.5.dp.toPx()
            val ox = LeftMargin.toPx()

            // Body: rounded rectangle with fully round ends
            drawRoundRect(
                color = OnSurface,
                topLeft = Offset(ox + stroke / 2, stroke / 2),
                size = androidx.compose.ui.geometry.Size(bodyW.toPx() - stroke, bodyH.toPx() - stroke),
                cornerRadius = CornerRadius(bodyW.toPx() / 2),
                style = Stroke(stroke)
            )

            // D-pad ring with the OK button in the middle
            val dpad = Offset(ox + cx.toPx(), cy.toPx())
            drawCircle(color = OnSurface, radius = dp(DPadR).toPx(), center = dpad, style = Stroke(stroke))
            drawCircle(color = AccentSoft, radius = dp(OkR).toPx(), center = dpad)

            // Back button: circle with a left arrow
            val back = Offset(ox + dp(BackCx).toPx(), dp(BackCy).toPx())
            val backR = dp(BackR).toPx()
            drawCircle(color = OnSurface, radius = backR, center = back, style = Stroke(stroke))
            val half = backR * 0.42f
            val head = backR * 0.32f
            drawLine(OnSurface, Offset(back.x - half, back.y), Offset(back.x + half, back.y), stroke, StrokeCap.Round)
            val arrowHead = Path().apply {
                moveTo(back.x - half + head, back.y - head)
                lineTo(back.x - half, back.y)
                lineTo(back.x - half + head, back.y + head)
            }
            drawPath(arrowHead, OnSurface, style = Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))

            // Leader lines: dot on the button, line out past the body's edge
            callouts.forEach { c ->
                val start = Offset(ox + c.px.toPx(), c.py.toPx())
                val endX = if (c.side > 0) ox + bodyW.toPx() + LineReach.toPx() else ox - LineReach.toPx()
                drawLine(OnSurfaceVariant, start, Offset(endX, start.y), 1.5.dp.toPx(), StrokeCap.Round)
                drawCircle(if (c.text == "OK") Color.Black else OnSurfaceVariant, 2.5.dp.toPx(), start)
            }
        }
        callouts.forEach { c ->
            val y = c.py - 8.dp
            if (c.side > 0) {
                Text(
                    text = c.text,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Light,
                    color = OnSurface,
                    modifier = Modifier.offset(LeftMargin + bodyW + LineReach + 4.dp, y)
                )
            } else {
                Text(
                    text = c.text,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Light,
                    color = OnSurface,
                    textAlign = TextAlign.End,
                    modifier = Modifier.offset(0.dp, y).width(LeftMargin - LineReach - 4.dp)
                )
            }
        }
    }
}

@Composable
private fun KeyLine(key: String, result: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = key,
            fontSize = 17.sp,
            fontWeight = FontWeight.Medium,
            color = AccentSoft,
            modifier = Modifier.width(120.dp)
        )
        Text(text = result, fontSize = 17.sp, color = OnSurface)
    }
}

@Composable
private fun HelpGroup(title: String, modifier: Modifier = Modifier, lines: List<Pair<String, String>>) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = title.uppercase(),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 2.sp,
            color = OnSurfaceVariant
        )
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(DividerColor))
        Spacer(modifier = Modifier.height(2.dp))
        lines.forEach { (key, result) -> KeyLine(key, result) }
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
                color = HackerGreen,
                modifier = Modifier.padding(bottom = 6.dp)
            )
            Text(text = "Remote Guide", fontSize = 34.sp, color = OnSurfaceVariant)

            // Remaining space below the title: this group centers within it
            Column(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LabeledRemote()
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(40.dp)) {
                            HelpGroup(
                                title = "Watching a channel",
                                modifier = Modifier.weight(1f),
                                lines = listOf(
                                    "OK" to "Channel info",
                                    "Up / Down" to "Change channel",
                                    "Hold OK" to "Record on / off",
                                    "Back" to "Show the guide"
                                )
                            )
                            HelpGroup(
                                title = "In the guide",
                                modifier = Modifier.weight(1f),
                                lines = listOf(
                                    "Left / Right" to "Move through time",
                                    "Up / Down" to "Change channel",
                                    "OK" to "Back to full screen",
                                    "Back" to "Open the menu"
                                )
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(40.dp)) {
                            HelpGroup(
                                title = "In the menu",
                                modifier = Modifier.weight(1f),
                                lines = listOf(
                                    "Up / Down" to "Choose a page",
                                    "OK" to "Open it",
                                    "Right" to "Back to the video",
                                    "Back" to "Exit the app"
                                )
                            )
                            HelpGroup(
                                title = "In any list",
                                modifier = Modifier.weight(1f),
                                lines = listOf(
                                    "Hold OK" to "Favorite or default"
                                )
                            )
                        }
                    }
                }
            }
        }
    }
    }
}
