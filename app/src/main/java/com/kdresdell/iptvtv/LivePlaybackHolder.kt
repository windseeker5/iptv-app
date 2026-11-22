package com.kdresdell.iptvtv

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer

// Live stream health, shown as a corner notice (see ConnectionNotice):
// Reconnecting while the app retries on its own, GaveUp after
// GIVE_UP_AFTER_MS without success ("check your internet or provider,
// press OK to try again").
enum class LiveConnection { Ok, Reconnecting, GaveUp }

private const val GIVE_UP_AFTER_MS = 5L * 60 * 1000
// Buffering this long with no picture counts as a dropped stream - a dead
// Wi-Fi doesn't always raise an error, sometimes it just waits forever.
private const val STALL_TIMEOUT_MS = 20_000L

// One ExoPlayer instance shared by full-screen live playback (PlayerScreen)
// and the reduced/embedded view over the EPG guide (FavoritesScreen's
// GuideMode.Embedded) - so Back/OK toggling between those two views never
// tears down and rebuilds playback, just re-parents the same player into a
// different-sized AndroidView. tune() is a no-op when already on the
// requested channel, which is what makes re-rendering after a screen
// transition cheap and glitch-free.
//
// Released explicitly, from exactly one place - the app-exit path
// (WithRail's onExitApp, wired in SideRail.kt) - never from a composable's
// DisposableEffect. That's the actual fix for the Fire TV bug where audio
// kept playing after "exiting" the app: the old code tied release() to
// Compose disposing the player composable, which never happened if the OS
// only backgrounded the Activity instead of destroying it.
//
// 0.23: the ExoPlayer itself is only alive while live TV is on screen.
// stop() (leaving live for a show, Search, Settings...) releases it, and the
// next read of `player` builds a fresh one - one player at a time, so a weak
// Fire TV never carries an idle live player while a movie plays.
class LivePlaybackHolder(context: Context) {
    private val appContext = context.applicationContext
    private var current: ExoPlayer? = null
    val player: ExoPlayer
        get() = current ?: AppMemory.buildPlayer(appContext).also {
            it.addListener(listener)
            current = it
        }

