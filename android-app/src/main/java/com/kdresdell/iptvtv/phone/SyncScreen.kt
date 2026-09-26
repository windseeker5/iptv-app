package com.kdresdell.iptvtv.phone

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

// One step of the catalog download, for the progress screen.
data class SyncProgress(val step: Int, val totalSteps: Int, val label: String)

// Full-screen "downloading your list" feedback: the logo gently pulsing
// inside a spinning yellow ring (like the launcher's install animation),
// the current step, and a step progress bar. Shown on first launch and on
// top of the app during a manual refresh - it swallows touches so nothing
// underneath gets tapped mid-sync. On failure it swaps to the error with
// Try again / Close (Close only when there's an existing list to go back to).
@Composable
fun SyncScreen(
    progress: SyncProgress?,
    error: String?,
    onRetry: () -> Unit,
    onClose: (() -> Unit)?
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.97f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val pulse = rememberInfiniteTransition(label = "logoPulse")
            val scale by pulse.animateFloat(
                initialValue = 0.92f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
                label = "logoScale"
            )
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(168.dp)) {
                if (error == null) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(168.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        strokeWidth = 5.dp
                    )
                }
                Box(modifier = Modifier.scale(if (error == null) scale else 1f)) {
                    AppLogo(size = 132.dp)
                }
            }
            Spacer(Modifier.height(8.dp))
            if (error == null) {
                Text(
                    "Updating your list from the provider",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Text(
                    progress?.label ?: "Connecting...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (progress != null) {
                    val fraction by animateFloatAsState(
                        targetValue = progress.step.toFloat() / progress.totalSteps,
                        label = "syncFraction"
                    )
                    LinearProgressIndicator(
                        progress = { fraction },
                        modifier = Modifier
                            .fillMaxWidth(0.7f)
                            .height(6.dp)
                            .clip(RoundedCornerShape(50)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    )
                    Text(
                        "Step ${progress.step} of ${progress.totalSteps}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Text(
                    "Couldn't update the list",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    error,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (onClose != null) OutlinedButton(onClick = onClose) { Text("Close") }
                    Button(onClick = onRetry) { Text("Try again") }
                }
            }
        }
    }
}
