package com.kdresdell.iptvtv

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme as PhoneMaterialTheme
import androidx.compose.material3.darkColorScheme as phoneDarkColorScheme
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import androidx.tv.material3.IconButtonDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.kdresdell.iptvtv.theme.FocusDefaults
import com.kdresdell.iptvtv.theme.LocalAppColors
import com.kdresdell.iptvtv.theme.RailIcons
import com.kdresdell.iptvtv.theme.appCardBorder
import com.kdresdell.iptvtv.theme.appCardGlow
import com.kdresdell.iptvtv.theme.appCardScale
import com.kdresdell.iptvtv.theme.appIconButtonBorder
import com.kdresdell.iptvtv.theme.appIconButtonGlow
import com.kdresdell.iptvtv.theme.appIconButtonScale
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    accountCreatedAt: Long?,
    accountExpiresAt: Long?,
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
    onToggleSeriesSaved: (SeriesShow) -> Unit,
    defaultStreamId: Int?,
    onSetDefault: (LiveChannel) -> Unit,
    getCachedNowPlaying: (Int) -> NowPlayingInfo?,
    isEpgStale: (Int) -> Boolean,
    onFetchNowPlaying: suspend (Int) -> NowPlayingInfo?,
    onCacheNowPlaying: (Int, NowPlayingInfo) -> Unit
) {
    var results by remember { mutableStateOf<List<SearchResult>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var nowPlaying by remember { mutableStateOf<Map<Int, NowPlayingInfo>>(emptyMap()) }
    val onBackground = MaterialTheme.colorScheme.onBackground
    val focusRequester = remember { FocusRequester() }
    val firstResultFocus = remember { FocusRequester() }
    val firstHistoryFocus = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val coroutineScope = rememberCoroutineScope()

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

    // Same shortlist-only EPG pattern as Favorites (see MainActivity.kt's
    // Screen.Favorites branch): only the live channels currently visible in
    // results get a get_short_epg call, never the whole catalog.
    LaunchedEffect(results) {
        val liveChannels = results.filterIsInstance<SearchResult.Live>().map { it.channel }
        val cached = withContext(Dispatchers.IO) {
            liveChannels.mapNotNull { channel ->
                getCachedNowPlaying(channel.streamId)?.let { channel.streamId to it }
            }.toMap()
        }
        nowPlaying = cached
        liveChannels.forEach { channel ->
            val stale = withContext(Dispatchers.IO) { isEpgStale(channel.streamId) }
            if (stale) {
                val info = onFetchNowPlaying(channel.streamId)
                if (info != null) {
                    withContext(Dispatchers.IO) { onCacheNowPlaying(channel.streamId, info) }
                    nowPlaying = nowPlaying + (channel.streamId to info)
                }
            }
        }
    }

    // §6.4 - results group by content type, single-column list per section
    // (not a grid, not a horizontal shelf - see STYLE_GUIDE.md for why).
    val liveResults = results.filterIsInstance<SearchResult.Live>()
    val vodResults = results.filterIsInstance<SearchResult.Vod>()
    val seriesResults = results.filterIsInstance<SearchResult.Series>()
    val liveIsFirstSection = liveResults.isNotEmpty()
    val vodIsFirstSection = !liveIsFirstSection && vodResults.isNotEmpty()
    val seriesIsFirstSection = !liveIsFirstSection && !vodIsFirstSection && seriesResults.isNotEmpty()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            // §4.1 Google TV safe-margin spec is 48dp/24dp - bumped a step
            // further here on explicit user feedback that 48dp still read
            // as too tight for this screen.
            .padding(horizontal = 64.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // App-defined, informational-only cluster: at-a-glance catalog
        // totals + subscription status. Kept as its own tightly-spaced
        // group (not the screen's normal 14dp rhythm) so it reads as a
        // compact strip, not a third of the screen.
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SearchKpiRow(
                liveCount = syncedLiveCount,
                seriesCount = syncedSeriesCount,
                vodCount = syncedVodCount
            )
            // Only rendered when the provider actually returned both dates
            // - some providers omit exp_date entirely for lifetime
            // accounts, and there's nothing meaningful to draw without a
            // start date to measure elapsed time from.
            if (accountCreatedAt != null && accountExpiresAt != null && accountExpiresAt > accountCreatedAt) {
                SubscriptionProgressBar(createdAtEpochSeconds = accountCreatedAt, expiresAtEpochSeconds = accountExpiresAt)
            }
        }

        // §6.4 - full-width pill search field only, no mic/settings icon.
        SearchField(
            query = query,
            onQueryChange = onQueryChange,
            enabled = syncState is LoadState.Success,
            focusRequester = focusRequester,
            onSearchImeAction = {
                onRecordHistory(query)
                keyboardController?.hide()
            },
            // The text field swallows DirectionDown itself (same issue
            // fixed in SettingsScreen.kt) - without this, Down from the
            // search field goes nowhere and results/history are only
            // reachable via touch/Right, never Down.
            onDirectionDown = {
                when {
                    results.isNotEmpty() -> {
                        firstResultFocus.requestFocus()
                        coroutineScope.launch {
                            delay(300)
                            keyboardController?.hide()
                        }
                        true
                    }
                    query.isBlank() && history.isNotEmpty() -> {
                        firstHistoryFocus.requestFocus()
                        coroutineScope.launch {
                            delay(300)
                            keyboardController?.hide()
                        }
                        true
                    }
                    else -> false
                }
            }
        )

        // History only makes sense before a query is typed - once results
        // are showing, this section would just compete with them. Rendered
        // as a vertical stack of small pills, one per row, most recent
        // search at the top (the order SearchHistoryStore already
        // returns) - see STYLE_GUIDE.md §6.4. A wrapping horizontal row was
        // tried, but real-remote D-pad Left/Right between pills didn't feel
        // reliable enough on hardware; a single column needs only Up/Down,
        // and Left from any pill predictably opens the side rail like every
        // other list in the app (see WithRail in SideRail.kt).
        if (query.isBlank() && history.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Historique des recherches",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(
                        // Clearing history removes this very button from
                        // composition (the whole section is conditional on
                        // history.isNotEmpty()) - if it still has focus when
                        // that happens, focus loss was resolving somewhere
                        // unpredictable (observed: the side rail popping
                        // open over the results underneath). Moving focus
                        // back to the search field first, synchronously,
                        // avoids ever losing focus on a node that's about
                        // to disappear.
                        onClick = {
                            focusRequester.requestFocus()
                            onClearHistory()
                        },
                        scale = appIconButtonScale(),
                        border = appIconButtonBorder(),
                        glow = appIconButtonGlow(),
                        // Explicit focused/pressed colors - the IconButton
                        // default focused container is near-white
                        // (`colorScheme.onSurface`), which combined with a
                        // hardcoded icon tint made the icon invisible
                        // (white-on-white) when focused.
                        colors = IconButtonDefaults.colors(
                            containerColor = LocalAppColors.current.surfaceContainer,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                            focusedContainerColor = LocalAppColors.current.selectedSurface,
                            focusedContentColor = MaterialTheme.colorScheme.onSurface
                        )
                    ) {
                        // No explicit tint - inherits LocalContentColor from
                        // the IconButton above, so it follows focus state
                        // instead of staying a fixed color.
                        Icon(
                            imageVector = RailIcons.Trash,
                            contentDescription = "Clear search history",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    history.forEachIndexed { index, term ->
                        HistoryPill(
                            term = term,
                            // Same focus-loss fix as the trash button above:
                            // picking a term blanks the query, which hides
                            // this whole section (including the pill that
                            // was just clicked) - reclaim focus on the
                            // search field first so nothing is left focusing
                            // a node that's about to be removed.
                            onClick = {
                                focusRequester.requestFocus()
                                onQueryChange(term)
                            },
                            modifier = if (index == 0) Modifier.focusRequester(firstHistoryFocus) else Modifier
                        )
                    }
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
                    if (liveResults.isNotEmpty()) {
                        item { ResultSectionHeader(title = "Live TV", count = liveResults.size, unit = "channels") }
                        itemsIndexed(liveResults) { index, result ->
                            val firstItemModifier =
                                if (liveIsFirstSection && index == 0) Modifier.focusRequester(firstResultFocus) else Modifier
                            ChannelRow(
                                channel = result.channel,
                                isFavorite = isFavorite(result.channel.streamId),
                                onPlay = { onPlayLive(result.channel) },
                                onToggleFavorite = { onToggleFavorite(result.channel) },
                                subtitle = nowPlaying[result.channel.streamId]?.let { "Now: ${TitleFormat.clean(it.title)}" },
                                isDefault = defaultStreamId == result.channel.streamId,
                                onSetDefault = { onSetDefault(result.channel) },
                                playCardModifier = firstItemModifier,
                                typeBadge = {
                                    ResultTypeBadge(label = "LIVE", tonalColor = MaterialTheme.colorScheme.primaryContainer)
                                }
                            )
                        }
                    }
                    if (vodResults.isNotEmpty()) {
                        item { ResultSectionHeader(title = "Movies", count = vodResults.size, unit = "titles") }
                        itemsIndexed(vodResults) { index, result ->
                            val firstItemModifier =
                                if (vodIsFirstSection && index == 0) Modifier.focusRequester(firstResultFocus) else Modifier
                            VodRow(
                                movie = result.movie,
                                isSaved = isMovieSaved(result.movie.streamId),
                                onPlay = { onPlayVod(result.movie) },
                                onToggleSaved = { onToggleMovieSaved(result.movie) },
                                playCardModifier = firstItemModifier
                            )
                        }
                    }
                    if (seriesResults.isNotEmpty()) {
                        item { ResultSectionHeader(title = "Series", count = seriesResults.size, unit = "titles") }
                        itemsIndexed(seriesResults) { index, result ->
                            val firstItemModifier =
                                if (seriesIsFirstSection && index == 0) Modifier.focusRequester(firstResultFocus) else Modifier
                            SeriesRow(
                                series = result.series,
                                isSaved = isSeriesSaved(result.series.seriesId),
                                onOpenEpisodes = { onOpenEpisodes(result.series) },
                                onToggleSaved = { onToggleSeriesSaved(result.series) },
                                playCardModifier = firstItemModifier
                            )
                        }
                    }
                }
            }
        }
    }
}

