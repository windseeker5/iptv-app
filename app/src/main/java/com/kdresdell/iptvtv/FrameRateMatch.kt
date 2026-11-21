package com.kdresdell.iptvtv

import android.app.Activity
import android.media.MediaFormat
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Display
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.Format
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.video.VideoFrameMetadataListener
import kotlin.math.abs
import kotlin.math.roundToInt

// Switches the TV's refresh rate to match a movie/episode's frame rate, and
// back to the default when the player leaves. Google TV otherwise runs at a
// fixed 59.94Hz: a 24fps film shown at 60Hz has to hold every other frame a
// bit longer (3:2 pulldown), a visible judder on slow pans; at 24Hz each
// frame is shown for the same time. ExoPlayer only asks for "seamless"
// switches, which an HDMI mode change never is, hence doing it here. The
// screen blanks for ~1s while the TV changes mode - the same as Netflix/Kodi
// with "match frame rate" on.
//
// The provider's MKV files don't declare a frame rate (Format.frameRate is
// unset for them - checked on the Chromecast), so when it's missing it is
// measured from the timestamps of the first frames instead.
//
// Live channels too since 0.20, but only when the feed doesn't already fit
// the TV's current rate: most channels are 59.94fps and the TV already runs
// at 59.94Hz, so zapping between them never blanks the screen. A 50fps or
// 60.000fps feed on a 59.94Hz TV has to repeat or skip a frame on a steady
// rhythm - a subtle stutter ("tired eyes") that was reported on sports
// channels on the dining room Chromecast 4K, 2026-10-28. Never while the
// video is shrunk into the guide (`active` false): browsing channels there
// must not blank the screen.
@OptIn(UnstableApi::class)
@Composable
fun MatchDisplayFrameRate(player: ExoPlayer, contentId: Int, isLive: Boolean, active: Boolean) {
    val activity = LocalContext.current as? Activity ?: return
    // Back to the TV's default only when this player goes away, not between
    // live channels - two 50fps channels in a row would otherwise blank the
    // screen twice (once back to default, once to 50Hz again).
    DisposableEffect(player) {
        onDispose {
            val window = activity.window
            val params = window.attributes
            if (params.preferredDisplayModeId != 0) {
                params.preferredDisplayModeId = 0
                window.attributes = params
            }
        }
    }
    DisposableEffect(player, contentId, active) {
        if (!active) return@DisposableEffect onDispose {}
        val window = activity.window
        val mainHandler = Handler(Looper.getMainLooper())

        fun switchTo(fps: Float) {
            val display = window.decorView.display ?: return
            val current = display.mode
            if (isLive && isMultiple(current.refreshRate, fps)) {
                AppLog.log("Video rhythm: feed ${fps.pretty()}fps, TV ${current.refreshRate.pretty()}Hz - OK")
                return
            }
            val mode = bestModeFor(fps, display)
            Log.i(TAG, "Video ${fps}fps -> display mode ${mode?.modeId} (${mode?.refreshRate}Hz)")
            AppLog.log(
                "Video rhythm: feed ${fps.pretty()}fps, TV ${current.refreshRate.pretty()}Hz - " +
                    (mode?.let { "switching TV to ${it.refreshRate.pretty()}Hz" } ?: "TV has no matching rate")
            )
            mode ?: return
            val params = window.attributes
            if (params.preferredDisplayModeId != mode.modeId) {
                params.preferredDisplayModeId = mode.modeId
                window.attributes = params
            }
        }

        // Called on the playback thread for every frame until a rate is found.
        val timestamps = ArrayList<Long>()
        var done = false
        // The shared live player can still be showing the previous
        // channel's last frames when this starts - don't measure those.
        var skip = SKIP_FRAMES
        val listener = object : VideoFrameMetadataListener {
            override fun onVideoFrameAboutToBeRendered(
                presentationTimeUs: Long,
                releaseTimeNs: Long,
                format: Format,
                mediaFormat: MediaFormat?
            ) {
                if (done) return
                if (skip > 0) { skip--; return }
                val declared = format.frameRate.takeIf { it > 0f && it != Format.NO_VALUE.toFloat() }
                val fps = declared ?: run {
                    // A gap outside 10-100fps means a seek or a hiccup: start over.
                    val last = timestamps.lastOrNull()
                    if (last != null && (presentationTimeUs - last !in 10_000..100_000)) timestamps.clear()
                    timestamps += presentationTimeUs
                    if (timestamps.size < SAMPLE_FRAMES) return
                    // Average over the window, not the median: MKV timestamps
                    // are rounded to the millisecond (24fps = 41/42/42ms...).
                    val avgUs = (timestamps.last() - timestamps.first()).toFloat() / (timestamps.size - 1)
                    snapToStandardRate(1_000_000f / avgUs) ?: run { timestamps.clear(); return }
                }
                done = true
                mainHandler.post {
                    player.clearVideoFrameMetadataListener(this)
                    switchTo(fps)
                }
            }
        }
        player.setVideoFrameMetadataListener(listener)

        onDispose {
            mainHandler.removeCallbacksAndMessages(null)
            player.clearVideoFrameMetadataListener(listener)
        }
    }
}

private val STANDARD_RATES = floatArrayOf(24000f / 1001f, 24f, 25f, 30000f / 1001f, 30f, 50f, 60000f / 1001f, 60f)

// A measured 23.98 is film at 23.976; 24.4 or anything else odd is left
// alone (null) rather than guessed at.
private fun snapToStandardRate(fps: Float): Float? =
    STANDARD_RATES.minByOrNull { abs(it - fps) }?.takeIf { abs(it - fps) / it < 0.004f }

// Same resolution as now, refresh rate an exact multiple of the frame rate
// (24 -> 24Hz, 25 -> 50Hz, 23.976 -> 23.976Hz, 30 -> 60Hz). Highest such
// rate wins so menus stay as smooth as possible. null = no match, leave the
// TV alone.
private fun bestModeFor(fps: Float, display: Display): Display.Mode? {
    val current = display.mode
    return display.supportedModes
        .filter { it.physicalWidth == current.physicalWidth && it.physicalHeight == current.physicalHeight }
        .filter { isMultiple(it.refreshRate, fps) }
        .maxByOrNull { it.refreshRate }
}

private fun isMultiple(refreshRate: Float, fps: Float): Boolean {
    val multiple = (refreshRate / fps).roundToInt()
    return multiple >= 1 && abs(refreshRate - multiple * fps) / refreshRate < 0.0005f
}

private fun Float.pretty(): String = String.format(java.util.Locale.US, "%.2f", this).trimEnd('0').trimEnd('.')

// ~4s of 24fps video - long enough to tell 23.976 from 24 despite the
// millisecond-rounded timestamps.
private const val SAMPLE_FRAMES = 96

private const val SKIP_FRAMES = 30

private const val TAG = "KdtvFrameRate"
