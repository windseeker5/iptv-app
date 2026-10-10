package com.kdresdell.iptvtv.phone

// TODO: duplicated from the TV app's XtreamApi.kt, trimmed for the phone
// draft. Extract a shared :core module later instead of
// maintaining two copies.

import android.util.JsonReader
import android.util.JsonToken
import android.util.Xml
import java.io.InputStream
import java.net.URLEncoder
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser

data class LiveCategory(val categoryId: String, val categoryName: String)
data class LiveChannel(
    val streamId: Int,
    val name: String,
    val categoryId: String,
    val streamIcon: String = "",
    // Links the channel to its xmltv.php guide entries; blank = no guide.
    val epgChannelId: String = ""
)

data class VodStream(
    val streamId: Int,
    val name: String,
    val categoryId: String,
    val streamIcon: String = "",
    val containerExtension: String = "mp4",
    // What's New only, same as the TV app: releaseYear is parsed from the
    // "(2026)" in the name (0 = none), tmdb is the key into ratings.json,
    // imdbRating is only filled in by CatalogDatabase.topMovies.
    val releaseYear: Int = 0,
    val tmdb: Int = 0,
    val imdbRating: Double = 0.0
)

data class SeriesShow(
    val seriesId: Int,
    val name: String,
    val categoryId: String,
    val cover: String = "",
    val releaseYear: Int = 0,
    val tmdb: Int = 0,
    val imdbRating: Double = 0.0
)

private val YEAR_IN_NAME = Regex("\\((?:19|20)\\d\\d\\)")

// The provider puts the release year in the title, "EN - Moana (2026)".
internal fun releaseYearFromName(name: String): Int =
    YEAR_IN_NAME.find(name)?.value?.substring(1, 5)?.toIntOrNull() ?: 0

data class SeriesEpisode(
    val episodeId: Int,
    val episodeNum: Int,
    val season: Int,
    val title: String,
    val containerExtension: String = "mp4"
)

// Same as the TV app's EpgProgram - one guide entry, times in epoch seconds.
data class EpgProgram(
    val title: String,
    val description: String,
    val startEpochSeconds: Long,
    val stopEpochSeconds: Long
)

// Same as the TV app's AccountInfo - both nullable so "unknown" (e.g. a
// lifetime account with no exp_date) stays distinct from 0.
data class AccountInfo(
    val createdAtEpochSeconds: Long?,
    val expDateEpochSeconds: Long?
)

