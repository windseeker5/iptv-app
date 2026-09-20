package com.kdresdell.iptvtv

import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.rtsp.RtspMediaSource

// The one place a live stream URL becomes a media source. HTTP/HLS/TS goes
// through ExoPlayer's defaults as before; rtsp:// (the doorbell camera)
// forces RTP over TCP, the same as the PC script's --rtsp-transport=tcp -
// UDP is often dropped on home Wi-Fi and would stall before falling back.
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
