package com.kdresdell.iptvtv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
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

// §6.6 My Librairie - saved movies and series, poster-grid browse screen
// (separate from live-channel My TV/Favorites, since VOD playback is a
// different flow: direct play for movies, an episode picker for series).
// Recordings aren't part of the approved mockup (poster art doesn't exist
// for them) - kept as a plain row section below the poster grids, same
// treatment it always had.
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
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val isEmpty = savedMovies.isEmpty() && savedSeries.isEmpty() && recordings.isEmpty()
    val firstItemFocus = remember { FocusRequester() }
    val totalCount = savedMovies.size + savedSeries.size + recordings.size

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
            .background(MaterialTheme.colorScheme.background)
    ) {
        if (isEmpty) {
            Text(
                text = "No saved movies, shows, or recordings yet - use Search to add some",
                color = onSurfaceVariant,
                modifier = Modifier.align(Alignment.Center)
            )
            return@Box
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(28.dp),
            contentPadding = PaddingValues(horizontal = 48.dp, vertical = 24.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(text = "My Librairie", style = libraryTitleStyle(), color = onBackground, modifier = Modifier.alignByBaseline())
                    Text(text = "$totalCount titles", style = MaterialTheme.typography.labelMedium, color = onSurfaceVariant, modifier = Modifier.alignByBaseline())
                }
            }
            if (savedMovies.isNotEmpty()) {
                item {
                    PosterSection(
                        title = "Movies",
                        count = savedMovies.size,
                        headingColor = onBackground,
                        countColor = onSurfaceVariant
                    ) {
                        savedMovies.forEachIndexed { index, movie ->
                            PosterCard(
                                title = TitleFormat.clean(movie.name),
                                cover = movie.streamIcon,
                                isSaved = true,
                                onOpen = { onPlayMovie(movie) },
                                onToggleSaved = { onRemoveMovie(movie) },
                                cardModifier = if (index == 0) Modifier.focusRequester(firstItemFocus) else Modifier
                            )
                        }
                    }
                }
            }
            if (savedSeries.isNotEmpty()) {
                item {
                    PosterSection(
                        title = "Series",
                        count = savedSeries.size,
                        headingColor = onBackground,
                        countColor = onSurfaceVariant
                    ) {
                        savedSeries.forEachIndexed { index, series ->
                            PosterCard(
                                title = TitleFormat.clean(series.name),
                                cover = series.cover,
                                isSaved = true,
                                onOpen = { onOpenEpisodes(series) },
                                onToggleSaved = { onRemoveSeries(series) },
                                cardModifier = if (index == 0 && savedMovies.isEmpty()) {
                                    Modifier.focusRequester(firstItemFocus)
                                } else {
                                    Modifier
                                }
                            )
                        }
                    }
                }
            }
            if (recordings.isNotEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(text = "Recordings", style = librarySectionHeaderStyle(), color = onBackground, modifier = Modifier.alignByBaseline())
                            Text(text = "${recordings.size}", style = MaterialTheme.typography.labelMedium, color = onSurfaceVariant, modifier = Modifier.alignByBaseline())
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            recordings.forEachIndexed { index, file ->
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
        }
    }
}

// §6.6 - poster-grid section: header + count, then a wrapping 5-card grid
// that grows downward as the library grows.
@Composable
private fun PosterSection(
    title: String,
    count: Int,
    headingColor: androidx.compose.ui.graphics.Color,
    countColor: androidx.compose.ui.graphics.Color,
    content: @Composable () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = title, style = librarySectionHeaderStyle(), color = headingColor, modifier = Modifier.alignByBaseline())
            Text(text = "$count", style = MaterialTheme.typography.labelMedium, color = countColor, modifier = Modifier.alignByBaseline())
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            content()
        }
    }
}

// Explicit user request (2026-09-17): the screen title and section headers
// read too large, and the oversized headers combined with no bottom scroll
// room meant the last poster row's bottom edge got clipped with no way to
// scroll further to see it. Trimming these (and PosterCardWidth below, and
// the contentPadding fix above) address both the cosmetic and scroll bugs.
@Composable
private fun libraryTitleStyle() = MaterialTheme.typography.headlineLarge.let {
    it.copy(fontSize = it.fontSize * 0.75f, lineHeight = it.lineHeight * 0.75f)
}

@Composable
private fun librarySectionHeaderStyle() = MaterialTheme.typography.headlineSmall.let {
    it.copy(fontSize = it.fontSize * 0.85f, lineHeight = it.lineHeight * 0.85f)
}
