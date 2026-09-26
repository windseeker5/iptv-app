package com.kdresdell.iptvtv.phone

// TODO: duplicated/trimmed from the TV app's VodFavoritesStore.kt.

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

// "My library" - saved movies & series.
class VodFavoritesStore(context: Context) {
    private val prefs = context.getSharedPreferences("vod_favorites", Context.MODE_PRIVATE)

    fun loadMovies(): List<VodStream> {
        val json = prefs.getString(KEY_MOVIES, null) ?: return emptyList()
        return try {
            val array = JSONArray(json)
            (0 until array.length()).map { i ->
                val obj = array.getJSONObject(i)
                VodStream(
                    streamId = obj.getInt("stream_id"),
                    name = obj.getString("name"),
                    categoryId = obj.getString("category_id"),
                    streamIcon = obj.optString("stream_icon"),
                    containerExtension = obj.optString("container_extension").ifBlank { "mp4" }
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun loadSeries(): List<SeriesShow> {
        val json = prefs.getString(KEY_SERIES, null) ?: return emptyList()
        return try {
            val array = JSONArray(json)
            (0 until array.length()).map { i ->
                val obj = array.getJSONObject(i)
                SeriesShow(
                    seriesId = obj.getInt("series_id"),
                    name = obj.getString("name"),
                    categoryId = obj.getString("category_id"),
                    cover = obj.optString("cover")
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun toggleMovie(movie: VodStream, current: List<VodStream>): List<VodStream> {
        val updated = if (current.any { it.streamId == movie.streamId }) {
            current.filterNot { it.streamId == movie.streamId }
        } else {
            current + movie
        }
        saveMovies(updated)
        return updated
    }

    fun toggleSeries(series: SeriesShow, current: List<SeriesShow>): List<SeriesShow> {
        val updated = if (current.any { it.seriesId == series.seriesId }) {
            current.filterNot { it.seriesId == series.seriesId }
        } else {
            current + series
        }
        saveSeries(updated)
        return updated
    }

    private fun saveMovies(movies: List<VodStream>) {
        val array = JSONArray()
        for (movie in movies) {
            array.put(
                JSONObject().apply {
                    put("stream_id", movie.streamId)
                    put("name", movie.name)
                    put("category_id", movie.categoryId)
                    put("stream_icon", movie.streamIcon)
                    put("container_extension", movie.containerExtension)
                }
            )
        }
        prefs.edit().putString(KEY_MOVIES, array.toString()).apply()
    }

    private fun saveSeries(series: List<SeriesShow>) {
        val array = JSONArray()
        for (show in series) {
            array.put(
                JSONObject().apply {
                    put("series_id", show.seriesId)
                    put("name", show.name)
                    put("category_id", show.categoryId)
                    put("cover", show.cover)
                }
            )
        }
        prefs.edit().putString(KEY_SERIES, array.toString()).apply()
    }

    companion object {
        private const val KEY_MOVIES = "saved_movies"
        private const val KEY_SERIES = "saved_series"
    }
}
