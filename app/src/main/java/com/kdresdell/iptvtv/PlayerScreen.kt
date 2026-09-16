package com.kdresdell.iptvtv

import android.app.Activity
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
    api: XtreamApi,
    subtitle: String? = null,
    loadDescription: suspend () -> String? = { null },
    onSelectRail: (RailItem) -> Unit,
    onChannelChange: (direction: Int) -> Unit = {}
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
        val dir = RecordingStorage.findRecordingDirectory(context)
        if (dir == null) {
            AppLog.log("Recording failed: no USB drive found")
            return
        }
        val safeName = displayTitle.ifBlank { title }
            .replace(Regex("[^A-Za-z0-9_-]"), "_")
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
    DisposableEffect(Unit) {
        onDispose { liveRecorder.stop() }
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

    // Live channels show the EPG "now playing" + "next up". Uses
    // get_simple_data_table (XtreamApi.getCurrentAndNextProgram), not
    // get_short_epg - confirmed on this app's own provider that
    // get_short_epg can return nothing covering the real current time for
    // some channels (entries starting an hour+ in the future), which is
    // what caused the progress bar / status lines to show the wrong
    // program. VOD movies and series episodes show their own synopsis via
    // loadDescription instead (there's no EPG for on-demand content).
    LaunchedEffect(contentId, isLive) {
        if (isLive) {
            val (current, next) = try {
                api.getCurrentAndNextProgram(contentId)
            } catch (e: Exception) {
                null to null
            }
            nowProgram = current
            nextProgram = next
            displayTitle = current?.title ?: title
            description = current?.description
        } else {
            displayTitle = title
            description = loadDescription()
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

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    // Back closes the rail (same as Right) before falling through to the
    // screen's own BackHandler (registered by the caller) that leaves the
    // player.
    BackHandler(enabled = menuOpen) {
        menuOpen = false
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
                if (!menuOpen &&
                    (event.key == Key.DirectionCenter || event.key == Key.Enter) &&
                    event.type == KeyEventType.KeyDown &&
                    event.nativeKeyEvent.repeatCount == 0
                ) {
                    longPressFired = false
                    longPressJob?.cancel()
                    longPressJob = coroutineScope.launch {
                        delay(500)
                        longPressFired = true
                        if (isLive) {
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
                // Once the menu is open, focus lives inside it - let the
                // rail's own Cards handle Up/Down/Center (navigate/select)
                // instead of this root intercepting them for channel/pause
                // control. Right mirrors Left's open action, closing the
                // rail back to the stream, same as Back.
                if (menuOpen) {
                    if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                    if (event.key == Key.DirectionRight) {
                        menuOpen = false
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
                        // Opens the same rail used everywhere else in the app.
                        // Playback keeps running underneath, untouched - the
                        // rail itself is the only thing that appears.
                        showInfo = false
                        menuOpen = true
                        true
                    }
                    else -> false
                }
            }
    ) {
        if (isRecording && isLive) {
            // Replaces the video entirely rather than layering a message
            // over the frozen frame the provider leaves once it cuts the
            // live connection - see RecordingLockoutScreen. OK is fully
            // blocked here (no peeking at the frame underneath); the
            // long-press-to-stop logic above is unchanged, only the visual
            // layer differs.
            RecordingLockoutScreen(
                channelTitle = displayTitle.ifBlank { title },
                elapsedSeconds = recordingElapsedSeconds,
                capMinutes = recordingDurationMinutes,
                recordedBytes = recordedBytes
            )
        } else {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
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

            if (showInfo) {
                PlayerInfoOverlay(
                    channelName = title,
                    programTitle = displayTitle,
                    description = description,
                    iconUrl = iconUrl,
                    subtitle = subtitle,
                    isLive = isLive,
                    nowProgram = nowProgram,
                    nextProgram = nextProgram,
                    nowEpochSeconds = nowEpochSeconds
                )

                Box(
                    modifier = Modifier
                        // Dead-center overlapped the program title text
                        // below it - shifted up to clear that zone.
                        .align(BiasAlignment(horizontalBias = 0f, verticalBias = -0.35f))
                        .size(96.dp)
                        .clip(CircleShape)
                        .background(LocalAppColors.current.vividAccent),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isPlaying) PlayerIcons.Pause else PlayerIcons.Play,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(40.dp)
                    )
                }
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
                selected = null,
                onSelect = { item ->
                    menuOpen = false
                    onSelectRail(item)
                },
                requestInitialFocus = true
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
    iconUrl: String,
    subtitle: String?,
    isLive: Boolean,
    nowProgram: EpgProgram?,
    nextProgram: EpgProgram?,
    nowEpochSeconds: Long
) {
    val appColors = LocalAppColors.current
    val textShadow = Shadow(color = Color.Black.copy(alpha = 0.8f), blurRadius = 12f)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                // Black at the bottom, fading to transparent going up. Two
                // hard constraints pinned this shape:
                // - must reach solid black by ~0.48 (measured on-device:
                //   the info block's icon/title top edge), or they lose
                //   their dark backing and become unreadable again.
                // - the user explicitly wants the fade compressed into a
                //   short span ("transparent quicker") rather than starting
                //   near the very top of the screen - video should stay
                //   fully clear until ~30% down, then fade over a short
                //   stretch into the required-solid point.
                // Same eased curve (smoothstep) as before, just narrowed to
                // the 0.30-0.48 span instead of starting at 0.05.
                Brush.verticalGradient(
                    colorStops = arrayOf(
                        0.30f to Color.Transparent,
                        0.35f to Color.Black.copy(alpha = 0.16f),
                        0.39f to Color.Black.copy(alpha = 0.5f),
                        0.44f to Color.Black.copy(alpha = 0.84f),
                        0.48f to Color.Black
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
            Text(
                text = channelName,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleLarge.copy(shadow = textShadow)
            )
            Text(
                text = formatNowDateTime(nowEpochSeconds),
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleMedium.copy(shadow = textShadow)
            )
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
                if (iconUrl.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .size(88.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(appColors.surfaceContainerHigh)
                    ) {
                        AsyncImage(
                            model = iconUrl,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                Column(modifier = Modifier.padding(start = 20.dp)) {
                    Text(
                        text = programTitle,
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.headlineSmall.copy(shadow = textShadow)
                    )
                    if (!subtitle.isNullOrBlank()) {
                        Text(
                            text = subtitle,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium.copy(shadow = textShadow)
                        )
                    }
                    if (!description.isNullOrBlank()) {
                        Text(
                            text = description,
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.bodyLarge.copy(shadow = textShadow),
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
                                text = "${formatTime(nextProgram.startEpochSeconds)}–${formatTime(nextProgram.stopEpochSeconds)}  ${nextProgram.title}",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 20.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Hold",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    // A pill instead of the bare word "OK" - reads as a
                    // pressable button, not just a label.
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 8.dp)
                            .clip(RoundedCornerShape(50))
                            .background(appColors.surfaceContainerHighest)
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "OK",
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                    Text(
                        text = "to ",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "Record",
                        color = ScreenColors.RecordAccent,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
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
