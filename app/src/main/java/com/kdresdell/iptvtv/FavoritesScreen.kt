package com.kdresdell.iptvtv

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.animateScrollBy
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
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
import androidx.media3.common.MediaItem
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
// Top preview: a real live decode of the tuned (default) channel, with real
// audio - this is "the live stream you're actually listening to", carried
// over from the player (whose own playback stops when you navigate here).
// Falls back to a neutral placeholder if no default channel is set.
private const val ChannelColumnFraction = 0.26f
private val RowHeight = 44.dp
private const val PxPerMinute = 3.2f
private const val HalfHourSeconds = 30 * 60L
// D-pad right/left scrolls the timeline by this many minutes at a time, to
// browse future/past programs without leaving the guide row focus.
private const val ScrollStepMinutes = 30f

// BrowseOnly is today's "My TV" screen, reached from the rail, unchanged.
// Embedded is the guide shown while watching live (Back from full-screen):
// PlayerScreen draws its own live video on top of this guide's top-left
// slot, so this composable draws no video itself in that mode. Up/Down
// report the highlighted channel (onChannelTuned) so the player can switch to
// it, Left/Right scroll the timeline both ways (no side menu - Back opens
// that), and OK calls onExpand() to go back to full-screen.
enum class GuideMode { BrowseOnly, Embedded }