class XtreamApiException(message: String) : Exception(message)

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

    // Bare player_api.php call (no "action") -> { "user_info": {...} }.
    // Display-only, so any failure just returns nulls rather than failing
    // the catalog sync.
    suspend fun getAccountInfo(): AccountInfo {
        val base = normalizedBaseUrl()
        val user = encode(credentials.username)
        val pass = encode(credentials.password)
        return try {
            val body = getJson("$base/player_api.php?username=$user&password=$pass")
            val userInfo = JSONObject(body).optJSONObject("user_info")
            AccountInfo(
                createdAtEpochSeconds = userInfo?.optString("created_at")?.toLongOrNull(),
                expDateEpochSeconds = userInfo?.optString("exp_date")?.toLongOrNull()
            )
        } catch (e: Exception) {
            AccountInfo(null, null)
        }
    }

    suspend fun getLiveCategories(): List<LiveCategory> =
        getCategories("get_live_categories", "Unexpected response listing categories - check server URL/credentials")

    suspend fun getVodCategories(): List<LiveCategory> =
        getCategories("get_vod_categories", "Unexpected response listing movie categories")

    suspend fun getSeriesCategories(): List<LiveCategory> =
        getCategories("get_series_categories", "Unexpected response listing series categories")

    private suspend fun getCategories(action: String, errorMessage: String): List<LiveCategory> {
        val body = getJson(playerApiUrl(action))
        val array = parseArray(body, errorMessage)
        return (0 until array.length()).map { i ->
            val obj = array.getJSONObject(i)
            LiveCategory(
                categoryId = obj.optString("category_id"),
                categoryName = obj.optString("category_name")
            )
        }
    }

    // The three catalogs are streamed straight into [consume] one item at a
    // time instead of parsing the whole response - a real provider's movie
    // list is big enough that holding the JSON text, a JSONArray and the
    // parsed list at once ran the phone out of memory (2026-09-27).
    suspend fun streamLiveStreams(consume: (Sequence<LiveChannel>) -> Unit) =
        streamArray("get_live_streams", setOf("stream_id", "name", "category_id", "stream_icon", "epg_channel_id")) { rows ->
            consume(rows.map { f ->
                LiveChannel(
                    streamId = f["stream_id"]?.toIntOrNull() ?: 0,
                    name = f["name"] ?: "",
                    categoryId = f["category_id"] ?: "",
                    streamIcon = f["stream_icon"] ?: "",
                    epgChannelId = f["epg_channel_id"] ?: ""
                )
            })
        }

    suspend fun streamVodStreams(consume: (Sequence<VodStream>) -> Unit) =
        streamArray("get_vod_streams", setOf("stream_id", "name", "category_id", "stream_icon", "container_extension", "tmdb")) { rows ->
            consume(rows.map { f ->
                val name = f["name"] ?: ""
                VodStream(
                    streamId = f["stream_id"]?.toIntOrNull() ?: 0,
                    name = name,
                    categoryId = f["category_id"] ?: "",
                    streamIcon = f["stream_icon"] ?: "",
                    containerExtension = f["container_extension"]?.ifBlank { null } ?: "mp4",
                    releaseYear = releaseYearFromName(name),
                    tmdb = f["tmdb"]?.toIntOrNull() ?: 0
                )
            })
        }

    suspend fun streamSeriesList(consume: (Sequence<SeriesShow>) -> Unit) =
        streamArray("get_series", setOf("series_id", "name", "category_id", "cover", "tmdb")) { rows ->
            consume(rows.map { f ->
                val name = f["name"] ?: ""
                SeriesShow(
                    seriesId = f["series_id"]?.toIntOrNull() ?: 0,
                    name = name,
                    categoryId = f["category_id"] ?: "",
                    cover = f["cover"] ?: "",
                    releaseYear = releaseYearFromName(name),
                    tmdb = f["tmdb"]?.toIntOrNull() ?: 0
                )
            })
        }

    // Reads a top-level JSON array of objects, handing each one over as a
    // small map of just the [wanted] fields (nulls skipped). Numbers come
    // back as strings, so "stream_id": 5 and "stream_id": "5" both work.
    private suspend fun streamArray(
        action: String,
        wanted: Set<String>,
        consume: (Sequence<Map<String, String>>) -> Unit
    ) = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(playerApiUrl(action)).build()
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw XtreamApiException("Server returned HTTP ${response.code}")
                val body = response.body ?: throw XtreamApiException("Empty response from server")
                JsonReader(body.charStream()).use { json ->
                    consume(sequence {
                        json.beginArray()
                        while (json.hasNext()) {
                            val fields = HashMap<String, String>()
                            json.beginObject()
                            while (json.hasNext()) {
                                val field = json.nextName()
                                if (field in wanted && json.peek() != JsonToken.NULL) {
                                    fields[field] = json.nextString()
                                } else {
                                    json.skipValue()
                                }
                            }
                            json.endObject()
                            yield(fields)
                        }
                        json.endArray()
                    })
                }
            }
        } catch (e: XtreamApiException) {
            throw e
        } catch (e: Exception) {
            throw XtreamApiException("Could not reach server: ${e.message}")
        }
    }

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
                episodes.add(
                    SeriesEpisode(
                        episodeId = obj.optString("id").toIntOrNull() ?: 0,
                        episodeNum = obj.optInt("episode_num"),
                        season = seasonNumber,
                        title = obj.optString("title"),
                        containerExtension = obj.optString("container_extension").ifBlank { "mp4" }
                    )
                )
            }
        }
        return episodes.sortedWith(compareBy({ it.season }, { it.episodeNum }))
    }

    // Movie synopsis - not part of get_vod_streams, so it's fetched per
    // movie only when shown in My Library. "" = provider has no synopsis;
    // null = the call failed (so the caller doesn't cache a blank).
    suspend fun getVodDescription(streamId: Int): String? =
        fetchPlot(playerApiUrl("get_vod_info", "&vod_id=$streamId"))

    // Series synopsis - get_series_info's root-level "info" plot.
    suspend fun getSeriesDescription(seriesId: Int): String? =
        fetchPlot(playerApiUrl("get_series_info", "&series_id=$seriesId"))

    private suspend fun fetchPlot(url: String): String? =
        try {
            val body = getJson(url)
            JSONObject(body).optJSONObject("info")?.optString("plot")?.trim() ?: ""
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }

    // Ported from the TV app. get_short_epg / get_simple_data_table return
    // times +2h late on this provider (see docs/xtream-api.md), so the guide
    // comes from xmltv.php - one big file (~13MB gzipped) covering every
    // channel, stream-parsed keeping only the wanted epg_channel_ids.
    suspend fun fetchXmltvPrograms(wantedEpgIds: Set<String>): Map<String, List<EpgProgram>> =
        withContext(Dispatchers.IO) {
            val context = currentCoroutineContext()
            try {
                val request = Request.Builder()
                    .url("${normalizedBaseUrl()}/xmltv.php?username=${encode(credentials.username)}&password=${encode(credentials.password)}")
                    .build()
                // The server can go quiet while it builds the file - the
                // default 10s read timeout would kill it.
                val xmltvClient = client.newBuilder().readTimeout(2, TimeUnit.MINUTES).build()
                xmltvClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw XtreamApiException("Server returned HTTP ${response.code}")
                    }
                    val body = response.body ?: throw XtreamApiException("Empty response from server")
                    parseXmltv(body.byteStream(), wantedEpgIds) { context.ensureActive() }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: XtreamApiException) {
                throw e
            } catch (e: Exception) {
                throw XtreamApiException("Could not load guide: ${e.javaClass.simpleName}: ${e.message}")
            }
        }

    private fun parseXmltv(
        input: InputStream,
        wantedEpgIds: Set<String>,
        checkCancelled: () -> Unit
    ): Map<String, List<EpgProgram>> {
        val parser = Xml.newPullParser()
        parser.setInput(input, null)
        val timeFormat = SimpleDateFormat("yyyyMMddHHmmss Z", Locale.US)
        val programs = HashMap<String, MutableList<EpgProgram>>()
        var seen = 0
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && parser.name == "programme") {
                if ((++seen and 0x3FF) == 0) checkCancelled()
                val channel = parser.getAttributeValue(null, "channel")
                if (channel != null && channel in wantedEpgIds) {
                    val start = parseXmltvTime(parser.getAttributeValue(null, "start"), timeFormat)
                    val stop = parseXmltvTime(parser.getAttributeValue(null, "stop"), timeFormat)
                    var title = ""
                    var description = ""
                    val depth = parser.depth
                    while (true) {
                        val inner = parser.next()
                        if (inner == XmlPullParser.END_DOCUMENT) break
                        if (inner == XmlPullParser.END_TAG && parser.depth == depth) break
                        if (inner == XmlPullParser.START_TAG) {
                            when (parser.name) {
                                "title" -> parser.nextText().let { if (title.isEmpty()) title = it }
                                "desc" -> parser.nextText().let { if (description.isEmpty()) description = it }
                            }
                        }
                    }
                    if (start != null && stop != null && title.isNotBlank()) {
                        programs.getOrPut(channel) { mutableListOf() }.add(EpgProgram(title, description, start, stop))
                    }
                }
            }
            event = parser.next()
        }
        return programs.mapValues { (_, list) -> list.sortedBy { it.startEpochSeconds } }
    }

    // "20260918235900 +0200" -> epoch seconds. No offset means UTC per the
    // XMLTV spec.
    private fun parseXmltvTime(value: String?, format: SimpleDateFormat): Long? {
        if (value.isNullOrBlank()) return null
        val withOffset = if (value.contains(' ')) value else "$value +0000"
        return try {
            format.parse(withOffset)?.time?.div(1000)
        } catch (e: ParseException) {
            null
        }
    }

    // {server}/live/{username}/{password}/{stream_id}.ts - path-based, no API call needed.
    fun liveStreamUrl(streamId: Int): String {
        val base = normalizedBaseUrl()
        val user = encode(credentials.username)
        val pass = encode(credentials.password)
        return "$base/live/$user/$pass/$streamId.ts"
    }

    // The phone plays the raw .ts stream, but Chromecast's default receiver
    // can't play MPEG-TS over plain HTTP - it needs HLS, which Xtream serves
    // for the same channel at .m3u8.
    fun liveNowPlaying(channel: LiveChannel): NowPlaying {
        val tsUrl = liveStreamUrl(channel.streamId)
        return NowPlaying(
            title = channel.name,
            streamUrl = tsUrl,
            castUrl = tsUrl.removeSuffix(".ts") + ".m3u8",
            castMimeType = "application/x-mpegURL",
            isLive = true,
            kind = CatalogKind.LIVE,
            itemId = channel.streamId,
            posterUrl = channel.streamIcon,
            categoryId = channel.categoryId
        )
    }

    fun vodNowPlaying(movie: VodStream): NowPlaying {
        val url = vodStreamUrl(movie.streamId, movie.containerExtension)
        return NowPlaying(
            title = movie.name,
            streamUrl = url,
            castMimeType = mimeForExtension(movie.containerExtension),
            kind = CatalogKind.VOD,
            itemId = movie.streamId,
            posterUrl = movie.streamIcon,
            categoryId = movie.categoryId
        )
    }

    fun episodeNowPlaying(series: SeriesShow, episode: SeriesEpisode): NowPlaying {
        val url = seriesEpisodeUrl(episode.episodeId, episode.containerExtension)
        return NowPlaying(
            title = "${series.name} S${episode.season}E${episode.episodeNum}",
            streamUrl = url,
            castMimeType = mimeForExtension(episode.containerExtension),
            kind = CatalogKind.SERIES,
            itemId = series.seriesId,
            posterUrl = series.cover,
            categoryId = series.categoryId
        )
    }

    private fun mimeForExtension(ext: String): String = when (ext.lowercase()) {
        "mkv" -> "video/x-matroska"
        "webm" -> "video/webm"
        "m3u8" -> "application/x-mpegURL"
        "ts" -> "video/mp2t"
        else -> "video/mp4"
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
