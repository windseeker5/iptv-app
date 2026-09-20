package com.kdresdell.iptvtv

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.view.WindowManager
import androidx.core.content.ContextCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.layout
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.kdresdell.iptvtv.theme.LocalAppColors
import com.kdresdell.iptvtv.theme.PlayerIcons
import com.kdresdell.iptvtv.theme.ScreenColors
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    title: String,
    iconUrl: String,
    streamUrl: String,
    contentId: Int,
    isLive: Boolean,
    channelDb: LiveChannelDatabase,
    // Live-only: asks the background guide sync to cover this channel if it
    // doesn't yet; true when it refreshed the cache. Never blocks the
    // overlay - what's already cached shows first.
    ensureEpg: suspend () -> Boolean = { false },
    subtitle: String? = null,
    loadDetails: suspend () -> ContentDetails = { ContentDetails() },
    onSelectRail: (RailItem) -> Unit,
    hasSettingsAlert: Boolean = false,
    onChannelChange: (direction: Int) -> Unit = {},
    // Non-null only for live playback - the single ExoPlayer instance shared
    // with the reduced/embedded guide view (FavoritesScreen's
    // GuideMode.Embedded), so Back/OK toggling between full-screen and
    // reduced never reloads the stream. VOD/episode/recording keep their own
    // throwaway player below, unaffected.
    livePlaybackHolder: LivePlaybackHolder? = null,
    // Live-only: Back reduces to the embedded guide instead of opening the
    // in-player rail (see the Key.Back branch below). VOD/episode keep
    // opening the rail via openMenu(), unchanged.
    onReduceToGuide: () -> Unit = {},
    // Live-only. The ONE-SCREEN design: the video view below is the same
    // view in both modes - full-screen, or (reduced) shrunk to the top-left
    // slot of the guide, drawn on top of a guide that's laid out behind it.
    // Only the video's size changes between the two, never its surface,
    // which is what avoids the freeze the old two-screen version hit
    // (see [[android_tv_dev_loop]] on video hot-swap ANRs).
    reduced: Boolean = false,
    guideContent: @Composable () -> Unit = {},
    // Live-only: lets the embedded guide's long-press menu start/stop a
    // recording of this channel (see GuideRecordingBridge).
    guideRecording: GuideRecordingBridge? = null,
    // Back while the menu is open, live only - exits the whole app.
    onExitApp: () -> Unit = {}
) {
    val context = LocalContext.current
    // Recording is opt-in (see RecordingPrefs/SettingsScreen) - off by
    // default since most Google TV boxes have no drive attached. Checking
    // the saved toggle alone isn't enough - confirmed on real hardware
    // (2026-09-17) that pulling the drive without revisiting Settings first
    // left the toggle stale at "on", which still let the record hint and
    // duration menu show here even though nothing would actually record.
    // Re-checking the drive too, once per screen instance, closes that gap.
    // The doorbell is RTSP - LiveRecorder only copies HTTP streams.
    val recordingEnabled = remember(contentId) {
        !DoorbellChannel.isDoorbell(contentId) &&
            RecordingPrefs.isEnabled(context) && RecordingStorage.isDriveAvailable(context)
    }
    // Surfaced on screen below (see playbackError) instead of failing
    // silently - a real gap this fixed: on a stream error the video area
    // just stayed black forever with the paused icon frozen on top, giving
    // no sign of whether the app, the content, or the network was at fault.
    var playbackError by remember(contentId) { mutableStateOf<String?>(null) }
    // key() (not a plain if/else around remember/DisposableEffect) so that
    // if isLive/livePlaybackHolder ever differ between two calls at this
    // same composable slot (e.g. switching straight from a live channel to
    // a VOD title), Compose discards the old branch's remembered state
    // cleanly instead of misaligning its slot table.
    val exoPlayer = key(isLive, livePlaybackHolder != null) {
        if (isLive && livePlaybackHolder != null) {
            DisposableEffect(livePlaybackHolder) {
                livePlaybackHolder.onError = { message -> playbackError = message }
                onDispose {
                    livePlaybackHolder.onError = null
                    livePlaybackHolder.stop()
                }
            }
            LaunchedEffect(contentId, streamUrl) {
                // Scrolling through the guide changes the highlighted
                // channel on every Up/Down - this effect is cancelled and
                // restarted per change, so the short delay makes the stream
                // only switch once scrolling pauses, not on every row passed.
                if (reduced && livePlaybackHolder.currentStreamId != contentId) delay(350)
                livePlaybackHolder.tune(contentId, streamUrl)
            }
            livePlaybackHolder.player
        } else {
            val player = remember(streamUrl) {
                ExoPlayer.Builder(context).build().apply {
                    setMediaItem(MediaItem.fromUri(streamUrl))
                    addListener(object : Player.Listener {
                        override fun onPlayerError(error: PlaybackException) {
                            playbackError = error.message ?: "Playback error"
                        }
                    })
                    prepare()
                    playWhenReady = true
                }
            }
            DisposableEffect(player) {
                onDispose { player.release() }
            }
            player
        }
    }

    // Home button / switching apps stops the Activity but nothing stopped the
    // player, so the stream's audio kept playing over the launcher (confirmed
    // on real hardware, 2026-09-18 - the same bug first seen on Fire TV).
    // Pause when the app leaves the screen, and on return resume - live
    // streams jump back to the live edge rather than continuing stale.
    val resumeAfterStop = remember { booleanArrayOf(false) }
    DisposableEffect(exoPlayer, isLive) {
        val lifecycle = (context as? androidx.activity.ComponentActivity)?.lifecycle
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_STOP -> {
                    resumeAfterStop[0] = exoPlayer.playWhenReady
                    exoPlayer.playWhenReady = false
                }
                androidx.lifecycle.Lifecycle.Event.ON_START -> if (resumeAfterStop[0]) {
                    resumeAfterStop[0] = false
                    if (isLive) exoPlayer.seekToDefaultPosition()
                    exoPlayer.prepare()
                    exoPlayer.playWhenReady = true
                }
                else -> {}
            }
        }
        lifecycle?.addObserver(observer)
        onDispose { lifecycle?.removeObserver(observer) }
    }

    // Google TV's screensaver/sleep timer has no idea video is playing here
    // (there's no user input during playback) - without this it kicks in a
    // few minutes into any live channel or VOD, same as if the TV sat idle.
    DisposableEffect(Unit) {
        val window = (context as? Activity)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    var showInfo by remember(contentId) { mutableStateOf(true) }
    var isPlaying by remember(contentId) { mutableStateOf(true) }
    var displayTitle by remember(contentId) { mutableStateOf(title) }
    var description by remember(contentId) { mutableStateOf<String?>(null) }
    var rating by remember(contentId) { mutableStateOf<String?>(null) }
    // Live only (STYLE_GUIDE.md §6.1 progress bar + status block) - VOD has
    // no EPG, so these stay null there and the overlay falls back to the
    // plain title/description block.
    var nowProgram by remember(contentId) { mutableStateOf<EpgProgram?>(null) }
    var nextProgram by remember(contentId) { mutableStateOf<EpgProgram?>(null) }
    val focusRequester = remember { FocusRequester() }
    val coroutineScope = rememberCoroutineScope()

    // First prototype of live recording (see docs/next-session.md §2):
    // raw copy of the stream's bytes to a file on an attached USB drive,
    // no transcoding. OK isn't a Card here (see the onKeyEvent block
    // below), so unlike the row long-press elsewhere in the app, there's
    // no native onLongClick to lean on - press duration is tracked by
    // hand with longPressJob/longPressFired.
    val liveRecorder = remember { LiveRecorder() }
    var isRecording by remember(contentId) { mutableStateOf(false) }
    var recordingElapsedSeconds by remember(contentId) { mutableStateOf(0) }
    // Surfaced live in the REC indicator below - if the account only
    // supports one stream, live playback freezes once recording starts,
    // but this number climbing is direct on-screen proof the recording
    // itself is still going (or isn't, if it stalls too).
    var recordedBytes by remember(contentId) { mutableStateOf(0L) }
    // null = no cap ("Until I stop it"); otherwise the chosen duration from
    // RecordingDurationMenu, enforced by the ticker below.
    var recordingDurationMinutes by remember(contentId) { mutableStateOf<Int?>(null) }
    var showDurationMenu by remember(contentId) { mutableStateOf(false) }
    // The menu doesn't open the instant the long-press timer fires - it
    // opens once the button is actually released (see the KeyUp handler
    // below). Opening it while the key is still physically held races
    // against the Popup grabbing Android input focus: if the user holds
    // past the popup's own grace window before releasing, that release
    // lands on the popup's first item and gets misread as picking it -
    // confirmed on real hardware (30 min kept getting selected instead of
    // 60). Deferring to a confirmed release means the popup only ever
    // opens into an idle key state, so there's no stray key-up left to
    // misfire on.
    var pendingDurationMenu by remember { mutableStateOf(false) }
    var longPressJob by remember { mutableStateOf<Job?>(null) }
    var longPressFired by remember { mutableStateOf(false) }

    fun stopRecording() {
        liveRecorder.stop()
        isRecording = false
    }

    fun startRecording(durationMinutes: Int?) {
        // Re-checked here, not just at long-press time: the setting or the
        // drive itself could have changed since this screen was entered.
        if (!recordingEnabled) return
        val dir = RecordingStorage.findRecordingDirectory(context)
        if (dir == null) {
            AppLog.log("Recording failed: no USB drive found")
            return
        }
        // Keep accented letters (French titles like "Le Rêve Américain") -
        // only characters that are unsafe in a filename become "_".
        val safeName = displayTitle.ifBlank { title }
            .replace(Regex("[^\\p{L}\\p{M}\\p{N}_-]"), "_")
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val outFile = File(dir, "${safeName}_$timestamp.ts")
        isRecording = true
        recordingElapsedSeconds = 0
        recordedBytes = 0L
        recordingDurationMinutes = durationMinutes
        coroutineScope.launch {
            try {
                liveRecorder.record(streamUrl, outFile) { bytesWritten ->
                    // Throttled: the recorder calls this on every 64KB
                    // chunk, which is too often to recompose on - updating
                    // in ~256KB steps still reads as "live" without the churn.
                    if (bytesWritten - recordedBytes >= 256 * 1024) {
                        recordedBytes = bytesWritten
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Normal stop path (Back, channel change, leaving the
                // screen) cancels this coroutine's scope - Compose's own
                // ForgottenCoroutineScopeException surfaces here as this.
                // Confirmed on real hardware (2026-09-17) it was getting
                // caught by the broad Exception branch below and logged as
                // a scary "Recording error" for completely normal
                // stop-while-recording navigation - rethrown instead so it
                // just completes the coroutine like any other cancellation.
                throw e
            } catch (e: Exception) {
                AppLog.log("Recording error: ${e.javaClass.simpleName}: ${e.message}")
            } finally {
                isRecording = false
            }
        }
    }

    // Recording stops automatically on channel change (Up/Down) and when
    // this screen is left entirely - never silently keeps recording the
    // wrong thing or orphaned in the background. See DisposableEffect(Unit)
    // below for the "leaving the screen" half of that.
    // Keyed on contentId so a channel change made from the guide (which
    // doesn't go through the Up/Down stopRecording() calls) also ends it.
    DisposableEffect(contentId) {
        onDispose { liveRecorder.stop() }
    }

    // Guide bridge: publish state, and act on the guide's requests.
    LaunchedEffect(guideRecording, recordingEnabled, isRecording, recordingElapsedSeconds) {
        guideRecording?.available = recordingEnabled && isLive
        guideRecording?.isRecording = isRecording
        guideRecording?.elapsedSeconds = recordingElapsedSeconds
    }
    DisposableEffect(guideRecording) {
        onDispose {
            guideRecording?.available = false
            guideRecording?.isRecording = false
        }
    }
    val requestedMinutes = guideRecording?.pendingMinutes
    LaunchedEffect(requestedMinutes) {
        if (requestedMinutes != null) {
            guideRecording?.pendingMinutes = null
            if (!isRecording) startRecording(requestedMinutes)
        }
    }
    val stopRequested = guideRecording?.stopRequested == true
    LaunchedEffect(stopRequested) {
        if (stopRequested) {
            guideRecording?.stopRequested = false
            if (isRecording) stopRecording()
        }
    }

    // Confirmed on real hardware (2026-09-17): pulling the USB drive
    // mid-recording doesn't surface as a catchable IOException at all.
    // vold notices the app still holds the recording file open, sends the
    // process a kill signal to force it closed, and the whole app dies
    // ("Sending Interrupt to pid ...", then "Process ... has died") before
    // any try/catch in startRecording ever runs. The only way to survive
    // this is to close the file ourselves *before* vold's kill deadline -
    // ACTION_MEDIA_EJECT/UNMOUNTED/BAD_REMOVAL fire the moment the unmount
    // starts, giving a brief window to react. Registered only while
    // actually recording, since that's the only time an open file handle
    // on removable media is at risk.
    DisposableEffect(isRecording) {
        if (!isRecording) return@DisposableEffect onDispose {}
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receivedContext: Context, intent: Intent) {
                AppLog.log("Recording stopped - USB drive was disconnected")
                stopRecording()
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_MEDIA_EJECT)
            addAction(Intent.ACTION_MEDIA_UNMOUNTED)
            addAction(Intent.ACTION_MEDIA_BAD_REMOVAL)
            addAction(Intent.ACTION_MEDIA_REMOVED)
            addDataScheme("file")
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose { context.unregisterReceiver(receiver) }
    }

    // The lockout screen replaces the *video view*, but ExoPlayer itself
    // keeps playing underneath unless told otherwise - left alone, its
    // audio keeps going (into nothing visible) until the provider cuts the
    // connection ~30s in, which sounded like a crash rather than the
    // intentional "you can't watch while recording" state. Pausing here and
    // resuming on stop makes that silence deliberate instead of a glitch.
    LaunchedEffect(isRecording) {
        exoPlayer.playWhenReady = if (isRecording) false else isPlaying
    }

    // Stopping a recording swaps the lockout screen back for the video view,
    // which can leave keyboard focus off this screen's root - and then Back
    // goes to Android (exits the app) instead of reducing to the guide.
    // Reclaim focus once the swap has settled.
    LaunchedEffect(isRecording) {
        if (!isRecording && !reduced) {
            delay(100)
            focusRequester.requestFocus()
        }
    }

    LaunchedEffect(isRecording) {
        while (isRecording) {
            delay(1000)
            recordingElapsedSeconds++
            val cap = recordingDurationMinutes
            if (cap != null && recordingElapsedSeconds >= cap * 60) {
                AppLog.log("Recording auto-stopped after ${cap}m")
                stopRecording()
            }
        }
    }

    // Left shows the rail alone, over the still-playing video/audio;
    // picking anything in it closes the rail and navigates to that item's
    // own full-screen page via onSelectRail (leaving the player entirely,
    // same as Back would). There is no "browse while watching" content pane
    // anymore - that produced three things on screen at once (video, rail,
    // panel) instead of the simple menu-or-full-page flow this needs.
    var menuOpen by remember(contentId) { mutableStateOf(false) }

    // VOD/series seek (Left/Right, !isLive only). seekTargetMs tracks where
    // the *last* seek key landed, separate from exoPlayer.currentPosition,
    // so repeated Left/Right presses during the feedback window stack
    // against each other instead of each one re-reading a currentPosition
    // that lags behind ExoPlayer's actual seek completion. Reset to null
    // (falls back to exoPlayer.currentPosition) once the feedback overlay
    // hides.
    var seekTargetMs by remember(contentId) { mutableStateOf<Long?>(null) }
    var seekDeltaMs by remember(contentId) { mutableStateOf(0L) }
    var seekDurationMs by remember(contentId) { mutableStateOf(0L) }
    var seekFeedbackAtMs by remember(contentId) { mutableStateOf<Long?>(null) }
    LaunchedEffect(seekFeedbackAtMs) {
        if (seekFeedbackAtMs != null) {
            delay(2_000)
            seekFeedbackAtMs = null
            seekTargetMs = null
        }
    }

    // VOD/series only - seeks relative to the current position, accelerating
    // the step size the longer Left/Right is held (native D-pad key-repeat
    // re-fires KeyDown with an increasing repeatCount, so no separate timer
    // is needed here). Thresholds are a starting guess, not measured against
    // real hardware repeat rates yet.
    fun seekBy(direction: Int, repeatCount: Int) {
        val rawDuration = exoPlayer.duration
        val durationMs = if (rawDuration == C.TIME_UNSET) 0L else rawDuration
        val step = seekStepForRepeatCount(repeatCount)
        val basePosition = seekTargetMs ?: exoPlayer.currentPosition
        val target = basePosition + step * direction
        val clamped = if (durationMs > 0) target.coerceIn(0L, durationMs) else target.coerceAtLeast(0L)
        exoPlayer.seekTo(clamped)
        seekTargetMs = clamped
        seekDeltaMs = step * direction
        seekDurationMs = durationMs
        seekFeedbackAtMs = System.currentTimeMillis()
        // Don't stack the seek flash on top of the OK-triggered info overlay.
        showInfo = false
    }

    // Live channels show the EPG "now playing" + "next up", read straight
    // from the guide cache (channel_epg_window, filled from xmltv.php by
    // EpgSync) - instant, no network wait here. If the channel's category
    // wasn't synced yet, ensureEpg() fetches it in the background and the
    // overlay updates when it lands. VOD movies and series episodes show
    // their own synopsis via loadDescription instead (there's no EPG for
    // on-demand content).
    LaunchedEffect(contentId, isLive) {
        if (isLive) {
            suspend fun showCached() {
                val programs = withContext(Dispatchers.IO) { channelDb.getCachedEpgWindow(contentId) }
                val (current, next) = XtreamApi.currentAndNextFrom(programs)
                nowProgram = current
                nextProgram = next
                displayTitle = current?.title ?: title
                description = current?.description
            }
            showCached()
            try {
                if (ensureEpg()) showCached()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.log("Guide refresh failed: ${e.javaClass.simpleName}: ${e.message}")
            }
        } else {
            displayTitle = title
            val details = loadDetails()
            description = details.description
            rating = details.rating
        }
    }

    // Ticks the progress bar/time-range block forward while the overlay can
    // be on screen - same 30s cadence as the My TV guide's own EPG clock
    // (FavoritesScreen.kt), no need for anything finer for a progress bar.
    var nowEpochSeconds by remember { mutableStateOf(System.currentTimeMillis() / 1000) }
    LaunchedEffect(showInfo) {
        while (showInfo) {
            nowEpochSeconds = System.currentTimeMillis() / 1000
            delay(30_000)
        }
    }

    // Auto-hide the info overlay after a few seconds, same as any TV
    // channel-change banner - resets whenever it's shown again (OK press
    // or a new channel/title). Doesn't auto-hide while paused, since the
    // pause button lives in this same overlay.
    LaunchedEffect(showInfo, contentId, isPlaying) {
        if (showInfo && isPlaying) {
            delay(10_000)
            showInfo = false
        }
    }

    // Full-screen: focus lives on this screen's own root (it handles every
    // key itself). Reduced: focus belongs to the guide's grid, which
    // requests its own initial focus when it appears - so only claim it here
    // when going (or starting) full-screen.
    LaunchedEffect(reduced) {
        if (!reduced) focusRequester.requestFocus()
    }
    // Lets us hand focus back to the guide (restoring the row that had it)
    // after the menu closes over it.
    val guideFocusRequester = remember { FocusRequester() }

    fun openMenu() {
        showInfo = false
        seekFeedbackAtMs = null
        menuOpen = true
    }

    // Back opens the in-player rail for VOD/episode (replacing
    // DirectionLeft's old role - Left/Right are freed up for VOD seek, see
    // the onKeyEvent block below); for live it instead reduces to the
    // embedded guide (onReduceToGuide) - see that param's doc above. Handled
    // directly in onKeyEvent below rather than via BackHandler/the system
    // back dispatcher - confirmed on real hardware (Chromecast with Google
    // TV) that the dispatcher silently drops every other Back invocation on
    // this device (predictive-back quirk), while the raw KeyEvent for Back
    // reliably reaches onKeyEvent on every single press. VOD/episode never
    // falls through to leave the player on its own - an earlier version
    // tried a timing window to let a quick second Back "really" leave, but
    // that made Back unpredictably dump straight out to the (slow-loading)
    // guide during ordinary open/close fumbling. So: Back opens the rail,
    // and Back again while it is open exits the app (same as everywhere
    // else); Right closes it; picking a destination (onSelectRail below)
    // leaves the player - deterministic, no accidental exits.

    // 0 = full-screen, 1 = shrunk into the guide's slot. Only read inside
    // layout/draw blocks below, so animating it re-lays-out and redraws
    // without recomposing the screen on every frame.
    val reduceProgress = androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (reduced) 1f else 0f,
        animationSpec = androidx.compose.animation.core.tween(220),
        label = "reduceProgress"
    )
    // Keep the guide composed until the expand animation has finished, so it
    // is still there behind the growing video instead of vanishing at once.
    val guideVisible by remember(reduced) {
        androidx.compose.runtime.derivedStateOf { reduced || reduceProgress.value > 0.001f }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                // .focusable() swallows KeyDown for Center/Enter internally
                // (for its own press-state handling) before onKeyEvent below
                // ever sees it - only KeyUp makes it through. onPreviewKeyEvent
                // fires on the way down the tree, ahead of .focusable(), so
                // this is the only place the real KeyDown can be caught to
                // start the long-press timer. Always returns false so the
                // event keeps propagating normally afterward.
                if (!menuOpen && !reduced &&
                    (event.key == Key.DirectionCenter || event.key == Key.Enter) &&
                    event.type == KeyEventType.KeyDown &&
                    event.nativeKeyEvent.repeatCount == 0
                ) {
                    longPressFired = false
                    longPressJob?.cancel()
                    longPressJob = coroutineScope.launch {
                        delay(500)
                        longPressFired = true
                        if (isLive && recordingEnabled) {
                            // Stopping is unambiguous - do it immediately.
                            // Starting asks how long to record first (see
                            // RecordingDurationMenu) rather than starting an
                            // unbounded recording right away.
                            if (isRecording) stopRecording() else pendingDurationMenu = true
                        }
                    }
                }
                false
            }
            .onKeyEvent { event ->
                // Back while the rail is open exits the app (below); Right
                // closes it - a quick "return to the stream" gesture
                // (matches the old Left-opens/Right-closes muscle memory) -
                // everything else here is left alone so the rail's own
                // Cards get default TV focus/click handling (Up/Down move
                // focus, Center/Enter selects).
                if (menuOpen) {
                    if (event.type == KeyEventType.KeyDown) {
                        if (event.key == Key.Back) {
                            // Back while the menu is open exits the app (the
                            // nav spec's final step) - live AND VOD/episode.
                            // VOD used to just close the menu again here,
                            // so Back toggled it forever and there was no
                            // way out except picking a menu item first
                            // (reported 2026-09-20 on a series episode).
                            onExitApp()
                            return@onKeyEvent true
                        }
                        if (event.key == Key.DirectionRight) {
                            menuOpen = false
                            if (reduced) coroutineScope.launch { guideFocusRequester.requestFocus() }
                            return@onKeyEvent true
                        }
                    }
                    return@onKeyEvent false
                }

                // Reduced live view: the guide owns every key (Up/Down move
                // between channels, Left/Right scroll time, OK expands) - it
                // consumes those itself. The only thing reaching here that
                // this screen cares about is Back, which opens the menu.
                if (reduced) {
                    if (event.type == KeyEventType.KeyDown && event.key == Key.Back) {
                        openMenu()
                        return@onKeyEvent true
                    }
                    return@onKeyEvent false
                }

                // The long-press timer itself is started in onPreviewKeyEvent
                // above (KeyDown for Center/Enter never reaches here -
                // .focusable() swallows it). By the time KeyUp arrives,
                // longPressFired already tells us which gesture happened.
                if (event.key == Key.DirectionCenter || event.key == Key.Enter) {
                    if (event.type != KeyEventType.KeyUp) return@onKeyEvent false
                    longPressJob?.cancel()
                    longPressJob = null
                    if (pendingDurationMenu) {
                        // Button is confirmed released now - safe to open.
                        pendingDurationMenu = false
                        showDurationMenu = true
                    } else if (!longPressFired) {
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
                    }
                    return@onKeyEvent true
                }

                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (event.key) {
                    Key.Back -> {
                        // Live: Back reduces to the embedded guide instead of
                        // opening the in-player rail (see onReduceToGuide
                        // doc above) - VOD/episode keep opening the rail,
                        // since the reduce-to-EPG concept doesn't apply to
                        // on-demand content.
                        if (isLive && livePlaybackHolder != null) onReduceToGuide() else openMenu()
                        true
                    }
                    Key.DirectionUp -> {
                        // Channel-cycling only makes sense for live playback.
                        // Recording never silently follows a channel switch -
                        // it always stops first.
                        if (isLive) { stopRecording(); onChannelChange(-1); true } else false
                    }
                    Key.DirectionDown -> {
                        if (isLive) { stopRecording(); onChannelChange(1); true } else false
                    }
                    Key.DirectionLeft -> {
                        // VOD/series: seek. Live has nothing to rewind/fast
                        // forward, and only Back should do anything in
                        // full-screen live (per the nav spec) - a true no-op
                        // here now, not a fallback to open the menu.
                        if (!isLive) { seekBy(-1, event.nativeKeyEvent.repeatCount); true } else false
                    }
                    Key.DirectionRight -> {
                        if (!isLive) { seekBy(1, event.nativeKeyEvent.repeatCount); true } else false
                    }
                    else -> false
                }
            }
    ) {
        // Reduced live view: the guide is drawn FIRST (behind), then the
        // video view below is drawn on top of it in the guide's top-left
        // slot. Order matters - a video surface punches a hole through
        // whatever was drawn before it, so the guide's own background fills
        // the screen and the video simply shows through its slot.
        if (guideVisible) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .focusRequester(guideFocusRequester)
                    .focusRestorer()
                    .focusGroup()
            ) {
                guideContent()
            }
        }

        if (isRecording && isLive && !reduced) {
            // Replaces the video entirely rather than layering a message
            // over the frozen frame the provider leaves once it cuts the
            // live connection - see RecordingLockoutScreen. OK is fully
            // blocked here (no peeking at the frame underneath); the
            // long-press-to-stop logic above is unchanged, only the visual
            // layer differs.
            RecordingLockoutScreen(
                channelTitle = TitleFormat.clean(displayTitle.ifBlank { title }),
                elapsedSeconds = recordingElapsedSeconds,
                capMinutes = recordingDurationMinutes,
                recordedBytes = recordedBytes
            )
        } else {
            AndroidView(
                // Same view in both modes - only its size/position changes
                // (full-screen, or the guide's top-left 213x120 slot, which
                // lines up with FavoritesScreen's TopPreviewBlock: 32dp/16dp
                // screen padding).
                modifier = Modifier.layout { measurable, constraints ->
                    val p = reduceProgress.value
                    val fw = constraints.maxWidth
                    val fh = constraints.maxHeight
                    val sw = 213.dp.roundToPx()
                    val sh = 120.dp.roundToPx()
                    val w = (fw + (sw - fw) * p).toInt()
                    val h = (fh + (sh - fh) * p).toInt()
                    val x = (32.dp.roundToPx() * p).toInt()
                    val y = (16.dp.roundToPx() * p).toInt()
                    val placeable = measurable.measure(androidx.compose.ui.unit.Constraints.fixed(w, h))
                    layout(fw, fh) { placeable.place(x, y) }
                },
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = exoPlayer
                        useController = false
                        // Letterbox bars (top/bottom on wide content, left/right
                        // on narrow content) are this view's own fill color, not
                        // the Compose Box behind it - default is a visible gray,
                        // not black, so it has to be set explicitly.
                        setShutterBackgroundColor(android.graphics.Color.BLACK)
                        setBackgroundColor(android.graphics.Color.BLACK)
                    }
                },
                update = { view -> view.player = exoPlayer }
            )

            // Rounded corners for the video slot, without touching the video
            // surface (clipping/hot-swapping video surfaces is risky on this
            // TV and would cost speed): draws the guide's background color
            // over just the four corners, on top of the video, following the
            // video as it resizes. Matches TopPreviewBlock's 10dp corners.
            val cornerColor = ScreenColors.FavoritesBackground
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawBehind {
                        val p = reduceProgress.value
                        if (p > 0f) {
                            val w = size.width + (213.dp.toPx() - size.width) * p
                            val h = size.height + (120.dp.toPx() - size.height) * p
                            val r = 10.dp.toPx() * p
                            val path = androidx.compose.ui.graphics.Path().apply {
                                fillType = androidx.compose.ui.graphics.PathFillType.EvenOdd
                                addRect(androidx.compose.ui.geometry.Rect(0f, 0f, w, h))
                                addRoundRect(
                                    androidx.compose.ui.geometry.RoundRect(
                                        0f, 0f, w, h,
                                        androidx.compose.ui.geometry.CornerRadius(r, r)
                                    )
                                )
                            }
                            translate(32.dp.toPx() * p, 16.dp.toPx() * p) {
                                drawPath(path, cornerColor)
                            }
                        }
                    }
            )

            if (playbackError != null && !reduced) {
                Text(
                    text = if (DoorbellChannel.isDoorbell(contentId)) {
                        "Doorbell not reachable - are you on the home network?"
                    } else {
                        "Could not play this title: $playbackError"
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.align(Alignment.Center).padding(horizontal = 48.dp)
                )
            }

            if (seekFeedbackAtMs != null && !reduced) {
                SeekFeedbackOverlay(
                    deltaMs = seekDeltaMs,
                    positionMs = seekTargetMs ?: exoPlayer.currentPosition,
                    durationMs = seekDurationMs
                )
            }

            if (showInfo && seekFeedbackAtMs == null && !reduced) {
                PlayerInfoOverlay(
                    channelName = TitleFormat.clean(title),
                    programTitle = TitleFormat.clean(displayTitle),
                    description = description,
                    rating = rating,
                    iconUrl = iconUrl,
                    subtitle = subtitle,
                    isLive = isLive,
                    nowProgram = nowProgram,
                    nextProgram = nextProgram,
                    nowEpochSeconds = nowEpochSeconds,
                    recordingEnabled = recordingEnabled
                )

                Box(
                    modifier = Modifier
                        // Explicitly re-centered per direction - a previous
                        // pass shifted this up to dodge the info block's
                        // text, but that's no longer dead-center like the
                        // user wants now that both info layouts dock at the
                        // bottom (see PlayerInfoOverlay) and don't reach mid-screen.
                        .align(Alignment.Center)
                        // 30% smaller per direction (was 96dp) - proportions
                        // between circle and icon stayed the same, both cut.
                        .size(67.dp)
                        .clip(CircleShape)
                        .background(LocalAppColors.current.vividAccent),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isPlaying) PlayerIcons.Pause else PlayerIcons.Play,
                        contentDescription = null,
                        tint = Color.Black,
                        // 30% smaller per direction (was 52dp).
                        modifier = Modifier.size(36.dp)
                    )
                }
            }
        }

        if (isRecording && isLive && reduced) {
            // The guide shows no lockout screen, so this small badge over
            // the (paused) preview slot is the only sign a recording is on.
            // Same slot geometry as the video's reduced layout above.
            Row(
                modifier = Modifier
                    .padding(start = 40.dp, top = 24.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.Black.copy(alpha = 0.7f))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color.Red))
                Text(
                    text = "REC %d:%02d".format(recordingElapsedSeconds / 60, recordingElapsedSeconds % 60),
                    color = Color.White,
                    fontSize = 12.sp
                )
            }
        }

        RecordingDurationMenu(
            expanded = showDurationMenu,
            onDismiss = { showDurationMenu = false },
            onSelect = { minutes ->
                showDurationMenu = false
                startRecording(minutes)
            }
        )

        // Left shows only the rail, over the still-playing video/audio.
        // Picking anything closes it and navigates to that item's own
        // full-screen page via onSelectRail - the same real SideRail (§6.2)
        // used everywhere else in the app, not a separate implementation.
        if (menuOpen) {
            SideRail(
                selected = if (reduced) RailItem.MyChannel else null,
                onSelect = { item ->
                    menuOpen = false
                    onSelectRail(item)
                },
                requestInitialFocus = true,
                hasSettingsAlert = hasSettingsAlert
            )
        }
    }
}