// §6.4 - single header row per content-type section: Title Large section
// title, muted result count trailing. (Headline Small - 34sp - read as too
// large next to compact result rows; Title Large keeps it as the tallest
// text on screen without dominating.)
@Composable
private fun ResultSectionHeader(title: String, count: Int, unit: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom
    ) {
        Text(text = title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
        Text(text = "$count $unit", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// §6.4 - pill search field: search icon + input only, no label/mic/gear.
// Hand-rolled on BasicTextField (rather than the phone OutlinedTextField
// used before) so it can match the approved visual recipe exactly; keyboard
// behavior (IME search action, DirectionDown handoff to results) is
// preserved unchanged from the previous implementation.
@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    enabled: Boolean,
    focusRequester: FocusRequester,
    onSearchImeAction: () -> Unit,
    onDirectionDown: () -> Boolean
) {
    val appColors = LocalAppColors.current
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(targetValue = if (isFocused) FocusDefaults.FocusedScale else 1f, label = "searchFieldScale")
    val borderColor = if (isFocused) appColors.vividAccent else androidx.compose.ui.graphics.Color.Transparent
    val fieldShape = RoundedCornerShape(50)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .background(appColors.surfaceContainerLow, fieldShape)
            .border(FocusDefaults.OutlineWidth, borderColor, fieldShape)
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Icon(
            imageVector = RailIcons.Search,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
        )
        Box(modifier = Modifier.fillMaxWidth()) {
            if (query.isEmpty()) {
                Text(
                    text = "Search channels, movies, series…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                enabled = enabled,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(appColors.vividAccent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearchImeAction() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .onFocusChanged { isFocused = it.isFocused }
                    .onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown) {
                            onDirectionDown()
                        } else {
                            false
                        }
                    }
            )
        }
    }
}

