package com.kdresdell.iptvtv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import java.util.Calendar
import java.util.Locale

// What's New - a "what should I watch this weekend" screen for someone whose
// only source is the provider. Four rows of 10 (English/French x movies/
// series): recent releases, best IMDb score first. The scores are downloaded
// in the background once a day (CatalogRefresher); this screen only reads what
// is already stored, so it opens instantly and never does any lookup.
data class WhatsNewContent(
    val englishMovies: List<VodStream>,
    val englishSeries: List<SeriesShow>,
    val frenchMovies: List<VodStream>,
    val frenchSeries: List<SeriesShow>
) {
    val isEmpty: Boolean
        get() = englishMovies.isEmpty() && englishSeries.isEmpty() && frenchMovies.isEmpty() && frenchSeries.isEmpty()

    companion object {
        // Same window as the server job (this year and the two before).
        fun load(db: LiveChannelDatabase): WhatsNewContent {
            val minYear = Calendar.getInstance().get(Calendar.YEAR) - 2
            return WhatsNewContent(
                englishMovies = db.topMovies(WhatsNewLanguage.English, minYear),
                englishSeries = db.topSeries(WhatsNewLanguage.English, minYear),
                frenchMovies = db.topMovies(WhatsNewLanguage.French, minYear),
                frenchSeries = db.topSeries(WhatsNewLanguage.French, minYear)
            )
        }
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
    onToggleSeriesSaved: (SeriesShow) -> Unit
) {
    val onBackground = MaterialTheme.colorScheme.onBackground
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val firstItemFocus = remember { FocusRequester() }
    val content = (state as? LoadState.Success)?.data

    // See CategoryListScreen for why this is needed - without it, D-pad
    // focus lands nowhere when this screen opens.
    LaunchedEffect(content) {
        if (content != null && !content.isEmpty) {
            firstItemFocus.requestFocus()
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
                text = "Could not load What's New - ${state.message}",
                color = onSurfaceVariant,
                modifier = Modifier.align(Alignment.Center)
            )

            is LoadState.Success -> {
                val data = state.data
                if (data.isEmpty) {
                    Text(
                        text = "What's New is getting ready - come back in a few minutes",
                        color = onSurfaceVariant,
                        modifier = Modifier.align(Alignment.Center)
                    )
                    return@Box
                }
                // The first card of the first row that has any gets the
                // initial focus.
                val focusRow = listOf(
                    data.englishMovies.isNotEmpty(), data.englishSeries.isNotEmpty(),
                    data.frenchMovies.isNotEmpty(), data.frenchSeries.isNotEmpty()
                ).indexOf(true)
                fun focusFor(row: Int, isFirst: Boolean): Modifier =
                    if (row == focusRow && isFirst) Modifier.focusRequester(firstItemFocus) else Modifier

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                    // 36 + the rows' own 12 = the app's usual 48dp gutter; the extra 12
                    // in the rows leaves room for a focused card's scale-up.
                    contentPadding = PaddingValues(horizontal = 36.dp, vertical = 24.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    item {
                        Text(text = "What's New", style = whatsNewTitleStyle(), color = onBackground, modifier = Modifier.padding(start = 12.dp))
                    }
                    movieRow("English movies", data.englishMovies, 0, ::focusFor, isMovieSaved, onPlayMovie, onToggleMovieSaved)
                    seriesRow("English series", data.englishSeries, 1, ::focusFor, isSeriesSaved, onOpenEpisodes, onToggleSeriesSaved)
                    movieRow("French movies", data.frenchMovies, 2, ::focusFor, isMovieSaved, onPlayMovie, onToggleMovieSaved)
                    seriesRow("French series", data.frenchSeries, 3, ::focusFor, isSeriesSaved, onOpenEpisodes, onToggleSeriesSaved)
                }
            }
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
