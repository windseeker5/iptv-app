package com.kdresdell.iptvtv

import android.app.Activity
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.Player

// Google TV's screensaver has no idea video is playing (there's no remote
// input while you watch), so the window is kept awake for as long as
// something is meant to be playing - on any screen, not just full screen.
// It used to be tied to PlayerScreen being open, so My TV's live preview
// (sound and all) got the screensaver on top of it after a few minutes
// (2026-10-28). Paused, stopped or failed = the screensaver is allowed again,
// same as Netflix/YouTube.
//
// "Meant to be playing" (playWhenReady, not isPlaying): a live stream
// rebuffering for a second must not release it - the screensaver timer
// counts from the last key press, so it could start straight away.
@Composable
fun KeepScreenAwakeWhilePlaying(player: Player, alsoWhen: Boolean = false) {
    val activity = LocalContext.current as? Activity ?: return
    var playing by remember(player) { mutableStateOf(wantsToPlay(player)) }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                playing = wantsToPlay(player)
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }
    val awake = playing || alsoWhen
    DisposableEffect(activity, awake) {
        if (!awake) return@DisposableEffect onDispose {}
        ScreenAwake.acquire(activity)
        onDispose { ScreenAwake.release(activity) }
    }
}

private fun wantsToPlay(player: Player): Boolean =
    player.playWhenReady && player.playbackState != Player.STATE_IDLE && player.playbackState != Player.STATE_ENDED

// One window flag, possibly several players at once (the guide's preview
// while another screen's player winds down): counted, so one letting go
// doesn't put the TV to sleep under the other. Main thread only.
private object ScreenAwake {
    private var holders = 0

    fun acquire(activity: Activity) {
        if (holders++ == 0) activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    fun release(activity: Activity) {
        if (--holders == 0) activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
}
