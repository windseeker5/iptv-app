package com.kdresdell.iptvtv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import androidx.tv.material3.IconButtonDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import coil.compose.AsyncImage
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
    onBack: () -> Unit,
    streamUrlFor: (Int) -> String
) {
    val firstItemFocus = remember { FocusRequester() }
    // Live preview (top right) of the channel the remote rests on. Only a
    // row *gaining* focus changes it, so the long-press menu taking focus
    // leaves the preview playing. The delay means fast scrolling doesn't
    // open a stream for every row passed - only where the cursor stops.
    var focusedChannel by remember(state) { mutableStateOf<LiveChannel?>(null) }
    var previewChannel by remember(state) { mutableStateOf<LiveChannel?>(null) }
    LaunchedEffect(focusedChannel) {
        delay(PreviewDelayMs)
        previewChannel = focusedChannel
    }
    // Up from the first channel goes to the Back button as before, so the
    // wrap to the last channel happens from the Back button instead.
    val wrap = rememberListWrap(
        count = (state as? LoadState.Success)?.data?.size ?: 0,
        wrapUpFromFirst = false
    )

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
                modifier = wrap.upFromAbove,
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
                Row(modifier = Modifier.padding(start = 48.dp, end = 48.dp)) {
                    LazyColumn(
                        state = wrap.listState,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f).padding(vertical = 8.dp).then(wrap.keys)
                    ) {
                        itemsIndexed(state.data) { index, channel ->
                            ChannelRow(
                                channel = channel,
                                isFavorite = isFavorite(channel.streamId),
                                onPlay = { onSelectChannel(channel) },
                                onToggleFavorite = { onToggleFavorite(channel) },
                                playCardModifier = (if (index == 0) Modifier.focusRequester(firstItemFocus) else Modifier)
                                    .then(wrap.itemModifier(index))
                                    .onFocusChanged { if (it.isFocused) focusedChannel = channel }
                            )
                        }
                    }
                    ChannelPreview(
                        channel = previewChannel ?: focusedChannel,
                        streamUrl = previewChannel?.let { streamUrlFor(it.streamId) },
                        modifier = Modifier.padding(start = 24.dp, top = 8.dp)
                    )
                }
            }
        }
    }
}

// Same throwaway player as the guide's preview (LivePreview in
// FavoritesScreen.kt) - its own ExoPlayer per channel, released when the
// channel changes or this screen goes away (OK -> full screen, Back), so
// only one stream is ever open. Until the cursor settles, the channel's
// icon stands in.
@Composable
private fun ChannelPreview(channel: LiveChannel?, streamUrl: String?, modifier: Modifier = Modifier) {
    Column(modifier = modifier.width(400.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(10.dp))
                .background(LocalAppColors.current.surfaceContainer)
        ) {
            if (streamUrl != null) {
                key(streamUrl) {
                    LivePreview(streamUrl = streamUrl, modifier = Modifier.fillMaxSize())
                }
            } else if (channel != null) {
                AsyncImage(
                    model = channel.streamIcon,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().padding(32.dp)
                )
            }
        }
        if (channel != null) {
            Text(
                text = TitleFormat.clean(channel.name),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 10.dp)
            )
        }
    }
}

private const val PreviewDelayMs = 600L
