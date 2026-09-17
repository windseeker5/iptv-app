package com.kdresdell.iptvtv

import android.content.Context

// Recording defaults to OFF: most Google TV boxes have no USB storage
// attached, and a feature that silently fails (or worse, only "works" for
// the minority with a drive plugged in) shouldn't be on by default. Users
// opt in from Settings once they've actually attached a drive - see
// RecordingStorage.isDriveAvailable for the check gating that toggle.
object RecordingPrefs {
    private const val PREFS_NAME = "recording_settings"
    private const val KEY_ENABLED = "enabled"

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ENABLED, enabled)
            .apply()
    }
}
