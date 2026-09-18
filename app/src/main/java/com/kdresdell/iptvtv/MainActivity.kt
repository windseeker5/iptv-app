package com.kdresdell.iptvtv

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.kdresdell.iptvtv.theme.IptvTvTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Favorites ("My Channel" in the rail) is the app's home screen. The
// persistent side rail (see SideRail.kt) is how you reach the others - it
// is not itself part of this back-stack, it just changes which Screen is
// current, same as selecting a rail item.
// Fullscreen is today's full-screen player. ReducedWithGuide is the new
// Back-from-live destination: the same shared live player, shrunk and
// embedded over the "My TV" EPG guide (FavoritesScreen's GuideMode.Embedded)
// - see [[navigation_model_spec]] for the full Back chain this implements.
// Only ever set for live items; VOD/episode/recording always stay Fullscreen.
private enum class PlayerViewMode { Fullscreen, ReducedWithGuide }

private sealed class Screen {
    data object Settings : Screen()
    data object Help : Screen()
    data object Favorites : Screen()
    data object MyVod : Screen()
    data object Categories : Screen()
    data object Search : Screen()
    data class Channels(val category: LiveCategory) : Screen()
    data class SeriesEpisodes(val series: SeriesShow, val returnTo: Screen) : Screen()
    // Unifies live/VOD/episode playback under one screen (see PlayableItem)
    // instead of three separate ones - that's what lets the in-player
    // browse overlay switch what's playing without tearing down and
    // rebuilding the whole player (same `when` branch, same call site).
    data class NowPlaying(
        val item: PlayableItem,
        val returnTo: Screen,
        val viewMode: PlayerViewMode = PlayerViewMode.Fullscreen
    ) : Screen()
}

private fun Screen.toRailItem(): RailItem? = when (this) {
    is Screen.Search -> RailItem.Search
    is Screen.Favorites -> RailItem.MyChannel
    is Screen.MyVod -> RailItem.MyVod
    is Screen.Categories, is Screen.Channels -> RailItem.Categories
    is Screen.Settings -> RailItem.Settings
    is Screen.Help -> RailItem.Help
    else -> null
}

private fun RailItem.toScreen(): Screen = when (this) {
    RailItem.Search -> Screen.Search
    RailItem.MyChannel -> Screen.Favorites
    RailItem.MyVod -> Screen.MyVod
    RailItem.Categories -> Screen.Categories
    RailItem.Settings -> Screen.Settings
    RailItem.Help -> Screen.Help
}

// Precomputes everything PlayerScreen needs from a PlayableItem - keeps the
// three content-type cases (live/VOD/episode) in one place instead of
// duplicated across separate screen branches.
// Description + provider rating together, since both come from the same
// API call for VOD/series - separate suspend fields would mean fetching
// twice.
data class ContentDetails(val description: String? = null, val rating: String? = null)

private data class PlayerParams(
    val title: String,
    val iconUrl: String,
    val streamUrl: String,
    val contentId: Int,
    val isLive: Boolean,
    val subtitle: String?,
    val loadDetails: suspend () -> ContentDetails
)

