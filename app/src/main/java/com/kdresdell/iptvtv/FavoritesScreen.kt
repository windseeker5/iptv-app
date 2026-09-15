package com.kdresdell.iptvtv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Card
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

@Composable
fun FavoritesScreen(
    favorites: List<LiveChannel>,
    onPlay: (LiveChannel) -> Unit,
    onRemove: (LiveChannel) -> Unit,
    onBack: () -> Unit
) {
    val onBackground = MaterialTheme.colorScheme.onBackground
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
                Text(
                    text = if (favorites.isEmpty()) "No favorites yet" else "Favorites",
                    color = onBackground
                )
            }
            items(favorites) { channel ->
                ChannelRow(
                    channel = channel,
                    isFavorite = true,
                    onPlay = { onPlay(channel) },
                    onToggleFavorite = { onRemove(channel) }
                )
            }
            item {
                Card(onClick = onBack) {
                    Text(text = "Back", modifier = Modifier.padding(24.dp))
                }
            }
        }
    }
}
