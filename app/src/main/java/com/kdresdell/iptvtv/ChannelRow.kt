package com.kdresdell.iptvtv

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Card
import androidx.tv.material3.Text

// Two separate focusable targets per row (not one Card with two actions) -
// TV remotes have no reliable long-press, so "play" and "favorite" each
// need their own D-pad-selectable element.
@Composable
fun ChannelRow(
    channel: LiveChannel,
    isFavorite: Boolean,
    onPlay: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Card(onClick = onPlay, modifier = Modifier.weight(1f)) {
            Text(text = channel.name, modifier = Modifier.padding(24.dp))
        }
        Card(onClick = onToggleFavorite) {
            Text(
                text = if (isFavorite) "★ Remove" else "☆ Favorite",
                modifier = Modifier.padding(24.dp)
            )
        }
    }
}
