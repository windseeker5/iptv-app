package com.kdresdell.iptvtv

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Card
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

// Two separate focusable targets per row (not one Card with two actions) -
// TV remotes have no reliable long-press, so "play" and "favorite" each
// need their own D-pad-selectable element.
@Composable
fun ChannelRow(
    channel: LiveChannel,
    isFavorite: Boolean,
    onPlay: () -> Unit,
    onToggleFavorite: () -> Unit,
    subtitle: String? = null,
    playCardModifier: Modifier = Modifier,
    isDefault: Boolean = false,
    onSetDefault: (() -> Unit)? = null
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Card(onClick = onPlay, modifier = Modifier.weight(1f).then(playCardModifier)) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(text = channel.name)
                if (subtitle != null) {
                    Text(text = subtitle, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        Card(onClick = onToggleFavorite) {
            Text(
                text = if (isFavorite) "★ Remove" else "☆ Favorite",
                modifier = Modifier.padding(24.dp)
            )
        }
        if (onSetDefault != null) {
            Card(onClick = onSetDefault) {
                Text(
                    text = if (isDefault) "★ Default" else "Set as Default",
                    modifier = Modifier.padding(24.dp)
                )
            }
        }
    }
}
