package com.kdresdell.iptvtv

import androidx.compose.runtime.mutableStateListOf
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Prototype-stage in-app error log: no logcat/adb access for the people
// actually testing this, so failures need to be visible from Settings
// instead. Backed by mutableStateListOf so any screen reading `entries`
// recomposes automatically when a new one is logged.
object AppLog {
    private const val MAX_ENTRIES = 50
    val entries = mutableStateListOf<String>()

    // The same failure repeating (e.g. a provider outage retried every few
    // minutes) used to add one identical line per attempt - a single ongoing
    // problem read as dozens of distinct ones. Consecutive repeats of the
    // same message now update one line's timestamp and count instead.
    private var lastMessage: String? = null
    private var lastCount = 0

    fun log(message: String) {
        val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        if (message == lastMessage && entries.isNotEmpty()) {
            lastCount++
            entries[0] = "[$timestamp] $message (×$lastCount)"
        } else {
            lastMessage = message
            lastCount = 1
            entries.add(0, "[$timestamp] $message")
            while (entries.size > MAX_ENTRIES) entries.removeAt(entries.size - 1)
        }
    }
}
