package com.kdresdell.iptvtv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.kdresdell.iptvtv.theme.LocalAppColors
import java.util.Locale

// What's Hot / What's New - "what should I watch this weekend" for someone
// whose only source is the provider. Rows of titles from the lists the KD
// server builds every night (see WhatsLists), already matched to this
// catalog in the background, so the screen only reads the local database:
//   Hot = what people watch this week, any year (in the server's order).
//   New = released this year, best IMDb score first.
// English first, then French (original-language French / Quebec titles).
data class WhatsNewRow(
    val title: String,
    val movies: List<VodStream> = emptyList(),
    val series: List<SeriesShow> = emptyList()
)

data class WhatsNewContent(val rows: List<WhatsNewRow>) {
    val isEmpty: Boolean
        get() = rows.all { it.movies.isEmpty() && it.series.isEmpty() }

    companion object {
        fun load(db: LiveChannelDatabase): WhatsNewContent = WhatsNewContent(
            listOf(
                WhatsNewRow("Hot movies", movies = db.whatsMovies("hot", "en")),
                WhatsNewRow("Hot series", series = db.whatsSeries("hot", "en")),
                WhatsNewRow("New movies", movies = db.whatsMovies("new", "en")),
                WhatsNewRow("New series", series = db.whatsSeries("new", "en")),
                WhatsNewRow("Films populaires (français)", movies = db.whatsMovies("hot", "fr")),
                WhatsNewRow("Séries populaires (français)", series = db.whatsSeries("hot", "fr")),
                WhatsNewRow("Nouveaux films (français)", movies = db.whatsMovies("new", "fr")),
                WhatsNewRow("Nouvelles séries (français)", series = db.whatsSeries("new", "fr"))
            ).filter { it.movies.isNotEmpty() || it.series.isNotEmpty() }
        )
    }
}

@Composable
fun WhatsNewScreen(
    state: LoadState<WhatsNewContent>,
    isMovieSaved: (Int) -> Boolean,
    isSeriesSaved: (Int) -> Boolean,
    onPlayMovie: (VodStream) -> Unit,
    onOpenEpisodes: (SeriesShow) -> Unit,
    onToggleMovieSaved: (VodStream) -> Unit,
    onToggleSeriesSaved: (SeriesShow) -> Unit,
    whatsStatus: LiveChannelDatabase.WhatsStatus? = null,
    whatsRefreshing: Boolean = false,
    onRefreshWhats: () -> Unit = {}
) {
    val onBackground = MaterialTheme.colorScheme.onBackground
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val firstItemFocus = remember { FocusRequester() }
    val content = (state as? LoadState.Success)?.data

    // See CategoryListScreen for why this is needed - without it, D-pad
    // focus lands nowhere when this screen opens.
    LaunchedEffect(content) {
        if (content != null && !content.isEmpty) {
            // On a slow box the first card may not be laid out yet; a missed
            // focus is harmless, a crash is not.
            runCatching { firstItemFocus.requestFocus() }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        when (state) {
            is LoadState.Loading -> Text(
                text = "Loading",
                color = onSurfaceVariant,
                modifier = Modifier.align(Alignment.Center)
            )

            is LoadState.Error -> Text(
                text = "Could not load Discover - ${state.message}",
                color = onSurfaceVariant,
                modifier = Modifier.align(Alignment.Center)
            )

            is LoadState.Success -> {
                val data = state.data
                if (data.isEmpty) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        DiscoverHeader(whatsStatus, whatsRefreshing, onRefreshWhats)
                        Text(
                            text = "What's Hot is getting ready - your movie and series lists need to finish downloading first",
                            color = onSurfaceVariant
                        )
                    }
                    return@Box
                }
                // Empty rows are already dropped, so the first card of the
                // first row gets the initial focus.
                fun focusFor(row: Int, isFirst: Boolean): Modifier =
                    if (row == 0 && isFirst) Modifier.focusRequester(firstItemFocus) else Modifier

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                    // 36 + the rows' own 12 = the app's usual 48dp gutter; the extra 12
                    // in the rows leaves room for a focused card's scale-up.
                    contentPadding = PaddingValues(horizontal = 36.dp, vertical = 24.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    item {
                        DiscoverHeader(whatsStatus, whatsRefreshing, onRefreshWhats, Modifier.padding(horizontal = 12.dp))
                    }
                    data.rows.forEachIndexed { index, row ->
                        if (row.movies.isNotEmpty()) {
                            movieRow(row.title, row.movies, index, ::focusFor, isMovieSaved, onPlayMovie, onToggleMovieSaved)
                        } else {
                            seriesRow(row.title, row.series, index, ::focusFor, isSeriesSaved, onOpenEpisodes, onToggleSeriesSaved)
                        }
                    }
                }
            }
        }
    }
}

