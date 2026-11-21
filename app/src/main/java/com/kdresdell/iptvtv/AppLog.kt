package com.kdresdell.iptvtv

import android.content.Context
import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import org.json.JSONArray
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Prototype-stage in-app log: no logcat/adb access for the people actually
// testing this, so failures need to be visible from Settings instead.
// Backed by mutableStateListOf so any screen reading `entries` recomposes
// automatically when a new one is logged.
//
// Saved to a file since 0.20: the playback health notes (PlaybackHealth.kt)
// are only useful if they're still there after the app is restarted - which
// is the first thing anyone does when the picture looks wrong.
object AppLog {
    private const val MAX_ENTRIES = 100
    val entries = mutableStateListOf<String>()

    private var file: File? = null

    // The same failure repeating (e.g. a provider outage retried every few
    // minutes) used to add one identical line per attempt - a single ongoing
    // problem read as dozens of distinct ones. Consecutive repeats of the
    // same message now update one line's timestamp and count instead.
    private var lastMessage: String? = null
    private var lastCount = 0

    fun init(context: Context) = synchronized(this) {
        if (file != null) return
        val f = File(context.filesDir, "app_log.json")
        file = f
        try {
            if (f.exists()) {
                val saved = JSONArray(f.readText())
                for (i in 0 until minOf(saved.length(), MAX_ENTRIES)) entries.add(saved.getString(i))
            }
        } catch (e: Exception) {
            // A corrupt log isn't worth losing the app over - start a new one.
        }
    }

    fun log(message: String) = synchronized(this) {
        // Also to logcat, so a box on adb can be read without opening Settings.
        Log.i("KdtvLog", message)
        val timestamp = SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        if (message == lastMessage && entries.isNotEmpty()) {
            lastCount++
            entries[0] = "[$timestamp] $message (×$lastCount)"
        } else {
            lastMessage = message
            lastCount = 1
            entries.add(0, "[$timestamp] $message")
            while (entries.size > MAX_ENTRIES) entries.removeAt(entries.size - 1)
        }
        try {
            file?.writeText(JSONArray(entries.toList()).toString())
        } catch (e: Exception) {
            // Still in memory; the file is a convenience.
        }
    }
}
