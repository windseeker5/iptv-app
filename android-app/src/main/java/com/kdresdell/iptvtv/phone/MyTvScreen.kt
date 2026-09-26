package com.kdresdell.iptvtv.phone

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Whether the guide for the saved channels is being fetched - the fetch
// itself runs in MainActivity so switching tabs doesn't cancel a 13MB
// download halfway through.
sealed class GuideStatus {
    data object Idle : GuideStatus()
    data object Loading : GuideStatus()
    data class Failed(val message: String) : GuideStatus()
}

@Composable
fun MyTvScreen(
    favorites: List<LiveChannel>,
    db: CatalogDatabase,
    api: XtreamApi,
    guideStatus: GuideStatus,
    guideVersion: Int,
    onPlay: (NowPlaying) -> Unit,
    onRemove: (LiveChannel) -> Unit
) {
    var categoryNames by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var epgIds by remember { mutableStateOf<Map<Int, String>>(emptyMap()) }
    var airing by remember { mutableStateOf<Map<String, EpgProgram>>(emptyMap()) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis() / 1000) }

    LaunchedEffect(favorites) {
        withContext(Dispatchers.IO) {
            categoryNames = db.categoryNames(CatalogKind.LIVE)
            epgIds = db.epgChannelIds(favorites.map { it.streamId })
        }
    }

    // Re-check once a minute so "now playing" moves on when a show ends.
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            now = System.currentTimeMillis() / 1000
        }
    }

    LaunchedEffect(epgIds, guideVersion, now) {
        airing = withContext(Dispatchers.IO) { db.programsAiringAt(epgIds.values.toSet(), now) }
    }

    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    // Removing is one tap on a small icon, so offer Undo (re-adding puts the
    // channel back at the end of the list).
    val removeWithUndo: (LiveChannel) -> Unit = { channel ->
        onRemove(channel)
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            val result = snackbar.showSnackbar(
                "Removed ${TitleFormat.clean(channel.name)}",
                actionLabel = "Undo",
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) onRemove(channel)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
    if (favorites.isEmpty()) {
        Text("No channels saved yet - find some in Search and tap \"+ My TV\".", modifier = Modifier.padding(16.dp))
    } else LazyColumn(modifier = Modifier.fillMaxSize()) {
        if (guideStatus is GuideStatus.Failed) {
            item {
                Text(
                    "Guide unavailable: ${guideStatus.message}",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }
        items(favorites, key = { it.streamId }) { channel ->
            val program = epgIds[channel.streamId]?.let { airing[it] }
            val description = when {
                program != null -> buildString {
                    append("Now: ${program.title} (until ${timeFormat.format(Date(program.stopEpochSeconds * 1000))})")
                    if (program.description.isNotBlank()) append("\n${program.description}")
                }
                channel.streamId !in epgIds && epgIds.isNotEmpty() -> "No guide for this channel"
                guideStatus is GuideStatus.Loading -> "Loading guide..."
                else -> ""
            }
            MediaRow(
                imageUrl = channel.streamIcon,
                title = TitleFormat.clean(channel.name),
                category = categoryNames[channel.categoryId] ?: "",
                description = description,
                onClick = { onPlay(api.liveNowPlaying(channel)) },
                onRemove = { removeWithUndo(channel) },
                imageSize = LogoSize,
                imageIsLogo = true
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
    SnackbarHost(snackbar, modifier = Modifier.align(Alignment.BottomCenter))
    }
}
