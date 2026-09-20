package com.kdresdell.iptvtv

import android.content.Context
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer

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
class LivePlaybackHolder(context: Context) {
    val player: ExoPlayer = ExoPlayer.Builder(context.applicationContext).build()

    var currentStreamId: Int? = null
        private set

    // Set/cleared by whichever screen is currently showing full-screen
    // playback (only PlayerScreen surfaces a playback-error message today) -
    // not read anywhere while the embedded guide view is current.
    var onError: ((String) -> Unit)? = null

    init {
        player.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                onError?.invoke(error.message ?: "Playback error")
            }
        })
    }

    fun tune(streamId: Int, streamUrl: String) {
        if (currentStreamId == streamId) return
        currentStreamId = streamId
        player.setLiveStream(streamUrl)
        player.prepare()
        player.playWhenReady = true
    }

    private var released = false

    // Stops playback and forgets the current channel, so the next tune() to
    // the same channel starts fresh instead of being skipped as "already
    // tuned". Called whenever the live screen goes away (menu -> Search etc.)
    // - the shared player is no longer tied to a composable's lifecycle, so
    // without this its audio kept playing on every other screen.
    fun stop() {
        if (released) return
        currentStreamId = null
        player.stop()
        player.clearMediaItems()
    }

    fun release() {
        released = true
        player.release()
    }
}
