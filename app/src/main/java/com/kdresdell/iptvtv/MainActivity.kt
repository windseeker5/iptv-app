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
import androidx.compose.material3.MaterialTheme as PhoneMaterialTheme
import androidx.compose.material3.darkColorScheme as phoneDarkColorScheme
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme as tvDarkColorScheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private sealed class Screen {
    data object Settings : Screen()
    data object Categories : Screen()
    data object Search : Screen()
    data object Favorites : Screen()
    data class Channels(val category: LiveCategory) : Screen()
    data class Player(val channel: LiveChannel, val returnTo: Screen) : Screen()
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val context = LocalContext.current
            val prefs = remember { ProviderPrefs(context) }
            val favoritesStore = remember { FavoritesStore(context) }
            var credentials by remember { mutableStateOf(prefs.load()) }
            var favorites by remember { mutableStateOf(favoritesStore.load()) }
            var screen by remember {
                mutableStateOf<Screen>(if (credentials.isComplete) Screen.Categories else Screen.Settings)
            }

            val isFavorite: (Int) -> Boolean = { id -> favorites.any { it.streamId == id } }
            val toggleFavorite: (LiveChannel) -> Unit = { channel ->
                favorites = favoritesStore.toggle(channel, favorites)
            }

            when (val currentScreen = screen) {
                is Screen.Settings -> {
                    // Only intercept back when there's somewhere to return to
                    // (i.e. this was reached via "Edit settings", not first launch) -
                    // otherwise let back behave normally and exit the app.
                    BackHandler(enabled = credentials.isComplete) {
                        screen = Screen.Categories
                    }
                    PhoneMaterialTheme(colorScheme = phoneDarkColorScheme()) {
                        SettingsScreen(
                            initial = credentials,
                            onSave = { saved ->
                                prefs.save(saved)
                                credentials = saved
                                screen = Screen.Categories
                            }
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
                            LoadState.Error(e.message ?: "Unknown error")
                        }
                    }
                    MaterialTheme(colorScheme = tvDarkColorScheme()) {
                        CategoryListScreen(
                            state = categoriesState,
                            favoritesCount = favorites.size,
                            onSelectCategory = { category -> screen = Screen.Channels(category) },
                            onSearch = { screen = Screen.Search },
                            onFavorites = { screen = Screen.Favorites },
                            onEditSettings = { screen = Screen.Settings }
                        )
                    }
                }

                is Screen.Channels -> {
                    BackHandler {
                        screen = Screen.Categories
                    }
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
                    MaterialTheme(colorScheme = tvDarkColorScheme()) {
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
                    BackHandler {
                        screen = Screen.Categories
                    }
                    val api = remember(credentials) { XtreamApi(credentials) }
                    val channelDb = remember { LiveChannelDatabase(context) }
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
                    MaterialTheme(colorScheme = tvDarkColorScheme()) {
                        SearchScreen(
                            syncState = syncState,
                            syncedCount = syncedCount,
                            onSearch = { query -> withContext(Dispatchers.IO) { channelDb.search(query) } },
                            isFavorite = isFavorite,
                            onPlay = { channel -> screen = Screen.Player(channel, returnTo = Screen.Search) },
                            onToggleFavorite = toggleFavorite,
                            onBack = { screen = Screen.Categories }
                        )
                    }
                }

                is Screen.Favorites -> {
                    BackHandler {
                        screen = Screen.Categories
                    }
                    MaterialTheme(colorScheme = tvDarkColorScheme()) {
                        FavoritesScreen(
                            favorites = favorites,
                            onPlay = { channel -> screen = Screen.Player(channel, returnTo = Screen.Favorites) },
                            onRemove = toggleFavorite,
                            onBack = { screen = Screen.Categories }
                        )
                    }
                }

                is Screen.Player -> {
                    val api = remember(credentials) { XtreamApi(credentials) }
                    BackHandler {
                        screen = currentScreen.returnTo
                    }
                    PlayerScreen(streamUrl = api.liveStreamUrl(currentScreen.channel.streamId))
                }
            }
        }
    }
}
