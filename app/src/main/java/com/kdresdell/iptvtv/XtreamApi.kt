package com.kdresdell.iptvtv

import android.util.JsonReader
import android.util.JsonToken
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray

data class LiveCategory(val categoryId: String, val categoryName: String)
data class LiveChannel(val streamId: Int, val name: String, val categoryId: String)

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
                categoryId = obj.optString("category_id")
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
                                        else -> json.skipValue()
                                    }
                                }
                                json.endObject()
                                yield(LiveChannel(streamId, name, categoryId))
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

    // {server}/live/{username}/{password}/{stream_id}.ts - path-based, no API call needed.
    fun liveStreamUrl(streamId: Int): String {
        val base = normalizedBaseUrl()
        val user = encode(credentials.username)
        val pass = encode(credentials.password)
        return "$base/live/$user/$pass/$streamId.ts"
    }

    private fun parseArray(body: String, errorMessage: String): JSONArray =
        try {
            JSONArray(body)
        } catch (e: Exception) {
            throw XtreamApiException(errorMessage)
        }
}
