package com.kdresdell.iptvtv

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

// Local-only list of favorited live channels, keyed by stream_id.
class FavoritesStore(context: Context) {
    private val prefs = context.getSharedPreferences("favorites", Context.MODE_PRIVATE)

    fun load(): List<LiveChannel> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).map { i ->
                val obj = array.getJSONObject(i)
                LiveChannel(
                    streamId = obj.getInt("stream_id"),
                    name = obj.getString("name"),
                    categoryId = obj.optString("category_id"),
                    streamIcon = obj.optString("stream_icon")
                )
            }
        } catch (e: Exception) {
            // Silently returning an empty list here used to mean a corrupt
            // favorites file looked identical to "no favorites yet" - now
            // at least visible in Settings' error log.
            AppLog.log("Load favorites failed (data reset to empty): ${e.javaClass.simpleName}: ${e.message}")
            emptyList()
        }
    }

    private fun save(channels: List<LiveChannel>) {
        val array = JSONArray()
        channels.filterNot { DoorbellChannel.isDoorbell(it.streamId) }.forEach { channel ->
            array.put(
                JSONObject().apply {
                    put("stream_id", channel.streamId)
                    put("name", channel.name)
                    put("category_id", channel.categoryId)
                    put("stream_icon", channel.streamIcon)
                }
            )
        }
        prefs.edit().putString(KEY, array.toString()).apply()
    }

    // Returns the updated list so the caller can update its own UI state.
    fun toggle(channel: LiveChannel, current: List<LiveChannel>): List<LiveChannel> {
        val updated = if (current.any { it.streamId == channel.streamId }) {
            current.filterNot { it.streamId == channel.streamId }
        } else {
            current + channel
        }
        save(updated)
        return updated
    }

    companion object {
        private const val KEY = "favorite_channels"
    }
}
