package com.kdresdell.iptvtv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Favorites ("My Channel" in the rail) is the app's home screen. The
// persistent side rail (see SideRail.kt) is how you reach the others - it
// is not itself part of this back-stack, it just changes which Screen is
// current, same as selecting a rail item.
private sealed class Screen {
    data object Settings : Screen()
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
    data class NowPlaying(val item: PlayableItem, val returnTo: Screen) : Screen()
}

private fun Screen.toRailItem(): RailItem? = when (this) {
    is Screen.Search -> RailItem.Search
    is Screen.Favorites -> RailItem.MyChannel
    is Screen.MyVod -> RailItem.MyVod
    is Screen.Categories, is Screen.Channels -> RailItem.Categories
    is Screen.Settings -> RailItem.Settings
    else -> null
}

private fun RailItem.toScreen(): Screen = when (this) {
    RailItem.Search -> Screen.Search
    RailItem.MyChannel -> Screen.Favorites
    RailItem.MyVod -> Screen.MyVod
    RailItem.Categories -> Screen.Categories
    RailItem.Settings -> Screen.Settings
}

// Precomputes everything PlayerScreen needs from a PlayableItem - keeps the
// three content-type cases (live/VOD/episode) in one place instead of
// duplicated across separate screen branches.
private data class PlayerParams(
    val title: String,
    val iconUrl: String,
    val streamUrl: String,
    val contentId: Int,
    val isLive: Boolean,
    val subtitle: String?,
    val loadDescription: suspend () -> String?
)