@Composable
fun FavoritesScreen(
    favorites: List<LiveChannel>,
    epgWindows: Map<Int, List<EpgProgram>>,
    defaultStreamId: Int?,
    streamUrlFor: (Int) -> String,
    onPlay: (LiveChannel) -> Unit,
    onRemove: (LiveChannel) -> Unit,
    onSetDefault: (LiveChannel) -> Unit,
    mode: GuideMode = GuideMode.BrowseOnly,
    // Embedded-only params (see GuideMode doc above) - null/no-op defaults
    // keep every existing BrowseOnly call site unchanged.
    tunedChannel: LiveChannel? = null,
    onChannelTuned: (LiveChannel) -> Unit = {},
    onExpand: () -> Unit = {}
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

        val defaultTunedChannel = defaultStreamId?.let { id -> favorites.find { it.streamId == id } }
        // The top preview follows whichever row currently has D-pad focus (not
        // necessarily the default/tuned channel) - lets you browse the guide
        // and hear/see each channel's own live audio + description without
        // leaving this screen. Starts on the tuned channel since that's where
        // initial focus lands in the grid below.
        var previewChannel by remember { mutableStateOf(defaultTunedChannel) }
        val displayedChannel = if (mode == GuideMode.Embedded) tunedChannel else (previewChannel ?: defaultTunedChannel)

        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 16.dp)) {
            TopPreviewBlock(
                channel = displayedChannel,
                program = displayedChannel?.let { channel ->
                    epgWindows[channel.streamId].orEmpty()
                        .firstOrNull { nowEpoch in it.startEpochSeconds until it.stopEpochSeconds }
                },
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
                defaultStreamId = defaultStreamId,
                nowEpoch = nowEpoch,
                initialFocusStreamId = if (mode == GuideMode.Embedded) tunedChannel?.streamId else defaultStreamId,
                mode = mode,
                onPlay = if (mode == GuideMode.Embedded) { _ -> onExpand() } else onPlay,
                onSetDefault = onSetDefault,
                onRemove = onRemove,
                onFocusedChannelChanged = { channel ->
                    previewChannel = channel
                    if (mode == GuideMode.Embedded) onChannelTuned(channel)
                },
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
    defaultStreamId: Int?,
    nowEpoch: Long,
    initialFocusStreamId: Int?,
    mode: GuideMode,
    onPlay: (LiveChannel) -> Unit,
    onSetDefault: (LiveChannel) -> Unit,
    onRemove: (LiveChannel) -> Unit,
    onFocusedChannelChanged: (LiveChannel) -> Unit,
    modifier: Modifier = Modifier
) {
    val appColors = LocalAppColors.current
    val windowStartEpoch = (nowEpoch / HalfHourSeconds) * HalfHourSeconds
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val scrollStepPx = with(density) { (ScrollStepMinutes * PxPerMinute).dp.toPx() }
    val firstItemFocusRequester = remember { FocusRequester() }
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

        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                GuideHeader(windowStartEpoch, favorites, epgWindows, scrollState, channelColumnWidth)
                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(ScreenColors.SectionDivider))
                Spacer(modifier = Modifier.height(6.dp))
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .onKeyEvent { event ->
                            // BrowseOnly ("My TV" from the rail): Right only,
                            // browses into the future - Left is left
                            // unhandled so it bubbles up to WithRail's Left
                            // handler and summons the side menu, same as
                            // every other screen.
                            // Embedded (reduced live view, reached via Back
                            // from full-screen live): both directions scroll
                            // the timeline - Left now moves backward instead
                            // of opening anything (Back takes over that role
                            // there, same as it does in the player) -
                            // animateScrollBy already clamps at 0, so this
                            // naturally stops at "now" without extra bounds
                            // logic.
                            if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                            when (event.key) {
                                Key.DirectionRight -> {
                                    coroutineScope.launch { scrollState.animateScrollBy(scrollStepPx) }
                                    true
                                }
                                Key.DirectionLeft -> {
                                    if (mode == GuideMode.Embedded) {
                                        coroutineScope.launch { scrollState.animateScrollBy(-scrollStepPx) }
                                        true
                                    } else {
                                        false
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
                            isTuned = channel.streamId == defaultStreamId,
                            scrollState = scrollState,
                            channelColumnWidth = channelColumnWidth,
                            onPlay = { onPlay(channel) },
                            onSetDefault = { onSetDefault(channel) },
                            onRemoveFavorite = { onRemove(channel) },
                            onFocused = { onFocusedChannelChanged(channel) },
                            rowModifier = if (index == initialFocusIndex) Modifier.focusRequester(firstItemFocusRequester) else Modifier
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
private fun TopPreviewBlock(channel: LiveChannel?, program: EpgProgram?, previewSource: PreviewSource?, nowEpoch: Long) {
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
                null -> if (channel != null) {
                    AsyncImage(
                        model = channel.streamIcon,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().padding(24.dp)
                    )
                } else {
                    Text(
                        text = "No default channel set",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.align(Alignment.Center).padding(8.dp)
                    )
                }
            }
        }
        if (channel != null) {
            Column(modifier = Modifier.padding(start = 16.dp).weight(1f)) {
                Text(
                    text = TitleFormat.clean(program?.title ?: channel.name),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (program != null) {
                    Text(
                        text = "${formatTime(program.startEpochSeconds)} - ${formatTime(program.stopEpochSeconds)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
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
            setMediaItem(MediaItem.fromUri(streamUrl))
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
    channelColumnWidth: androidx.compose.ui.unit.Dp
) {
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
                while (slotEpoch < lastSlot) {
                    val offsetMinutes = (slotEpoch - windowStartEpoch) / 60f
                    Text(
                        text = formatTime(slotEpoch),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
    isTuned: Boolean,
    scrollState: ScrollState,
    channelColumnWidth: androidx.compose.ui.unit.Dp,
    onPlay: () -> Unit,
    onSetDefault: () -> Unit,
    onRemoveFavorite: () -> Unit,
    onFocused: () -> Unit,
    rowModifier: Modifier = Modifier
) {
    val appColors = LocalAppColors.current
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    var menuExpanded by remember { mutableStateOf(false) }

    // Drives the top preview block: whichever row the D-pad lands on becomes
    // the live audio/video + description shown above, without leaving this
    // screen (see FavoritesScreen's previewChannel state).
    LaunchedEffect(isFocused) {
        if (isFocused) onFocused()
    }

    // Per-row focus tint (§6.3): a faint wash of the vivid accent over the
    // guide's near-black background, not a flat color swap - keeps this
    // distinct from the "currently playing" cell's plain gray highlight.
    val focusedRowTint = appColors.vividAccent.copy(alpha = 0.06f).compositeOver(ScreenColors.FavoritesBackground)
    // Focus (white text + green row tint) is now the only highlight state a
    // row gets - the default/tuned channel no longer gets a permanent green
    // name color, since that read as a confusing second "selected" state
    // once focus navigation between rows was added. isTuned still drives the
    // "currently playing" cell logic below and the RowActionsMenu toggle.
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
                focusedContainerColor = focusedRowTint,
                pressedContainerColor = focusedRowTint
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
                            Box(
                                modifier = Modifier
                                    .offset(x = (startMinutes * PxPerMinute).dp)
                                    .width(((durationMinutes * PxPerMinute).dp).coerceAtLeast(2.dp))
                                    .fillMaxHeight()
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
                                    color = ScreenColors.MutedQualityTag,
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
                                Box(
                                    modifier = Modifier
                                        .offset(x = (startMinutes * PxPerMinute).dp)
                                        .width(((durationMinutes * PxPerMinute).dp).coerceAtLeast(2.dp))
                                        .fillMaxHeight()
                                        .then(
                                            if (isCurrent) {
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
                                        color = if (isCurrent) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
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

    RowActionsMenu(
        expanded = menuExpanded,
        onDismiss = { menuExpanded = false },
        actions = listOf(
            MenuAction("Remove from Favorite", onRemoveFavorite),
            MenuAction(if (isTuned) "Remove Default" else "Set as Default", onSetDefault)
        )
    )
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
