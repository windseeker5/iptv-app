package com.kdresdell.iptvtv

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Card
import androidx.tv.material3.MaterialTheme as TvMaterialTheme
import androidx.tv.material3.Text as TvText
import androidx.tv.material3.darkColorScheme as tvDarkColorScheme
import coil.compose.AsyncImage
import kotlinx.coroutines.delay

// Playback URLs contain the provider username/password in plain text -
// never log streamUrl.
//
// Remote keys are handled directly here rather than relying on
// PlayerView's built-in controller: that approach made OK stop working
// after the controller auto-hid once (the embedded AndroidView silently
// lost key focus, and only Back kept working). Handling keys ourselves on
// a focusable Compose root is more reliable and also lets DPAD up/down
// double as "previous/next favorite channel".
@Composable
fun PlayerScreen(
    title: String,
    iconUrl: String,
    streamUrl: String,
    contentId: Int,
    isLive: Boolean,
    api: XtreamApi,
    subtitle: String? = null,
    loadDescription: suspend () -> String? = { null },
    onSelectRail: (RailItem) -> Unit,
    onPlayItem: (PlayableItem) -> Unit,
    favorites: List<LiveChannel>,
    defaultStreamId: Int?,
    isFavorite: (Int) -> Boolean,
    onToggleFavorite: (LiveChannel) -> Unit,
    onSetDefault: (LiveChannel) -> Unit,
    savedMovies: List<VodStream>,
    savedSeries: List<SeriesShow>,
    isMovieSaved: (Int) -> Boolean,
    isSeriesSaved: (Int) -> Boolean,
    onToggleMovieSaved: (VodStream) -> Unit,
    onToggleSeriesSaved: (SeriesShow) -> Unit,
    onChannelChange: (direction: Int) -> Unit = {}
) {
    val context = LocalContext.current
    val exoPlayer = remember(streamUrl) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(streamUrl))
            prepare()
            playWhenReady = true
        }
    }

    DisposableEffect(exoPlayer) {
        onDispose { exoPlayer.release() }
    }

    var showInfo by remember(contentId) { mutableStateOf(true) }
    var isPlaying by remember(contentId) { mutableStateOf(true) }
    var displayTitle by remember(contentId) { mutableStateOf(title) }
    var description by remember(contentId) { mutableStateOf<String?>(null) }
    val focusRequester = remember { FocusRequester() }

    // Menu overlay state: null browseScreen = rail only (or menu fully
    // closed, when menuOpen is also false). Kept as two separate pieces
    // rather than one enum so the rail can be shown alone before a
    // destination is picked.
    var menuOpen by remember(contentId) { mutableStateOf(false) }
    var browseScreen by remember(contentId) { mutableStateOf<BrowseScreen?>(null) }

    // Live channels show the EPG "now playing" title/synopsis; VOD movies
    // and series episodes show their own synopsis via loadDescription
    // instead (there's no EPG for on-demand content).
    LaunchedEffect(contentId, isLive) {
        if (isLive) {
            val info = try {
                api.getNowPlayingInfo(contentId)
            } catch (e: Exception) {
                null
            }
            displayTitle = info?.title ?: title
            description = info?.description
        } else {
            displayTitle = title
            description = loadDescription()
        }
    }

    // Auto-hide the info overlay after a few seconds, same as any TV
    // channel-change banner - resets whenever it's shown again (OK press
    // or a new channel/title). Doesn't auto-hide while paused, since the
    // pause button lives in this same overlay.
    LaunchedEffect(showInfo, contentId, isPlaying) {
        if (showInfo && isPlaying) {
            delay(5000)
            showInfo = false
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    // Back unwinds the browse overlay one level at a time (channels-in-
    // category -> categories; episodes -> My VOD; any top-level pane ->
    // rail only; rail only -> fully closed) before falling through to the
    // screen's own BackHandler (registered by the caller) that leaves the
    // player. Each is enabled only at the depth it applies to, so exactly
    // one fires for a given state.
    BackHandler(enabled = browseScreen is BrowseScreen.ChannelsInCategory) {
        browseScreen = BrowseScreen.Categories
    }
    BackHandler(enabled = browseScreen is BrowseScreen.SeriesEpisodesOverlay) {
        browseScreen = BrowseScreen.MyVod
    }
    BackHandler(
        enabled = browseScreen != null &&
            browseScreen !is BrowseScreen.ChannelsInCategory &&
            browseScreen !is BrowseScreen.SeriesEpisodesOverlay
    ) {
        browseScreen = null
    }
    BackHandler(enabled = menuOpen && browseScreen == null) {
        menuOpen = false
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                // Once the menu is open, focus lives inside it - let its own
                // Cards handle D-pad navigation/selection instead of this
                // root intercepting Up/Down/Center for channel/pause control.
                if (menuOpen) return@onKeyEvent false
                when (event.key) {
                    Key.DirectionCenter, Key.Enter -> {
                        // First OK reveals the overlay; while it's showing,
                        // OK toggles play/pause directly - this is the only
                        // way to reach it, so it must not require navigating
                        // to a separately-focused button.
                        if (!showInfo) {
                            showInfo = true
                        } else {
                            isPlaying = !isPlaying
                            exoPlayer.playWhenReady = isPlaying
                        }
                        true
                    }
                    Key.DirectionUp -> {
                        // Channel-cycling only makes sense for live playback.
                        if (isLive) { onChannelChange(-1); true } else false
                    }
                    Key.DirectionDown -> {
                        if (isLive) { onChannelChange(1); true } else false
                    }
                    Key.DirectionLeft -> {
                        // Opens the same rail used everywhere else in the app,
                        // as a translucent overlay - playback keeps running
                        // underneath while browsing where to go next.
                        showInfo = false
                        menuOpen = true
                        browseScreen = null
                        true
                    }
                    else -> false
                }
            }
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                    // Letterbox bars (top/bottom on wide content, left/right
                    // on narrow content) are this view's own fill color, not
                    // the Compose Box behind it - default is a visible gray,
                    // not black, so it has to be set explicitly.
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                    setBackgroundColor(android.graphics.Color.BLACK)
                }
            },
            update = { view -> view.player = exoPlayer }
        )

        if (showInfo) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.7f))
                    .padding(24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (iconUrl.isNotBlank()) {
                    AsyncImage(
                        model = iconUrl,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp)
                    )
                }
                Column(modifier = Modifier.padding(start = 16.dp)) {
                    Text(text = displayTitle, color = Color.White)
                    if (!subtitle.isNullOrBlank()) {
                        Text(text = subtitle, color = Color.White)
                    }
                    val synopsis = description
                    if (!synopsis.isNullOrBlank()) {
                        Text(text = synopsis, color = Color.White)
                    }
                }
            }

            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(24.dp)
            ) {
                Text(
                    text = if (isPlaying) "⏸" else "▶",
                    color = Color.White,
                    fontSize = 48.sp
                )
            }
        }

        if (menuOpen) {
            PlayerBrowseOverlay(
                browseScreen = browseScreen,
                onSelectRailLocal = { item ->
                    when (item) {
                        RailItem.MyChannel -> browseScreen = BrowseScreen.MyChannel
                        RailItem.MyVod -> browseScreen = BrowseScreen.MyVod
                        RailItem.Categories -> browseScreen = BrowseScreen.Categories
                        // Search and Settings aren't browsable-while-watching
                        // (Search needs the keyboard, Settings is a config
                        // form) - both leave the player like before.
                        RailItem.Search, RailItem.Settings -> {
                            menuOpen = false
                            browseScreen = null
                            onSelectRail(item)
                        }
                    }
                },
                onBackToCategories = { browseScreen = BrowseScreen.Categories },
                api = api,
                favorites = favorites,
                defaultStreamId = defaultStreamId,
                isFavorite = isFavorite,
                onToggleFavorite = onToggleFavorite,
                onSetDefault = onSetDefault,
                savedMovies = savedMovies,
                savedSeries = savedSeries,
                isMovieSaved = isMovieSaved,
                isSeriesSaved = isSeriesSaved,
                onToggleMovieSaved = onToggleMovieSaved,
                onToggleSeriesSaved = onToggleSeriesSaved,
                onOpenEpisodesOverlay = { series -> browseScreen = BrowseScreen.SeriesEpisodesOverlay(series) },
                onOpenCategory = { category -> browseScreen = BrowseScreen.ChannelsInCategory(category) },
                onPlay = { item ->
                    menuOpen = false
                    browseScreen = null
                    onPlayItem(item)
                }
            )
        }
    }
}

