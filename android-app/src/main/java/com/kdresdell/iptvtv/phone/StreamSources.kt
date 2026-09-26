package com.kdresdell.iptvtv.phone

// TODO: duplicated from the TV app's StreamSources.kt.

import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.rtsp.RtspMediaSource

@OptIn(UnstableApi::class)
fun ExoPlayer.setLiveStream(url: String) {
    if (url.startsWith("rtsp://")) {
        setMediaSource(
            RtspMediaSource.Factory()
                .setForceUseRtpTcp(true)
                .createMediaSource(MediaItem.fromUri(url))
        )
    } else {
        setMediaItem(MediaItem.fromUri(url))
    }
}
