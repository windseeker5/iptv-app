package com.kdresdell.iptvtv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Card
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.kdresdell.iptvtv.theme.LocalAppColors
import com.kdresdell.iptvtv.theme.appCardBorder
import com.kdresdell.iptvtv.theme.appCardGlow
import com.kdresdell.iptvtv.theme.appRowCardColors
import com.kdresdell.iptvtv.theme.appRowCardScale

// §6.6 My Librairie episode picker, reached from a series poster. The
// series poster/title/synopsis header is a fixed block ABOVE the episode
// list, not a scrollable LazyColumn item - it must stay on screen no matter
// which episode has D-pad focus. First tried it as the first LazyColumn
// item, which meant claiming focus on episode 1 auto-scrolled the header
// straight off screen with no way to navigate back up to it - a real bug,
// not just a layout preference.
@Composable
fun SeriesEpisodesScreen(
    seriesName: String,
    seriesCover: String,
    state: LoadState<SeriesDetails>,
    onSelectEpisode: (SeriesEpisode) -> Unit
) {
    val onBackground = MaterialTheme.colorScheme.onBackground
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val firstItemFocus = remember { FocusRequester() }

    // See CategoryListScreen for why this is needed - without it, D-pad
    // focus lands nowhere when this screen opens.
    LaunchedEffect(state) {
        if (state is LoadState.Success && state.data.episodes.isNotEmpty()) {
            firstItemFocus.requestFocus()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 48.dp, vertical = 24.dp)
    ) {
        SeriesHeader(
            name = seriesName,
            cover = seriesCover,
            description = (state as? LoadState.Success)?.data?.description.orEmpty(),
            rating = (state as? LoadState.Success)?.data?.rating.orEmpty(),
            headingColor = onBackground,
            bodyColor = onSurfaceVariant
        )
        Box(modifier = Modifier.fillMaxSize()) {
            when (state) {
                is LoadState.Loading -> Text(
                    text = "Loading episodes...",
                    color = onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center)
                )
                is LoadState.Error -> Text(
                    text = "Could not load episodes: ${state.message}",
                    color = onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center)
                )
                is LoadState.Success -> {
                    val episodes = state.data.episodes
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
                    if (episodes.isEmpty()) {
                        Text(text = "No episodes found", color = onSurfaceVariant, modifier = Modifier.align(Alignment.Center))
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            itemsIndexed(rows) { index, (episode, isNewSeason) ->
                                if (isNewSeason) {
                                    Text(
                                        text = "Season ${episode.season}",
                                        style = MaterialTheme.typography.titleLarge,
                                        color = onBackground,
                                        modifier = Modifier.padding(top = if (index == 0) 0.dp else 12.dp, bottom = 4.dp)
                                    )
                                }
                                EpisodeRow(
                                    episode = episode,
                                    onSelect = { onSelectEpisode(episode) },
                                    rowModifier = if (index == 0) Modifier.focusRequester(firstItemFocus) else Modifier
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// Poster + title + synopsis - a fixed header, not a scrolling list item
// (see the screen-level comment above for why that matters).
@Composable
private fun SeriesHeader(
    name: String,
    cover: String,
    description: String,
    rating: String,
    headingColor: androidx.compose.ui.graphics.Color,
    bodyColor: androidx.compose.ui.graphics.Color
) {
    Row(horizontalArrangement = Arrangement.spacedBy(20.dp), modifier = Modifier.padding(bottom = 20.dp)) {
        Box(
            modifier = Modifier
                // +12.5% per direction (was 120dp).
                .width(135.dp)
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(8.dp))
                .background(LocalAppColors.current.surfaceContainer)
        ) {
            AsyncImage(
                model = cover,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
            // Reduced from headlineLarge (44sp) - a series name read as too
            // dominant at that size once real synopsis text sat next to it.
            Text(text = TitleFormat.clean(name), style = MaterialTheme.typography.headlineSmall, color = headingColor)
            // Provider-supplied rating, never fabricated - blank whenever
            // the provider doesn't return one.
            if (rating.isNotBlank()) {
                Text(text = "★ $rating", style = MaterialTheme.typography.labelMedium, color = bodyColor)
            }
            if (description.isNotBlank()) {
                Text(text = description, style = MaterialTheme.typography.bodyMedium, color = bodyColor, maxLines = 4, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

// One full-width row per episode (back to the app's standard list pattern,
// §5's row exception - no scale on focus), with that episode's own synopsis
// underneath in smaller text when the provider actually supplies one.
@Composable
private fun EpisodeRow(
    episode: SeriesEpisode,
    onSelect: () -> Unit,
    rowModifier: Modifier = Modifier
) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant

    Card(
        onClick = onSelect,
        modifier = Modifier.fillMaxWidth().then(rowModifier),
        scale = appRowCardScale(),
        colors = appRowCardColors(),
        border = appCardBorder(shape = RoundedCornerShape(8.dp)),
        glow = appCardGlow()
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            Text(
                text = "E${episode.episodeNum}" + if (episode.title.isNotBlank()) " · ${TitleFormat.clean(episode.title)}" else "",
                style = MaterialTheme.typography.labelMedium,
                color = onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (episode.description.isNotBlank()) {
                Text(
                    text = episode.description,
                    style = MaterialTheme.typography.labelSmall,
                    color = onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
