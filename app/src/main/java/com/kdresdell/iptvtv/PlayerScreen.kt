package com.kdresdell.iptvtv

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import kotlinx.coroutines.delay

// Playback URLs contain the provider username/password in plain text -
// never log streamUrl.
//
// Remote keys are handled directly here rather than relying on
// PlayerView's built-in controller: that approach made OK stop working
// after the controller auto-hid once (the embedded AndroidView silently
// lost key focus, and only Back kept working). Handling keys ourselves on
// a focusable Compose root is more reliable and also lets DPAD up/down
// double as "previous/next favorite channel".
@Composable
fun PlayerScreen(
    channel: LiveChannel,
    streamUrl: String,
    api: XtreamApi,
    onChannelChange: (direction: Int) -> Unit
) {
    val context = LocalContext.current
    val exoPlayer = remember(streamUrl) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(streamUrl))
            prepare()
            playWhenReady = true
        }
    }

    DisposableEffect(exoPlayer) {
        onDispose { exoPlayer.release() }
    }

    var showInfo by remember(channel.streamId) { mutableStateOf(true) }
    var isPlaying by remember(channel.streamId) { mutableStateOf(true) }
    var nowPlaying by remember(channel.streamId) { mutableStateOf<NowPlayingInfo?>(null) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(channel.streamId) {
        nowPlaying = try {
            api.getNowPlayingInfo(channel.streamId)
        } catch (e: Exception) {
            null
        }
    }

    // Auto-hide the info overlay after a few seconds, same as any TV
    // channel-change banner - resets whenever it's shown again (OK press
    // or a new channel). Doesn't auto-hide while paused, since the pause
    // button lives in this same overlay.
    LaunchedEffect(showInfo, channel.streamId, isPlaying) {
        if (showInfo && isPlaying) {
            delay(5000)
            showInfo = false
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (event.key) {
                    Key.DirectionCenter, Key.Enter -> {
                        // First OK reveals the overlay; while it's showing,
                        // OK toggles play/pause directly - this is the only
                        // way to reach it, so it must not require navigating
                        // to a separately-focused button.
                        if (!showInfo) {
                            showInfo = true
                        } else {
                            isPlaying = !isPlaying
                            exoPlayer.playWhenReady = isPlaying
                        }
                        true
                    }
                    Key.DirectionUp -> {
                        onChannelChange(-1)
                        true
                    }
                    Key.DirectionDown -> {
                        onChannelChange(1)
                        true
                    }
                    else -> false
                }
            }
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                }
            },
            update = { view -> view.player = exoPlayer }
        )

        if (showInfo) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.7f))
                    .padding(24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (channel.streamIcon.isNotBlank()) {
                    AsyncImage(
                        model = channel.streamIcon,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp)
                    )
                }
                Column(modifier = Modifier.padding(start = 16.dp)) {
                    Text(text = nowPlaying?.title ?: channel.name, color = Color.White)
                    val description = nowPlaying?.description
                    if (!description.isNullOrBlank()) {
                        Text(text = description, color = Color.White)
                    }
                }
            }

            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(24.dp)
            ) {
                Text(
                    text = if (isPlaying) "⏸" else "▶",
                    color = Color.White,
                    fontSize = 48.sp
                )
            }
        }
    }
}
