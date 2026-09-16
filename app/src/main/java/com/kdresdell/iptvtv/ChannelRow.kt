package com.kdresdell.iptvtv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Card
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.kdresdell.iptvtv.theme.LocalAppColors
import com.kdresdell.iptvtv.theme.appCardBorder
import com.kdresdell.iptvtv.theme.appCardGlow
import com.kdresdell.iptvtv.theme.appRowCardColors
import com.kdresdell.iptvtv.theme.appRowCardScale

// §6 "Lists" row pattern - small 16:9 thumbnail ahead of the title, same
// shape convention as the live-channel logo used elsewhere (FavoritesScreen's
// top preview block): a tonal box behind a Fit-scaled logo, since provider
// logos are usually small art on a transparent background, not a full still.
@Composable
private fun LiveThumbnail(streamIcon: String) {
    Box(
        modifier = Modifier
            .width(72.dp)
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(6.dp))
            .background(LocalAppColors.current.surfaceContainer),
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = streamIcon,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).padding(6.dp)
        )
    }
}

// One focusable Card per row: short press (onClick) plays, long press
// (onClick's onLongClick - native to tv.material3.Card, not a hand-rolled
// hold-duration hack) opens RowActionsMenu for favorite/default. Used to be
// two separate Cards (play + a big always-visible favorite button) because
// of a since-revisited assumption that TV remotes have no reliable
// long-press - androidx.tv.material3.Card supports it natively.
@Composable
fun ChannelRow(
    channel: LiveChannel,
    isFavorite: Boolean,
    onPlay: () -> Unit,
    onToggleFavorite: () -> Unit,
    subtitle: String? = null,
    playCardModifier: Modifier = Modifier,
    isDefault: Boolean = false,
    onSetDefault: (() -> Unit)? = null,
    typeBadge: (@Composable () -> Unit)? = null
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Card(
        onClick = onPlay,
        onLongClick = { menuExpanded = true },
        modifier = Modifier.fillMaxWidth().then(playCardModifier),
        // §5 recipe, minus scale - explicitly no scale-up on focus here (see
        // appRowCardScale()): a full-width row growing 1.05-1.1x reads as a
        // huge, jarring jump. Background lift (appRowCardColors) + outline +
        // glow only, matching the approved mockup's row-focus CSS.
        scale = appRowCardScale(),
        colors = appRowCardColors(),
        border = appCardBorder(shape = RoundedCornerShape(8.dp)),
        glow = appCardGlow()
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            LiveThumbnail(channel.streamIcon)
            Column(modifier = Modifier.weight(1f)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    // Long-press replaced the always-visible favorite
                    // button, so this star is the only in-line feedback
                    // that a row is already a favorite - without it there
                    // was no way to tell short of opening the menu.
                    if (isFavorite) {
                        Text(text = "★", color = Color(0xFFA6F2A6))
                    }
                    // §6 "Lists": Title Small primary text.
                    Text(text = channel.name, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                }
                if (subtitle != null) {
                    // §6 "Lists": Body Medium secondary text.
                    Text(text = subtitle, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            // Trailing, not leading - matches the approved search-results
            // mockup (§6.4): the type tag sits at the far right of the row,
            // not between the thumbnail and the title.
            typeBadge?.invoke()
        }
    }

    RowActionsMenu(
        expanded = menuExpanded,
        onDismiss = { menuExpanded = false },
        actions = buildList {
            add(MenuAction(if (isFavorite) "Remove from Favorite" else "Add to Favorite", onToggleFavorite))
            if (onSetDefault != null) {
                add(MenuAction(if (isDefault) "Remove Default" else "Set as Default", onSetDefault))
            }
        }
    )
}
