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

// Favorites is the app's home screen. The persistent side rail (see
// SideRail.kt) is how you reach Search, Categories, and Settings - it is
// not itself part of this back-stack, it just changes which Screen is
// current, same as selecting a rail item.
private sealed class Screen {
    data object Settings : Screen()
    data object Favorites : Screen()
    data object Categories : Screen()
    data object Search : Screen()
    data class Channels(val category: LiveCategory) : Screen()
    data class Player(val channel: LiveChannel, val returnTo: Screen) : Screen()
}

private fun Screen.toRailItem(): RailItem? = when (this) {
    is Screen.Search -> RailItem.Search
    is Screen.Categories, is Screen.Channels -> RailItem.Categories
    is Screen.Settings -> RailItem.Settings
    else -> null
}

private fun RailItem.toScreen(): Screen = when (this) {
    RailItem.Search -> Screen.Search
    RailItem.Categories -> Screen.Categories
    RailItem.Settings -> Screen.Settings
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val context = LocalContext.current
            val prefs = remember { ProviderPrefs(context) }
            val favoritesStore = remember { FavoritesStore(context) }
            val channelDb = remember { LiveChannelDatabase(context) }
            var credentials by remember { mutableStateOf(prefs.load()) }
            var favorites by remember { mutableStateOf(favoritesStore.load()) }
            var screen by remember {
                mutableStateOf<Screen>(if (credentials.isComplete) Screen.Favorites else Screen.Settings)
            }

            val isFavorite: (Int) -> Boolean = { id -> favorites.any { it.streamId == id } }
            val toggleFavorite: (LiveChannel) -> Unit = { channel ->
                favorites = favoritesStore.toggle(channel, favorites)
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
                    WithRail(selected = null, onSelectRail = onSelectRail) {
                        FavoritesScreen(
                            favorites = favorites,
                            nowPlaying = nowPlaying,
                            onPlay = { channel -> screen = Screen.Player(channel, returnTo = Screen.Favorites) },
                            onRemove = toggleFavorite
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
                                screen = Screen.Player(channel, returnTo = currentScreen)
                            },
                            onToggleFavorite = toggleFavorite,
                            onBack = { screen = Screen.Categories }
                        )
                    }
                }

                is Screen.Search -> {
                    BackHandler { screen = Screen.Favorites }
                    val api = remember(credentials) { XtreamApi(credentials) }
                    var syncState by remember(credentials) {
                        mutableStateOf<LoadState<Unit>>(LoadState.Loading)
                    }
                    var syncedCount by remember(credentials) { mutableStateOf(0) }
                    LaunchedEffect(credentials) {
                        syncState = try {
                            if (channelDb.isEmpty()) {
                                api.syncAllLiveChannelsInto(channelDb) { count -> syncedCount = count }
                            }
                            LoadState.Success(Unit)
                        } catch (e: Exception) {
                            LoadState.Error(e.message ?: "Unknown error")
                        }
                    }
                    WithRail(selected = currentScreen.toRailItem(), onSelectRail = onSelectRail) {
                        SearchScreen(
                            syncState = syncState,
                            syncedCount = syncedCount,
                            onSearch = { query -> withContext(Dispatchers.IO) { channelDb.search(query) } },
                            isFavorite = isFavorite,
                            onPlay = { channel -> screen = Screen.Player(channel, returnTo = Screen.Search) },
                            onToggleFavorite = toggleFavorite
                        )
                    }
                }

                is Screen.Player -> {
                    val api = remember(credentials) { XtreamApi(credentials) }
                    BackHandler {
                        screen = currentScreen.returnTo
                    }
                    // Up/down always cycles through favorites specifically
                    // (not whatever list you arrived from), per the user's
                    // request - a no-op if the current channel isn't one or
                    // there's nothing to cycle to.
                    val favoriteIndex = favorites.indexOfFirst { it.streamId == currentScreen.channel.streamId }
                    PlayerScreen(
                        channel = currentScreen.channel,
                        streamUrl = api.liveStreamUrl(currentScreen.channel.streamId),
                        api = api,
                        onChannelChange = { direction ->
                            if (favorites.isNotEmpty() && favoriteIndex >= 0) {
                                val nextIndex = (favoriteIndex + direction + favorites.size) % favorites.size
                                screen = Screen.Player(favorites[nextIndex], returnTo = currentScreen.returnTo)
                            }
                        }
                    )
                }
            }
        }
    }
}
