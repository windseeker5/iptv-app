package com.kdresdell.iptvtv

import android.content.Context

// Local-only: the stream_id of the last live channel actually watched, so
// app start can resume it instead of always landing on the Favorites list.
// Self-healing, same as the default-channel feature this replaced - if that
// channel is later removed from favorites, callers simply won't find a
// match and fall back to the first favorite in guide order.
class LastWatchedStore(context: Context) {
    private val prefs = context.getSharedPreferences("last_watched_channel", Context.MODE_PRIVATE)

    fun getLastWatched(): Int? {
        val value = prefs.getInt(KEY_STREAM_ID, -1)
        return if (value == -1) null else value
    }

    fun setLastWatched(streamId: Int) {
        prefs.edit().putInt(KEY_STREAM_ID, streamId).apply()
    }

    companion object {
        private const val KEY_STREAM_ID = "last_watched_stream_id"
    }
}
