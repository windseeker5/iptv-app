package com.kdresdell.iptvtv.phone

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Keys for the per-screen description map - a movie and a series can share
// a numeric id.
private fun movieKey(id: Int) = "vod:$id"
private fun seriesKey(id: Int) = "series:$id"

@Composable
fun MyLibraryScreen(
    movies: List<VodStream>,
    series: List<SeriesShow>,
    db: CatalogDatabase,
    api: XtreamApi,
    onPlay: (NowPlaying) -> Unit,
    onOpenSeries: (SeriesShow) -> Unit,
    onRemoveMovie: (VodStream) -> Unit,
    onRemoveSeries: (SeriesShow) -> Unit
) {
    var movieCategories by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var seriesCategories by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var descriptions by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

    // Synopses aren't in the catalog lists - each needs its own call, so
    // they're cached in the DB and only fetched for items saved here.
    // Cached ones show immediately; missing ones fill in one at a time.
    LaunchedEffect(movies, series) {
        withContext(Dispatchers.IO) {
            movieCategories = db.categoryNames(CatalogKind.VOD)
            seriesCategories = db.categoryNames(CatalogKind.SERIES)
            val cached = HashMap<String, String>()
            movies.forEach { m -> db.cachedDescription(CatalogKind.VOD, m.streamId)?.let { cached[movieKey(m.streamId)] = it } }
            series.forEach { s -> db.cachedDescription(CatalogKind.SERIES, s.seriesId)?.let { cached[seriesKey(s.seriesId)] = it } }
            descriptions = cached
        }
        for (movie in movies) {
            if (movieKey(movie.streamId) in descriptions) continue
            val text = api.getVodDescription(movie.streamId)
            if (text != null) withContext(Dispatchers.IO) { db.saveDescription(CatalogKind.VOD, movie.streamId, text) }
            descriptions = descriptions + (movieKey(movie.streamId) to (text ?: ""))
        }
        for (show in series) {
            if (seriesKey(show.seriesId) in descriptions) continue
            val text = api.getSeriesDescription(show.seriesId)
            if (text != null) withContext(Dispatchers.IO) { db.saveDescription(CatalogKind.SERIES, show.seriesId, text) }
            descriptions = descriptions + (seriesKey(show.seriesId) to (text ?: ""))
        }
    }

    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    // Removing is one tap on a small icon, so offer Undo (re-adding puts the
    // item back at the end of its section).
    fun removeWithUndo(name: String, remove: () -> Unit) {
        remove()
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            val result = snackbar.showSnackbar(
                "Removed ${TitleFormat.clean(name)}",
                actionLabel = "Undo",
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) remove()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
    if (movies.isEmpty() && series.isEmpty()) {
        Text("Nothing saved yet - find movies/series in Search and tap \"+ Library\".", modifier = Modifier.padding(16.dp))
    } else LazyColumn(modifier = Modifier.fillMaxSize()) {
        if (movies.isNotEmpty()) {
            item { SectionHeader("Movies", movies.size) }
            items(movies, key = { movieKey(it.streamId) }) { movie ->
                MediaRow(
                    imageUrl = movie.streamIcon,
                    title = TitleFormat.clean(movie.name),
                    category = movieCategories[movie.categoryId] ?: "",
                    description = descriptions[movieKey(movie.streamId)] ?: "Loading description...",
                    onClick = {
                        onPlay(api.vodNowPlaying(movie.name, movie.streamId, movie.containerExtension))
                    },
                    onRemove = { removeWithUndo(movie.name) { onRemoveMovie(movie) } }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
        if (series.isNotEmpty()) {
            item { SectionHeader("Series", series.size) }
            items(series, key = { seriesKey(it.seriesId) }) { show ->
                MediaRow(
                    imageUrl = show.cover,
                    title = TitleFormat.clean(show.name),
                    category = seriesCategories[show.categoryId] ?: "",
                    description = descriptions[seriesKey(show.seriesId)] ?: "Loading description...",
                    onClick = { onOpenSeries(show) },
                    onRemove = { removeWithUndo(show.name) { onRemoveSeries(show) } }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
    SnackbarHost(snackbar, modifier = Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun SectionHeader(title: String, count: Int) {
    Text(
        text = "$title ($count)",
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp)
    )
}

@Composable
fun EpisodePickerScreen(
    series: SeriesShow,
    api: XtreamApi,
    onPlay: (NowPlaying) -> Unit,
    onBack: () -> Unit
) {
    var episodes by remember { mutableStateOf<LoadState<List<SeriesEpisode>>>(LoadState.Loading) }

    LaunchedEffect(series) {
        episodes = try {
            LoadState.Success(api.getSeriesEpisodes(series.seriesId))
        } catch (e: XtreamApiException) {
            LoadState.Error(e.message ?: "Failed to load episodes")
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Button(onClick = onBack) { Text("Back") }
        Text(series.name, modifier = Modifier.padding(16.dp))
        when (val state = episodes) {
            is LoadState.Loading -> Text("Loading episodes...", modifier = Modifier.padding(16.dp))
            is LoadState.Error -> Text("Error: ${state.message}", modifier = Modifier.padding(16.dp))
            is LoadState.Success -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(state.data) { episode ->
                    Text(
                        text = "S${episode.season}E${episode.episodeNum} - ${episode.title}",
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onPlay(
                                    api.episodeNowPlaying(
                                        "${series.name} S${episode.season}E${episode.episodeNum}",
                                        episode.episodeId,
                                        episode.containerExtension
                                    )
                                )
                            }
                            .padding(16.dp)
                    )
                }
            }
        }
    }
}
