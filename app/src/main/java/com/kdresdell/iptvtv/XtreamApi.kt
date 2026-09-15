package com.kdresdell.iptvtv

import android.util.Base64
import android.util.JsonReader
import android.util.JsonToken
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

data class LiveCategory(val categoryId: String, val categoryName: String)
data class LiveChannel(
    val streamId: Int,
    val name: String,
    val categoryId: String,
    val streamIcon: String = ""
)

data class VodCategory(val categoryId: String, val categoryName: String)
data class VodStream(
    val streamId: Int,
    val name: String,
    val categoryId: String,
    val streamIcon: String = "",
    val containerExtension: String = "mp4"
)

// Xtream treats series (TV shows) as a third content type, separate from
// VOD movies - a series has no direct stream_id of its own, only episodes
// (fetched via getSeriesEpisodes), each with its own playable id.
data class SeriesShow(
    val seriesId: Int,
    val name: String,
    val categoryId: String,
    val cover: String = ""
)

data class SeriesEpisode(
    val episodeId: Int,
    val episodeNum: Int,
    val season: Int,
    val title: String,
    val containerExtension: String = "mp4",
    val description: String = ""
)

data class NowPlayingInfo(
    val title: String,
    val description: String,
    val startEpochSeconds: Long?,
    val stopEpochSeconds: Long?
)

class XtreamApiException(message: String) : Exception(message)

// Talks to the Xtream Codes player_api.php contract documented in
// docs/xtream-api.md. Auth is plain username/password query params, same
// as every other call to this API - nothing here should ever be logged.
class XtreamApi(private val credentials: ProviderCredentials) {
    private val client = OkHttpClient()

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")

