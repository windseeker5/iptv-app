package com.kdresdell.iptvtv

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Glow
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.kdresdell.iptvtv.theme.LocalAppColors
import com.kdresdell.iptvtv.theme.ScreenColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// STYLE_GUIDE.md §6.3 - the app's home screen. Full EPG timeline grid, near-1:1
// to the user's reference TV-box guide photo and to the approved mockup
// (https://claude.ai/artifact/7qbbtXWvz3rSC5AmKupzJL), only color/depth changed.
// Sizing is driven by fractions of the available width (BoxWithConstraints),
// matching the mockup's cqw-relative proportions, not fixed dp - a first pass
// using fixed dp + the app's full 10-foot type scale (Headline/Title Small,
// Body Medium) came out far too large and too sparse next to the mockup; a
// dense EPG grid needs the app's smallest official roles (Label Small/Medium),
// not the roles built for headline/list content.
//
// Top preview: a real live decode of the tuned channel (the last one
// watched), with real audio - this is "the live stream you're actually
// listening to", carried over from the player (whose own playback stops
// when you navigate here). Falls back to a neutral placeholder if nothing
// has been watched yet.
private const val ChannelColumnFraction = 0.26f
private val RowHeight = 44.dp
private const val PxPerMinute = 3.2f
private const val HalfHourSeconds = 30 * 60L
// D-pad right/left scrolls the timeline by this many minutes at a time, to
// browse future/past programs without leaving the guide row focus.
private const val ScrollStepMinutes = 30f
// Recording length when a channel has no guide info for right now (see
// recordActionFor) - long enough for a hockey game with overtime.
private const val NO_GUIDE_RECORD_MINUTES = 180

// BrowseOnly is today's "My TV" screen, reached from the rail, unchanged.
// Embedded is the guide shown while watching live (Back from full-screen):
// PlayerScreen draws its own live video on top of this guide's top-left
// slot, so this composable draws no video itself in that mode. In both modes
// the arrows only move a browse cursor (info panel + green highlight follow
// it, the preview does not change); the first OK on another channel tunes
// the preview to it (onChannelTuned in Embedded), OK on the previewed channel
// goes full screen (onExpand / onPlay). In Embedded, Left never opens the side
// menu - Back does that.
enum class GuideMode { BrowseOnly, Embedded }

