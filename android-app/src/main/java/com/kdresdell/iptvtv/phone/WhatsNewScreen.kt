package com.kdresdell.iptvtv.phone

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import java.util.Calendar
import java.util.Locale

// Same idea as the TV app's What's New: "what should I watch this weekend".
// Four rows of 10 (English/French x movies/series) - recent releases, best
// IMDb score first. Scores come from the KD server's nightly ratings.json
// (downloaded in MainActivity); this screen only reads what's stored.
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
        fun load(db: CatalogDatabase): WhatsNewContent {
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
    onOpenSeries: (SeriesShow) -> Unit
) {
    when (state) {
        is LoadState.Loading -> CenteredNote("Loading...")
        is LoadState.Error -> CenteredNote("Could not load What's New - ${state.message}")
        is LoadState.Success -> {
            val data = state.data
            if (data.isEmpty) {
                CenteredNote("What's New is getting ready - come back in a few minutes.")
                return
            }
            val savedMovies = movieFavorites.map { it.streamId }.toSet()
            val savedSeries = seriesFavorites.map { it.seriesId }.toSet()
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                movieRow("English movies", data.englishMovies, savedMovies, onToggleMovieFavorite) { onPlay(api.vodNowPlaying(it)) }
                seriesRow("English series", data.englishSeries, savedSeries, onToggleSeriesFavorite, onOpenSeries)
                movieRow("French movies", data.frenchMovies, savedMovies, onToggleMovieFavorite) { onPlay(api.vodNowPlaying(it)) }
                seriesRow("French series", data.frenchSeries, savedSeries, onToggleSeriesFavorite, onOpenSeries)
            }
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