    private fun normalizedBaseUrl(): String {
        val trimmed = credentials.serverUrl.trim().trimEnd('/')
        return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            trimmed
        } else {
            "http://$trimmed"
        }
    }

    private fun playerApiUrl(action: String, extraParams: String = ""): String {
        val base = normalizedBaseUrl()
        val user = encode(credentials.username)
        val pass = encode(credentials.password)
        return "$base/player_api.php?username=$user&password=$pass&action=$action$extraParams"
    }

    private suspend fun getJson(url: String): String = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw XtreamApiException("Server returned HTTP ${response.code}")
                }
                response.body?.string()?.takeIf { it.isNotBlank() }
                    ?: throw XtreamApiException("Empty response from server")
            }
        } catch (e: XtreamApiException) {
            throw e
        } catch (e: Exception) {
            throw XtreamApiException("Could not reach server: ${e.message}")
        }
    }

    suspend fun getLiveCategories(): List<LiveCategory> {
        val body = getJson(playerApiUrl("get_live_categories"))
        val array = parseArray(body, "Unexpected response listing categories - check server URL/credentials")
        return (0 until array.length()).map { i ->
            val obj = array.getJSONObject(i)
            LiveCategory(
                categoryId = obj.optString("category_id"),
                categoryName = obj.optString("category_name")
            )
        }
    }

    // Pass null to fetch every live channel across all categories (used by search).
    suspend fun getLiveStreams(categoryId: String? = null): List<LiveChannel> {
        val extra = categoryId?.let { "&category_id=${encode(it)}" } ?: ""
        val body = getJson(playerApiUrl("get_live_streams", extra))
        val array = parseArray(body, "Unexpected response listing channels")
        return (0 until array.length()).map { i ->
            val obj = array.getJSONObject(i)
            LiveChannel(
                streamId = obj.optInt("stream_id"),
                name = obj.optString("name"),
                categoryId = obj.optString("category_id"),
                streamIcon = obj.optString("stream_icon")
            )
        }
    }

    // Used to build the local search index. Streams the response body
    // token-by-token straight into SQLite instead of materializing the
    // whole (potentially huge, provider-dependent) catalog as one big
    // JSONArray/List<LiveChannel> in memory - that approach caused real
    // ANRs against a provider with a very large channel count.
    suspend fun syncAllLiveChannelsInto(
        db: LiveChannelDatabase,
        onProgress: (Int) -> Unit = {}
    ) = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(playerApiUrl("get_live_streams")).build()
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw XtreamApiException("Server returned HTTP ${response.code}")
                }
                val body = response.body ?: throw XtreamApiException("Empty response from server")
                body.charStream().use { reader ->
                    JsonReader(reader).use { json ->
                        val channels = sequence {
                            json.beginArray()
                            while (json.hasNext()) {
                                var streamId = 0
                                var name = ""
                                var categoryId = ""
                                var streamIcon = ""
                                json.beginObject()
                                while (json.hasNext()) {
                                    val fieldName = json.nextName()
                                    if (json.peek() == JsonToken.NULL) {
                                        json.skipValue()
                                        continue
                                    }
                                    when (fieldName) {
                                        "stream_id" -> streamId = json.nextString().toIntOrNull() ?: 0
                                        "name" -> name = json.nextString()
                                        "category_id" -> categoryId = json.nextString()
                                        "stream_icon" -> streamIcon = json.nextString()
                                        else -> json.skipValue()
                                    }
                                }
                                json.endObject()
                                yield(LiveChannel(streamId, name, categoryId, streamIcon))
                            }
                            json.endArray()
                        }
                        db.replaceAll(channels, onProgress)
                    }
                }
            }
        } catch (e: XtreamApiException) {
            throw e
        } catch (e: Exception) {
            throw XtreamApiException("Could not reach server: ${e.message}")
        }
    }

    suspend fun getVodCategories(): List<VodCategory> {
        val body = getJson(playerApiUrl("get_vod_categories"))
        val array = parseArray(body, "Unexpected response listing VOD categories")
        return (0 until array.length()).map { i ->
            val obj = array.getJSONObject(i)
            VodCategory(
                categoryId = obj.optString("category_id"),
                categoryName = obj.optString("category_name")
            )
        }
    }

    // Mirrors syncAllLiveChannelsInto - a real provider's VOD catalog can be
    // just as large as its live one, so this streams into SQLite the same way.
    suspend fun syncAllVodStreamsInto(
        db: LiveChannelDatabase,
        onProgress: (Int) -> Unit = {}
    ) = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(playerApiUrl("get_vod_streams")).build()
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw XtreamApiException("Server returned HTTP ${response.code}")
                }
                val body = response.body ?: throw XtreamApiException("Empty response from server")
                body.charStream().use { reader ->
                    JsonReader(reader).use { json ->
                        val streams = sequence {
                            json.beginArray()
                            while (json.hasNext()) {
                                var streamId = 0
                                var name = ""
                                var categoryId = ""
                                var streamIcon = ""
                                var containerExtension = "mp4"
                                json.beginObject()
                                while (json.hasNext()) {
                                    val fieldName = json.nextName()
                                    if (json.peek() == JsonToken.NULL) {
                                        json.skipValue()
                                        continue
                                    }
                                    when (fieldName) {
                                        "stream_id" -> streamId = json.nextString().toIntOrNull() ?: 0
                                        "name" -> name = json.nextString()
                                        "category_id" -> categoryId = json.nextString()
                                        "stream_icon" -> streamIcon = json.nextString()
                                        "container_extension" -> containerExtension = json.nextString()
                                        else -> json.skipValue()
                                    }
                                }
                                json.endObject()
                                yield(VodStream(streamId, name, categoryId, streamIcon, containerExtension))
                            }
                            json.endArray()
                        }
                        db.replaceAllVod(streams, onProgress)
                    }
                }
            }
        } catch (e: XtreamApiException) {
            throw e
        } catch (e: Exception) {
            throw XtreamApiException("Could not reach server: ${e.message}")
        }
    }

    // Mirrors syncAllVodStreamsInto for the series catalog. Only the show
    // list is synced/searchable here - episodes are fetched per-series
    // on demand via getSeriesEpisodes, not indexed up front.
    suspend fun syncAllSeriesInto(
        db: LiveChannelDatabase,
        onProgress: (Int) -> Unit = {}
    ) = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(playerApiUrl("get_series")).build()
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw XtreamApiException("Server returned HTTP ${response.code}")
                }
                val body = response.body ?: throw XtreamApiException("Empty response from server")
                body.charStream().use { reader ->
                    JsonReader(reader).use { json ->
                        val seriesSeq = sequence {
                            json.beginArray()
                            while (json.hasNext()) {
                                var seriesId = 0
                                var name = ""
                                var categoryId = ""
                                var cover = ""
                                json.beginObject()
                                while (json.hasNext()) {
                                    val fieldName = json.nextName()
                                    if (json.peek() == JsonToken.NULL) {
                                        json.skipValue()
                                        continue
                                    }
                                    when (fieldName) {
                                        "series_id" -> seriesId = json.nextString().toIntOrNull() ?: 0
                                        "name" -> name = json.nextString()
                                        "category_id" -> categoryId = json.nextString()
                                        "cover" -> cover = json.nextString()
                                        else -> json.skipValue()
                                    }
                                }
                                json.endObject()
                                yield(SeriesShow(seriesId, name, categoryId, cover))
                            }
                            json.endArray()
                        }
                        db.replaceAllSeries(seriesSeq, onProgress)
                    }
                }
            }
        } catch (e: XtreamApiException) {
            throw e
        } catch (e: Exception) {
            throw XtreamApiException("Could not reach server: ${e.message}")
        }
    }

    // A single series' episodes, grouped by season in the response
    // ("episodes": {"1": [...], "2": [...]}) - small enough per-series to
    // parse as one JSONObject, unlike the full catalogs above.
    suspend fun getSeriesEpisodes(seriesId: Int): List<SeriesEpisode> {
        val body = getJson(playerApiUrl("get_series_info", "&series_id=$seriesId"))
        val root = try {
            JSONObject(body)
        } catch (e: Exception) {
            throw XtreamApiException("Unexpected response loading episodes")
        }
        val episodesBySeason = root.optJSONObject("episodes") ?: return emptyList()
        val episodes = mutableListOf<SeriesEpisode>()
        val seasonKeys = episodesBySeason.keys()
        while (seasonKeys.hasNext()) {
            val seasonKey = seasonKeys.next()
            val seasonNumber = seasonKey.toIntOrNull() ?: 0
            val array = episodesBySeason.optJSONArray(seasonKey) ?: continue
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                // Per-episode synopsis lives in a nested "info" object on
                // most providers, occasionally at the top level - check both.
                val info = obj.optJSONObject("info")
                val description = info?.optString("plot")?.takeIf { it.isNotBlank() }
                    ?: obj.optString("plot").takeIf { it.isNotBlank() }
                    ?: ""
                episodes.add(
                    SeriesEpisode(
                        episodeId = obj.optString("id").toIntOrNull() ?: 0,
                        episodeNum = obj.optInt("episode_num"),
                        season = seasonNumber,
                        title = obj.optString("title"),
                        containerExtension = obj.optString("container_extension").ifBlank { "mp4" },
                        description = description
                    )
                )
            }
        }
        return episodes.sortedWith(compareBy({ it.season }, { it.episodeNum }))
    }

    // Movie synopsis - not included in get_vod_streams, needs its own call.
    // Only invoked when a movie is actually opened in the player, never for
    // the whole catalog.
    suspend fun getVodDescription(streamId: Int): String {
        return try {
            val body = getJson(playerApiUrl("get_vod_info", "&vod_id=$streamId"))
            val info = JSONObject(body).optJSONObject("info")
            info?.optString("plot")?.takeIf { it.isNotBlank() } ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    // Deliberately only called for a small shortlist (favorites), never
    // the whole catalog - unlike get_live_streams, there's no cheap way
    // to batch this across many channels, so it must stay opt-in per
    // channel. Some providers base64-encode the title field.
    suspend fun getNowPlayingInfo(streamId: Int): NowPlayingInfo? {
        val body = getJson(playerApiUrl("get_short_epg", "&stream_id=$streamId&limit=1"))
        return try {
            val listings = JSONObject(body).optJSONArray("epg_listings") ?: return null
            if (listings.length() == 0) return null
            val entry = listings.getJSONObject(0)
            val title = decodeIfBase64(entry.optString("title")).takeIf { it.isNotBlank() } ?: return null
            NowPlayingInfo(
                title = title,
                description = decodeIfBase64(entry.optString("description")),
                startEpochSeconds = entry.optString("start_timestamp").toLongOrNull(),
                stopEpochSeconds = entry.optString("stop_timestamp").toLongOrNull()
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun decodeIfBase64(value: String): String {
        return try {
            val decoded = String(Base64.decode(value, Base64.DEFAULT), Charsets.UTF_8)
            if (decoded.isNotBlank() && decoded.none { it.code < 32 && it != '\n' }) decoded else value
        } catch (e: Exception) {
            value
        }
    }

    // {server}/live/{username}/{password}/{stream_id}.ts - path-based, no API call needed.
    fun liveStreamUrl(streamId: Int): String {
        val base = normalizedBaseUrl()
        val user = encode(credentials.username)
        val pass = encode(credentials.password)
        return "$base/live/$user/$pass/$streamId.ts"
    }

    // {server}/movie/{username}/{password}/{stream_id}.{container_extension} - path-based, no API call needed.
    fun vodStreamUrl(streamId: Int, containerExtension: String): String {
        val base = normalizedBaseUrl()
        val user = encode(credentials.username)
        val pass = encode(credentials.password)
        val ext = containerExtension.ifBlank { "mp4" }
        return "$base/movie/$user/$pass/$streamId.$ext"
    }

    // {server}/series/{username}/{password}/{episode_id}.{container_extension} - path-based, no API call needed.
    fun seriesEpisodeUrl(episodeId: Int, containerExtension: String): String {
        val base = normalizedBaseUrl()
        val user = encode(credentials.username)
        val pass = encode(credentials.password)
        val ext = containerExtension.ifBlank { "mp4" }
        return "$base/series/$user/$pass/$episodeId.$ext"
    }

    private fun parseArray(body: String, errorMessage: String): JSONArray =
        try {
            JSONArray(body)
        } catch (e: Exception) {
            throw XtreamApiException(errorMessage)
        }
}