// §6.4 - one past-search term as a small, compact pill: quiet grey fill
// (surface-container, matching the reference mockup) at rest, with the
// standard §5 focus recipe (vivid-accent outline + glow) as the only
// "selected" indicator - no permanent color change. `border`/`glow` are
// passed explicitly with the same pill shape as the container: the Card
// default's focused-border shape does NOT inherit the `shape` parameter, so
// without this the focus outline rendered as a small-radius rectangle
// sitting crooked on top of the fully-rounded fill.
@Composable
private fun HistoryPill(term: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val pillShape = RoundedCornerShape(50)
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = CardDefaults.shape(pillShape),
        colors = CardDefaults.colors(
            containerColor = LocalAppColors.current.surfaceContainer,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        scale = appCardScale(),
        border = appCardBorder(shape = pillShape),
        glow = appCardGlow()
    ) {
        Text(
            text = term,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
        )
    }
}

// App-defined - three catalog-size stat tiles above the search field, not
// from the reference mockups. Static/display-only (not focusable): they're
// informational, not another D-pad stop between the rail and the field.
@Composable
private fun SearchKpiRow(liveCount: Int, seriesCount: Int, vodCount: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SearchKpiTile(count = liveCount, label = "Live Channels", modifier = Modifier.weight(1f))
        SearchKpiTile(count = seriesCount, label = "TV Shows", modifier = Modifier.weight(1f))
        SearchKpiTile(count = vodCount, label = "Movies", modifier = Modifier.weight(1f))
    }
}

@Composable
private fun SearchKpiTile(count: Int, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(LocalAppColors.current.surfaceContainer, RoundedCornerShape(10.dp))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(1.dp)
    ) {
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// App-defined, not from the reference mockups - a thin subscription-elapsed
// indicator between the KPI row and the search field. Elapsed fraction is
// (now - createdAt) / (expiresAt - createdAt), clamped to [0, 1] since the
// account may already be past its expiration date. Reuses the vivid-accent
// green already established as this app's one "progress" color (§6.1
// player scrub bar, §6.6 in-progress poster indicator) rather than
// inventing a second progress color.
@Composable
private fun SubscriptionProgressBar(createdAtEpochSeconds: Long, expiresAtEpochSeconds: Long) {
    val nowSeconds = remember { System.currentTimeMillis() / 1000 }
    val fraction = ((nowSeconds - createdAtEpochSeconds).toFloat() / (expiresAtEpochSeconds - createdAtEpochSeconds).toFloat())
        .coerceIn(0f, 1f)
    val expiresLabel = remember(expiresAtEpochSeconds) {
        SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(expiresAtEpochSeconds * 1000))
    }
    val appColors = LocalAppColors.current

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(4.dp)
                .clip(RoundedCornerShape(50))
                .background(appColors.surfaceContainer)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(50))
                    .background(appColors.vividAccent)
            )
        }
        Text(
            text = "Expires $expiresLabel",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