@Composable
fun FavoritesScreen(
    favorites: List<LiveChannel>,
    epgWindows: Map<Int, List<EpgProgram>>,
    tunedStreamId: Int?,
    streamUrlFor: (Int) -> String,
    onPlay: (LiveChannel) -> Unit,
    onRemove: (LiveChannel) -> Unit,
    mode: GuideMode = GuideMode.BrowseOnly,
    // Recording from the long-press menu: `recording` is the player's live
    // state (see GuideRecordingBridge, null when no player is mounted);
    // onRecordShow tunes the channel and records it for the given minutes.
    recording: GuideRecordingBridge? = null,
    onRecordShow: (LiveChannel, Int) -> Unit = { _, _ -> },
    // Embedded-only params (see GuideMode doc above) - null/no-op defaults
    // keep every existing BrowseOnly call site unchanged.
    tunedChannel: LiveChannel? = null,
    onChannelTuned: (LiveChannel) -> Unit = {},
    onExpand: () -> Unit = {},
    // BrowseOnly only - see WithRail's onDirectionLeft doc. Left/null default
    // for every other call site (Embedded has no WithRail ancestor to hook).
    onProvideDirectionLeftHandler: ((() -> Boolean) -> Unit)? = null
) {
    val nowEpoch = rememberNowEpochSeconds()

    Box(modifier = Modifier.fillMaxSize().background(ScreenColors.FavoritesBackground)) {
        if (favorites.isEmpty()) {
            Text(
                text = "No favorites yet - use Search to add some",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.Center)
            )
            return@Box
        }

        val lastTunedChannel = tunedStreamId?.let { id -> favorites.find { it.streamId == id } }
        // The top preview follows whichever row currently has D-pad focus (not
        // necessarily the tuned channel) - lets you browse the guide
        // and hear/see each channel's own live audio + description without
        // leaving this screen. Starts on the tuned channel since that's where
        // initial focus lands in the grid below.
        // Moving around the guide no longer retunes anything: previewChannel
        // only changes on OK (see onOk below).
        var previewChannel by remember { mutableStateOf(lastTunedChannel) }
        val displayedChannel = if (mode == GuideMode.Embedded) tunedChannel else (previewChannel ?: lastTunedChannel)

        // The browse cursor: which channel row + which moment in time the
        // D-pad is on. The info panel and the green highlight follow it; the
        // preview (displayedChannel) does not.
        val windowStartEpoch = windowStartFor(nowEpoch)
        var cursorEpoch by remember { mutableStateOf(nowEpoch) }
        // Falls back to the first favorite when the tuned/last-watched channel
        // isn't one (e.g. last watched via Search, not from My TV) - without
        // this, cursorStreamId stayed null and no row ever matched it, so the
        // green cursor silently never appeared anywhere in the guide.
        var cursorStreamId by remember {
            mutableStateOf(
                (if (mode == GuideMode.Embedded) tunedChannel?.streamId else lastTunedChannel?.streamId)
                    ?: favorites.firstOrNull()?.streamId
            )
        }
        val effectiveCursorEpoch = max(cursorEpoch, windowStartEpoch)
        val cursorChannel = favorites.find { it.streamId == cursorStreamId } ?: displayedChannel
        val cursorPrograms = cursorChannel?.let { epgWindows[it.streamId].orEmpty() }.orEmpty()
        val cursorProgram = programAt(cursorPrograms, effectiveCursorEpoch)
        val cursorSpan = cursorChannel?.let { spanAtCursor(cursorPrograms, effectiveCursorEpoch, windowStartEpoch) }

        // "Record this show" is offered for whichever program airing right now
        // the cursor is on, on any channel: choosing it tunes that channel
        // (onRecordShow) and the player starts recording until the program
        // ends. Future shows are not scheduled yet. While a recording runs
        // only "Stop recording" is offered - tuning elsewhere would end it.
        val context = LocalContext.current
        val recordingAllowed = remember {
            RecordingPrefs.isEnabled(context) && RecordingStorage.isDriveAvailable(context)
        }
        val recordActionFor: (LiveChannel) -> MenuAction? = { channel ->
            when {
                !recordingAllowed -> null
                recording?.isRecording == true ->
                    if (channel.streamId == tunedChannel?.streamId) {
                        MenuAction("Stop recording", { recording.stopRequested = true })
                    } else {
                        null
                    }
                channel.streamId == cursorChannel?.streamId &&
                    cursorProgram != null &&
                    cursorProgram.startEpochSeconds <= nowEpoch &&
                    nowEpoch < cursorProgram.stopEpochSeconds -> {
                    val minutes = ((cursorProgram.stopEpochSeconds - nowEpoch + 59) / 60).toInt().coerceAtLeast(1)
                    MenuAction("Record this show", { onRecordShow(channel, minutes) })
                }
                // No guide info for right now (pay-per-view / event channels
                // like a local hockey game) - nothing to take an end time
                // from, so record a fixed 3 hours instead of offering nothing.
                // Stoppable any time from this same menu.
                channel.streamId == cursorChannel?.streamId &&
                    programAt(cursorPrograms, nowEpoch) == null ->
                    MenuAction("Record next 3 hours", { onRecordShow(channel, NO_GUIDE_RECORD_MINUTES) })
                else -> null
            }
        }

        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 16.dp)) {
            TopPreviewBlock(
                previewChannel = displayedChannel,
                cursorChannel = cursorChannel,
                program = cursorProgram,
                // Embedded never gets a live video surface here (see
                // SharedLivePreview's removal note below) - falls to the
                // channel-icon fallback branch, same as "no stream" does in
                // BrowseOnly.
                previewSource = if (mode == GuideMode.BrowseOnly && displayedChannel != null) {
                    PreviewSource.OwnPlayer(streamUrlFor(displayedChannel.streamId))
                } else {
                    null
                },
                nowEpoch = nowEpoch
            )
            Spacer(modifier = Modifier.height(10.dp))
            EpgTimelineGrid(
                favorites = favorites,
                epgWindows = epgWindows,
                tunedStreamId = tunedStreamId,
                nowEpoch = nowEpoch,
                initialFocusStreamId = if (mode == GuideMode.Embedded) tunedChannel?.streamId else tunedStreamId,
                mode = mode,
                cursorStreamId = cursorChannel?.streamId,
                cursorEpoch = effectiveCursorEpoch,
                cursorSpan = cursorSpan,
                onCursorEpochChange = { cursorEpoch = it },
                // First OK on another channel tunes the preview to it; OK on
                // the channel already in the preview goes full screen.
                onOk = { channel ->
                    if (channel.streamId == displayedChannel?.streamId) {
                        if (mode == GuideMode.Embedded) onExpand() else onPlay(channel)
                    } else {
                        previewChannel = channel
                        if (mode == GuideMode.Embedded) onChannelTuned(channel)
                    }
                },
                onRemove = onRemove,
                recordActionFor = recordActionFor,
                onFocusedChannelChanged = { channel -> cursorStreamId = channel.streamId },
                onProvideDirectionLeftHandler = onProvideDirectionLeftHandler,
                modifier = Modifier.weight(1f).fillMaxWidth()
            )
        }
    }
}

