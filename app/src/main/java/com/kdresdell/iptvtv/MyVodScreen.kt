package com.kdresdell.iptvtv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

// Saved movies and series - separate from live-channel Favorites (now
// "My Channel") since VOD playback is a different flow (direct play for
// movies, an episode picker for series).
@Composable
fun MyVodScreen(
    savedMovies: List<VodStream>,
    savedSeries: List<SeriesShow>,
    onPlayMovie: (VodStream) -> Unit,
    onOpenEpisodes: (SeriesShow) -> Unit,
    onRemoveMovie: (VodStream) -> Unit,
    onRemoveSeries: (SeriesShow) -> Unit
) {
    val onBackground = MaterialTheme.colorScheme.onBackground
    val isEmpty = savedMovies.isEmpty() && savedSeries.isEmpty()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(24.dp)
        ) {
            item {
                Text(
                    text = if (isEmpty) "No saved movies or shows yet - use Search to add some" else "My VOD",
                    color = onBackground,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            items(savedMovies) { movie ->
                Box(modifier = Modifier.fillMaxWidth()) {
                    VodRow(
                        movie = movie,
                        isSaved = true,
                        onPlay = { onPlayMovie(movie) },
                        onToggleSaved = { onRemoveMovie(movie) }
                    )
                }
            }
            items(savedSeries) { series ->
                Box(modifier = Modifier.fillMaxWidth()) {
                    SeriesRow(
                        series = series,
                        isSaved = true,
                        onOpenEpisodes = { onOpenEpisodes(series) },
                        onToggleSaved = { onRemoveSeries(series) }
                    )
                }
            }
        }
    }
}
