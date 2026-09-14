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

private sealed class Screen {
    data object Settings : Screen()
    data object Categories : Screen()
    data class Channels(val category: LiveCategory) : Screen()
    data class Player(val category: LiveCategory, val channel: LiveChannel) : Screen()
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val context = LocalContext.current
            val prefs = remember { ProviderPrefs(context) }
            var credentials by remember { mutableStateOf(prefs.load()) }
            var screen by remember {
                mutableStateOf<Screen>(if (credentials.isComplete) Screen.Categories else Screen.Settings)
            }

            when (val currentScreen = screen) {
                is Screen.Settings -> {
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
                            onSelectCategory = { category -> screen = Screen.Channels(category) },
                            onEditSettings = { screen = Screen.Settings }
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
                            LoadState.Error(e.message ?: "Unknown error")
                        }
                    }
                    MaterialTheme(colorScheme = tvDarkColorScheme()) {
                        ChannelListScreen(
                            categoryName = currentScreen.category.categoryName,
                            state = channelsState,
                            onSelectChannel = { channel ->
                                screen = Screen.Player(currentScreen.category, channel)
                            },
                            onBack = { screen = Screen.Categories }
                        )
                    }
                }

                is Screen.Player -> {
                    val api = remember(credentials) { XtreamApi(credentials) }
                    BackHandler {
                        screen = Screen.Channels(currentScreen.category)
                    }
                    PlayerScreen(streamUrl = api.liveStreamUrl(currentScreen.channel.streamId))
                }
            }
        }
    }
}
