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
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import java.io.File

// Saved movies and series - separate from live-channel Favorites (now
// "My Channel") since VOD playback is a different flow (direct play for
// movies, an episode picker for series).
@Composable
fun MyVodScreen(
    savedMovies: List<VodStream>,
    savedSeries: List<SeriesShow>,
    recordings: List<File>,
    onPlayMovie: (VodStream) -> Unit,
    onOpenEpisodes: (SeriesShow) -> Unit,
    onPlayRecording: (File) -> Unit,
    onRemoveMovie: (VodStream) -> Unit,
    onRemoveSeries: (SeriesShow) -> Unit,
    onDeleteRecording: (File) -> Unit
) {
    val onBackground = MaterialTheme.colorScheme.onBackground
    val isEmpty = savedMovies.isEmpty() && savedSeries.isEmpty() && recordings.isEmpty()
    val firstItemFocus = remember { FocusRequester() }

    // See CategoryListScreen for why this is needed - without it, D-pad
    // focus lands nowhere when this screen opens.
    LaunchedEffect(savedMovies, savedSeries, recordings) {
        if (!isEmpty) {
            firstItemFocus.requestFocus()
        }
    }

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
                    text = if (isEmpty) "No saved movies, shows, or recordings yet - use Search to add some" else "My VOD",
                    color = onBackground,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            itemsIndexed(savedMovies) { index, movie ->
                Box(modifier = Modifier.fillMaxWidth()) {
                    VodRow(
                        movie = movie,
                        isSaved = true,
                        onPlay = { onPlayMovie(movie) },
                        onToggleSaved = { onRemoveMovie(movie) },
                        playCardModifier = if (index == 0) Modifier.focusRequester(firstItemFocus) else Modifier
                    )
                }
            }
            itemsIndexed(savedSeries) { index, series ->
                Box(modifier = Modifier.fillMaxWidth()) {
                    SeriesRow(
                        series = series,
                        isSaved = true,
                        onOpenEpisodes = { onOpenEpisodes(series) },
                        onToggleSaved = { onRemoveSeries(series) },
                        playCardModifier = if (index == 0 && savedMovies.isEmpty()) Modifier.focusRequester(firstItemFocus) else Modifier
                    )
                }
            }
            itemsIndexed(recordings) { index, file ->
                Box(modifier = Modifier.fillMaxWidth()) {
                    RecordingRow(
                        file = file,
                        onPlay = { onPlayRecording(file) },
                        onDelete = { onDeleteRecording(file) },
                        playCardModifier = if (index == 0 && savedMovies.isEmpty() && savedSeries.isEmpty()) {
                            Modifier.focusRequester(firstItemFocus)
                        } else {
                            Modifier
                        }
                    )
                }
            }
        }
    }
}
