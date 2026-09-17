package com.kdresdell.iptvtv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import androidx.tv.material3.IconButtonDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.kdresdell.iptvtv.theme.LocalAppColors
import com.kdresdell.iptvtv.theme.RailIcons
import com.kdresdell.iptvtv.theme.appIconButtonBorder
import com.kdresdell.iptvtv.theme.appIconButtonGlow
import com.kdresdell.iptvtv.theme.appIconButtonScale

// STYLE_GUIDE.md §6.5 - back navigation is a circular icon button top-left
// next to the screen title, replacing the old bottom "Back to categories"
// Card. Keeps the same explicit D-pad-focusable affordance (Back isn't
// always reliably mapped), just moved to the conventional TV back-button
// position.
@Composable
fun ChannelListScreen(
    categoryName: String,
    state: LoadState<List<LiveChannel>>,
    isFavorite: (Int) -> Boolean,
    onSelectChannel: (LiveChannel) -> Unit,
    onToggleFavorite: (LiveChannel) -> Unit,
    onBack: () -> Unit
) {
    val firstItemFocus = remember { FocusRequester() }

    // See CategoryListScreen for why this is needed - without it, D-pad
    // focus lands nowhere when this screen opens.
    LaunchedEffect(state) {
        if (state is LoadState.Success && state.data.isNotEmpty()) {
            firstItemFocus.requestFocus()
        }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(horizontal = 48.dp, vertical = 24.dp)
        ) {
            IconButton(
                onClick = onBack,
                scale = appIconButtonScale(),
                border = appIconButtonBorder(),
                glow = appIconButtonGlow(),
                colors = IconButtonDefaults.colors(
                    containerColor = LocalAppColors.current.surfaceContainer,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    focusedContainerColor = LocalAppColors.current.selectedSurface,
                    focusedContentColor = MaterialTheme.colorScheme.onSurface
                )
            ) {
                Icon(
                    imageVector = RailIcons.ArrowLeft,
                    contentDescription = "Back to categories",
                    modifier = Modifier.size(20.dp)
                )
            }
            Text(
                text = categoryName,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        when (state) {
            is LoadState.Loading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "Loading channels...",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            is LoadState.Error -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "Could not load channels: ${state.message}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 48.dp)
                )
            }
            is LoadState.Success -> {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(horizontal = 48.dp, vertical = 8.dp)
                ) {
                    itemsIndexed(state.data) { index, channel ->
                        ChannelRow(
                            channel = channel,
                            isFavorite = isFavorite(channel.streamId),
                            onPlay = { onSelectChannel(channel) },
                            onToggleFavorite = { onToggleFavorite(channel) },
                            playCardModifier = if (index == 0) Modifier.focusRequester(firstItemFocus) else Modifier
                        )
                    }
                }
            }
        }
    }
}