// STYLE_GUIDE.md §6.3 grid: header + timeline rows + "now" line. Selecting a
// channel here plays it full-screen (Screen.NowPlaying) - the OK button
// while already watching a channel is its own separate, simpler overlay
// (PlayerScreen's showInfo), not this grid.
@Composable
private fun EpgTimelineGrid(
    favorites: List<LiveChannel>,
    epgWindows: Map<Int, List<EpgProgram>>,
    tunedStreamId: Int?,
    nowEpoch: Long,
    initialFocusStreamId: Int?,
    mode: GuideMode,
    cursorStreamId: Int?,
    cursorEpoch: Long,
    cursorSpan: Pair<Long, Long>?,
    onCursorEpochChange: (Long) -> Unit,
    onOk: (LiveChannel) -> Unit,
    onRemove: (LiveChannel) -> Unit,
    recordActionFor: (LiveChannel) -> MenuAction?,
    onFocusedChannelChanged: (LiveChannel) -> Unit,
    // BrowseOnly only (see WithRail's onDirectionLeft doc) - republished
    // every recomposition so WithRail always calls the current cursor/
    // programs state, not a stale closure from first composition.
    onProvideDirectionLeftHandler: ((() -> Boolean) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val appColors = LocalAppColors.current
    val windowStartEpoch = windowStartFor(nowEpoch)
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val firstItemFocusRequester = remember { FocusRequester() }
    // Up on the first channel / Down on the last wraps around. The time
    // cursor is untouched by a wrap: it lives in cursorEpoch, not the row.
    val wrap = rememberListWrap(count = favorites.size)
    val initialFocusIndex = remember(favorites, initialFocusStreamId) {
        initialFocusStreamId?.let { id -> favorites.indexOfFirst { it.streamId == id } }
            ?.takeIf { it >= 0 } ?: 0
    }

    LaunchedEffect(Unit) {
        if (favorites.isNotEmpty()) {
            firstItemFocusRequester.requestFocus()
        }
    }

    BoxWithConstraints(modifier = modifier) {
        val channelColumnWidth = maxWidth * ChannelColumnFraction
        val viewportPx = with(density) { (maxWidth - channelColumnWidth).toPx() }

        // Scroll the shared timeline just enough to bring a program (or the
        // part of it that fits) into view.
        val revealSpan: (Long, Long) -> Unit = { startEpoch, stopEpoch ->
            val startPx = with(density) { ((startEpoch - windowStartEpoch) / 60f * PxPerMinute).dp.toPx() }
            val endPx = with(density) { ((stopEpoch - windowStartEpoch) / 60f * PxPerMinute).dp.toPx() }
            val current = scrollState.value
            val target = when {
                startPx < current -> startPx
                endPx > current + viewportPx -> min(startPx, endPx - viewportPx)
                else -> null
            }
            if (target != null) {
                coroutineScope.launch { scrollState.animateScrollTo(target.roundToInt().coerceAtLeast(0)) }
            }
        }
        val moveCursorTo: (EpgProgram) -> Unit = { program ->
            onCursorEpochChange(max(program.startEpochSeconds, windowStartEpoch))
            revealSpan(max(program.startEpochSeconds, windowStartEpoch), program.stopEpochSeconds)
        }
        // The real "walk cursor to the previous program" logic, republished
        // to WithRail (via FavoritesScreen) every recomposition so Left
        // reaches this before WithRail's own rail-opening fallback - see
        // WithRail's onDirectionLeft doc for why this hook exists at all.
        val tryMoveCursorLeft: () -> Boolean = {
            val programs = cursorStreamId?.let { epgWindows[it] }.orEmpty()
            previousProgram(programs, cursorEpoch, windowStartEpoch)?.let { moveCursorTo(it); true } ?: false
        }
        SideEffect { onProvideDirectionLeftHandler?.invoke(tryMoveCursorLeft) }

        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                GuideHeader(windowStartEpoch, favorites, epgWindows, scrollState, channelColumnWidth, cursorSpan)
                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(ScreenColors.SectionDivider))
                Spacer(modifier = Modifier.height(6.dp))
                LazyColumn(
                    state = wrap.listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .then(wrap.keys)
                        .onKeyEvent { event ->
                            // Left/Right move the browse cursor to the
                            // previous/next program on the cursor's channel
                            // (Up/Down use default row focus traversal and
                            // keep the cursor's time). Nothing here retunes
                            // the preview - only OK does.
                            // Embedded (reduced live view, reached via Back
                            // from full-screen live): Left never opens
                            // anything (Back takes over that role there, same
                            // as it does in the player), it just stops at the
                            // earliest program - handled right here, since
                            // Embedded has no WithRail ancestor to bubble to.
                            // BrowseOnly ("My TV" from the rail): this branch
                            // is effectively unreachable - WithRail's
                            // onPreviewKeyEvent, an ancestor of this
                            // LazyColumn, always intercepts Left first (see
                            // its onDirectionLeft doc). tryMoveCursorLeft
                            // above is what actually runs Left's logic there,
                            // via that hook; this branch only still matters
                            // for Embedded.
                            if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                            val programs = cursorStreamId?.let { epgWindows[it] }.orEmpty()
                            when (event.key) {
                                Key.DirectionRight -> {
                                    nextProgram(programs, cursorEpoch)?.let(moveCursorTo)
                                    true
                                }
                                Key.DirectionLeft -> {
                                    val previous = previousProgram(programs, cursorEpoch, windowStartEpoch)
                                    if (previous != null) {
                                        moveCursorTo(previous)
                                        true
                                    } else {
                                        mode == GuideMode.Embedded
                                    }
                                }
                                else -> false
                            }
                        }
                ) {
                    itemsIndexed(favorites, key = { _, channel -> channel.streamId }) { index, channel ->
                        EpgChannelRow(
                            channel = channel,
                            programs = epgWindows[channel.streamId].orEmpty(),
                            windowStartEpoch = windowStartEpoch,
                            nowEpoch = nowEpoch,
                            scrollState = scrollState,
                            channelColumnWidth = channelColumnWidth,
                            cursorEpoch = if (channel.streamId == cursorStreamId) cursorEpoch else null,
                            onPlay = { onOk(channel) },
                            onRemoveFavorite = { onRemove(channel) },
                            recordAction = recordActionFor(channel),
                            onFocused = { onFocusedChannelChanged(channel) },
                            rowModifier = (if (index == initialFocusIndex) Modifier.focusRequester(firstItemFocusRequester) else Modifier)
                                .then(wrap.itemModifier(index))
                        )
                    }
                }
            }

            val nowOffsetPx = with(density) {
                channelColumnWidth.toPx() + ((nowEpoch - windowStartEpoch) / 60f * PxPerMinute).dp.toPx()
            }
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .offset { IntOffset(x = (nowOffsetPx - scrollState.value).roundToInt(), y = 0) }
                    .width(1.dp)
            ) {
                Box(modifier = Modifier.fillMaxSize().background(appColors.tunedIndicator))
                // requiredSize, not size - this Box's own parent is pinned to
                // 1dp wide (matching the line), which would otherwise clamp
                // the circle down to that same 1dp and make it invisible.
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .offset(y = (-3).dp)
                        .requiredSize(7.dp)
                        .clip(CircleShape)
                        .background(appColors.tunedIndicator)
                )
            }
        }
    }
}

