package com.kdresdell.iptvtv.phone

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.util.Log
import android.view.ContextThemeWrapper
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpSize
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.util.EventLogger
import androidx.media3.ui.PlayerView
import androidx.mediarouter.app.MediaRouteButton
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.MediaStatus
import com.google.android.gms.cast.framework.CastButtonFactory
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManagerListener
import com.google.android.gms.cast.framework.media.RemoteMediaClient

// What the player shows. castUrl/castMimeType are what gets sent to a
// Chromecast, which can differ from the phone's own URL (see
// XtreamApi.liveNowPlaying). kind/itemId/posterUrl/categoryId feed the
// info panel under the video in portrait (itemId is the channel, movie or
// series id - for an episode, its series).
data class NowPlaying(
    val title: String,
    val streamUrl: String,
    val castUrl: String = streamUrl,
    val castMimeType: String = "video/mp4",
    val isLive: Boolean = false,
    val kind: CatalogKind? = null,
    val itemId: Int = 0,
    val posterUrl: String = "",
    val categoryId: String = ""
)

// The provider sends each live channel at one fixed quality (a single .ts
// stream), so there's nothing for the player to adapt between - the only
// lever against a phone's bursty Wi-Fi/cell link is buffering. ExoPlayer's
// defaults start playback after 2.5s buffered and resume after 5s; on fast
// sports that means frequent short stalls. Waiting for more before (re)starting
// costs a few seconds of delay behind live but rides out network dips.
@OptIn(UnstableApi::class)
private fun buildPlayer(context: Context): ExoPlayer {
    val loadControl = DefaultLoadControl.Builder()
        .setBufferDurationsMs(
            /* minBufferMs = */ 30_000,
            /* maxBufferMs = */ 60_000,
            /* bufferForPlaybackMs = */ 5_000,
            /* bufferForPlaybackAfterRebufferMs = */ 10_000
        )
        .build()
    // If a decoder fails to start, fall back to the next one instead of
    // erroring out.
    val renderers = DefaultRenderersFactory(context)
        .setEnableDecoderFallback(true)
        .setMediaCodecSelector(preferSoftwareAvcSelector)
    return ExoPlayer.Builder(context, renderers)
        .setLoadControl(loadControl)
        .build()
        .apply {
            // Logs formats, decoder choice, dropped frames and errors
            // under the "EventLogger" logcat tag - the only way to see why
            // a given channel stutters on a given phone.
            addAnalyticsListener(EventLogger())
        }
}

// Pixel's hardware H.264 decoder (c2.exynos.h264.decoder on a Pixel 9)
// steadily drops frames on some broadcast channels - TVA Sports HD, a mere
// 960x540 stream, dropped ~50 frames every 3s (seen via EventLogger) while
// the SD version of the same channel played fine. Android's software AVC
// decoder handles these streams cleanly and a modern phone CPU has plenty
// of headroom for broadcast-resolution H.264, so it goes first for AVC.
// Other formats (HEVC etc.) keep the default hardware-first order.
@OptIn(UnstableApi::class)
private val preferSoftwareAvcSelector = MediaCodecSelector { mimeType, requiresSecure, requiresTunneling ->
    val decoders = MediaCodecSelector.DEFAULT.getDecoderInfos(mimeType, requiresSecure, requiresTunneling)
    if (mimeType == MimeTypes.VIDEO_H264) {
        decoders.sortedByDescending { it.softwareOnly }
    } else {
        decoders
    }
}

