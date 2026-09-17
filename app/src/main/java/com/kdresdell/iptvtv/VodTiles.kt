package com.kdresdell.iptvtv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.kdresdell.iptvtv.theme.LocalAppColors
import com.kdresdell.iptvtv.theme.ScreenColors
import com.kdresdell.iptvtv.theme.appCardBorder
import com.kdresdell.iptvtv.theme.appCardGlow
import com.kdresdell.iptvtv.theme.appCardScale
import com.kdresdell.iptvtv.theme.appRowCardColors
import com.kdresdell.iptvtv.theme.appRowCardScale
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// §4.2/§4.3, §6.6 - the 5-card (124dp) poster grid density used by My
// Librairie (VodTiles.kt/MyVodScreen.kt/SeriesEpisodesScreen.kt). Unlike
// VodRow/SeriesRow below (full-width list rows, no scale on focus), this is
// a real grid card - standard appCardScale() applies.
// -10% from the original 124dp - explicit user request (2026-09-17), part
// of fixing My Librairie's poster grid being cut off at the bottom with no
// way to scroll further (see MyVodScreen's contentPadding fix for the rest
// of that).
val PosterCardWidth = 112.dp

// One focusable poster: art fills the card, title sits on a bottom scrim
// (per the approved mockup) rather than a flat surface with side-by-side
// text. Movies play directly on click; series open the episode picker -
// both share this same visual, only the click target differs.
@Composable
fun PosterCard(
    title: String,
    cover: String,
    isSaved: Boolean,
    onOpen: () -> Unit,
    onToggleSaved: () -> Unit,
    cardModifier: Modifier = Modifier
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(8.dp)

    Card(
        onClick = onOpen,
        onLongClick = { menuExpanded = true },
        modifier = Modifier.width(PosterCardWidth).aspectRatio(2f / 3f).then(cardModifier),
        scale = appCardScale(),
        colors = CardDefaults.colors(containerColor = LocalAppColors.current.surfaceContainer),
        shape = CardDefaults.shape(shape = shape),
        border = appCardBorder(shape = shape),
        glow = appCardGlow()
    ) {
        AsyncImage(
            model = cover,
            contentDescription = title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
    }

    RowActionsMenu(
        expanded = menuExpanded,
        onDismiss = { menuExpanded = false },
        actions = listOf(MenuAction(if (isSaved) "Remove from My Librairie" else "Add to My Librairie", onToggleSaved))
    )
}

// §6 "Lists" row pattern - 2:3 poster, matches each VOD/series result's real
// artwork shape (as opposed to LiveThumbnail's 16:9 logo box in ChannelRow.kt).
@Composable
private fun PosterThumbnail(cover: String) {
    Box(
        modifier = Modifier
            .width(42.dp)
            .aspectRatio(2f / 3f)
            .clip(RoundedCornerShape(6.dp))
            .background(LocalAppColors.current.surfaceContainer)
    ) {
        AsyncImage(
            model = cover,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f)
        )
    }
}

// Shared row tiles for VOD content (movies, series) - used by both Search
// results and the My VOD saved list, so both look and behave the same.

// A small MD3 tonal-container pill marking a result's content type - live,
// movie, or series.
@Composable
fun ResultTypeBadge(label: String, tonalColor: Color) {
    Surface(
        shape = RoundedCornerShape(50),
        colors = SurfaceDefaults.colors(containerColor = tonalColor)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

// One focusable Card: short press plays, long press (native to
// tv.material3.Card) opens RowActionsMenu to add/remove from My VOD. See
// ChannelRow.kt for why this replaced two separate Cards.
@Composable
fun VodRow(
    movie: VodStream,
    isSaved: Boolean,
    onPlay: () -> Unit,
    onToggleSaved: () -> Unit,
    playCardModifier: Modifier = Modifier,
    typeBadge: (@Composable () -> Unit)? = null
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Card(
        onClick = onPlay,
        onLongClick = { menuExpanded = true },
        modifier = Modifier.fillMaxWidth().then(playCardModifier),
        // See ChannelRow.kt - explicitly no scale-up on focus for rows.
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
            PosterThumbnail(movie.streamIcon)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                // See ChannelRow.kt - the only in-line feedback for "already
                // saved" now that the always-visible button is gone.
                if (isSaved) {
                    Text(text = "★", color = Color(0xFFA6F2A6))
                }
                Text(text = TitleFormat.clean(movie.name), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
            }
            typeBadge?.invoke()
        }
    }

    RowActionsMenu(
        expanded = menuExpanded,
        onDismiss = { menuExpanded = false },
        actions = listOf(MenuAction(if (isSaved) "Remove from My VOD" else "Add to My VOD", onToggleSaved))
    )
}

// A series has no single playable stream - selecting it opens the episode
// picker instead of playing directly.
@Composable
fun SeriesRow(
    series: SeriesShow,
    isSaved: Boolean,
    onOpenEpisodes: () -> Unit,
    onToggleSaved: () -> Unit,
    playCardModifier: Modifier = Modifier,
    typeBadge: (@Composable () -> Unit)? = null
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Card(
        onClick = onOpenEpisodes,
        onLongClick = { menuExpanded = true },
        modifier = Modifier.fillMaxWidth().then(playCardModifier),
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
            PosterThumbnail(series.cover)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                if (isSaved) {
                    Text(text = "★", color = Color(0xFFA6F2A6))
                }
                Text(text = TitleFormat.clean(series.name), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
            }
            typeBadge?.invoke()
        }
    }

    RowActionsMenu(
        expanded = menuExpanded,
        onDismiss = { menuExpanded = false },
        actions = listOf(MenuAction(if (isSaved) "Remove from My VOD" else "Add to My VOD", onToggleSaved))
    )
}

// A recorded file, not a catalog item - it always "belongs" to the library
// (there's no un-recorded state to toggle back to), so the long-press menu
// is used purely as a delete action ("Remove from Recordings").
@Composable
fun RecordingRow(
    file: File,
    onPlay: () -> Unit,
    onDelete: () -> Unit,
    playCardModifier: Modifier = Modifier
) {
    var menuExpanded by remember { mutableStateOf(false) }

    // See ChannelRow.kt / VodRow above - this Card was the one card in the
    // app left on the tv.material3 library defaults (no appRowCardScale()/
    // appRowCardColors()/appCardBorder()/appCardGlow()), which reproduces
    // the exact "focused row scales up huge" bug already fixed everywhere
    // else - a focused full-width row must never scale.
    Card(
        onClick = onPlay,
        onLongClick = { menuExpanded = true },
        modifier = Modifier.fillMaxWidth().then(playCardModifier),
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
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(ScreenColors.RecordAccent, CircleShape)
            )
            Column {
                Text(text = RecordingStorage.displayName(file), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                val date = SimpleDateFormat("MMM d - HH:mm", Locale.getDefault()).format(Date(file.lastModified()))
                val sizeMb = file.length() / (1024 * 1024)
                Text(text = "$date · $sizeMb MB", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    RowActionsMenu(
        expanded = menuExpanded,
        onDismiss = { menuExpanded = false },
        actions = listOf(MenuAction("Remove from Recordings", onDelete, isDestructive = true))
    )
}