// STYLE_GUIDE.md §6.1 "Player overlay" - approved recipe, validated against
// a user-supplied reference mockup (https://claude.ai/artifact/8zMP1RLmCz257xqzuUKmpz).
// Deviation from that reference: the mockup's status line also showed a
// channel/quality tag and HD/frame-rate/audio badges - the Xtream API this
// app talks to (XtreamApi.kt) never returns that metadata for a stream, so
// rather than fabricate it, this block only renders the time range +
// duration, which is real data.
@Composable
private fun PlayerInfoOverlay(
    channelName: String,
    programTitle: String,
    description: String?,
    rating: String?,
    iconUrl: String,
    subtitle: String?,
    isLive: Boolean,
    nowProgram: EpgProgram?,
    nextProgram: EpgProgram?,
    nowEpochSeconds: Long,
    recordingEnabled: Boolean
) {
    val appColors = LocalAppColors.current
    val textShadow = Shadow(color = Color.Black.copy(alpha = 0.8f), blurRadius = 12f)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                // Black at the bottom, fading to transparent going up. Two
                // hard constraints pinned this shape:
                // - must reach solid black by ~0.55 (measured on-device
                //   after the text-size pass below shrank the info block:
                //   its icon/title top edge sits lower on screen now than
                //   when this was tuned to 0.48, so the fade needed to move
                //   down with it or the top of the block lost its dark
                //   backing - explicit user request 2026-09-17).
                // - the user explicitly wants the fade compressed into a
                //   short span ("transparent quicker") rather than starting
                //   near the very top of the screen - video should stay
                //   fully clear until ~37% down, then fade over a short
                //   stretch into the required-solid point.
                // Same eased curve (smoothstep) as before, just shifted down
                // to the 0.37-0.55 span instead of 0.30-0.48.
                Brush.verticalGradient(
                    colorStops = arrayOf(
                        0.37f to Color.Transparent,
                        0.42f to Color.Black.copy(alpha = 0.16f),
                        0.46f to Color.Black.copy(alpha = 0.5f),
                        0.51f to Color.Black.copy(alpha = 0.84f),
                        0.55f to Color.Black
                    )
                )
            )
    ) {
        // Top row - matches the approved mockup exactly (see STYLE_GUIDE.md
        // §6.1, https://claude.ai/artifact/8zMP1RLmCz257xqzuUKmpz): no
        // background box here at all, only a text-shadow. A background box
        // was added here in an earlier pass to fix legibility, but that's
        // not what was approved and it read as a second, ugly black box
        // stacked above the real scrim - removed.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 48.dp, vertical = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isLive && iconUrl.isNotBlank()) {
                    // Small channel logo, about the height of the name text.
                    AsyncImage(
                        model = iconUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .height(32.dp)
                            .clip(RoundedCornerShape(4.dp))
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                }
                Text(
                    text = channelName,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleLarge.copy(shadow = textShadow)
                )
            }
            Text(
                text = formatNowDateTime(nowEpochSeconds),
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleMedium.copy(shadow = textShadow)
            )
        }

        if (!isLive) {
            // VOD/episode/recording: left-aligned poster + rating + full
            // synopsis, bottom-docked - same placement idea as the live bar
            // below, not a centered hero block (tried that, rejected: too
            // large, centered text reads dated on a 10-foot UI, and the
            // title was a pointless repeat of the top-left title already on
            // screen). Fixes from the original version of this block: the
            // poster keeps its real 2:3 shape (was a squashed 88dp square
            // with a visible grey letterbox behind it), and the synopsis is
            // no longer hard-capped at 2 lines.
            Box(modifier = Modifier.weight(1f))
            Row(
                verticalAlignment = Alignment.Top,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 48.dp)
                    .padding(bottom = 32.dp)
            ) {
                if (iconUrl.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            // +12.5% per direction (was 120dp).
                            .width(135.dp)
                            .aspectRatio(2f / 3f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(appColors.surfaceContainerHigh)
                    ) {
                        AsyncImage(
                            model = iconUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    Spacer(modifier = Modifier.width(20.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    if (!subtitle.isNullOrBlank()) {
                        Text(
                            text = subtitle,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium.copy(shadow = textShadow)
                        )
                    }
                    if (!rating.isNullOrBlank()) {
                        Text(
                            text = "★ $rating",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelMedium.copy(shadow = textShadow)
                        )
                    }
                    if (!description.isNullOrBlank()) {
                        Text(
                            text = description,
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.bodyMedium.copy(shadow = textShadow),
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
            return@Column
        }

        // Pushes the block below to the bottom of the frame.
        Box(modifier = Modifier.weight(1f))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 48.dp)
                .padding(top = 64.dp, bottom = 24.dp)
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Column {
                    Text(
                        text = programTitle,
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.titleLarge.copy(shadow = textShadow)
                    )
                    if (!subtitle.isNullOrBlank()) {
                        Text(
                            text = subtitle,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium.copy(shadow = textShadow)
                        )
                    }
                    if (!rating.isNullOrBlank()) {
                        // Provider-supplied rating (§6.6 My Librairie shows
                        // the same value) - never fabricated, only rendered
                        // when the provider actually returns one.
                        Text(
                            text = "★ $rating",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelMedium.copy(shadow = textShadow)
                        )
                    }
                    if (!description.isNullOrBlank()) {
                        Text(
                            text = description,
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.bodyMedium.copy(shadow = textShadow),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // isLive always renders the same shape (program row above, this
            // block below) - only the progress bar/status lines fade in once
            // the EPG call resolves, instead of swapping to a whole different
            // layout for that moment. Two visually distinct overlays flashing
            // in sequence on every channel change read as a bug, not a
            // loading state - see git history for the two-box progress bar
            // this replaced too (it doubled as an accidental third color).
            if (isLive) {
                // Some providers' EPG data has real gaps (confirmed against
                // this app's own provider: some favorite channels' "now
                // playing" entry hasn't actually started yet, by up to 99
                // minutes) - in that case the fraction below is correctly 0,
                // not broken. The bar itself always renders when there's a
                // program to measure against; a channel with bad EPG data
                // just always shows it empty, same as it would for any
                // player at the very start of a real program.
                if (nowProgram != null) {
                    val fraction = progressFraction(nowProgram, nowEpochSeconds)
                    Box(
                        modifier = Modifier
                            .padding(top = 28.dp, bottom = 20.dp)
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            // Outline (#435643, a dark muted green) blended
                            // into the scrim and read as "not working" - a
                            // real neutral grey makes the unfilled track
                            // visible against both the dark scrim and the
                            // bright green fill.
                            .background(MaterialTheme.colorScheme.onSurfaceVariant)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(fraction)
                                .shadow(
                                    elevation = 4.dp,
                                    shape = RoundedCornerShape(4.dp),
                                    ambientColor = appColors.vividAccent,
                                    spotColor = appColors.vividAccent
                                )
                                .clip(RoundedCornerShape(4.dp))
                                .background(appColors.vividAccent)
                        )
                    }

                    Column {
                        val durationMinutes = ((nowProgram.stopEpochSeconds - nowProgram.startEpochSeconds) / 60).coerceAtLeast(0)
                        Text(
                            text = "${formatTime(nowProgram.startEpochSeconds)}–${formatTime(nowProgram.stopEpochSeconds)}  ·  ${durationMinutes} min",
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        if (nextProgram != null) {
                            Text(
                                text = "${formatTime(nextProgram.startEpochSeconds)}–${formatTime(nextProgram.stopEpochSeconds)}  ${TitleFormat.clean(nextProgram.title)}",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }

                if (recordingEnabled) Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 20.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Hold",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                    // A real circle (matching the remote's physical OK
                    // button) instead of the earlier stadium-shaped pill -
                    // explicit user request. White fill + black label reads
                    // as a distinct physical button against the rest of the
                    // light-grey hint text, the way the real remote's OK key
                    // looks against its own bezel. Shrunk from 28dp - explicit
                    // user request that this whole hint read as a small,
                    // secondary hint, not a same-size sibling of the program
                    // text above it.
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 6.dp)
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(Color.White),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "OK",
                            color = Color.Black,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp)
                        )
                    }
                    Text(
                        // "Record" no longer picked out in red - explicit
                        // user request that this whole hint read as one
                        // uniform light-grey line, not call out the word.
                        text = "to Record",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

// New overlay, not covered by STYLE_GUIDE.md §6.1 verbatim (that section
// only specs the OK-triggered info overlay) - reuses the same approved
// primitives: bottom scrim gradient, text-shadow style, vivid-accent
// progress bar with glow, Title/Body type scale, 48dp/24dp safe margin.
// Shown only for VOD/series (isLive == false) seeking; live channels never
// seek, so this never renders there.
@Composable
private fun SeekFeedbackOverlay(
    deltaMs: Long,
    positionMs: Long,
    durationMs: Long
) {
    val appColors = LocalAppColors.current
    val textShadow = Shadow(color = Color.Black.copy(alpha = 0.8f), blurRadius = 12f)
    val deltaSeconds = deltaMs / 1000
    val sign = if (deltaSeconds >= 0) "+" else ""

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colorStops = arrayOf(
                        0.55f to Color.Transparent,
                        0.70f to Color.Black.copy(alpha = 0.5f),
                        0.84f to Color.Black.copy(alpha = 0.84f),
                        1.0f to Color.Black
                    )
                )
            )
            .padding(horizontal = 48.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.Bottom
    ) {
        Text(
            text = "$sign${deltaSeconds}s",
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.headlineSmall.copy(shadow = textShadow),
            modifier = Modifier.padding(bottom = 12.dp)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.onSurfaceVariant)
        ) {
            if (durationMs > 0) {
                val fraction = (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(fraction)
                        .shadow(
                            elevation = 4.dp,
                            shape = RoundedCornerShape(4.dp),
                            ambientColor = appColors.vividAccent,
                            spotColor = appColors.vividAccent
                        )
                        .clip(RoundedCornerShape(4.dp))
                        .background(appColors.vividAccent)
                )
            }
        }
        if (durationMs > 0) {
            Text(
                text = "${formatElapsed(positionMs / 1000)} / ${formatElapsed(durationMs / 1000)}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

private fun progressFraction(program: EpgProgram, nowEpochSeconds: Long): Float {
    val span = program.stopEpochSeconds - program.startEpochSeconds
    if (span <= 0) return 0f
    return ((nowEpochSeconds - program.startEpochSeconds).toFloat() / span).coerceIn(0f, 1f)
}

private fun formatTime(epochSeconds: Long): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(epochSeconds * 1000))

private fun formatNowDateTime(epochSeconds: Long): String =
    SimpleDateFormat("EEE, MMM d · HH:mm", Locale.getDefault()).format(Date(epochSeconds * 1000))

private fun formatElapsed(totalSeconds: Long): String {
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

// Empirically-tunable: Android doesn't guarantee a fixed key-repeat rate, so
// these repeatCount thresholds may need adjusting on real hardware.
private const val SeekStepBaseMs = 10_000L
private const val SeekStepMediumMs = 30_000L
private const val SeekStepLargeMs = 60_000L
private const val SeekAccelMediumRepeatCount = 8
private const val SeekAccelLargeRepeatCount = 24

private fun seekStepForRepeatCount(repeatCount: Int): Long = when {
    repeatCount >= SeekAccelLargeRepeatCount -> SeekStepLargeMs
    repeatCount >= SeekAccelMediumRepeatCount -> SeekStepMediumMs
    else -> SeekStepBaseMs
}
