package com.kdresdell.iptvtv.phone

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.text.Normalizer
import java.util.concurrent.TimeUnit

private const val WHATS_URL = "https://omt.dresdell.com/whats.json"

// TODO: duplicated from the TV app (app/.../WhatsLists.kt) - keep the two in sync.
//
// What's Hot / What's New, as lists built every night on the KD server
// (OMT/ratings_job/whats.py) and published at /whats.json:
//   Hot = what people watch this week, any year.
//   New = released this year, best IMDb score first.
// Each in English and French (original language) for movies and series.
//
// The lists come from TMDB, not from any provider, because everyone brings
// their own provider. This file downloads them and matches every entry
// against THIS device's catalog - by TMDB id when the catalog has one, else
// by title + year - so it works right after a normal catalog sync, without
// the long background re-download the TV app used to wait for (which slow
// Google TV boxes often never finished).
object WhatsLists {
    val LISTS = listOf("hot", "new")
    val KINDS = listOf("movie", "tv")
    val LANGS = listOf("en", "fr")

    data class Item(
        val rank: Int,
        val tmdb: Int,
        val titleKey: String,
        val originalKey: String,
        val year: Int,
        val rating: Double,
        val votes: Int
    )

    // One matched catalog row, ready for CatalogDatabase.replaceWhatsMatches.
    data class Match(
        val list: String,
        val kind: String,
        val lang: String,
        val rank: Int,
        val itemId: Int,
        val rating: Double,
        val votes: Int
    )

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    // {"hot": {"movie": {"en": [item, ...]}}} -> map keyed "hot/movie/en".
    fun download(): Map<String, List<Item>> {
        val request = Request.Builder().url(WHATS_URL).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw XtreamApiException("What's Hot server returned HTTP ${response.code}")
            val body = response.body?.string()?.takeIf { it.isNotBlank() }
                ?: throw XtreamApiException("Empty What's Hot file")
            return parse(JSONObject(body))
        }
    }

    fun parse(json: JSONObject): Map<String, List<Item>> {
        val out = HashMap<String, List<Item>>()
        for (list in LISTS) for (kind in KINDS) for (lang in LANGS) {
            val array = json.optJSONObject(list)?.optJSONObject(kind)?.optJSONArray(lang) ?: continue
            out["$list/$kind/$lang"] = (0 until array.length()).mapNotNull { i ->
                val o = array.optJSONObject(i) ?: return@mapNotNull null
                Item(
                    rank = i,
                    tmdb = o.optInt("tmdb"),
                    titleKey = titleKey(o.optString("title")),
                    originalKey = titleKey(o.optString("original_title")),
                    year = o.optInt("year"),
                    rating = if (o.isNull("rating")) 0.0 else o.optDouble("rating", 0.0),
                    votes = o.optInt("votes")
                )
            }
        }
        return out
    }

    // Walks one catalog table ("movie" = vod_streams, "tv" = series) once and
    // returns every row that is on one of the lists. A title the provider has
    // more than once (HD / 4K copies) is matched once, to its first row.
    fun match(lists: Map<String, List<Item>>, kind: String, catalog: (onRow: (id: Int, name: String, tmdb: Int) -> Unit) -> Unit): List<Match> {
        class Index(val key: String, val items: List<Item>) {
            val byTmdb = items.filter { it.tmdb > 0 }.associateBy { it.tmdb }
            val byTitle = HashMap<String, Item>().also { map ->
                for (item in items) {
                    for (title in listOf(item.titleKey, item.originalKey)) {
                        if (title.isNotEmpty()) map.putIfAbsent("$title|${item.year}", item)
                    }
                }
            }
            val taken = HashSet<Int>()
        }
        val indexes = LANGS.associateWith { lang ->
            LISTS.mapNotNull { list -> lists["$list/$kind/$lang"]?.let { Index("$list/$kind/$lang", it) } }
        }
        val matches = mutableListOf<Match>()
        catalog { id, name, tmdb ->
            val lang = languageOf(name) ?: return@catalog
            val key = titleKey(name)
            val year = releaseYearFromName(name)
            for (index in indexes[lang].orEmpty()) {
                // Release years differ by a year between countries, so allow one either way.
                val item = (if (tmdb > 0) index.byTmdb[tmdb] else null)
                    ?: (if (year > 0) listOf(year, year - 1, year + 1).firstNotNullOfOrNull { index.byTitle["$key|$it"] } else null)
                    ?: continue
                if (!index.taken.add(item.rank)) continue
                val (list, _, _) = index.key.split("/")
                matches.add(Match(list, kind, lang, item.rank, id, item.rating, item.votes))
            }
        }
        return matches
    }

    // "EN - Moana" -> en, "FR - Dune" / "QC - Loups-Garous" -> fr. Same
    // case-sensitive rule as WhatsNewLanguage, so "Frozen" is never French.
    private fun languageOf(name: String): String? = when {
        EN_PREFIX.containsMatchIn(name) -> "en"
        FR_PREFIX.containsMatchIn(name) -> "fr"
        else -> null
    }

    private val EN_PREFIX = Regex("^EN[ :.\\-]")
    private val FR_PREFIX = Regex("^(?:FR|QC)[ :.\\-]")
    private val PREFIX = Regex("^(?i:EN|FR|QC)\\s*[ :.\\-|]+\\s*")
    private val YEAR = Regex("\\((?:19|20)\\d\\d\\)")
    private val MARKS = Regex("\\p{Mn}+")
    private val NON_ASCII = Regex("[^\\x00-\\x7F]")
    private val NON_ALNUM = Regex("[^a-z0-9]+")

    // Must stay identical to norm_title() in OMT/ratings_job/whats.py:
    // "FR - Le Comte de Monte-Cristo (2024) VOSTFR" -> "le comte de monte cristo".
    fun titleKey(raw: String): String {
        var s = PREFIX.replace(raw, "")
        YEAR.find(s)?.let { s = s.substring(0, it.range.first) }
        s = Normalizer.normalize(s, Normalizer.Form.NFKD)
        s = MARKS.replace(s, "")
        s = NON_ASCII.replace(s, "")
        return NON_ALNUM.replace(s.lowercase(), " ").trim()
    }
}