private fun PlayableItem.toPlayerParams(api: XtreamApi, channelDb: LiveChannelDatabase): PlayerParams = when (this) {
    is PlayableItem.Live -> PlayerParams(
        title = channel.name,
        iconUrl = channel.streamIcon,
        streamUrl = api.liveStreamUrl(channel.streamId),
        contentId = channel.streamId,
        isLive = true,
        subtitle = null,
        loadDetails = { ContentDetails() }
    )
    is PlayableItem.Vod -> PlayerParams(
        title = movie.name,
        iconUrl = movie.streamIcon,
        streamUrl = api.vodStreamUrl(movie.streamId, movie.containerExtension),
        contentId = movie.streamId,
        isLive = false,
        subtitle = null,
        loadDetails = {
            // Cached on-demand: fetched once, instant on every later open -
            // confirmed on real hardware (2026-09-17) reopening the same
            // movie no longer re-hits the network.
            val cached = withContext(Dispatchers.IO) { channelDb.getCachedVodDetails(movie.streamId) }
            val details = cached ?: api.getVodDetails(movie.streamId).also {
                withContext(Dispatchers.IO) { channelDb.setVodDetails(movie.streamId, it) }
            }
            ContentDetails(details.description.ifBlank { null }, details.rating.ifBlank { null })
        }
    )
    is PlayableItem.Episode -> PlayerParams(
        title = seriesName,
        iconUrl = cover,
        streamUrl = api.seriesEpisodeUrl(episode.episodeId, episode.containerExtension),
        contentId = episode.episodeId,
        isLive = false,
        subtitle = "Season ${episode.season} Episode ${episode.episodeNum}" +
            episode.title.let { if (it.isNotBlank()) " - $it" else "" },
        loadDetails = { ContentDetails(episode.description.ifBlank { null }) }
    )
    is PlayableItem.Recording -> PlayerParams(
        title = RecordingStorage.displayName(file),
        iconUrl = "",
        streamUrl = file.toURI().toString(),
        contentId = file.absolutePath.hashCode(),
        isLive = false,
        subtitle = java.text.SimpleDateFormat("MMM d - HH:mm", java.util.Locale.getDefault())
            .format(java.util.Date(file.lastModified())),
        loadDetails = { ContentDetails() }
    )
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
          IptvTvTheme {
            val context = LocalContext.current
            val activity = context as Activity
            // Single ExoPlayer for live playback, shared between full-screen
            // and the reduced/embedded guide view - see LivePlaybackHolder's
            // doc for why (continuous playback across Back/OK, and a
            // deterministic release() point for the exit-audio fix below).
            val livePlaybackHolder = remember { LivePlaybackHolder(context) }
            // The app's one, uniform exit gesture (Back while the side rail
            // is focused, from any screen - see WithRail in SideRail.kt).
            // Explicitly releasing the shared player before finishing is
            // what fixes the Fire TV bug where audio kept playing after
            // exit: it no longer depends on whatever the OS's default back
            // behavior happens to be on a given device.
            val onExitApp: () -> Unit = {
                livePlaybackHolder.release()
                activity.finishAndRemoveTask()
            }
            val prefs = remember { ProviderPrefs(context) }
            val favoritesStore = remember { FavoritesStore(context) }
            val channelDb = remember { LiveChannelDatabase(context) }
            val defaultChannelStore = remember { DefaultChannelStore(context) }
            val searchHistoryStore = remember { SearchHistoryStore(context) }
            val vodFavoritesStore = remember { VodFavoritesStore(context) }
            var credentials by remember { mutableStateOf(prefs.load()) }
            var favorites by remember { mutableStateOf(favoritesStore.load()) }
            var defaultStreamId by remember { mutableStateOf(defaultChannelStore.getDefaultStreamId()) }
            var searchHistory by remember { mutableStateOf(searchHistoryStore.load()) }
            var savedMovies by remember { mutableStateOf(vodFavoritesStore.loadMovies()) }
            var savedSeries by remember { mutableStateOf(vodFavoritesStore.loadSeries()) }
            var availableUpdate by remember { mutableStateOf<UpdateInfo?>(null) }
            val coroutineScope = rememberCoroutineScope()
            LaunchedEffect(Unit) {
                try {
                    val update = UpdateChecker().checkForUpdate()
                    if (update != null && update.versionCode > BuildConfig.VERSION_CODE) {
                        availableUpdate = update
                    }
                } catch (e: Exception) {
                    AppLog.log("Update check failed: ${e.javaClass.simpleName}: ${e.message}")
                }
            }
            val onUpdateClick: () -> Unit = {
                val update = availableUpdate
                if (update != null) {
                    val checker = UpdateChecker()
                    coroutineScope.launch {
                        try {
                            val apkFile = checker.downloadApk(context, update)
                            checker.installApk(context, apkFile)
                        } catch (e: Exception) {
                            AppLog.log("Update install failed: ${e.javaClass.simpleName}: ${e.message}")
                        }
                    }
                }
            }
            var searchQuery by remember { mutableStateOf("") }
            // Computed once, at cold start: if a default channel is set and
            // still among the favorites, launch straight into it instead of
            // the Favorites list.
            var screen by remember {
                mutableStateOf<Screen>(
                    when {
                        !credentials.isComplete -> Screen.Settings
                        else -> {
                            val defaultChannel = defaultStreamId?.let { id -> favorites.find { it.streamId == id } }
                            if (defaultChannel != null) {
                                Screen.NowPlaying(PlayableItem.Live(defaultChannel), returnTo = Screen.Favorites)
                            } else {
                                Screen.Favorites
                            }
                        }
                    }
                )
            }

            val isFavorite: (Int) -> Boolean = { id -> favorites.any { it.streamId == id } }
            val toggleFavorite: (LiveChannel) -> Unit = { channel ->
                favorites = favoritesStore.toggle(channel, favorites)
            }
            val onSetDefault: (LiveChannel) -> Unit = { channel ->
                val newDefault = if (defaultStreamId == channel.streamId) null else channel.streamId
                defaultStreamId = newDefault
                defaultChannelStore.setDefault(newDefault)
            }
            val isMovieSaved: (Int) -> Boolean = { id -> savedMovies.any { it.streamId == id } }
            val isSeriesSaved: (Int) -> Boolean = { id -> savedSeries.any { it.seriesId == id } }
            val toggleMovieSaved: (VodStream) -> Unit = { movie ->
                savedMovies = vodFavoritesStore.toggleMovie(movie, savedMovies)
            }
            val toggleSeriesSaved: (SeriesShow) -> Unit = { series ->
                savedSeries = vodFavoritesStore.toggleSeries(series, savedSeries)
            }
            val onSelectRail: (RailItem) -> Unit = { item -> screen = item.toScreen() }

            // Shared across both places that show the EPG timeline grid (the
            // My TV home screen and the OK-button info+guide overlay while
            // watching live) so there's exactly one fetch/cache path, not
            // two - and so the guide has fresh data no matter which of those
            // two screens is current when it's opened.
            val epgApi = remember(credentials) { XtreamApi(credentials) }
            var epgWindows by remember { mutableStateOf<Map<Int, List<EpgProgram>>>(emptyMap()) }
            LaunchedEffect(favorites) {
                val cached = withContext(Dispatchers.IO) {
                    favorites.associate { channel ->
                        channel.streamId to channelDb.getCachedEpgWindow(channel.streamId)
                    }
                }
                epgWindows = cached
                // fetchEpgWindow (get_simple_data_table) returns ~1 week of
                // programs per channel, not a short rolling window like the
                // old get_short_epg-based call did - so one fetch per
                // channel per day is enough to keep now/next accurate,
                // versus the 30min cadence this used to need. Looping with a
                // delay (rather than a one-shot check keyed only on
                // `favorites`) still matters: real time keeps advancing past
                // whatever was fetched, so the loop has to periodically
                // refresh even if `favorites` itself never changes.
                val maxAgeMillis = 24 * 60 * 60 * 1000L
                while (true) {
                    // Fetched concurrently, not one-at-a-time - confirmed on
                    // real hardware (2026-09-17) that a sequential
                    // favorites.forEach here was the actual cause of both the
                    // slow guide load and the "only one row highlighted" bug:
                    // with 15-20 favorites, rows whose turn hadn't come up
                    // yet just sat on the empty "No information" placeholder
                    // for tens of seconds. Each channel's try/catch stays
                    // inside its own async{} so one failing fetch can't
                    // cancel the others.
                    coroutineScope {
                        favorites.map { channel ->
                            async {
                                val stale = withContext(Dispatchers.IO) {
                                    channelDb.isEpgWindowStale(channel.streamId, maxAgeMillis)
                                }
                                if (stale) {
                                    val programs = try {
                                        epgApi.fetchEpgWindow(channel.streamId)
                                    } catch (e: Exception) {
                                        emptyList()
                                    }
                                    if (programs.isNotEmpty()) {
                                        withContext(Dispatchers.IO) {
                                            channelDb.setEpgWindow(channel.streamId, programs)
                                        }
                                        epgWindows = epgWindows + (channel.streamId to programs)
                                    }
                                }
                            }
                        }.awaitAll()
                    }
                    delay(maxAgeMillis)
                }
            }

            when (val currentScreen = screen) {
                is Screen.Settings -> {
                    if (!credentials.isComplete) {
                        // First run: full screen, no rail yet (nothing else works
                        // without credentials).
                        SettingsScreen(
                            initial = credentials,
                            onSave = { saved ->
                                prefs.save(saved)
                                credentials = saved
                                screen = Screen.Favorites
                            },
                            availableUpdate = availableUpdate,
                            onUpdateClick = onUpdateClick
                        )
                    } else {
                        WithRail(selected = currentScreen.toRailItem(), onSelectRail = onSelectRail, hasSettingsAlert = availableUpdate != null, onExitApp = onExitApp) {
                            SettingsScreen(
                                initial = credentials,
                                onSave = { saved ->
                                    prefs.save(saved)
                                    credentials = saved
                                    screen = Screen.Favorites
                                },
                                availableUpdate = availableUpdate,
                                onUpdateClick = onUpdateClick
                            )
                        }
                    }
                }

                is Screen.Help -> {
                    WithRail(selected = currentScreen.toRailItem(), onSelectRail = onSelectRail, hasSettingsAlert = availableUpdate != null, onExitApp = onExitApp) {
                        HelpScreen()
                    }
                }

                is Screen.Favorites -> {
                    WithRail(selected = currentScreen.toRailItem(), onSelectRail = onSelectRail, hasSettingsAlert = availableUpdate != null, onExitApp = onExitApp) {
                        FavoritesScreen(
                            favorites = favorites,
                            epgWindows = epgWindows,
                            defaultStreamId = defaultStreamId,
                            streamUrlFor = { id -> epgApi.liveStreamUrl(id) },
                            onPlay = { channel ->
                                screen = Screen.NowPlaying(PlayableItem.Live(channel), returnTo = Screen.Favorites)
                            },
                            onRemove = toggleFavorite,
                            onSetDefault = onSetDefault
                        )
                    }
                }

                is Screen.Categories -> {
                    val api = remember(credentials) { XtreamApi(credentials) }
                    var categoriesState by remember(credentials) {
                        mutableStateOf<LoadState<List<LiveCategory>>>(LoadState.Loading)
                    }
                    LaunchedEffect(credentials) {
                        categoriesState = try {
                            LoadState.Success(api.getLiveCategories())
                        } catch (e: Exception) {
                            AppLog.log("Load categories failed: ${e.javaClass.simpleName}: ${e.message}")
                            LoadState.Error(e.message ?: "Unknown error")
                        }
                    }
                    WithRail(selected = currentScreen.toRailItem(), onSelectRail = onSelectRail, hasSettingsAlert = availableUpdate != null, onExitApp = onExitApp) {
                        CategoryListScreen(
                            state = categoriesState,
                            onSelectCategory = { category -> screen = Screen.Channels(category) }
                        )
                    }
                }

                is Screen.Channels -> {
                    val api = remember(credentials) { XtreamApi(credentials) }
                    var channelsState by remember(currentScreen.category) {
                        mutableStateOf<LoadState<List<LiveChannel>>>(LoadState.Loading)
                    }
                    LaunchedEffect(currentScreen.category) {
                        channelsState = try {
                            LoadState.Success(api.getLiveStreams(currentScreen.category.categoryId))
                        } catch (e: Exception) {
                            AppLog.log("Load channels failed (${currentScreen.category.categoryName}): ${e.javaClass.simpleName}: ${e.message}")
                            LoadState.Error(e.message ?: "Unknown error")
                        }
                    }
                    WithRail(selected = currentScreen.toRailItem(), onSelectRail = onSelectRail, hasSettingsAlert = availableUpdate != null, onExitApp = onExitApp) {
                        ChannelListScreen(
                            categoryName = currentScreen.category.categoryName,
                            state = channelsState,
                            isFavorite = isFavorite,
                            onSelectChannel = { channel ->
                                screen = Screen.NowPlaying(PlayableItem.Live(channel), returnTo = currentScreen)
                            },
                            onToggleFavorite = toggleFavorite,
                            onBack = { screen = Screen.Categories }
                        )
                    }
                }

                is Screen.Search -> {
                    val api = remember(credentials) { XtreamApi(credentials) }
                    var syncState by remember(credentials) {
                        mutableStateOf<LoadState<Unit>>(LoadState.Loading)
                    }
                    var syncedLiveCount by remember(credentials) { mutableStateOf(0) }
                    var syncedVodCount by remember(credentials) { mutableStateOf(0) }
                    var syncedSeriesCount by remember(credentials) { mutableStateOf(0) }
                    // §6.4 subscription progress bar - fetched once,
                    // independently of the catalog sync above (a failure
                    // here should never block search).
                    var accountInfo by remember(credentials) { mutableStateOf<AccountInfo?>(null) }
                    LaunchedEffect(credentials) {
                        accountInfo = try {
                            api.getAccountInfo()
                        } catch (e: Exception) {
                            AppLog.log("Load account info failed: ${e.javaClass.simpleName}: ${e.message}")
                            null
                        }
                    }
                    LaunchedEffect(credentials) {
                        syncState = try {
                            if (channelDb.isEmpty()) {
                                api.syncAllLiveChannelsInto(channelDb) { count -> syncedLiveCount = count }
                            }
                            if (channelDb.isVodEmpty()) {
                                api.syncAllVodStreamsInto(channelDb) { count -> syncedVodCount = count }
                            }
                            if (channelDb.isSeriesEmpty()) {
                                api.syncAllSeriesInto(channelDb) { count -> syncedSeriesCount = count }
                            }
                            // The counters above only move while a sync is
                            // actually running - on a returning session the
                            // catalog is already populated and every sync
                            // call is skipped, so read the real totals back
                            // from the database instead of leaving them at 0.
                            syncedLiveCount = channelDb.countLiveChannels()
                            syncedVodCount = channelDb.countVodStreams()
                            syncedSeriesCount = channelDb.countSeries()
                            LoadState.Success(Unit)
                        } catch (e: kotlinx.coroutines.CancellationException) {
                            // Normal path when the user leaves Search before
                            // the sync finishes - confirmed on real hardware
                            // (2026-09-17) this was getting caught below and
                            // logged as a scary "Catalog sync failed" for
                            // completely ordinary navigation, same class of
                            // bug as PlayerScreen's recording coroutine (see
                            // startRecording). Rethrown instead so it just
                            // completes the coroutine like any cancellation.
                            throw e
                        } catch (e: Exception) {
                            AppLog.log("Catalog sync failed: ${e.javaClass.simpleName}: ${e.message}")
                            LoadState.Error(e.message ?: "Unknown error")
                        }
                    }
                    WithRail(selected = currentScreen.toRailItem(), onSelectRail = onSelectRail, hasSettingsAlert = availableUpdate != null, onExitApp = onExitApp) {
                        SearchScreen(
                            query = searchQuery,
                            onQueryChange = { searchQuery = it },
                            syncState = syncState,
                            syncedLiveCount = syncedLiveCount,
                            syncedVodCount = syncedVodCount,
                            syncedSeriesCount = syncedSeriesCount,
                            accountCreatedAt = accountInfo?.createdAtEpochSeconds,
                            accountExpiresAt = accountInfo?.expDateEpochSeconds,
                            history = searchHistory,
                            onSearch = { query -> withContext(Dispatchers.IO) { channelDb.searchAll(query) } },
                            onRecordHistory = { term -> searchHistory = searchHistoryStore.add(term) },
                            onClearHistory = { searchHistory = searchHistoryStore.clear() },
                            isFavorite = isFavorite,
                            isMovieSaved = isMovieSaved,
                            isSeriesSaved = isSeriesSaved,
                            onPlayLive = { channel ->
                                screen = Screen.NowPlaying(PlayableItem.Live(channel), returnTo = Screen.Search)
                            },
                            onPlayVod = { movie ->
                                screen = Screen.NowPlaying(PlayableItem.Vod(movie), returnTo = Screen.Search)
                            },
                            onOpenEpisodes = { series -> screen = Screen.SeriesEpisodes(series, returnTo = Screen.Search) },
                            onToggleFavorite = toggleFavorite,
                            onToggleMovieSaved = toggleMovieSaved,
                            onToggleSeriesSaved = toggleSeriesSaved,
                            defaultStreamId = defaultStreamId,
                            onSetDefault = onSetDefault,
                            getCachedNowPlaying = { id -> channelDb.getCachedNowPlaying(id) },
                            isEpgStale = { id -> channelDb.isEpgStale(id, 24 * 60 * 60 * 1000L) },
                            onFetchNowPlaying = { id ->
                                try {
                                    api.getNowPlayingInfo(id)
                                } catch (e: Exception) {
                                    null
                                }
                            },
                            onCacheNowPlaying = { id, info -> channelDb.setNowPlaying(id, info) }
                        )
                    }
                }

                is Screen.MyVod -> {
                    // Recordings only ever show here when the Settings
                    // toggle is on (see RecordingPrefs) - listRecordings
                    // already returns empty with no drive attached, so
                    // together this matches todo.md's "enabled AND a USB
                    // drive is attached" requirement without a separate flag.
                    fun loadVisibleRecordings() =
                        if (RecordingPrefs.isEnabled(context)) RecordingStorage.listRecordings(context) else emptyList()
                    var recordings by remember { mutableStateOf(loadVisibleRecordings()) }
                    LaunchedEffect(currentScreen) {
                        recordings = loadVisibleRecordings()
                    }
                    WithRail(selected = currentScreen.toRailItem(), onSelectRail = onSelectRail, hasSettingsAlert = availableUpdate != null, onExitApp = onExitApp) {
                        MyVodScreen(
                            savedMovies = savedMovies,
                            savedSeries = savedSeries,
                            recordings = recordings,
                            onPlayMovie = { movie ->
                                screen = Screen.NowPlaying(PlayableItem.Vod(movie), returnTo = Screen.MyVod)
                            },
                            onOpenEpisodes = { series -> screen = Screen.SeriesEpisodes(series, returnTo = Screen.MyVod) },
                            onPlayRecording = { file ->
                                screen = Screen.NowPlaying(PlayableItem.Recording(file), returnTo = Screen.MyVod)
                            },
                            onRemoveMovie = toggleMovieSaved,
                            onRemoveSeries = toggleSeriesSaved,
                            onDeleteRecording = { file ->
                                RecordingStorage.deleteRecording(file)
                                recordings = loadVisibleRecordings()
                            }
                        )
                    }
                }

                is Screen.SeriesEpisodes -> {
                    val api = remember(credentials) { XtreamApi(credentials) }
                    var episodesState by remember(currentScreen.series) {
                        mutableStateOf<LoadState<SeriesDetails>>(LoadState.Loading)
                    }
                    LaunchedEffect(currentScreen.series) {
                        episodesState = try {
                            // Cached on-demand, same as VOD details above -
                            // reopening the same series' episode list is
                            // instant after the first fetch.
                            val cached = withContext(Dispatchers.IO) {
                                channelDb.getCachedSeriesDetails(currentScreen.series.seriesId)
                            }
                            val details = cached ?: api.getSeriesEpisodes(currentScreen.series.seriesId).also {
                                withContext(Dispatchers.IO) {
                                    channelDb.setSeriesDetails(currentScreen.series.seriesId, it)
                                }
                            }
                            LoadState.Success(details)
                        } catch (e: Exception) {
                            AppLog.log("Load episodes failed (${currentScreen.series.name}): ${e.javaClass.simpleName}: ${e.message}")
                            LoadState.Error(e.message ?: "Unknown error")
                        }
                    }
                    WithRail(selected = null, onSelectRail = onSelectRail, hasSettingsAlert = availableUpdate != null, onExitApp = onExitApp) {
                        SeriesEpisodesScreen(
                            seriesName = currentScreen.series.name,
                            seriesCover = currentScreen.series.cover,
                            state = episodesState,
                            onSelectEpisode = { episode ->
                                screen = Screen.NowPlaying(
                                    PlayableItem.Episode(
                                        episode = episode,
                                        seriesName = currentScreen.series.name,
                                        cover = currentScreen.series.cover
                                    ),
                                    returnTo = currentScreen
                                )
                            }
                        )
                    }
                }

                is Screen.NowPlaying -> {
                    val api = remember(credentials) { XtreamApi(credentials) }
                    // PlayerScreen consumes Back directly in its own
                    // onKeyEvent (see PlayerScreen.kt), never via
                    // BackHandler - confirmed on real hardware that
                    // BackHandler/predictive back silently drops every other
                    // invocation, which is exactly why PlayerScreen doesn't
                    // rely on it (same reason WithRail's Back handling in
                    // SideRail.kt uses raw onKeyEvent too).
                    val item = currentScreen.item
                    val params = remember(item) { item.toPlayerParams(api, channelDb) }
                    // Up/down channel-cycling always cycles through favorites
                    // specifically (not whatever list you arrived from), per
                    // the user's request - a no-op if the current channel
                    // isn't one, there's nothing to cycle to, or this isn't
                    // live content at all.
                    val favoriteIndex = (item as? PlayableItem.Live)?.let { live ->
                        favorites.indexOfFirst { it.streamId == live.channel.streamId }
                    } ?: -1

                    // ONE screen for live, in two modes (see PlayerScreen's
                    // `reduced` doc): the same PlayerScreen instance - and so
                    // the same video view - stays mounted whether it's full
                    // screen or shrunk into the guide's top-left slot, so
                    // switching is a resize, not a screen swap.
                    val reduced = currentScreen.viewMode == PlayerViewMode.ReducedWithGuide && item is PlayableItem.Live
                    PlayerScreen(
                        title = params.title,
                        iconUrl = params.iconUrl,
                        streamUrl = params.streamUrl,
                        contentId = params.contentId,
                        isLive = params.isLive,
                        api = api,
                        channelDb = channelDb,
                        subtitle = params.subtitle,
                        loadDetails = params.loadDetails,
                        onSelectRail = onSelectRail,
                        onChannelChange = { direction ->
                            if (params.isLive && favorites.isNotEmpty() && favoriteIndex >= 0) {
                                val nextIndex = (favoriteIndex + direction + favorites.size) % favorites.size
                                screen = Screen.NowPlaying(
                                    PlayableItem.Live(favorites[nextIndex]),
                                    returnTo = currentScreen.returnTo
                                )
                            }
                        },
                        livePlaybackHolder = if (params.isLive) livePlaybackHolder else null,
                        onReduceToGuide = {
                            screen = Screen.NowPlaying(item, returnTo = currentScreen.returnTo, viewMode = PlayerViewMode.ReducedWithGuide)
                        },
                        reduced = reduced,
                        onExitApp = onExitApp,
                        guideContent = {
                            if (item is PlayableItem.Live) {
                                FavoritesScreen(
                                    favorites = favorites,
                                    epgWindows = epgWindows,
                                    defaultStreamId = defaultStreamId,
                                    streamUrlFor = { id -> epgApi.liveStreamUrl(id) },
                                    onPlay = { },
                                    onRemove = toggleFavorite,
                                    onSetDefault = onSetDefault,
                                    mode = GuideMode.Embedded,
                                    tunedChannel = item.channel,
                                    onChannelTuned = { channel ->
                                        screen = Screen.NowPlaying(
                                            PlayableItem.Live(channel),
                                            returnTo = currentScreen.returnTo,
                                            viewMode = PlayerViewMode.ReducedWithGuide
                                        )
                                    },
                                    onExpand = {
                                        screen = Screen.NowPlaying(item, returnTo = currentScreen.returnTo, viewMode = PlayerViewMode.Fullscreen)
                                    }
                                )
                            }
                        }
                    )
                }
            }
          }
        }
    }
}
