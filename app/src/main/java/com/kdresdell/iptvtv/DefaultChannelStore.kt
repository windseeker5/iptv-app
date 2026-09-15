package com.kdresdell.iptvtv

import android.content.Context

// Local-only: which favorite channel (if any) to launch straight into on
// app start, instead of landing on the Favorites list. Storing just the
// stream_id keeps this self-healing - if that channel is later removed
// from favorites, callers simply won't find a match and fall back to the
// normal Favorites home screen.
class DefaultChannelStore(context: Context) {
    private val prefs = context.getSharedPreferences("default_channel", Context.MODE_PRIVATE)

    fun getDefaultStreamId(): Int? {
        val value = prefs.getInt(KEY_STREAM_ID, -1)
        return if (value == -1) null else value
    }

    fun setDefault(streamId: Int?) {
        if (streamId == null) {
            prefs.edit().remove(KEY_STREAM_ID).apply()
        } else {
            prefs.edit().putInt(KEY_STREAM_ID, streamId).apply()
        }
    }

    companion object {
        private const val KEY_STREAM_ID = "default_stream_id"
    }
}
