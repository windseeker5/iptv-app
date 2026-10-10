package com.kdresdell.iptvtv.phone

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Same idea as the TV app's What's Hot: "what should I watch this weekend".
// Rows of titles from the lists the KD server builds every night (see
// WhatsLists), already matched to this catalog, so the screen only reads
// the local database:
//   Hot = what people watch this week, any year (in the server's order).
//   New = released this year, best IMDb score first.
// English or French (original-language French / Quebec titles), picked with
// the chips at the top.
data class WhatsNewRow(
    val title: String,
    val movies: List<VodStream> = emptyList(),
    val series: List<SeriesShow> = emptyList()
)

data class WhatsNewContent(val english: List<WhatsNewRow>, val french: List<WhatsNewRow>) {
    val isEmpty: Boolean
        get() = english.isEmpty() && french.isEmpty()

    companion object {
        fun load(db: CatalogDatabase): WhatsNewContent {
            fun rows(lang: String, labels: List<String>) = listOf(
                WhatsNewRow(labels[0], movies = db.whatsMovies("hot", lang)),
                WhatsNewRow(labels[1], series = db.whatsSeries("hot", lang)),
                WhatsNewRow(labels[2], movies = db.whatsMovies("new", lang)),
                WhatsNewRow(labels[3], series = db.whatsSeries("new", lang))
            ).filter { it.movies.isNotEmpty() || it.series.isNotEmpty() }
            return WhatsNewContent(
                english = rows("en", listOf("Hot movies", "Hot series", "New movies", "New series")),
                french = rows("fr", listOf("Films populaires", "Séries populaires", "Nouveaux films", "Nouvelles séries"))
            )
        }
    }
}

private val WhatsNewPosterSize = DpSize(116.dp, 174.dp)

@Composable
fun WhatsNewScreen(
    state: LoadState<WhatsNewContent>,
    api: XtreamApi,
    movieFavorites: List<VodStream>,
    seriesFavorites: List<SeriesShow>,
    onToggleMovieFavorite: (VodStream) -> Unit,
    onToggleSeriesFavorite: (SeriesShow) -> Unit,
    onPlay: (NowPlaying) -> Unit,
    onOpenSeries: (SeriesShow) -> Unit,
    whatsStatus: SyncPrefs.WhatsStatus,
    whatsRefreshing: Boolean,
    onRefreshWhats: () -> Unit
) {
    when (state) {
        is LoadState.Loading -> CenteredNote("Loading...")
        is LoadState.Error -> CenteredNote("Could not load Discover - ${state.message}")
        is LoadState.Success -> {
            val data = state.data
            if (data.isEmpty) {
                Column {
                    DiscoverStatusRow(whatsStatus, whatsRefreshing, onRefreshWhats)
                    CenteredNote("Discover is getting ready - come back in a minute.")
                }
                return
            }
            val savedMovies = movieFavorites.map { it.streamId }.toSet()
            val savedSeries = seriesFavorites.map { it.seriesId }.toSet()
            var french by rememberSaveable { mutableStateOf(false) }
            val rows = if (french) data.french else data.english
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                item { DiscoverStatusRow(whatsStatus, whatsRefreshing, onRefreshWhats) }
                item {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(horizontal = 16.dp)
                    ) {
                        FilterChip(selected = !french, onClick = { french = false }, label = { Text("English") })
                        FilterChip(selected = french, onClick = { french = true }, label = { Text("Français") })
                    }
                }
                if (rows.isEmpty()) {
                    item { CenteredNote(if (french) "Aucun titre français de ces listes dans ton catalogue pour l'instant." else "None of this week's English titles are in your catalog yet.") }
                }
                rows.forEach { row ->
                    if (row.movies.isNotEmpty()) {
                        movieRow(row.title, row.movies, savedMovies, onToggleMovieFavorite) { onPlay(api.vodNowPlaying(it)) }
                    } else {
                        seriesRow(row.title, row.series, savedSeries, onToggleSeriesFavorite, onOpenSeries)
                    }
                }
            }
        }
    }
}