// v0: a bare ExoPlayer per screen visit, released on dispose. No shared
// playback holder like the TV app's LivePlaybackHolder - not worth the
// complexity until this proves the basic play loop works on a phone.
// Plays live channels, VOD movies, and series episodes alike - they're all
// just a stream URL by this point. Always on black, like the TV app.
//
// Casting: the Cast button (only visible when a Chromecast / Google TV is
// on the network) hands the stream to the TV and pauses the phone. Opening
// another channel while connected sends that one straight to the TV, the
// same way YouTube behaves. Leaving the player keeps the TV playing; "Stop
// casting" (here or in the Cast dialog) ends it and resumes on the phone.
@Composable
fun PlayerScreen(item: NowPlaying, db: CatalogDatabase, api: XtreamApi, onBack: () -> Unit) {
    val context = LocalContext.current
    val player = remember { buildPlayer(context) }
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    // Null when Google Play services / Cast isn't available on this phone.
    val castContext = remember { runCatching { CastContext.getSharedInstance(context) }.getOrNull() }
    var castDeviceName by remember { mutableStateOf<String?>(null) }
    var castError by remember { mutableStateOf<String?>(null) }

    DisposableEffect(item) {
        player.setLiveStream(item.streamUrl)
        player.prepare()
        player.playWhenReady = castContext?.sessionManager?.currentCastSession?.isConnected != true
        onDispose {
            player.release()
        }
    }

    DisposableEffect(castContext, item) {
        val sessionManager = castContext?.sessionManager
        // Where the TV has got to (kept up to date while casting), so a
        // dropped stream can restart at the same spot and "Stop casting"
        // continues on the phone from there.
        var tvPositionMs: Long? = null
        var castClient: RemoteMediaClient? = null
        var retriesLeft = MAX_CAST_RETRIES
        var errorHandled = false
        val progressListener = RemoteMediaClient.ProgressListener { progressMs, _ ->
            if (progressMs > 0) tvPositionMs = progressMs
        }

        fun loadOnTv(client: RemoteMediaClient, startAtMs: Long) {
            val metadata = MediaMetadata(MediaMetadata.MEDIA_TYPE_MOVIE).apply {
                putString(MediaMetadata.KEY_TITLE, TitleFormat.clean(item.title))
            }
            val mediaInfo = MediaInfo.Builder(item.castUrl)
                .setContentType(item.castMimeType)
                .setStreamType(if (item.isLive) MediaInfo.STREAM_TYPE_LIVE else MediaInfo.STREAM_TYPE_BUFFERED)
                .setMetadata(metadata)
                .build()
            val request = MediaLoadRequestData.Builder()
                .setMediaInfo(mediaInfo)
                .setAutoplay(true)
                .setCurrentTime(startAtMs)
                .build()
            client.load(request).setResultCallback { result ->
                if (!result.status.isSuccess) {
                    Log.w(CAST_TAG, "Cast load failed: ${result.status}")
                    castError = "The TV couldn't play this stream"
                }
            }
        }

        // The Default Media Receiver gives up for good when its connection
        // to the provider drops mid-stream (seen on the Chromecast as
        // "FFmpegDemuxer: data source error" ~1.5 min into a movie) - it
        // never retries on its own. So the phone watches for that error and
        // reloads at the last known position, a few times per drop.
        val statusCallback = object : RemoteMediaClient.Callback() {
            override fun onStatusUpdated() {
                val client = castClient ?: return
                val status = client.mediaStatus ?: return
                // The same failed status can be reported several times -
                // errorHandled makes it one reaction per failure, re-armed
                // once the TV is loading/playing again.
                if (status.playerState != MediaStatus.PLAYER_STATE_IDLE) errorHandled = false
                when {
                    status.playerState == MediaStatus.PLAYER_STATE_PLAYING -> {
                        castError = null
                        retriesLeft = MAX_CAST_RETRIES
                    }
                    status.playerState == MediaStatus.PLAYER_STATE_IDLE &&
                        status.idleReason == MediaStatus.IDLE_REASON_ERROR &&
                        !errorHandled -> {
                        errorHandled = true
                        Log.w(CAST_TAG, "TV stream dropped at ${tvPositionMs}ms, retries left $retriesLeft")
                        if (retriesLeft > 0) {
                            retriesLeft--
                            castError = "The TV lost the stream - reconnecting..."
                            loadOnTv(client, if (item.isLive) 0L else tvPositionMs ?: 0L)
                        } else {
                            castError = "The TV keeps losing the stream. Tap Stop casting to watch on the phone."
                        }
                    }
                }
            }
        }

        fun detachFromClient() {
            castClient?.unregisterCallback(statusCallback)
            castClient?.removeProgressListener(progressListener)
            castClient = null
        }

        fun castTo(session: CastSession) {
            castDeviceName = session.castDevice?.friendlyName ?: "TV"
            castError = null
            // Movies/episodes pick up on the TV exactly where the phone was
            // (e.g. 50 minutes into a film) instead of restarting; live
            // just joins "now".
            val startAtMs = if (item.isLive) 0L else player.currentPosition.coerceAtLeast(0L)
            tvPositionMs = startAtMs
            // stop(), not pause(): a paused player keeps its HTTP connection
            // to the provider open, and with the TV connected too that can
            // exceed the account's connection limit - the provider then cuts
            // one off, and it was the TV's. stop() closes it; the item and
            // position are kept for when casting ends.
            player.stop()
            retriesLeft = MAX_CAST_RETRIES
            val client = session.remoteMediaClient ?: return
            if (castClient !== client) {
                detachFromClient()
                client.registerCallback(statusCallback)
                client.addProgressListener(progressListener, 1000)
                castClient = client
            }
            loadOnTv(client, startAtMs)
        }
        fun backToPhone() {
            detachFromClient()
            castDeviceName = null
            castError = null
            // Live: jump to "now" rather than resume from where the phone
            // stopped minutes ago.
            if (item.isLive) {
                player.seekToDefaultPosition()
            } else {
                tvPositionMs?.takeIf { it > 0 }?.let { player.seekTo(it) }
            }
            tvPositionMs = null
            player.prepare()
            player.play()
        }
        val listener = object : SessionManagerListener<CastSession> {
            override fun onSessionStarted(session: CastSession, sessionId: String) = castTo(session)
            override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) = castTo(session)
            override fun onSessionEnded(session: CastSession, error: Int) = backToPhone()
            override fun onSessionStarting(session: CastSession) {}
            override fun onSessionStartFailed(session: CastSession, error: Int) {
                castError = "Couldn't connect to the TV"
            }
            override fun onSessionEnding(session: CastSession) {
                session.remoteMediaClient?.approximateStreamPosition?.takeIf { it > 0 }?.let { tvPositionMs = it }
            }
            override fun onSessionResuming(session: CastSession, sessionId: String) {}
            override fun onSessionResumeFailed(session: CastSession, error: Int) {}
            override fun onSessionSuspended(session: CastSession, reason: Int) {}
        }
        sessionManager?.addSessionManagerListener(listener, CastSession::class.java)
        sessionManager?.currentCastSession?.takeIf { it.isConnected }?.let { castTo(it) }
        onDispose {
            sessionManager?.removeSessionManagerListener(listener, CastSession::class.java)
            detachFromClient()
        }
    }

    // Rotating to landscape goes truly full-screen (hides status/nav bars);
    // rotating back to portrait restores them. The activity keeps its
    // configChanges (android-app's AndroidManifest.xml) so this rotation
    // never recreates the activity/loses the player.
    DisposableEffect(isLandscape) {
        val activity = context as? Activity
        val window = activity?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        if (isLandscape) {
            controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller?.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
        onDispose {
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    val onStopCasting = { castContext?.sessionManager?.endCurrentSession(true); Unit }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        if (isLandscape) {
            Box(modifier = Modifier.fillMaxSize()) {
                VideoArea(player, castDeviceName, castError, onStopCasting, Modifier.fillMaxSize())
                if (castContext != null) {
                    CastButton(modifier = Modifier.align(Alignment.TopEnd).padding(12.dp))
                }
            }
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                    Text(
                        text = TitleFormat.clean(item.title),
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (castContext != null) CastButton()
                }
                VideoArea(
                    player,
                    castDeviceName,
                    castError,
                    onStopCasting,
                    Modifier.fillMaxWidth().aspectRatio(16f / 9f)
                )
                PlayerInfoPanel(item = item, db = db, api = api, modifier = Modifier.weight(1f))
            }
        }
    }
}

