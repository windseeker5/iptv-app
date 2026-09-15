package com.kdresdell.iptvtv

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Card
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text

// Shared row tiles for VOD content (movies, series) - used by both Search
// results and the My VOD saved list, so both look and behave the same.

// A small MD3 tonal-container pill marking a result's content type - live,
// movie, or series.
@Composable
fun ResultTypeBadge(label: String, tonalColor: Color) {
    Surface(
        shape = RoundedCornerShape(50),
        colors = SurfaceDefaults.colors(containerColor = tonalColor)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

// Two separate focusable targets, same reasoning as ChannelRow: TV remotes
// have no reliable long-press, so "play" and "save" each need their own
// D-pad-selectable element.
@Composable
fun VodRow(
    movie: VodStream,
    isSaved: Boolean,
    onPlay: () -> Unit,
    onToggleSaved: () -> Unit,
    playCardModifier: Modifier = Modifier
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Card(onClick = onPlay, modifier = Modifier.weight(1f).then(playCardModifier)) {
            Text(text = movie.name, modifier = Modifier.padding(24.dp))
        }
        Card(onClick = onToggleSaved) {
            Text(
                text = if (isSaved) "★ Remove" else "☆ Add to My VOD",
                modifier = Modifier.padding(24.dp)
            )
        }
    }
}

// A series has no single playable stream - selecting it opens the episode
// picker instead of playing directly.
@Composable
fun SeriesRow(
    series: SeriesShow,
    isSaved: Boolean,
    onOpenEpisodes: () -> Unit,
    onToggleSaved: () -> Unit,
    playCardModifier: Modifier = Modifier
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Card(onClick = onOpenEpisodes, modifier = Modifier.weight(1f).then(playCardModifier)) {
            Text(text = series.name, modifier = Modifier.padding(24.dp))
        }
        Card(onClick = onToggleSaved) {
            Text(
                text = if (isSaved) "★ Remove" else "☆ Add to My VOD",
                modifier = Modifier.padding(24.dp)
            )
        }
    }
}
