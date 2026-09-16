package com.kdresdell.iptvtv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Card
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

@Composable
fun ChannelListScreen(
    categoryName: String,
    state: LoadState<List<LiveChannel>>,
    isFavorite: (Int) -> Boolean,
    onSelectChannel: (LiveChannel) -> Unit,
    onToggleFavorite: (LiveChannel) -> Unit,
    onBack: () -> Unit
) {
    val onBackground = MaterialTheme.colorScheme.onBackground
    val firstItemFocus = remember { FocusRequester() }

    // See CategoryListScreen for why this is needed - without it, D-pad
    // focus lands nowhere when this screen opens.
    LaunchedEffect(state) {
        if (state is LoadState.Success && state.data.isNotEmpty()) {
            firstItemFocus.requestFocus()
        }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        when (state) {
            is LoadState.Loading -> Text(text = "Loading channels...", color = onBackground)
            is LoadState.Error -> {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(24.dp)
                ) {
                    item { Text(text = "Could not load channels: ${state.message}", color = onBackground) }
                    item {
                        Card(onClick = onBack) {
                            Text(text = "Back to categories", modifier = Modifier.padding(24.dp))
                        }
                    }
                }
            }
            is LoadState.Success -> {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(24.dp)
                ) {
                    item { Text(text = categoryName, color = onBackground) }
                    itemsIndexed(state.data) { index, channel ->
                        ChannelRow(
                            channel = channel,
                            isFavorite = isFavorite(channel.streamId),
                            onPlay = { onSelectChannel(channel) },
                            onToggleFavorite = { onToggleFavorite(channel) },
                            playCardModifier = if (index == 0) Modifier.focusRequester(firstItemFocus) else Modifier
                        )
                    }
                    item {
                        Card(onClick = onBack) {
                            Text(text = "Back to categories", modifier = Modifier.padding(24.dp))
                        }
                    }
                }
            }
        }
    }
}
