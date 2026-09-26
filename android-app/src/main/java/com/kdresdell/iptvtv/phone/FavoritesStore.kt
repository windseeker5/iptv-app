package com.kdresdell.iptvtv.phone

// TODO: duplicated/trimmed from the TV app's FavoritesStore.kt (no doorbell
// channel filtering - phone-only concern doesn't apply here).

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

// "My TV" - saved live channels.
class FavoritesStore(context: Context) {
    private val prefs = context.getSharedPreferences("favorites", Context.MODE_PRIVATE)

    fun load(): List<LiveChannel> {
        val json = prefs.getString(KEY, null) ?: return emptyList()
        return try {
            val array = JSONArray(json)
            (0 until array.length()).map { i ->
                val obj = array.getJSONObject(i)
                LiveChannel(
                    streamId = obj.getInt("stream_id"),
                    name = obj.getString("name"),
                    categoryId = obj.getString("category_id"),
                    streamIcon = obj.optString("stream_icon")
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun toggle(channel: LiveChannel, current: List<LiveChannel>): List<LiveChannel> {
        val updated = if (current.any { it.streamId == channel.streamId }) {
            current.filterNot { it.streamId == channel.streamId }
        } else {
            current + channel
        }
        save(updated)
        return updated
    }

    private fun save(channels: List<LiveChannel>) {
        val array = JSONArray()
        for (channel in channels) {
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

    companion object {
        private const val KEY = "favorite_channels"
    }
}
