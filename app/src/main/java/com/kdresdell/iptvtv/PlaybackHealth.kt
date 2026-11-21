package com.kdresdell.iptvtv

import android.app.ActivityManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.Format
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DecoderReuseEvaluation
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.DecoderCounters

// Watches the picture while something plays and writes what it sees to
// AppLog (Settings > Log), so a glitch reported the next day leaves a trace
// instead of a guess. Added for the "sports look choppy on the dining room
// TV until a reboot" report, 2026-10-28, which never reproduced on demand.
//
// Logs: the video format when it starts, frames the box dropped (shown too
// late, so skipped - choppy motion and choppy subtitles), and a summary when
// you leave. The feed vs TV refresh rate line comes from FrameRateMatch.kt.
//
// A burst of dropped frames restarts the stream once - a fresh decoder, the
// app-level version of the reboot that fixed it - at most every few minutes
// so a box that just can't keep up isn't restarted in a loop.
@OptIn(UnstableApi::class)
@Composable
fun WatchPlaybackHealth(player: ExoPlayer, contentId: Int, isLive: Boolean, title: String) {
    val context = LocalContext.current
    DisposableEffect(player, contentId) {
        val handler = Handler(Looper.getMainLooper())
        val name = TitleFormat.clean(title)
        val startedAt = SystemClock.elapsedRealtime()

        val formatListener = object : AnalyticsListener {
            override fun onVideoInputFormatChanged(
                eventTime: AnalyticsListener.EventTime,
                format: Format,
                decoderReuseEvaluation: DecoderReuseEvaluation?
            ) {
                AppLog.log("Playing $name: ${describe(format)}, ${freeMemoryMb(context)} MB free on the box")
            }
        }
        player.addAnalyticsListener(formatListener)

        // DecoderCounters is replaced (starting from zero) each time the
        // video decoder is set up again - a new channel, a restart.
        var counters: DecoderCounters? = null
        var seenDropped = 0
        var seenRendered = 0
        var totalDropped = 0
        var totalRendered = 0
        var minuteDropped = 0
        var ticks = 0
        var smoothSince = 0L
        val recent = ArrayDeque<Int>()

        fun restart() {
            lastRestartAt = SystemClock.elapsedRealtime()
            val position = player.currentPosition
            player.stop()
            if (isLive) player.seekToDefaultPosition() else player.seekTo(position)
            player.prepare()
            player.playWhenReady = true
        }

        val tick = object : Runnable {
            override fun run() {
                handler.postDelayed(this, TICK_MS)
                val c = player.videoDecoderCounters ?: return
                c.ensureUpdated()
                if (c !== counters) {
                    // First look at this channel's decoder: count from here.
                    val fresh = counters != null
                    counters = c
                    seenDropped = if (fresh) 0 else c.droppedBufferCount
                    seenRendered = if (fresh) 0 else c.renderedOutputBufferCount
                }
                val dropped = c.droppedBufferCount - seenDropped
                val rendered = c.renderedOutputBufferCount - seenRendered
                seenDropped = c.droppedBufferCount
                seenRendered = c.renderedOutputBufferCount
                totalDropped += dropped
                totalRendered += rendered
                minuteDropped += dropped

                val now = SystemClock.elapsedRealtime()
                val playing = player.playbackState == Player.STATE_READY && player.isPlaying
                if (!playing) {
                    smoothSince = 0L
                    recent.clear()
                } else {
                    if (smoothSince == 0L) smoothSince = now
                    recent.addLast(dropped)
                    while (recent.size > BURST_TICKS) recent.removeFirst()
                }

                // A few drops right after a channel starts are normal - only
                // judge once it has been playing a while.
                val burst = recent.sum()
                if (playing && now - smoothSince > SETTLE_MS && burst >= BURST_FRAMES) {
                    if (now - lastRestartAt > RESTART_COOLDOWN_MS) {
                        AppLog.log("Choppy video on $name: $burst frames dropped in ${BURST_TICKS * TICK_MS / 1000}s - restarting the stream")
                        Log.w(TAG, "burst $burst on $name, restarting")
                        restart()
                    } else {
                        AppLog.log("Choppy video on $name continues (restarted recently, not again)")
                    }
                    recent.clear()
                    smoothSince = 0L
                    minuteDropped = 0
                }

                if (++ticks % TICKS_PER_MINUTE == 0) {
                    if (minuteDropped >= MINUTE_LOG_FRAMES) {
                        AppLog.log("$name: $minuteDropped frames dropped in the last minute")
                    }
                    Log.i(TAG, "$name: minute dropped=$minuteDropped total=$totalDropped/$totalRendered")
                    minuteDropped = 0
                }
            }
        }
        handler.postDelayed(tick, TICK_MS)

        onDispose {
            handler.removeCallbacksAndMessages(null)
            player.removeAnalyticsListener(formatListener)
            val minutes = (SystemClock.elapsedRealtime() - startedAt) / 60_000
            if (minutes >= 1) {
                val percent = if (totalRendered > 0) totalDropped * 100f / (totalRendered + totalDropped) else 0f
                AppLog.log(
                    "Stopped $name after $minutes min: $totalDropped frames dropped" +
                        if (totalDropped > 0) " (${String.format(java.util.Locale.US, "%.1f", percent)}%)" else ""
                )
            }
        }
    }
}

private fun describe(format: Format): String {
    val codec = when (format.sampleMimeType) {
        "video/avc" -> "H.264"
        "video/hevc" -> "H.265"
        "video/mpeg2" -> "MPEG-2"
        else -> format.sampleMimeType ?: "unknown codec"
    }
    val size = if (format.width > 0) "${format.width}x${format.height} " else ""
    val rate = format.bitrate.takeIf { it > 0 }?.let { " %.1f Mbit/s".format(java.util.Locale.US, it / 1_000_000f) } ?: ""
    return "$size$codec$rate"
}

private fun freeMemoryMb(context: Context): Long {
    val info = ActivityManager.MemoryInfo()
    (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(info)
    return info.availMem / (1024 * 1024)
}

// App-wide, not per channel: zapping away and back mustn't reset it.
// Starts a full cooldown in the past so a box booted a minute ago can
// still restart.
private var lastRestartAt = -RESTART_COOLDOWN_MS

private const val TICK_MS = 5_000L
private const val TICKS_PER_MINUTE = 12
// 2 ticks = 10s. 30 dropped frames in 10s is ~5% at 60fps - clearly visible.
private const val BURST_TICKS = 2
private const val BURST_FRAMES = 30
private const val SETTLE_MS = 10_000L
private const val RESTART_COOLDOWN_MS = 5 * 60_000L
private const val MINUTE_LOG_FRAMES = 10

private const val TAG = "KdtvHealth"
