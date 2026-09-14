package com.kdresdell.iptvtv

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

    suspend fun getLiveStreams(categoryId: String): List<LiveChannel> {
        val body = getJson(playerApiUrl("get_live_streams", "&category_id=${encode(categoryId)}"))
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

    private fun parseArray(body: String, errorMessage: String): JSONArray =
        try {
            JSONArray(body)
        } catch (e: Exception) {
            throw XtreamApiException(errorMessage)
        }
}
