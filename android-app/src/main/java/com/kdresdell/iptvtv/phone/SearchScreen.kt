package com.kdresdell.iptvtv.phone

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

// Search runs against the local catalog DB (already synced), like the TV
// app - Xtream has no server-side search endpoint. Debounced so it's not
// re-querying on every keystroke.
@Composable
fun SearchScreen(
    db: CatalogDatabase,
    api: XtreamApi,
    liveFavorites: List<LiveChannel>,
    vodFavorites: List<VodStream>,
    seriesFavorites: List<SeriesShow>,
    onToggleLiveFavorite: (LiveChannel) -> Unit,
    onToggleMovieFavorite: (VodStream) -> Unit,
    onToggleSeriesFavorite: (SeriesShow) -> Unit,
    stats: CatalogStats,
    lastRefreshLabel: String,
    onRefresh: () -> Unit,
    onPlay: (NowPlaying) -> Unit,
    onOpenSeries: (SeriesShow) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<SearchResult>>(emptyList()) }
    // Query the current results belong to - the summary line only shows
    // once a search has actually run, not while the debounce is pending.
    var searchedQuery by remember { mutableStateOf("") }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current
    val historyStore = remember { SearchHistoryStore(context) }
    var history by remember { mutableStateOf(historyStore.load()) }
    // A term goes into history when the search is "committed": the
    // keyboard/glass Search, or tapping one of its results (most people
    // never press Search - results update as they type).
    val recordSearch = { if (query.isNotBlank()) history = historyStore.add(query) }
    val closeKeyboard = {
        recordSearch()
        keyboard?.hide()
        focusManager.clearFocus()
    }

    LaunchedEffect(query) {
        if (query.isBlank()) {
            results = emptyList()
            searchedQuery = ""
            return@LaunchedEffect
        }
        delay(300)
        results = withContext(Dispatchers.IO) { db.searchAll(query) }
        searchedQuery = query
    }

    Column(modifier = Modifier.fillMaxSize()) {
        SearchKpis(stats = stats, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp))
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Last refresh from provider: $lastRefreshLabel",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onRefresh) {
                Icon(
                    Icons.Filled.Refresh,
                    contentDescription = "Refresh list from provider",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Search live TV, movies, series") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            // Tapping the glass (or the keyboard's Search key) closes the
            // keyboard - results already update as you type.
            trailingIcon = {
                Row {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Filled.Clear, contentDescription = "Clear search")
                        }
                    }
                    IconButton(onClick = closeKeyboard) {
                        Icon(
                            Icons.Filled.Search,
                            contentDescription = "Search",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { closeKeyboard() }),
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp)
        )
        if (searchedQuery.isNotBlank()) {
            SearchSummary(results)
        }
        if (query.isBlank() && history.isNotEmpty()) {
            RecentSearches(
                history = history,
                onPick = { term ->
                    query = term
                    history = historyStore.add(term)
                    keyboard?.hide()
                    focusManager.clearFocus()
                },
                onRemove = { term -> history = historyStore.remove(term) },
                onClearAll = { history = historyStore.clear() }
            )
        }
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(results) { result ->
                when (result) {
                    is SearchResult.Live -> ResultRow(
                        title = result.channel.name,
                        isFavorite = liveFavorites.any { it.streamId == result.channel.streamId },
                        onClick = {
                            recordSearch()
                            onPlay(api.liveNowPlaying(result.channel))
                        },
                        onToggleFavorite = { onToggleLiveFavorite(result.channel) },
                        favoriteLabel = "My TV"
                    )
                    is SearchResult.Vod -> ResultRow(
                        title = "${result.movie.name} (movie)",
                        isFavorite = vodFavorites.any { it.streamId == result.movie.streamId },
                        onClick = {
                            recordSearch()
                            onPlay(
                                api.vodNowPlaying(result.movie)
                            )
                        },
                        onToggleFavorite = { onToggleMovieFavorite(result.movie) },
                        favoriteLabel = "Library"
                    )
                    is SearchResult.Series -> ResultRow(
                        title = "${result.series.name} (series)",
                        isFavorite = seriesFavorites.any { it.seriesId == result.series.seriesId },
                        onClick = {
                            recordSearch()
                            onOpenSeries(result.series)
                        },
                        onToggleFavorite = { onToggleSeriesFavorite(result.series) },
                        favoriteLabel = "Library"
                    )
                }
            }
        }
    }
}

