package com.kdresdell.iptvtv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Replaces the video entirely while recording a live channel (see
// PlayerScreen) rather than layering over the frozen frame the provider
// leaves behind once it cuts the second (viewing) connection - a frozen
// picture reads as broken, this reads as "expected, here's what to do."
// OK is fully blocked while this shows (handled by PlayerScreen's existing
// key logic, not here) - no peeking at the frame underneath.
@Composable
fun RecordingLockoutScreen(
    channelTitle: String,
    elapsedSeconds: Int,
    capMinutes: Int?,
    recordedBytes: Long
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(Color.Red)
                )
                Text(
                    text = "  Recording $channelTitle",
                    color = Color.White,
                    fontSize = 28.sp
                )
            }

            Text(
                text = "Live viewing isn't available while recording - this account only supports one stream at a time.",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 16.sp,
                modifier = Modifier.padding(top = 16.dp, start = 48.dp, end = 48.dp)
            )

            val elapsedText = "%02d:%02d".format(elapsedSeconds / 60, elapsedSeconds % 60)
            val sizeText = "%.1f MB".format(recordedBytes / (1024.0 * 1024.0))

            Text(
                text = if (capMinutes != null) "$elapsedText / %02d:00  ·  $sizeText".format(capMinutes) else "$elapsedText  ·  $sizeText",
                color = Color.White,
                fontSize = 20.sp,
                modifier = Modifier.padding(top = 32.dp)
            )

            if (capMinutes != null) {
                val fraction = (elapsedSeconds / (capMinutes * 60f)).coerceIn(0f, 1f)
                Box(
                    modifier = Modifier
                        .padding(top = 12.dp)
                        .width(320.dp)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color.White.copy(alpha = 0.2f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction)
                            .fillMaxSize()
                            .clip(RoundedCornerShape(3.dp))
                            .background(Color.Red)
                    )
                }
            }

            val stopHint = if (capMinutes != null) {
                "Hold OK to stop recording, or it stops automatically in $capMinutes min total."
            } else {
                "Hold OK to stop recording."
            }
            Text(
                text = stopHint,
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 14.sp,
                modifier = Modifier.padding(top = 24.dp)
            )
        }
    }
}