// What the browse overlay is currently showing next to the rail - null
// (not part of this type, held separately) means the rail alone. Deeper
// levels (a category's channels, a series' episodes) know how to pop back
// to their parent level via PlayerScreen's BackHandler chain above.
private sealed class BrowseScreen {
    data object MyChannel : BrowseScreen()
    data object MyVod : BrowseScreen()
    data object Categories : BrowseScreen()
    data class ChannelsInCategory(val category: LiveCategory) : BrowseScreen()
    data class SeriesEpisodesOverlay(val series: SeriesShow) : BrowseScreen()
}

// The normal rail, plus (when a destination is picked) that destination's
// usual screen content right next to it - both translucent, so the video
// keeps playing behind them. Reuses the exact same screen composables used
// everywhere else in the app (FavoritesScreen, MyVodScreen, CategoryListScreen,
// ChannelListScreen, SeriesEpisodesScreen) unmodified: overriding just the
// TV color scheme's `background` token to a translucent black makes every
// one of them (they all paint their root from MaterialTheme.colorScheme.background)
// render translucent without needing an alpha parameter of their own.
@Composable
private fun PlayerBrowseOverlay(
    browseScreen: BrowseScreen?,
    onSelectRailLocal: (RailItem) -> Unit,
    onBackToCategories: () -> Unit,
    api: XtreamApi,
    favorites: List<LiveChannel>,
    defaultStreamId: Int?,
    isFavorite: (Int) -> Boolean,
    onToggleFavorite: (LiveChannel) -> Unit,
    onSetDefault: (LiveChannel) -> Unit,
    savedMovies: List<VodStream>,
    savedSeries: List<SeriesShow>,
    isMovieSaved: (Int) -> Boolean,
    isSeriesSaved: (Int) -> Boolean,
    onToggleMovieSaved: (VodStream) -> Unit,
    onToggleSeriesSaved: (SeriesShow) -> Unit,
    onOpenEpisodesOverlay: (SeriesShow) -> Unit,
    onOpenCategory: (LiveCategory) -> Unit,
    onPlay: (PlayableItem) -> Unit
) {
    val firstItemFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        firstItemFocusRequester.requestFocus()
    }

    TvMaterialTheme(colorScheme = tvDarkColorScheme(background = Color.Black.copy(alpha = 0.85f))) {
        Row(modifier = Modifier.fillMaxHeight()) {
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(220.dp)
                    .background(TvMaterialTheme.colorScheme.background)
                    .padding(vertical = 24.dp, horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                RailItem.entries.forEachIndexed { index, item ->
                    Card(
                        onClick = { onSelectRailLocal(item) },
                        modifier = if (index == 0) Modifier.focusRequester(firstItemFocusRequester) else Modifier
                    ) {
                        TvText(text = "${item.icon}  ${item.label}", modifier = Modifier.padding(16.dp))
                    }
                }
            }

            if (browseScreen != null) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(420.dp)
                ) {
                    when (browseScreen) {
                        is BrowseScreen.MyChannel -> FavoritesScreen(
                            favorites = favorites,
                            // No EPG "Now:" subtitle here - it's only ever
                            // fetched for the standalone Favorites screen;
                            // not worth duplicating that fetch for this
                            // secondary, glanceable view.
                            nowPlaying = emptyMap(),
                            defaultStreamId = defaultStreamId,
                            onPlay = { channel -> onPlay(PlayableItem.Live(channel)) },
                            onRemove = onToggleFavorite,
                            onSetDefault = onSetDefault
                        )
                        is BrowseScreen.MyVod -> MyVodScreen(
                            savedMovies = savedMovies,
                            savedSeries = savedSeries,
                            onPlayMovie = { movie -> onPlay(PlayableItem.Vod(movie)) },
                            onOpenEpisodes = onOpenEpisodesOverlay,
                            onRemoveMovie = onToggleMovieSaved,
                            onRemoveSeries = onToggleSeriesSaved
                        )
                        is BrowseScreen.Categories -> {
                            var categoriesState by remember {
                                mutableStateOf<LoadState<List<LiveCategory>>>(LoadState.Loading)
                            }
                            LaunchedEffect(Unit) {
                                categoriesState = try {
                                    LoadState.Success(api.getLiveCategories())
                                } catch (e: Exception) {
                                    LoadState.Error(e.message ?: "Unknown error")
                                }
                            }
                            CategoryListScreen(
                                state = categoriesState,
                                onSelectCategory = onOpenCategory
                            )
                        }
                        is BrowseScreen.ChannelsInCategory -> {
                            var channelsState by remember(browseScreen.category) {
                                mutableStateOf<LoadState<List<LiveChannel>>>(LoadState.Loading)
                            }
                            LaunchedEffect(browseScreen.category) {
                                channelsState = try {
                                    LoadState.Success(api.getLiveStreams(browseScreen.category.categoryId))
                                } catch (e: Exception) {
                                    LoadState.Error(e.message ?: "Unknown error")
                                }
                            }
                            ChannelListScreen(
                                categoryName = browseScreen.category.categoryName,
                                state = channelsState,
                                isFavorite = isFavorite,
                                onSelectChannel = { channel -> onPlay(PlayableItem.Live(channel)) },
                                onToggleFavorite = onToggleFavorite,
                                onBack = onBackToCategories
                            )
                        }
                        is BrowseScreen.SeriesEpisodesOverlay -> {
                            var episodesState by remember(browseScreen.series) {
                                mutableStateOf<LoadState<List<SeriesEpisode>>>(LoadState.Loading)
                            }
                            LaunchedEffect(browseScreen.series) {
                                episodesState = try {
                                    LoadState.Success(api.getSeriesEpisodes(browseScreen.series.seriesId))
                                } catch (e: Exception) {
                                    LoadState.Error(e.message ?: "Unknown error")
                                }
                            }
                            SeriesEpisodesScreen(
                                seriesName = browseScreen.series.name,
                                state = episodesState,
                                onSelectEpisode = { episode ->
                                    onPlay(
                                        PlayableItem.Episode(
                                            episode = episode,
                                            seriesName = browseScreen.series.name,
                                            cover = browseScreen.series.cover
                                        )
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