// The phone's video, or - while casting - a "Playing on <TV>" panel in its
// place.
@Composable
private fun VideoArea(
    player: ExoPlayer,
    castDeviceName: String?,
    castError: String?,
    onStopCasting: () -> Unit,
    modifier: Modifier
) {
    if (castDeviceName == null) {
        Box(modifier = modifier) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { PlayerView(it).apply { this.player = player; keepScreenOn = true } }
            )
            if (castError != null) {
                Text(
                    castError,
                    color = Color.White,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp)
                )
            }
        }
        return
    }
    Column(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceContainer),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Playing on $castDeviceName", style = MaterialTheme.typography.titleMedium, color = Color.White)
        if (castError != null) {
            Text(castError, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        }
        Button(onClick = onStopCasting) { Text("Stop casting") }
    }
}

// The standard Cast icon. MediaRouteButton hides itself when no Cast device
// is on the network. Its device picker is an AppCompat dialog, hence the
// AppCompat theme wrapper (dark variant = white icon on the black player).
@Composable
private fun CastButton(modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            MediaRouteButton(ContextThemeWrapper(ctx, androidx.appcompat.R.style.Theme_AppCompat)).apply {
                CastButtonFactory.setUpMediaRouteButton(ctx, this)
            }
        }
    )
}