// When this phone last fetched the lists from OMT (and when OMT built them),
// with a small Refresh button. A failed fetch shows its error instead.
@Composable
private fun DiscoverStatusRow(status: SyncPrefs.WhatsStatus, refreshing: Boolean, onRefresh: () -> Unit) {
    val format = remember { SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()) }
    fun stamp(millis: Long?) = millis?.let { format.format(Date(it)) } ?: "never"
    val serverBuilt = status.serverGenerated?.takeIf { it.isNotBlank() }?.let { iso ->
        runCatching { format.format(Date(java.time.Instant.parse(iso.replace("+00:00", "Z")).toEpochMilli())) }.getOrNull()
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp)
    ) {
        if (status.error != null) {
            Text(
                text = "Update failed: ${status.error}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        } else {
            Text(
                text = "Updated ${stamp(status.syncedAt)}" + (serverBuilt?.let { " · list $it" } ?: ""),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }
        TextButton(onClick = { if (!refreshing) onRefresh() }, enabled = !refreshing) {
            Text(if (refreshing) "Refreshing..." else "Refresh")
        }
    }
}

private fun LazyListScope.movieRow(
    title: String,
    movies: List<VodStream>,
    saved: Set<Int>,
    onToggleSaved: (VodStream) -> Unit,
    onOpen: (VodStream) -> Unit
) {
    if (movies.isEmpty()) return
    item {
        PosterRow(title) {
            items(movies, key = { it.streamId }) { movie ->
                PosterWithCaption(
                    title = movie.name,
                    cover = movie.streamIcon,
                    imdbRating = movie.imdbRating,
                    isSaved = movie.streamId in saved,
                    onOpen = { onOpen(movie) },
                    onToggleSaved = { onToggleSaved(movie) }
                )
            }
        }
    }
}

private fun LazyListScope.seriesRow(
    title: String,
    shows: List<SeriesShow>,
    saved: Set<Int>,
    onToggleSaved: (SeriesShow) -> Unit,
    onOpen: (SeriesShow) -> Unit
) {
    if (shows.isEmpty()) return
    item {
        PosterRow(title) {
            items(shows, key = { it.seriesId }) { show ->
                PosterWithCaption(
                    title = show.name,
                    cover = show.cover,
                    imdbRating = show.imdbRating,
                    isSaved = show.seriesId in saved,
                    onOpen = { onOpen(show) },
                    onToggleSaved = { onToggleSaved(show) }
                )
            }
        }
    }
}

@Composable
private fun PosterRow(title: String, content: LazyListScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
            content = content
        )
    }
}

// Poster + a two-line caption (the art alone doesn't always say what a title
// is). Tapping the poster plays/opens it; the round badge adds it to My
// Library (a check once it's there).
@Composable
private fun PosterWithCaption(
    title: String,
    cover: String,
    imdbRating: Double,
    isSaved: Boolean,
    onOpen: () -> Unit,
    onToggleSaved: () -> Unit
) {
    val shown = displayTitle(title)
    Column(
        modifier = Modifier.width(WhatsNewPosterSize.width),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box {
            Box(modifier = Modifier.clip(MaterialTheme.shapes.medium).clickable(onClick = onOpen)) {
                Poster(imageUrl = cover, fallbackText = shown, size = WhatsNewPosterSize, isLogo = false)
            }
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(if (isSaved) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.6f))
                    .clickable(onClickLabel = if (isSaved) "Remove from My Library" else "Add to My Library", onClick = onToggleSaved),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (isSaved) Icons.Filled.Check else Icons.Filled.Add,
                    contentDescription = null,
                    tint = if (isSaved) MaterialTheme.colorScheme.onPrimary else Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Text(
            text = shown,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (imdbRating > 0) {
            Text(
                text = String.format(Locale.US, "IMDb %.1f", imdbRating),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
internal fun CenteredNote(text: String) {
    Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

// "EN - Moana (2026)" / "FR - Dune (2024) VOSTFR" / "QC - Loups-Garous (2024)"
// -> "Moana" / "Dune" / "Loups-Garous". Same as the TV app.
private val LANGUAGE_PREFIX = Regex("^(?:EN|FR|QC)[ :.\\-]+")
private val TRAILING_TAGS = Regex("(\\s*\\([^)]*\\))+\\s*$")

private fun displayTitle(raw: String): String {
    val cleaned = TRAILING_TAGS.replace(LANGUAGE_PREFIX.replace(raw, ""), "").trim()
    return cleaned.ifBlank { TitleFormat.clean(raw) }
}
