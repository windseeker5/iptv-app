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
fun CategoryListScreen(
    state: LoadState<List<LiveCategory>>,
    onSelectCategory: (LiveCategory) -> Unit
) {
    val onBackground = MaterialTheme.colorScheme.onBackground
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        when (state) {
            is LoadState.Loading -> Text(text = "Loading categories...", color = onBackground)
            is LoadState.Error -> Text(text = "Could not load channels: ${state.message}", color = onBackground)
            is LoadState.Success -> {
                if (state.data.isEmpty()) {
                    Text(text = "This provider returned no live categories.", color = onBackground)
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(24.dp)
                    ) {
                        item {
                            Text(text = "Live categories", color = onBackground)
                        }
                        items(state.data) { category ->
                            Card(onClick = { onSelectCategory(category) }) {
                                Text(text = category.categoryName, modifier = Modifier.padding(24.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}
