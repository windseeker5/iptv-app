package com.kdresdell.iptvtv.phone

// TODO: duplicated from the TV app's CatalogRefresher.downloadRatings.

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

private const val RATINGS_URL = "https://omt.dresdell.com/ratings.json"

// Downloads the IMDb score file the KD server builds every night (the one
// What's New ranks titles by) into the catalog DB. Best-effort: callers
// treat a failure as "keep the scores we have".
class RatingsDownloader(private val db: CatalogDatabase) {
    private val client = OkHttpClient()

    // ratings.json: {"generated": "...", "movie": {"<tmdb id>": [rating, votes, "fr"]}, "tv": {...}}
    suspend fun download() = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(RATINGS_URL).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw XtreamApiException("Ratings server returned HTTP ${response.code}")
            val body = response.body?.string()?.takeIf { it.isNotBlank() }
                ?: throw XtreamApiException("Empty ratings file")
            val json = JSONObject(body)
            val movies = parseScores(json.optJSONObject("movie"))
            val series = parseScores(json.optJSONObject("tv"))
            if (!db.replaceImdbRatings(movies, series)) throw XtreamApiException("Ratings file had no scores")
        }
    }

    // What's Hot / What's New lists, matched to this phone's catalog.
    class WhatsResult(val generated: String, val total: Map<String, Int>, val matched: Map<String, Int>)

    suspend fun syncWhats(): WhatsResult = withContext(Dispatchers.IO) {
        val download = WhatsLists.download()
        val lists = download.lists
        val matches = WhatsLists.match(lists, "movie") { onRow -> db.forEachCatalogRow("movie", onRow) } +
            WhatsLists.match(lists, "tv") { onRow -> db.forEachCatalogRow("tv", onRow) }
        if (!db.replaceWhatsMatches(matches)) throw XtreamApiException("None of the What's Hot titles are in your catalog yet")
        WhatsResult(
            download.generated,
            lists.mapValues { it.value.size },
            matches.groupingBy { "${it.list}/${it.kind}/${it.lang}" }.eachCount()
        )
    }

    private fun parseScores(obj: JSONObject?): Map<Int, ImdbScore> {
        val out = HashMap<Int, ImdbScore>()
        if (obj == null) return out
        val keys = obj.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val id = key.toIntOrNull() ?: continue
            val pair = obj.optJSONArray(key) ?: continue
            out[id] = ImdbScore(pair.optDouble(0), pair.optInt(1), pair.optString(2, ""))
        }
        return out
    }
}
