package com.kdresdell.iptvtv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

// This is the app's home screen - reached by default, and there's
// nothing "above" it to go back to (the rail is how you leave it).
@Composable
fun FavoritesScreen(
    favorites: List<LiveChannel>,
    nowPlaying: Map<Int, NowPlayingInfo>,
    onPlay: (LiveChannel) -> Unit,
    onRemove: (LiveChannel) -> Unit
) {
    val onBackground = MaterialTheme.colorScheme.onBackground
    val firstItemFocusRequester = remember { FocusRequester() }

    // Default D-pad focus lands on the side rail otherwise (first thing in
    // composition order) - explicitly claim it for the content instead,
    // since this is the home screen and should be scrollable immediately.
    LaunchedEffect(Unit) {
        if (favorites.isNotEmpty()) {
            firstItemFocusRequester.requestFocus()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(24.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = if (favorites.isEmpty()) "No favorites yet - use Search to add some" else "Favorites",
                        color = onBackground
                    )
                    LiveClock()
                }
            }
            itemsIndexed(favorites) { index, channel ->
                ChannelRow(
                    channel = channel,
                    isFavorite = true,
                    onPlay = { onPlay(channel) },
                    onToggleFavorite = { onRemove(channel) },
                    subtitle = epgSubtitle(nowPlaying[channel.streamId]),
                    playCardModifier = if (index == 0) Modifier.focusRequester(firstItemFocusRequester) else Modifier
                )
            }
        }
    }
}

private fun epgSubtitle(info: NowPlayingInfo?): String? =
    info?.let { "Now: ${it.title}" }

@Composable
private fun LiveClock() {
    var now by remember { mutableStateOf(formatNow()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = formatNow()
            delay(30_000)
        }
    }
    Text(text = now, color = MaterialTheme.colorScheme.onBackground)
}

private fun formatNow(): String =
    SimpleDateFormat("EEE, MMM d - HH:mm", Locale.getDefault()).format(Date())