private const val CAST_TAG = "KdtvCast"

// Automatic reconnects per drop before asking the user to take over.
private const val MAX_CAST_RETRIES = 3

// What's playing, under the video in portrait (landscape is video only):
// poster or channel logo, title, category, and the description - for a
// live channel, the program on now from the cached guide (see
// MainActivity's guide fetch; channels outside My TV usually have none),
// for a movie/series its synopsis (cached, fetched once if missing).
private data class PlayerInfo(
    val category: String = "",
    val nowOn: String = "",
    val description: String = ""
)

@Composable
private fun PlayerInfoPanel(item: NowPlaying, db: CatalogDatabase, api: XtreamApi, modifier: Modifier) {
    var info by remember(item) { mutableStateOf(PlayerInfo()) }
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    LaunchedEffect(item) {
        val kind = item.kind ?: return@LaunchedEffect
        while (true) {
            info = withContext(Dispatchers.IO) {
                val category = db.categoryNames(kind)[item.categoryId] ?: ""
                if (kind == CatalogKind.LIVE) {
                    val epgId = db.epgChannelIds(listOf(item.itemId))[item.itemId]
                    val program = epgId?.let {
                        db.programsAiringAt(listOf(it), System.currentTimeMillis() / 1000)[it]
                    }
                    PlayerInfo(
                        category = category,
                        nowOn = program?.let {
                            "Now: ${it.title} (until ${timeFormat.format(Date(it.stopEpochSeconds * 1000))})"
                        } ?: "",
                        description = program?.description ?: ""
                    )
                } else {
                    PlayerInfo(category = category, description = db.cachedDescription(kind, item.itemId) ?: "")
                }
            }
            if (kind != CatalogKind.LIVE) {
                if (info.description.isEmpty() && db.cachedDescription(kind, item.itemId) == null) {
                    val fetched = if (kind == CatalogKind.VOD) {
                        api.getVodDescription(item.itemId)
                    } else {
                        api.getSeriesDescription(item.itemId)
                    }
                    if (fetched != null) {
                        withContext(Dispatchers.IO) { db.saveDescription(kind, item.itemId, fetched) }
                        info = info.copy(description = fetched)
                    }
                }
                break
            }
            // Live: move on to the next program as the guide says.
            delay(60_000)
        }
    }

    val isLive = item.kind == CatalogKind.LIVE
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Poster(
                imageUrl = item.posterUrl,
                fallbackText = item.title,
                size = if (isLive) DpSize(96.dp, 96.dp) else DpSize(96.dp, 144.dp),
                isLogo = isLive
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = TitleFormat.clean(item.title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                if (info.category.isNotBlank()) {
                    Text(
                        text = info.category,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                if (info.nowOn.isNotBlank()) {
                    Text(
                        text = info.nowOn,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }
            }
        }
        if (info.description.isNotBlank()) {
            Text(
                text = info.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = "Turn your phone sideways for full screen",
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.5f)
        )
    }
}