// BrowseOnly builds/tears down its own throwaway ExoPlayer (OwnPlayer), same
// as before. Embedded mode never gets a live video surface here at all - see
// the removal note where SharedLivePreview used to live, further down - so
// it always passes null and falls to the channel-icon branch below.
private sealed class PreviewSource {
    data class OwnPlayer(val streamUrl: String) : PreviewSource()
}

@Composable
private fun TopPreviewBlock(
    previewChannel: LiveChannel?,
    cursorChannel: LiveChannel?,
    program: EpgProgram?,
    previewSource: PreviewSource?,
    nowEpoch: Long
) {
    Row(modifier = Modifier.fillMaxWidth().height(120.dp), verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(213.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(ScreenColors.CurrentlyPlayingCell)
        ) {
            when (previewSource) {
                is PreviewSource.OwnPlayer -> LivePreview(streamUrl = previewSource.streamUrl, modifier = Modifier.fillMaxSize())
                null -> if (previewChannel != null) {
                    AsyncImage(
                        model = previewChannel.streamIcon,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().padding(24.dp)
                    )
                } else {
                    Text(
                        text = "Nothing watched yet",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.align(Alignment.Center).padding(8.dp)
                    )
                }
            }
        }
        if (cursorChannel != null) {
            // Text follows the guide cursor (any channel, any time); only the
            // video/icon slot on the left follows the preview channel.
            Column(modifier = Modifier.padding(start = 16.dp).weight(1f)) {
                Text(
                    text = TitleFormat.clean(program?.title ?: "No information"),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val channelName = TitleFormat.clean(cursorChannel.name)
                Text(
                    text = if (program != null) "$channelName  -  ${formatProgramTimes(program, nowEpoch)}" else channelName,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (program != null) {
                    if (program.description.isNotBlank()) {
                        Text(
                            text = program.description,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
            Text(
                text = formatNow(nowEpoch),
                color = appColorsNowText(),
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}

// Real decode of the tuned channel, with real audio - this is what carries
// "you're still listening to it" while browsing the guide, since navigating
// here tears down PlayerScreen's own player entirely (see MainActivity).
// Not the primary playback surface (that's PlayerScreen); torn down
// whenever the stream URL changes or the guide is left, same lifecycle
// pattern as PlayerScreen's own ExoPlayer.
@Composable
private fun LivePreview(streamUrl: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val exoPlayer = remember(streamUrl) {
        ExoPlayer.Builder(context).build().apply {
            setLiveStream(streamUrl)
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(exoPlayer) {
        onDispose { exoPlayer.release() }
    }
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            PlayerView(ctx).apply {
                player = exoPlayer
                useController = false
                setShutterBackgroundColor(android.graphics.Color.BLACK)
            }
        },
        update = { view -> view.player = exoPlayer }
    )
}

// A live video surface for the embedded/reduced preview was tried and
// reverted (2026-09-18): reparenting the shared ExoPlayer's video output
// from PlayerScreen's full-screen SurfaceView to a second surface here -
// tried first as another PlayerView (SurfaceView), then as a raw TextureView
// via Player.setVideoTextureView - caused a genuine ANR on real hardware
// (this Chromecast with Google TV): the main thread blocked for ~18s
// handling the Back keypress that triggered the swap, and the OS force-killed
// the app. Audio kept playing fine throughout in both attempts - only video
// output hot-swapping is unstable on this hardware. The embedded view now
// shows the channel icon instead (TopPreviewBlock's existing "no live
// surface" branch) - audio continuity via the shared player is preserved,
// only the live video thumbnail in the reduced view is not. Revisit only
// with real hardware testing budget, and consider whether a fixed
// stop-then-attach sequence (rather than a live hot-swap) avoids the same
// stall before trying again.

// Confirmed on real hardware (2026-09-18): composing the guide's full week of
// programs (hundreds of boxes per channel + ~336 time labels) blocked this
// TV's main thread for ~18s (the "guide is slow" bug, and the freeze that was
// wrongly blamed on video). The timeline keeps its full width so scrolling
// still reaches the end, but only the part near the visible area is composed.
// Bucketed to one scroll step so this only recomposes when scrolling crosses
// a 30-minute boundary, not on every pixel.
private const val VisibleAheadMinutes = 480f
private const val VisibleBehindMinutes = 60f

@Composable
private fun rememberVisibleWindow(scrollState: ScrollState, windowStartEpoch: Long): Pair<Long, Long> {
    val density = LocalDensity.current
    val stepPx = with(density) { (ScrollStepMinutes * PxPerMinute).dp.toPx() }
    val bucket by remember(scrollState, stepPx) {
        androidx.compose.runtime.derivedStateOf { (scrollState.value / stepPx).toInt() }
    }
    val startMin = bucket * ScrollStepMinutes
    val visStart = windowStartEpoch + ((startMin - VisibleBehindMinutes).coerceAtLeast(0f) * 60).toLong()
    val visEnd = windowStartEpoch + ((startMin + VisibleAheadMinutes) * 60).toLong()
    return visStart to visEnd
}


@Composable
private fun GuideHeader(
    windowStartEpoch: Long,
    favorites: List<LiveChannel>,
    epgWindows: Map<Int, List<EpgProgram>>,
    scrollState: ScrollState,
    channelColumnWidth: androidx.compose.ui.unit.Dp,
    cursorSpan: Pair<Long, Long>?
) {
    val accent = LocalAppColors.current.vividAccent
    val windowEndEpoch = windowEndEpoch(favorites, epgWindows, windowStartEpoch)
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        // Title of the channel column, in the space left of the time labels -
        // same 20dp height as that row so the grid doesn't move. Explicit
        // user request (2026-09-18): shows this is the user's own My TV list.
        Box(modifier = Modifier.width(channelColumnWidth).height(20.dp), contentAlignment = Alignment.CenterStart) {
            Text(
                text = "My TV",
                color = LocalAppColors.current.vividAccent,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
            )
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .height(20.dp)
                .horizontalScroll(scrollState)
        ) {
            Box(modifier = Modifier.width(timelineWidth(windowStartEpoch, windowEndEpoch)).fillMaxHeight()) {
                val (visStart, visEnd) = rememberVisibleWindow(scrollState, windowStartEpoch)
                val firstSlot = ((windowStartEpoch / HalfHourSeconds) + 1) * HalfHourSeconds
                var slotEpoch = max(firstSlot, ((visStart / HalfHourSeconds) + 1) * HalfHourSeconds)
                val lastSlot = min(windowEndEpoch, visEnd)
                // Green bar under the time labels over the cursor program's
                // start-to-stop span, so you can see which period you're on.
                if (cursorSpan != null) {
                    val spanStart = max(cursorSpan.first, windowStartEpoch)
                    if (spanStart < cursorSpan.second) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .offset(x = ((spanStart - windowStartEpoch) / 60f * PxPerMinute).dp)
                                .width(((cursorSpan.second - spanStart) / 60f * PxPerMinute).dp.coerceAtLeast(2.dp))
                                .height(1.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(accent)
                        )
                    }
                }
                while (slotEpoch < lastSlot) {
                    val offsetMinutes = (slotEpoch - windowStartEpoch) / 60f
                    val inCursorSpan = cursorSpan != null && slotEpoch >= cursorSpan.first && slotEpoch < cursorSpan.second
                    Text(
                        text = formatTime(slotEpoch),
                        color = if (inCursorSpan) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.offset(x = (offsetMinutes * PxPerMinute).dp)
                    )
                    slotEpoch += HalfHourSeconds
                }
            }
        }
    }
}

@Composable
private fun appColorsNowText() = LocalAppColors.current.tunedIndicator

@Composable
private fun EpgChannelRow(
    channel: LiveChannel,
    programs: List<EpgProgram>,
    windowStartEpoch: Long,
    nowEpoch: Long,
    scrollState: ScrollState,
    channelColumnWidth: androidx.compose.ui.unit.Dp,
    // Non-null only on the cursor's row; the program/gap containing it gets
    // the green highlight (while this row has D-pad focus).
    cursorEpoch: Long?,
    onPlay: () -> Unit,
    onRemoveFavorite: () -> Unit,
    recordAction: MenuAction?,
    onFocused: () -> Unit,
    rowModifier: Modifier = Modifier
) {
    val appColors = LocalAppColors.current
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    var menuExpanded by remember { mutableStateOf(false) }
    var confirmRemoveExpanded by remember { mutableStateOf(false) }

    // Drives the top preview block: whichever row the D-pad lands on becomes
    // the live audio/video + description shown above, without leaving this
    // screen (see FavoritesScreen's previewChannel state).
    LaunchedEffect(isFocused) {
        if (isFocused) onFocused()
    }

    // The focused row gets no background of its own (user request
    // 2026-09-20) - the green cursor cell and the white channel name are the
    // only signs of where you are.
    val nameColor = if (isFocused) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant

    // No scale/border/glow here, unlike the app's standard §5 card recipe -
    // this dense EPG grid uses only the background tint above for focus, per
    // the approved mockup. Omitting scale/border/glow params entirely does
    // NOT mean "none" - androidx.tv.material3's own Card defaults apply their
    // own baked-in focus scale/border/glow (the exact reason appCardScale()/
    // appCardBorder()/appCardGlow() exist elsewhere in this app, to override
    // that same default look) - they must be explicitly neutralized here.
    Column(modifier = Modifier.fillMaxWidth()) {
        Card(
            onClick = onPlay,
            onLongClick = { menuExpanded = true },
            interactionSource = interactionSource,
            modifier = Modifier.fillMaxWidth().height(RowHeight).then(rowModifier),
            colors = CardDefaults.colors(
                containerColor = Color.Transparent,
                focusedContainerColor = Color.Transparent,
                pressedContainerColor = Color.Transparent
            ),
            scale = CardDefaults.scale(scale = 1f, focusedScale = 1f, pressedScale = 1f),
            border = CardDefaults.border(
                border = Border.None,
                focusedBorder = Border.None,
                pressedBorder = Border.None
            ),
            glow = CardDefaults.glow(
                glow = Glow.None,
                focusedGlow = Glow.None,
                pressedGlow = Glow.None
            )
        ) {
            Row(modifier = Modifier.fillMaxHeight().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(
                    modifier = Modifier.width(channelColumnWidth).padding(end = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    AsyncImage(
                        model = channel.streamIcon,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.size(24.dp).clip(RoundedCornerShape(5.dp))
                    )
                    Text(
                        text = TitleFormat.clean(channel.name),
                        color = nameColor,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                val rowWindowEnd = windowEndEpochForRow(programs, windowStartEpoch)
                val (visStart, visEnd) = rememberVisibleWindow(scrollState, windowStartEpoch)
                val visiblePrograms = remember(programs, visStart, visEnd) {
                    programs.filter { it.stopEpochSeconds > visStart && it.startEpochSeconds < visEnd }
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .horizontalScroll(scrollState)
                ) {
                    Box(modifier = Modifier.width(timelineWidth(windowStartEpoch, rowWindowEnd)).fillMaxHeight()) {
                        // Any stretch of the visible window with no program data gets an
                        // explicit "No information" cell (mockup row 13, ICI MONTREAL) -
                        // a silent gap read as a rendering bug, not a real EPG hole.
                        computeGaps(visiblePrograms, max(windowStartEpoch, visStart), min(rowWindowEnd, visEnd)).forEach { (gapStart, gapEnd) ->
                            val startMinutes = (gapStart - windowStartEpoch) / 60f
                            val durationMinutes = (gapEnd - gapStart) / 60f
                            val gapHasCursor = isFocused && cursorEpoch != null && cursorEpoch >= gapStart && cursorEpoch < gapEnd
                            Box(
                                modifier = Modifier
                                    .offset(x = (startMinutes * PxPerMinute).dp)
                                    .width(((durationMinutes * PxPerMinute).dp).coerceAtLeast(2.dp))
                                    .fillMaxHeight()
                                    .then(if (gapHasCursor) Modifier.cursorHighlight() else Modifier)
                                    .drawBehind {
                                        drawLine(
                                            color = appColors.surfaceContainer,
                                            start = Offset(size.width, 0f),
                                            end = Offset(size.width, size.height),
                                            strokeWidth = 1.dp.toPx()
                                        )
                                    }
                            ) {
                                Text(
                                    text = "No information",
                                    color = if (gapHasCursor) ScreenColors.FavoritesBackground else ScreenColors.MutedQualityTag,
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.align(Alignment.CenterStart).padding(horizontal = 6.dp)
                                )
                            }
                        }
                        visiblePrograms.forEach { program ->
                            val clampedStart = max(program.startEpochSeconds, windowStartEpoch)
                            if (clampedStart < program.stopEpochSeconds) {
                                val startMinutes = (clampedStart - windowStartEpoch) / 60f
                                val durationMinutes = (program.stopEpochSeconds - clampedStart) / 60f
                                val isCurrent = nowEpoch in program.startEpochSeconds until program.stopEpochSeconds
                                val hasCursor = isFocused && cursorEpoch != null &&
                                    cursorEpoch >= program.startEpochSeconds && cursorEpoch < program.stopEpochSeconds
                                Box(
                                    modifier = Modifier
                                        .offset(x = (startMinutes * PxPerMinute).dp)
                                        .width(((durationMinutes * PxPerMinute).dp).coerceAtLeast(2.dp))
                                        .fillMaxHeight()
                                        .then(
                                            if (hasCursor) {
                                                Modifier.cursorHighlight()
                                            } else if (isCurrent) {
                                                Modifier.padding(horizontal = 1.dp).clip(RoundedCornerShape(3.dp)).background(ScreenColors.CurrentlyPlayingCell)
                                            } else {
                                                Modifier
                                            }
                                        )
                                        .drawBehind {
                                            drawLine(
                                                color = appColors.surfaceContainer,
                                                start = Offset(size.width, 0f),
                                                end = Offset(size.width, size.height),
                                                strokeWidth = 1.dp.toPx()
                                            )
                                        }
                                ) {
                                    Text(
                                        text = TitleFormat.clean(program.title),
                                        color = when {
                                            hasCursor -> ScreenColors.FavoritesBackground
                                            isCurrent -> MaterialTheme.colorScheme.onSurface
                                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.align(Alignment.CenterStart).padding(horizontal = 6.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(appColors.surfaceContainer))
    }

    // Removing a favorite is the destructive one: last in the list, and it
    // asks again (a long press in the guide used to remove it outright).
    RowActionsMenu(
        expanded = menuExpanded,
        onDismiss = { menuExpanded = false },
        actions = listOfNotNull(
            recordAction,
            MenuAction("Remove from Favorite...", { confirmRemoveExpanded = true }, isDestructive = true)
        )
    )
    RowActionsMenu(
        expanded = confirmRemoveExpanded,
        onDismiss = { confirmRemoveExpanded = false },
        actions = listOf(
            MenuAction("Cancel", {}),
            MenuAction("Yes, remove from Favorites", onRemoveFavorite, isDestructive = true)
        )
    )
}

// The browse cursor's cell: solid fill in the user's accent green, no border.
// Visibly different from the plain gray "currently playing" cell
// (STYLE_GUIDE.md §6.3).
@Composable
private fun Modifier.cursorHighlight(): Modifier {
    val accent = LocalAppColors.current.vividAccent
    return this
        .padding(horizontal = 1.dp)
        .clip(RoundedCornerShape(3.dp))
        .background(accent)
}

private fun windowStartFor(nowEpoch: Long): Long = (nowEpoch / HalfHourSeconds) * HalfHourSeconds

private fun programAt(programs: List<EpgProgram>, epoch: Long): EpgProgram? =
    programs.firstOrNull { epoch >= it.startEpochSeconds && epoch < it.stopEpochSeconds }

private fun nextProgram(programs: List<EpgProgram>, epoch: Long): EpgProgram? =
    programs.filter { it.startEpochSeconds > epoch }.minByOrNull { it.startEpochSeconds }

// Only programs still visible in the grid (ending after windowStartEpoch) count,
// so Left stops at what's drawn.
private fun previousProgram(programs: List<EpgProgram>, epoch: Long, windowStartEpoch: Long): EpgProgram? {
    val from = programAt(programs, epoch)?.startEpochSeconds ?: epoch
    return programs
        .filter { it.startEpochSeconds < from && it.stopEpochSeconds > windowStartEpoch }
        .maxByOrNull { it.startEpochSeconds }
}

// Start-to-stop of the program under the cursor, or of the "No information"
// gap it sits in.
private fun spanAtCursor(programs: List<EpgProgram>, epoch: Long, windowStartEpoch: Long): Pair<Long, Long> {
    programAt(programs, epoch)?.let { return it.startEpochSeconds to it.stopEpochSeconds }
    val gapStart = programs.filter { it.stopEpochSeconds <= epoch }.maxOfOrNull { it.stopEpochSeconds } ?: windowStartEpoch
    val gapEnd = programs.filter { it.startEpochSeconds > epoch }.minOfOrNull { it.startEpochSeconds }
        ?: windowEndEpochForRow(programs, windowStartEpoch)
    return max(gapStart, windowStartEpoch) to gapEnd
}

// Adds the weekday when the program isn't today, since the guide now shows
// programs days ahead.
private fun formatProgramTimes(program: EpgProgram, nowEpoch: Long): String {
    val range = "${formatTime(program.startEpochSeconds)} - ${formatTime(program.stopEpochSeconds)}"
    val day = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
    val startDate = Date(program.startEpochSeconds * 1000)
    if (day.format(startDate) == day.format(Date(nowEpoch * 1000))) return range
    return "${SimpleDateFormat("EEE", Locale.getDefault()).format(startDate)} $range"
}

private fun windowEndEpoch(favorites: List<LiveChannel>, epgWindows: Map<Int, List<EpgProgram>>, windowStartEpoch: Long): Long {
    val lastStop = favorites.flatMap { epgWindows[it.streamId].orEmpty() }.maxOfOrNull { it.stopEpochSeconds }
    return max(lastStop ?: 0L, windowStartEpoch + 3 * 3600)
}

private fun windowEndEpochForRow(programs: List<EpgProgram>, windowStartEpoch: Long): Long {
    val lastStop = programs.maxOfOrNull { it.stopEpochSeconds }
    return max(lastStop ?: 0L, windowStartEpoch + 3 * 3600)
}

private fun computeGaps(programs: List<EpgProgram>, windowStartEpoch: Long, windowEndEpoch: Long): List<Pair<Long, Long>> {
    val sorted = programs
        .filter { it.stopEpochSeconds > windowStartEpoch && it.startEpochSeconds < windowEndEpoch }
        .sortedBy { it.startEpochSeconds }
    val gaps = mutableListOf<Pair<Long, Long>>()
    var cursor = windowStartEpoch
    for (program in sorted) {
        val start = max(program.startEpochSeconds, windowStartEpoch)
        if (start > cursor) gaps.add(cursor to start)
        cursor = max(cursor, min(program.stopEpochSeconds, windowEndEpoch))
    }
    if (cursor < windowEndEpoch) gaps.add(cursor to windowEndEpoch)
    return gaps
}

@Composable
private fun timelineWidth(windowStartEpoch: Long, windowEndEpoch: Long) =
    (((windowEndEpoch - windowStartEpoch) / 60f) * PxPerMinute).dp

@Composable
private fun rememberNowEpochSeconds(): Long {
    var now by remember { mutableStateOf(System.currentTimeMillis() / 1000) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis() / 1000
            delay(30_000)
        }
    }
    return now
}

private fun formatTime(epochSeconds: Long): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(epochSeconds * 1000))

private fun formatNow(epochSeconds: Long): String =
    SimpleDateFormat("EEE, MMM d - HH:mm", Locale.getDefault()).format(Date(epochSeconds * 1000))
