package com.kdresdell.iptvtv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Card
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

// A simple flat episode list, season headers included, in season/episode
// order - no season filtering/collapsing yet, just enough to pick an
// episode and play it.
@Composable
fun SeriesEpisodesScreen(
    seriesName: String,
    state: LoadState<List<SeriesEpisode>>,
    onSelectEpisode: (SeriesEpisode) -> Unit
) {
    val onBackground = MaterialTheme.colorScheme.onBackground
    val firstItemFocus = remember { FocusRequester() }

    // See CategoryListScreen for why this is needed - without it, D-pad
    // focus lands nowhere when this screen opens.
    LaunchedEffect(state) {
        if (state is LoadState.Success && state.data.isNotEmpty()) {
            firstItemFocus.requestFocus()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        when (state) {
            is LoadState.Loading -> Text(text = "Loading episodes...", color = onBackground)
            is LoadState.Error -> Text(text = "Could not load episodes: ${state.message}", color = onBackground)
            is LoadState.Success -> {
                val episodes = state.data
                // Precomputed (not derived during lazy composition, which
                // may compose items out of order while scrolling) so each
                // episode knows up front whether it starts a new season.
                val rows = remember(episodes) {
                    var lastSeason = -1
                    episodes.map { episode ->
                        val isNewSeason = episode.season != lastSeason
                        lastSeason = episode.season
                        episode to isNewSeason
                    }
                }
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp)
                ) {
                    item {
                        Text(text = seriesName, color = onBackground, modifier = Modifier.fillMaxWidth())
                    }
                    if (episodes.isEmpty()) {
                        item { Text(text = "No episodes found", color = onBackground) }
                    }
                    itemsIndexed(rows) { index, (episode, isNewSeason) ->
                        if (isNewSeason) {
                            Text(text = "Season ${episode.season}", color = onBackground)
                        }
                        Card(
                            onClick = { onSelectEpisode(episode) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(if (index == 0) Modifier.focusRequester(firstItemFocus) else Modifier)
                        ) {
                            Text(
                                text = "E${episode.episodeNum} - ${episode.title.ifBlank { "Episode ${episode.episodeNum}" }}",
                                modifier = Modifier.padding(24.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
