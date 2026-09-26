package com.kdresdell.iptvtv

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import java.text.NumberFormat

// Where Settings' "Refresh list now" is at. itemCount is the running
// number of entries saved in the current step (the sync reports it every
// 250 rows), so a long movie download visibly moves.
data class ListRefreshProgress(
    val step: Int,
    val totalSteps: Int,
    val label: String,
    val itemCount: Int = 0,
    val itemNoun: String = ""
)

// Same idea as the phone app's SyncScreen, sized as a card for Settings
// rather than a full-screen takeover (the refresh can run for a while and
// the user may go off and watch something meanwhile): the logo gently
// pulsing inside a spinning ring, the current step with a live count, a
// step bar and "Step x of y". Display-only - not focusable.
@Composable
fun ListRefreshProgressCard(progress: ListRefreshProgress) {
    val pulse = rememberInfiniteTransition(label = "logoPulse")
    val scale by pulse.animateFloat(
        initialValue = 0.9f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "logoScale"
    )
    val fraction by animateFloatAsState(
        targetValue = progress.step.coerceAtLeast(0).toFloat() / progress.totalSteps,
        label = "refreshFraction"
    )
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .widthIn(max = 560.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surfaceVariant)
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(88.dp)) {
            CircularProgressIndicator(
                modifier = Modifier.size(88.dp),
                color = colors.primary,
                trackColor = colors.surface,
                strokeWidth = 4.dp
            )
            Image(
                painter = painterResource(R.drawable.app_logo),
                contentDescription = null,
                modifier = Modifier
                    .size(68.dp)
                    .scale(scale)
                    .clip(CircleShape)
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "Updating your list from the provider",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = colors.onSurface
            )
            val count = if (progress.itemCount > 0) {
                " - " + NumberFormat.getIntegerInstance().format(progress.itemCount) + " " + progress.itemNoun
            } else {
                ""
            }
            Text(
                text = progress.label + count,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant
            )
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(50)),
                color = colors.primary,
                trackColor = colors.surface
            )
            if (progress.step > 0) {
                Text(
                    text = "Step ${progress.step} of ${progress.totalSteps}",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant
                )
            }
        }
    }
}