private fun PlayableItem.toPlayerParams(api: XtreamApi): PlayerParams = when (this) {
    is PlayableItem.Live -> PlayerParams(
        title = channel.name,
        iconUrl = channel.streamIcon,
        streamUrl = api.liveStreamUrl(channel.streamId),
        contentId = channel.streamId,
        isLive = true,
        subtitle = null,
        loadDescription = { null }
    )
    is PlayableItem.Vod -> PlayerParams(
        title = movie.name,
        iconUrl = movie.streamIcon,
        streamUrl = api.vodStreamUrl(movie.streamId, movie.containerExtension),
        contentId = movie.streamId,
        isLive = false,
        subtitle = null,
        loadDescription = { api.getVodDescription(movie.streamId) }
    )
    is PlayableItem.Episode -> PlayerParams(
        title = seriesName,
        iconUrl = cover,
        streamUrl = api.seriesEpisodeUrl(episode.episodeId, episode.containerExtension),
        contentId = episode.episodeId,
        isLive = false,
        subtitle = "Season ${episode.season} Episode ${episode.episodeNum}" +
            episode.title.let { if (it.isNotBlank()) " - $it" else "" },
        loadDescription = { episode.description }
    )
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val context = LocalContext.current
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
            // Hoisted (not owned by SearchScreen) so the Search screen's
            // BackHandler can inspect/clear it: first Back clears an
            // in-progress search and stays on the page, matching how search
            // works elsewhere on TV; only a second Back (query already
            // empty) leaves to Favorites.
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
                            }
                        )
                    } else {
                        BackHandler { screen = Screen.Favorites }
                        WithRail(selected = currentScreen.toRailItem(), onSelectRail = onSelectRail) {
                            SettingsScreen(
                                initial = credentials,
                                onSave = { saved ->
                                    prefs.save(saved)
                                    credentials = saved
                                    screen = Screen.Favorites
                                }
                            )
                        }
                    }
                }

                is Screen.Favorites -> {
                    val api = remember(credentials) { XtreamApi(credentials) }
                    var nowPlaying by remember { mutableStateOf<Map<Int, NowPlayingInfo>>(emptyMap()) }
                    LaunchedEffect(favorites) {
                        val cached = withContext(Dispatchers.IO) {
                            favorites.mapNotNull { channel ->
                                channelDb.getCachedNowPlaying(channel.streamId)?.let { channel.streamId to it }
                            }.toMap()
                        }
                        nowPlaying = cached
                        // 24h refresh, matching "this is a shortlist, not the
                        // full catalog" - one get_short_epg call per stale
                        // favorite, never the whole provider catalog.
                        val maxAgeMillis = 24 * 60 * 60 * 1000L
                        favorites.forEach { channel ->
                            val stale = withContext(Dispatchers.IO) {
                                channelDb.isEpgStale(channel.streamId, maxAgeMillis)
                            }
                            if (stale) {
                                val info = try {
                                    api.getNowPlayingInfo(channel.streamId)
                                } catch (e: Exception) {
                                    null
                                }
                                if (info != null) {
                                    withContext(Dispatchers.IO) {
                                        channelDb.setNowPlaying(channel.streamId, info)
                                    }
                                    nowPlaying = nowPlaying + (channel.streamId to info)
                                }
                            }
                        }
                    }
                    WithRail(selected = currentScreen.toRailItem(), onSelectRail = onSelectRail) {
                        FavoritesScreen(
                            favorites = favorites,
                            nowPlaying = nowPlaying,
                            defaultStreamId = defaultStreamId,
                            onPlay = { channel ->
                                screen = Screen.NowPlaying(PlayableItem.Live(channel), returnTo = Screen.Favorites)
                            },
                            onRemove = toggleFavorite,
                            onSetDefault = onSetDefault
                        )
                    }
                }

                is Screen.Categories -> {
                    BackHandler { screen = Screen.Favorites }
                    val api = remember(credentials) { XtreamApi(credentials) }
                    var categoriesState by remember(credentials) {
                        mutableStateOf<LoadState<List<LiveCategory>>>(LoadState.Loading)
                    }
                    LaunchedEffect(credentials) {
                        categoriesState = try {
                            LoadState.Success(api.getLiveCategories())
                        } catch (e: Exception) {
                            LoadState.Error(e.message ?: "Unknown error")
                        }
                    }
                    WithRail(selected = currentScreen.toRailItem(), onSelectRail = onSelectRail) {
                        CategoryListScreen(
                            state = categoriesState,
                            onSelectCategory = { category -> screen = Screen.Channels(category) }
                        )
                    }
                }

                is Screen.Channels -> {
                    BackHandler { screen = Screen.Categories }
                    val api = remember(credentials) { XtreamApi(credentials) }
                    var channelsState by remember(currentScreen.category) {
                        mutableStateOf<LoadState<List<LiveChannel>>>(LoadState.Loading)
                    }
                    LaunchedEffect(currentScreen.category) {
                        channelsState = try {
                            LoadState.Success(api.getLiveStreams(currentScreen.category.categoryId))
                        } catch (e: Exception) {
                            LoadState.Error(e.message ?: "Unknown error")
                        }
                    }
                    WithRail(selected = currentScreen.toRailItem(), onSelectRail = onSelectRail) {
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
                    BackHandler {
                        if (searchQuery.isNotBlank()) {
                            searchQuery = ""
                        } else {
                            screen = Screen.Favorites
                        }
                    }
                    val api = remember(credentials) { XtreamApi(credentials) }
                    var syncState by remember(credentials) {
                        mutableStateOf<LoadState<Unit>>(LoadState.Loading)
                    }
                    var syncedLiveCount by remember(credentials) { mutableStateOf(0) }
                    var syncedVodCount by remember(credentials) { mutableStateOf(0) }
                    var syncedSeriesCount by remember(credentials) { mutableStateOf(0) }
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
                            LoadState.Success(Unit)
                        } catch (e: Exception) {
                            LoadState.Error(e.message ?: "Unknown error")
                        }
                    }
                    WithRail(selected = currentScreen.toRailItem(), onSelectRail = onSelectRail) {
                        SearchScreen(
                            query = searchQuery,
                            onQueryChange = { searchQuery = it },
                            syncState = syncState,
                            syncedLiveCount = syncedLiveCount,
                            syncedVodCount = syncedVodCount,
                            syncedSeriesCount = syncedSeriesCount,
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
                            onToggleSeriesSaved = toggleSeriesSaved
                        )
                    }
                }

                is Screen.MyVod -> {
                    BackHandler { screen = Screen.Favorites }
                    WithRail(selected = currentScreen.toRailItem(), onSelectRail = onSelectRail) {
                        MyVodScreen(
                            savedMovies = savedMovies,
                            savedSeries = savedSeries,
                            onPlayMovie = { movie ->
                                screen = Screen.NowPlaying(PlayableItem.Vod(movie), returnTo = Screen.MyVod)
                            },
                            onOpenEpisodes = { series -> screen = Screen.SeriesEpisodes(series, returnTo = Screen.MyVod) },
                            onRemoveMovie = toggleMovieSaved,
                            onRemoveSeries = toggleSeriesSaved
                        )
                    }
                }

                is Screen.SeriesEpisodes -> {
                    BackHandler { screen = currentScreen.returnTo }
                    val api = remember(credentials) { XtreamApi(credentials) }
                    var episodesState by remember(currentScreen.series) {
                        mutableStateOf<LoadState<List<SeriesEpisode>>>(LoadState.Loading)
                    }
                    LaunchedEffect(currentScreen.series) {
                        episodesState = try {
                            LoadState.Success(api.getSeriesEpisodes(currentScreen.series.seriesId))
                        } catch (e: Exception) {
                            LoadState.Error(e.message ?: "Unknown error")
                        }
                    }
                    WithRail(selected = null, onSelectRail = onSelectRail) {
                        SeriesEpisodesScreen(
                            seriesName = currentScreen.series.name,
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
                    BackHandler { screen = currentScreen.returnTo }
                    val item = currentScreen.item
                    val params = remember(item) { item.toPlayerParams(api) }
                    // Up/down channel-cycling always cycles through favorites
                    // specifically (not whatever list you arrived from), per
                    // the user's request - a no-op if the current channel
                    // isn't one, there's nothing to cycle to, or this isn't
                    // live content at all.
                    val favoriteIndex = (item as? PlayableItem.Live)?.let { live ->
                        favorites.indexOfFirst { it.streamId == live.channel.streamId }
                    } ?: -1
                    PlayerScreen(
                        title = params.title,
                        iconUrl = params.iconUrl,
                        streamUrl = params.streamUrl,
                        contentId = params.contentId,
                        isLive = params.isLive,
                        api = api,
                        subtitle = params.subtitle,
                        loadDescription = params.loadDescription,
                        onSelectRail = onSelectRail,
                        onPlayItem = { newItem -> screen = Screen.NowPlaying(newItem, returnTo = currentScreen.returnTo) },
                        favorites = favorites,
                        defaultStreamId = defaultStreamId,
                        isFavorite = isFavorite,
                        onToggleFavorite = toggleFavorite,
                        onSetDefault = onSetDefault,
                        savedMovies = savedMovies,
                        savedSeries = savedSeries,
                        isMovieSaved = isMovieSaved,
                        isSeriesSaved = isSeriesSaved,
                        onToggleMovieSaved = toggleMovieSaved,
                        onToggleSeriesSaved = toggleSeriesSaved,
                        onChannelChange = { direction ->
                            if (params.isLive && favorites.isNotEmpty() && favoriteIndex >= 0) {
                                val nextIndex = (favoriteIndex + direction + favorites.size) % favorites.size
                                screen = Screen.NowPlaying(
                                    PlayableItem.Live(favorites[nextIndex]),
                                    returnTo = currentScreen.returnTo
                                )
                            }
                        }
                    )
                }
            }
        }
    }
}