    // Automatic reconnect (2026-11-21): a Wi-Fi reboot used to leave the
    // channel dead until someone zapped away and back. Now a dropped stream
    // is retried on its own - after 3, 5, 10, 20 then every 30 s, and right
    // away when the network comes back - for up to 5 minutes.
    var connection by mutableStateOf(LiveConnection.Ok)
        private set
    private val handler = Handler(Looper.getMainLooper())
    private var lostAtMs = 0L
    private var attempt = 0
    private val retryRunnable = Runnable { retry() }
    private val stallRunnable = Runnable { onStreamLost("no picture for ${STALL_TIMEOUT_MS / 1000} s") }
    private val connectivity = context.applicationContext.getSystemService(ConnectivityManager::class.java)
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            handler.post {
                if (connection != LiveConnection.Ok && currentStreamId != null) {
                    AppLog.log("Network is back - reconnecting the channel now")
                    if (connection == LiveConnection.GaveUp) startLostWindow()
                    handler.removeCallbacks(retryRunnable)
                    // Give the new connection a moment to get its address.
                    handler.postDelayed(retryRunnable, 2_000L)
                }
            }
        }
    }

    var currentStreamId: Int? = null
        private set

    // Set/cleared by whichever screen is currently showing full-screen
    // playback (only PlayerScreen surfaces a playback-error message today) -
    // not read anywhere while the embedded guide view is current.
    var onError: ((String) -> Unit)? = null

    private val listener = object : Player.Listener {
        override fun onPlayerError(error: PlaybackException) {
            onError?.invoke(error.message ?: "Playback error")
            onStreamLost(error.message ?: "playback error")
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            handler.removeCallbacks(stallRunnable)
            when (playbackState) {
                Player.STATE_READY -> onStreamBack()
                Player.STATE_BUFFERING -> if (currentStreamId != null) {
                    handler.postDelayed(stallRunnable, STALL_TIMEOUT_MS)
                }
                else -> {}
            }
        }
    }

    init {
        try {
            connectivity?.registerDefaultNetworkCallback(networkCallback)
        } catch (e: Exception) {
            AppLog.log("Network watch unavailable: ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    private fun startLostWindow() {
        lostAtMs = SystemClock.elapsedRealtime()
        attempt = 0
        connection = LiveConnection.Reconnecting
    }

    private fun onStreamLost(reason: String) {
        if (currentStreamId == null || released) return
        if (connection == LiveConnection.GaveUp) return
        if (connection == LiveConnection.Ok) {
            AppLog.log("Live channel lost ($reason) - reconnecting")
            startLostWindow()
        }
        if (SystemClock.elapsedRealtime() - lostAtMs >= GIVE_UP_AFTER_MS) {
            AppLog.log("Live channel still unreachable after ${GIVE_UP_AFTER_MS / 60000} min - stopped retrying")
            connection = LiveConnection.GaveUp
            handler.removeCallbacks(retryRunnable)
            current?.stop()
            return
        }
        val delays = longArrayOf(3_000L, 5_000L, 10_000L, 20_000L)
        val delay = delays.getOrElse(attempt) { 30_000L }
        attempt++
        handler.removeCallbacks(retryRunnable)
        handler.postDelayed(retryRunnable, delay)
    }

    private fun onStreamBack() {
        if (connection == LiveConnection.Ok) return
        val seconds = (SystemClock.elapsedRealtime() - lostAtMs) / 1000
        AppLog.log("Live channel back after $seconds s")
        resetConnection()
    }

    private fun resetConnection() {
        handler.removeCallbacks(retryRunnable)
        handler.removeCallbacks(stallRunnable)
        connection = LiveConnection.Ok
        attempt = 0
    }

    // One reconnect attempt: restart the stream at the live edge. If it
    // fails again, onPlayerError (or the stall timer) schedules the next.
    private fun retry() {
        if (currentStreamId == null || released) return
        if (SystemClock.elapsedRealtime() - lostAtMs >= GIVE_UP_AFTER_MS) {
            onStreamLost("timeout")
            return
        }
        val p = current ?: return
        p.stop()
        p.seekToDefaultPosition()
        // playWhenReady is left as it was: a retry while the app is in the
        // background must not start the sound over the launcher.
        p.prepare()
        // A retry that just hangs without an error still counts as failed.
        handler.removeCallbacks(stallRunnable)
        handler.postDelayed(stallRunnable, STALL_TIMEOUT_MS)
    }

    // "Press OK to try again" after giving up: a fresh 5-minute window.
    fun retryNow() {
        if (currentStreamId == null || released) return
        AppLog.log("Trying the live channel again (OK pressed)")
        startLostWindow()
        handler.removeCallbacks(retryRunnable)
        retry()
    }

    fun tune(streamId: Int, streamUrl: String) {
        if (currentStreamId == streamId) return
        resetConnection()
        currentStreamId = streamId
        player.setLiveStream(streamUrl)
        player.prepare()
        player.playWhenReady = true
    }

    private var released = false

    // Stops playback, forgets the current channel and frees the player, so
    // the next tune() starts fresh on a new one. Called whenever the live
    // screen goes away (menu -> Search, a movie etc.) - the shared player is
    // no longer tied to a composable's lifecycle, so without this its audio
    // kept playing on every other screen.
    fun stop() {
        if (released) return
        resetConnection()
        currentStreamId = null
        current?.release()
        current = null
    }

    fun release() {
        released = true
        resetConnection()
        try {
            connectivity?.unregisterNetworkCallback(networkCallback)
        } catch (e: Exception) {
        }
        current?.release()
        current = null
    }
}
