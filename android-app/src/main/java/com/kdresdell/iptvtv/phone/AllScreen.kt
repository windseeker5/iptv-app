package com.kdresdell.iptvtv.phone

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// The provider's raw list, like the TV app's "All": pick Live TV / Movies /
// Series, then a category, then play (or open) anything in it. Everything
// is read from the local catalog, in the provider's own order.
@Composable
fun AllScreen(
    db: CatalogDatabase,
    api: XtreamApi,
    catalogVersion: Int,
    liveFavorites: List<LiveChannel>,
    movieFavorites: List<VodStream>,
    seriesFavorites: List<SeriesShow>,
    onToggleLiveFavorite: (LiveChannel) -> Unit,
    onToggleMovieFavorite: (VodStream) -> Unit,
    onToggleSeriesFavorite: (SeriesShow) -> Unit,
    onPlay: (NowPlaying) -> Unit,
    onOpenSeries: (SeriesShow) -> Unit
) {
    // Saveable so coming back from the player lands in the same category.
    var kind by rememberSaveable { mutableStateOf(CatalogKind.LIVE) }
    var openCategoryId by rememberSaveable { mutableStateOf<String?>(null) }
    var openCategoryName by rememberSaveable { mutableStateOf("") }

    BackHandler(enabled = openCategoryId != null) { openCategoryId = null }

    Column(modifier = Modifier.fillMaxSize()) {
        val categoryId = openCategoryId
        if (categoryId == null) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                KindChip("Live TV", kind == CatalogKind.LIVE) { kind = CatalogKind.LIVE }
                KindChip("Movies", kind == CatalogKind.VOD) { kind = CatalogKind.VOD }
                KindChip("Series", kind == CatalogKind.SERIES) { kind = CatalogKind.SERIES }
            }
            CategoryList(db = db, kind = kind, catalogVersion = catalogVersion) { category ->
                openCategoryId = category.categoryId
                openCategoryName = category.name
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().padding(end = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { openCategoryId = null }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to categories")
                }
                Text(
                    openCategoryName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            when (kind) {
                CatalogKind.LIVE -> {
                    val saved = liveFavorites.map { it.streamId }.toSet()
                    ItemList(
                        load = { db.liveChannelsIn(categoryId) },
                        key = { it.streamId },
                        catalogVersion = catalogVersion
                    ) { channel ->
                        BrowseRow(
                            imageUrl = channel.streamIcon,
                            title = TitleFormat.clean(channel.name),
                            isLogo = true,
                            isSaved = channel.streamId in saved,
                            saveLabel = "My TV",
                            onClick = { onPlay(api.liveNowPlaying(channel)) },
                            onToggleSaved = { onToggleLiveFavorite(channel) }
                        )
                    }
                }
                CatalogKind.VOD -> {
                    val saved = movieFavorites.map { it.streamId }.toSet()
                    ItemList(
                        load = { db.vodStreamsIn(categoryId) },
                        key = { it.streamId },
                        catalogVersion = catalogVersion
                    ) { movie ->
                        BrowseRow(
                            imageUrl = movie.streamIcon,
                            title = TitleFormat.clean(movie.name),
                            isLogo = false,
                            isSaved = movie.streamId in saved,
                            saveLabel = "My Library",
                            onClick = { onPlay(api.vodNowPlaying(movie)) },
                            onToggleSaved = { onToggleMovieFavorite(movie) }
                        )
                    }
                }
                CatalogKind.SERIES -> {
                    val saved = seriesFavorites.map { it.seriesId }.toSet()
                    ItemList(
                        load = { db.seriesIn(categoryId) },
                        key = { it.seriesId },
                        catalogVersion = catalogVersion
                    ) { show ->
                        BrowseRow(
                            imageUrl = show.cover,
                            title = TitleFormat.clean(show.name),
                            isLogo = false,
                            isSaved = show.seriesId in saved,
                            saveLabel = "My Library",
                            onClick = { onOpenSeries(show) },
                            onToggleSaved = { onToggleSeriesFavorite(show) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun KindChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
        )
    )
}

@Composable
private fun CategoryList(
    db: CatalogDatabase,
    kind: CatalogKind,
    catalogVersion: Int,
    onOpen: (CategoryCount) -> Unit
) {
    var state by remember(kind) { mutableStateOf<LoadState<List<CategoryCount>>>(LoadState.Loading) }
    LaunchedEffect(kind, catalogVersion) {
        state = LoadState.Success(withContext(Dispatchers.IO) { db.categoriesWithCounts(kind) })
    }
    when (val current = state) {
        is LoadState.Loading -> CenteredNote("Loading categories...")
        is LoadState.Error -> CenteredNote(current.message)
        is LoadState.Success -> if (current.data.isEmpty()) {
            CenteredNote("The provider's list has nothing here.")
        } else {
            // Keyed per kind so each tab keeps its own scroll position.
            val listState = rememberSaveable(kind, saver = LazyListState.Saver) { LazyListState() }
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                items(current.data, key = { it.categoryId }) { category ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpen(category) }
                            .padding(start = 16.dp, end = 8.dp, top = 14.dp, bottom = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            category.name,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            category.count.toString(),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

@Composable
private fun <T : Any> ItemList(
    load: () -> List<T>,
    key: (T) -> Any,
    catalogVersion: Int,
    row: @Composable (T) -> Unit
) {
    var items by remember { mutableStateOf<List<T>?>(null) }
    LaunchedEffect(catalogVersion) {
        items = withContext(Dispatchers.IO) { load() }
    }
    val listState = rememberLazyListState()
    when (val loaded = items) {
        null -> CenteredNote("Loading...")
        else -> if (loaded.isEmpty()) {
            CenteredNote("This category is empty.")
        } else {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                items(loaded, key = key) { item ->
                    row(item)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

private val BrowseLogoSize = DpSize(48.dp, 48.dp)
private val BrowsePosterSize = DpSize(44.dp, 66.dp)

// Tap the row to play/open; the + adds it to My TV / My Library (a check
// once it's there, tap again to remove).
@Composable
private fun BrowseRow(
    imageUrl: String,
    title: String,
    isLogo: Boolean,
    isSaved: Boolean,
    saveLabel: String,
    onClick: () -> Unit,
    onToggleSaved: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Poster(imageUrl = imageUrl, fallbackText = title, size = if (isLogo) BrowseLogoSize else BrowsePosterSize, isLogo = isLogo)
        Text(
            title,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onToggleSaved) {
            Icon(
                if (isSaved) Icons.Filled.Check else Icons.Filled.Add,
                contentDescription = if (isSaved) "Remove from $saveLabel" else "Add to $saveLabel",
                tint = if (isSaved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
