package com.kdresdell.iptvtv

import android.os.Process
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

private const val RATINGS_URL = "https://omt.dresdell.com/ratings.json"
private const val MAX_AGE_MILLIS = 24L * 60 * 60 * 1000

// The daily background job behind What's Hot / What's New: downloads the
// lists the KD server builds every night (WhatsLists) and matches them to
// this catalog, keeps the provider's movie and series lists fresh (they used
// to be downloaded once and never updated) and downloads the IMDb scores. Runs on a
// low-priority background thread so it never competes with video playback,
// and every step fails on its own - a provider or server hiccup just means
// "try again later", never a broken screen.
class CatalogRefresher(private val api: XtreamApi, private val db: LiveChannelDatabase) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    // Returns true when anything new was stored.
    suspend fun refreshIfStale(): Boolean = withContext(Dispatchers.IO) {
        Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
        try {
            // What's Hot first: one small download, no provider call, so even
            // a slow box that gets interrupted has fresh lists.
            var changed = refreshWhatsIfStale()
            var catalogChanged = false
            if (db.isVodStale(MAX_AGE_MILLIS)) {
                catalogChanged = step("movies") { api.syncAllVodStreamsInto(db) } || catalogChanged
            }
            if (db.isSeriesStale(MAX_AGE_MILLIS)) {
                catalogChanged = step("series") { api.syncAllSeriesInto(db) } || catalogChanged
            }
            // A new catalog means new rows to match.
            if (catalogChanged) changed = step("What's Hot lists") { syncWhats() } || changed
            if (db.isRatingsStale(MAX_AGE_MILLIS)) {
                changed = step("IMDb scores") { downloadRatings() } || changed
            }
            changed || catalogChanged
        } finally {
            Process.setThreadPriority(Process.THREAD_PRIORITY_DEFAULT)
        }
    }

    // Called on launch (no 60 s wait) and by the daily job: downloads the
    // What's Hot / What's New lists when there are none yet or they are a day
    // old. True when new matches were stored.
    suspend fun refreshWhatsIfStale(): Boolean = withContext(Dispatchers.IO) {
        if (db.hasWhatsMatches() && !db.isWhatsStale(MAX_AGE_MILLIS)) return@withContext false
        step("What's Hot lists") { syncWhats() }
    }

    // Settings' "Refresh What's Hot now": only the lists, no provider call,
    // regardless of age. Failures are thrown so the screen can show them.
    suspend fun refreshWhatsNow() = withContext(Dispatchers.IO) { syncWhats() }

    private fun syncWhats() {
        try {
            val download = WhatsLists.download()
            val lists = download.lists
            val matches = WhatsLists.match(lists, "movie") { onRow -> db.forEachCatalogRow("movie", onRow) } +
                WhatsLists.match(lists, "tv") { onRow -> db.forEachCatalogRow("tv", onRow) }
            AppLog.log("What's Hot lists (built ${download.generated}): ${matches.size} titles found in your catalog")
            if (!db.replaceWhatsMatches(matches)) throw XtreamApiException("None of the What's Hot titles are in your catalog yet")
            db.recordWhatsSuccess(
                download.generated,
                lists.mapValues { it.value.size },
                matches.groupingBy { "${it.list}/${it.kind}/${it.lang}" }.eachCount()
            )
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            db.recordWhatsFailure(e.message ?: e.javaClass.simpleName)
            throw e
        }
    }

    // Settings' "Refresh list now": re-downloads everything regardless of
    // age, reporting each step plus a running item count (for
    // ListRefreshProgressCard). Unlike the daily job, a provider failure is
    // thrown so the screen can show it; the IMDb scores stay best-effort.
    suspend fun refreshAll(onProgress: (ListRefreshProgress) -> Unit) = withContext(Dispatchers.IO) {
        val total = 4
        fun report(step: Int, label: String, noun: String) = { count: Int ->
            onProgress(ListRefreshProgress(step, total, label, count, noun))
        }
        report(1, "Downloading live channels...", "channels")(0)
        api.syncAllLiveChannelsInto(db, report(1, "Downloading live channels...", "channels"))
        report(2, "Downloading movies...", "movies")(0)
        api.syncAllVodStreamsInto(db, report(2, "Downloading movies...", "movies"))
        report(3, "Downloading TV shows...", "shows")(0)
        api.syncAllSeriesInto(db, report(3, "Downloading TV shows...", "shows"))
        report(4, "Downloading What's Hot and IMDb scores...", "")(0)
        step("What's Hot lists") { syncWhats() }
        step("IMDb scores") { downloadRatings() }
    }

    private suspend fun step(label: String, block: suspend () -> Unit): Boolean = try {
        block()
        true
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Exception) {
        AppLog.log("Daily refresh ($label) failed: ${e.javaClass.simpleName}: ${e.message}")
        false
    }

    // ratings.json: {"generated": "...", "movie": {"<tmdb id>": [rating, votes, "fr"]}, "tv": {...}}
    private fun downloadRatings() {
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
