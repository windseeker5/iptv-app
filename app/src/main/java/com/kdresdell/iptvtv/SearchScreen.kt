package com.kdresdell.iptvtv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme as PhoneMaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text as PhoneText
import androidx.compose.material3.darkColorScheme as phoneDarkColorScheme
import androidx.tv.material3.AssistChip
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.delay

// Xtream has no search endpoint. The full live-channel, VOD, and series
// catalogs are each synced once into local SQLite tables (see
// LiveChannelDatabase) and this screen queries them with SQL LIKE instead
// of holding/filtering a big Kotlin list - a real provider's catalog can be
// huge (tens of thousands of entries), and filtering that in the JVM on
// every keystroke caused real ANRs on real hardware.
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SearchScreen(
    query: String,
    onQueryChange: (String) -> Unit,
    syncState: LoadState<Unit>,
    syncedLiveCount: Int,
    syncedVodCount: Int,
    syncedSeriesCount: Int,
    history: List<String>,
    onSearch: suspend (String) -> List<SearchResult>,
    onRecordHistory: (String) -> Unit,
    onClearHistory: () -> Unit,
    isFavorite: (Int) -> Boolean,
    isMovieSaved: (Int) -> Boolean,
    isSeriesSaved: (Int) -> Boolean,
    onPlayLive: (LiveChannel) -> Unit,
    onPlayVod: (VodStream) -> Unit,
    onOpenEpisodes: (SeriesShow) -> Unit,
    onToggleFavorite: (LiveChannel) -> Unit,
    onToggleMovieSaved: (VodStream) -> Unit,
    onToggleSeriesSaved: (SeriesShow) -> Unit
) {
    var results by remember { mutableStateOf<List<SearchResult>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    val onBackground = MaterialTheme.colorScheme.onBackground
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    // As soon as the local index is ready, jump straight into the text
    // field and pop the keyboard - no reason to make the user navigate to
    // it and press select first.
    LaunchedEffect(syncState) {
        if (syncState is LoadState.Success) {
            focusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    LaunchedEffect(query, syncState) {
        if (syncState !is LoadState.Success || query.isBlank()) {
            isSearching = false
            results = emptyList()
            return@LaunchedEffect
        }
        isSearching = true
        delay(300) // debounce - only search once typing pauses
        results = onSearch(query)
        isSearching = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        PhoneMaterialTheme(colorScheme = phoneDarkColorScheme()) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                label = { PhoneText("Search live channels, movies & shows") },
                singleLine = true,
                enabled = syncState is LoadState.Success,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                // History only records an explicit, finished search term -
                // not every intermediate keystroke the debounce above
                // happens to search on while the user is still typing.
                keyboardActions = KeyboardActions(onSearch = { onRecordHistory(query) }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
            )
        }

        // History pills only make sense before a query is typed - once
        // results are showing, this row would just compete with them.
        if (query.isBlank() && history.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(history) { term ->
                    AssistChip(onClick = { onQueryChange(term) }) { Text(term) }
                }
                item {
                    AssistChip(onClick = onClearHistory) { Text("🗑 Clear history") }
                }
            }
        }

        when (syncState) {
            is LoadState.Loading -> Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PhoneMaterialTheme(colorScheme = phoneDarkColorScheme()) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                }
                Text(
                    text = "Preparing search - indexed $syncedLiveCount channels, " +
                        "$syncedVodCount movies, $syncedSeriesCount shows so far...",
                    color = onBackground
                )
            }
            is LoadState.Error -> Text(text = "Could not load channels: ${syncState.message}", color = onBackground)
            is LoadState.Success -> {
                if (isSearching) {
                    Text(text = "Searching...", color = onBackground)
                } else if (query.isNotBlank() && results.isEmpty()) {
                    Text(text = "No matches", color = onBackground)
                }
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(results) { result ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            when (result) {
                                is SearchResult.Live -> {
                                    ResultTypeBadge(label = "LIVE", tonalColor = MaterialTheme.colorScheme.primaryContainer)
                                    Box(modifier = Modifier.weight(1f)) {
                                        ChannelRow(
                                            channel = result.channel,
                                            isFavorite = isFavorite(result.channel.streamId),
                                            onPlay = { onPlayLive(result.channel) },
                                            onToggleFavorite = { onToggleFavorite(result.channel) }
                                        )
                                    }
                                }
                                is SearchResult.Vod -> {
                                    ResultTypeBadge(label = "MOVIE", tonalColor = MaterialTheme.colorScheme.secondaryContainer)
                                    Box(modifier = Modifier.weight(1f)) {
                                        VodRow(
                                            movie = result.movie,
                                            isSaved = isMovieSaved(result.movie.streamId),
                                            onPlay = { onPlayVod(result.movie) },
                                            onToggleSaved = { onToggleMovieSaved(result.movie) }
                                        )
                                    }
                                }
                                is SearchResult.Series -> {
                                    ResultTypeBadge(label = "SERIES", tonalColor = MaterialTheme.colorScheme.tertiaryContainer)
                                    Box(modifier = Modifier.weight(1f)) {
                                        SeriesRow(
                                            series = result.series,
                                            isSaved = isSeriesSaved(result.series.seriesId),
                                            onOpenEpisodes = { onOpenEpisodes(result.series) },
                                            onToggleSaved = { onToggleSeriesSaved(result.series) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
