package com.kdresdell.iptvtv

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

// Lets the embedded guide start/stop the recording that lives inside
// PlayerScreen (the only place that owns the single live stream - the
// provider allows one connection, so recording pauses playback). The guide
// sets pendingMinutes / stopRequested; PlayerScreen consumes them and
// publishes available / isRecording / elapsedSeconds back.
class GuideRecordingBridge {
    var available by mutableStateOf(false)
    var isRecording by mutableStateOf(false)
    var elapsedSeconds by mutableStateOf(0)
    var pendingMinutes by mutableStateOf<Int?>(null)
    var stopRequested by mutableStateOf(false)
}
