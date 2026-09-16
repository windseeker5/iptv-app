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

    fun log(message: String) {
        val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        entries.add(0, "[$timestamp] $message")
        while (entries.size > MAX_ENTRIES) entries.removeAt(entries.size - 1)
    }
}