@Composable
private fun ResultRow(
    title: String,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    favoriteLabel: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(title, modifier = Modifier.weight(1f))
        Button(onClick = onToggleFavorite) {
            Text(if (isFavorite) "Remove" else "+ $favoriteLabel")
        }
    }
}

// Catalog counts + provider account dates for the KPI tiles.
data class CatalogStats(
    val liveCount: Int = 0,
    val seriesCount: Int = 0,
    val vodCount: Int = 0,
    val account: AccountInfo = AccountInfo(null, null)
)

// Same tiles as the TV app's SearchKpiRow, laid out for a phone: the three
// counts share a row, and the Expires tile gets its own full-width row
// under them so its date and progress bar aren't squeezed.
@Composable
private fun SearchKpis(stats: CatalogStats, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            KpiTile(stats.liveCount.toString(), "Live Channels", Modifier.weight(1f).fillMaxHeight())
            KpiTile(stats.seriesCount.toString(), "TV Shows", Modifier.weight(1f).fillMaxHeight())
            KpiTile(stats.vodCount.toString(), "Movies", Modifier.weight(1f).fillMaxHeight())
        }
        // Lifetime accounts have no exp_date - nothing to show then.
        val createdAt = stats.account.createdAtEpochSeconds
        val expiresAt = stats.account.expDateEpochSeconds
        if (expiresAt != null) {
            val expiresLabel = remember(expiresAt) {
                SimpleDateFormat("dd/MM/yyyy", Locale.US).format(Date(expiresAt * 1000))
            }
            // Elapsed fraction of the subscription, as on TV - only when
            // both dates exist and are in order.
            val progress = if (createdAt != null && expiresAt > createdAt) {
                val nowSeconds = System.currentTimeMillis() / 1000
                ((nowSeconds - createdAt).toFloat() / (expiresAt - createdAt).toFloat()).coerceIn(0f, 1f)
            } else {
                null
            }
            KpiTile(expiresLabel, "Subscription expires", Modifier.fillMaxWidth(), progress)
        }
    }
}

@Composable
private fun KpiTile(value: String, label: String, modifier: Modifier = Modifier, progressFraction: Float? = null) {
    val tileShape = RoundedCornerShape(10.dp)
    Column(
        modifier = modifier
            .clip(tileShape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(1.dp, Alignment.CenterVertically)
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (progressFraction != null) {
            val trackShape = RoundedCornerShape(50)
            Box(
                modifier = Modifier
                    .padding(top = 6.dp)
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(trackShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(progressFraction)
                        .clip(trackShape)
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
        }
    }
}

// "23 results - 8 live - 10 movies - 5 series" above the list. searchAll
// caps each kind at 50, so a full bucket reads "50+".
@Composable
private fun SearchSummary(results: List<SearchResult>) {
    val live = results.count { it is SearchResult.Live }
    val movies = results.count { it is SearchResult.Vod }
    val series = results.count { it is SearchResult.Series }
    fun label(n: Int) = if (n >= SEARCH_LIMIT_PER_KIND) "$n+" else "$n"
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (results.isEmpty()) "No results" else "${label(results.size)} results",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        if (results.isNotEmpty()) {
            SummaryChip("${label(live)} live")
            SummaryChip("${label(movies)} movies")
            SummaryChip("${label(series)} series")
        }
    }
}

@Composable
private fun SummaryChip(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    )
}

private const val SEARCH_LIMIT_PER_KIND = 50

// Past searches as pills under the box (only while it's empty): tap one to
// search it again, X to forget it, or clear them all.
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RecentSearches(
    history: List<String>,
    onPick: (String) -> Unit,
    onRemove: (String) -> Unit,
    onClearAll: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Recent searches",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onClearAll) { Text("Clear all") }
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            history.forEach { term ->
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(50))
                        .clickable { onPick(term) }
                        .padding(start = 14.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(term, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    IconButton(onClick = { onRemove(term) }, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "Remove $term from history",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