// Screen title with, on the right, when this device last fetched the lists
// from OMT (and when OMT built them) and a small Refresh button. A failed
// fetch shows its error in red instead.
@Composable
private fun DiscoverHeader(
    status: LiveChannelDatabase.WhatsStatus?,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    val format = remember { java.text.SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()) }
    fun stamp(millis: Long?) = millis?.let { format.format(java.util.Date(it)) } ?: "never"
    val serverBuilt = status?.serverGenerated?.takeIf { it.isNotBlank() }?.let { iso ->
        runCatching { format.format(java.util.Date(java.time.Instant.parse(iso.replace("+00:00", "Z")).toEpochMilli())) }.getOrNull()
    }
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = "Old Man Tricks", style = whatsNewTitleStyle(), color = MaterialTheme.colorScheme.onBackground)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            if (status?.error != null) {
                Text(
                    text = "Update failed: ${status.error}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 420.dp)
                )
            } else {
                Text(
                    text = "Updated ${stamp(status?.syncedAt)}" + (serverBuilt?.let { " · list $it" } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
            PrimaryActionButton(
                text = if (refreshing) "Refreshing..." else "Refresh",
                onClick = { if (!refreshing) onRefresh() },
                compact = true
            )
        }
    }
}

private fun LazyListScope.movieRow(
    title: String,
    movies: List<VodStream>,
    row: Int,
    focusFor: (Int, Boolean) -> Modifier,
    isSaved: (Int) -> Boolean,
    onPlay: (VodStream) -> Unit,
    onToggleSaved: (VodStream) -> Unit
) {
    if (movies.isEmpty()) return
    item {
        PosterRow(title = title) {
            itemsIndexed(movies, key = { _, movie -> movie.streamId }) { index, movie ->
                PosterWithCaption(
                    title = movie.name,
                    cover = movie.streamIcon,
                    imdbRating = movie.imdbRating,
                    isSaved = isSaved(movie.streamId),
                    onOpen = { onPlay(movie) },
                    onToggleSaved = { onToggleSaved(movie) },
                    cardModifier = focusFor(row, index == 0)
                )
            }
        }
    }
}

private fun LazyListScope.seriesRow(
    title: String,
    shows: List<SeriesShow>,
    row: Int,
    focusFor: (Int, Boolean) -> Modifier,
    isSaved: (Int) -> Boolean,
    onOpen: (SeriesShow) -> Unit,
    onToggleSaved: (SeriesShow) -> Unit
) {
    if (shows.isEmpty()) return
    item {
        PosterRow(title = title) {
            itemsIndexed(shows, key = { _, show -> show.seriesId }) { index, show ->
                PosterWithCaption(
                    title = show.name,
                    cover = show.cover,
                    imdbRating = show.imdbRating,
                    isSaved = isSaved(show.seriesId),
                    onOpen = { onOpen(show) },
                    onToggleSaved = { onToggleSaved(show) },
                    cardModifier = focusFor(row, index == 0)
                )
            }
        }
    }
}

@Composable
private fun PosterRow(
    title: String,
    content: LazyListScope.() -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = title,
            style = whatsNewSectionStyle(),
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(start = 12.dp)
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            // Room for the focused card's scale-up so it isn't clipped.
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            content = content
        )
    }
}

// A PosterCard plus a two-line caption: the provider's poster art alone
// isn't always enough to tell what a title is when you're choosing.
@Composable
private fun PosterWithCaption(
    title: String,
    cover: String,
    imdbRating: Double,
    isSaved: Boolean,
    onOpen: () -> Unit,
    onToggleSaved: () -> Unit,
    cardModifier: Modifier
) {
    val shown = displayTitle(title)
    Column(
        modifier = Modifier.width(PosterCardWidth),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        PosterCard(
            title = shown,
            cover = cover,
            isSaved = isSaved,
            onOpen = onOpen,
            onToggleSaved = onToggleSaved,
            cardModifier = cardModifier
        )
        Text(
            text = shown,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (imdbRating > 0) {
            Text(
                text = String.format(Locale.US, "IMDb %.1f", imdbRating),
                style = MaterialTheme.typography.labelSmall,
                color = LocalAppColors.current.vividAccent
            )
        }
    }
}

// "EN - Moana (2026)" / "FR - Dune (2024) VOSTFR" / "QC - Loups-Garous (2024)"
// -> "Moana" / "Dune" / "Loups-Garous".
private val LANGUAGE_PREFIX = Regex("^(?:EN|FR|QC)[ :.\\-]+")
private val TRAILING_TAGS = Regex("(\\s*\\([^)]*\\))+\\s*$")

private fun displayTitle(raw: String): String {
    val cleaned = TRAILING_TAGS.replace(LANGUAGE_PREFIX.replace(raw, ""), "").trim()
    return cleaned.ifBlank { TitleFormat.clean(raw) }
}

@Composable
private fun whatsNewTitleStyle() = MaterialTheme.typography.headlineLarge.let {
    it.copy(fontSize = it.fontSize * 0.75f, lineHeight = it.lineHeight * 0.75f)
}

@Composable
private fun whatsNewSectionStyle() = MaterialTheme.typography.headlineSmall.let {
    it.copy(fontSize = it.fontSize * 0.85f, lineHeight = it.lineHeight * 0.85f)
}
