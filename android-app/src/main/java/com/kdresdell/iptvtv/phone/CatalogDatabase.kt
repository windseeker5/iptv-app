package com.kdresdell.iptvtv.phone

// TODO: duplicated/trimmed from the TV app's LiveChannelDatabase.kt - the
// 3 catalog tables + search, plus category names, a description cache, a
// guide cache for the My TV / My Library rows, and the IMDb scores behind
// What's New.

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.database.sqlite.SQLiteStatement
import java.text.Normalizer

sealed class SearchResult {
    data class Live(val channel: LiveChannel) : SearchResult()
    data class Vod(val movie: VodStream) : SearchResult()
    data class Series(val series: SeriesShow) : SearchResult()
}

// Strips accents and lowercases so "MONTREAL" matches "montréal" - same
// approach as the TV app's search (NFD decompose, drop combining marks).
fun normalizeForSearch(value: String): String {
    val decomposed = Normalizer.normalize(value, Normalizer.Form.NFD)
    return decomposed.replace(Regex("\\p{Mn}+"), "").lowercase()
}

// Which catalog a category/description row belongs to - Xtream ids are
// only unique within one catalog.
enum class CatalogKind(val key: String) { LIVE("live"), VOD("vod"), SERIES("series") }

// One row of the All screen's category list.
data class CategoryCount(val categoryId: String, val name: String, val count: Int)

// One IMDb score from the server's ratings.json.
data class ImdbScore(val rating: Double, val votes: Int, val language: String)

// Same as the TV app: What's New's two languages, as SQL on the provider's
// language-prefixed names ("EN - Moana", "FR - Dune", "QC - Loups-Garous")
// plus the title's ORIGINAL language from TMDB, which keeps French dubs of
// English titles out of the French rows. GLOB is case-sensitive so "Frozen"
// is never mistaken for "FR".
enum class WhatsNewLanguage(val originalLanguage: String) {
    English("en"), French("fr");

    fun nameFilter(column: String): String = when (this) {
        English -> "$column GLOB 'EN[ :.-]*'"
        French -> "($column GLOB 'FR[ :.-]*' OR $column GLOB 'QC[ :.-]*')"
    }
}

// Same vote floor as the TV app - a score from a handful of people isn't a
// recommendation, but French titles get few votes.
private const val MIN_IMDB_VOTES = 25

class CatalogDatabase(context: Context) : SQLiteOpenHelper(context, "catalog.db", null, 3) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE live_channels (stream_id INTEGER PRIMARY KEY, name TEXT, category_id TEXT, stream_icon TEXT, epg_channel_id TEXT, name_normalized TEXT)"
        )
        db.execSQL(
            "CREATE TABLE vod_streams (stream_id INTEGER PRIMARY KEY, name TEXT, category_id TEXT, stream_icon TEXT, container_extension TEXT, name_normalized TEXT, release_year INTEGER NOT NULL DEFAULT 0, tmdb INTEGER NOT NULL DEFAULT 0)"
        )
        db.execSQL(
            "CREATE TABLE series (series_id INTEGER PRIMARY KEY, name TEXT, category_id TEXT, cover TEXT, name_normalized TEXT, release_year INTEGER NOT NULL DEFAULT 0, tmdb INTEGER NOT NULL DEFAULT 0)"
        )
        db.execSQL(
            "CREATE TABLE categories (kind TEXT, category_id TEXT, name TEXT, PRIMARY KEY (kind, category_id))"
        )
        db.execSQL(
            "CREATE TABLE descriptions (kind TEXT, item_id INTEGER, description TEXT, PRIMARY KEY (kind, item_id))"
        )
        db.execSQL(
            "CREATE TABLE epg_programs (epg_channel_id TEXT, title TEXT, description TEXT, start INTEGER, stop INTEGER)"
        )
        db.execSQL("CREATE INDEX epg_programs_channel ON epg_programs (epg_channel_id, stop)")
        db.execSQL(
            "CREATE TABLE imdb_ratings (kind TEXT NOT NULL, tmdb_id INTEGER NOT NULL, rating REAL NOT NULL, votes INTEGER NOT NULL, lang TEXT NOT NULL DEFAULT '', PRIMARY KEY (kind, tmdb_id))"
        )
    }

    // The All screen looks items up by category - without these, counting a
    // big movie catalog per category took seconds. In onOpen (IF NOT EXISTS)
    // so adding them doesn't need a version bump and a full re-download.
    override fun onOpen(db: SQLiteDatabase) {
        super.onOpen(db)
        db.execSQL("CREATE INDEX IF NOT EXISTS live_channels_category ON live_channels (category_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS vod_streams_category ON vod_streams (category_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS series_category ON series (category_id)")
    }

    // Everything here is a cache of the provider's data - dropping it just
    // means the next launch re-syncs (isEmpty() is true again).
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        for (table in listOf("live_channels", "vod_streams", "series", "categories", "descriptions", "epg_programs", "imdb_ratings")) {
            db.execSQL("DROP TABLE IF EXISTS $table")
        }
        onCreate(db)
    }

    private fun countRows(table: String): Int =
        readableDatabase.rawQuery("SELECT COUNT(*) FROM $table", null).use { cursor ->
            cursor.moveToFirst()
            cursor.getInt(0)
        }

    fun countLiveChannels(): Int = countRows("live_channels")
    fun countVodStreams(): Int = countRows("vod_streams")
    fun countSeries(): Int = countRows("series")

    fun replaceCategories(kind: CatalogKind, categories: List<LiveCategory>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("categories", "kind = ?", arrayOf(kind.key))
            for (category in categories) {
                val values = ContentValues().apply {
                    put("kind", kind.key)
                    put("category_id", category.categoryId)
                    put("name", category.categoryName)
                }
                db.insertWithOnConflict("categories", null, values, SQLiteDatabase.CONFLICT_REPLACE)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    // category_id -> name for one catalog, for labelling saved items.
    fun categoryNames(kind: CatalogKind): Map<String, String> {
        val names = HashMap<String, String>()
        readableDatabase.rawQuery(
            "SELECT category_id, name FROM categories WHERE kind = ?",
            arrayOf(kind.key)
        ).use { cursor ->
            while (cursor.moveToNext()) names[cursor.getString(0)] = cursor.getString(1) ?: ""
        }
        return names
    }

    // null = never fetched; "" = fetched, provider has no synopsis.
    fun cachedDescription(kind: CatalogKind, itemId: Int): String? =
        readableDatabase.rawQuery(
            "SELECT description FROM descriptions WHERE kind = ? AND item_id = ?",
            arrayOf(kind.key, itemId.toString())
        ).use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) ?: "" else null }

    fun saveDescription(kind: CatalogKind, itemId: Int, description: String) {
        val values = ContentValues().apply {
            put("kind", kind.key)
            put("item_id", itemId)
            put("description", description)
        }
        writableDatabase.insertWithOnConflict("descriptions", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    // Saved favorites predate epg_channel_id, so it's looked up from the
    // synced catalog instead of stored with the favorite.
    fun epgChannelIds(streamIds: List<Int>): Map<Int, String> {
        if (streamIds.isEmpty()) return emptyMap()
        val ids = HashMap<Int, String>()
        readableDatabase.rawQuery(
            "SELECT stream_id, epg_channel_id FROM live_channels WHERE stream_id IN (${streamIds.joinToString(",")})",
            null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val epgId = cursor.getString(1)
                if (!epgId.isNullOrBlank()) ids[cursor.getInt(0)] = epgId
            }
        }
        return ids
    }

    fun replaceEpgPrograms(programs: Map<String, List<EpgProgram>>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("epg_programs", null, null)
            for ((epgId, list) in programs) {
                for (program in list) {
                    val values = ContentValues().apply {
                        put("epg_channel_id", epgId)
                        put("title", program.title)
                        put("description", program.description)
                        put("start", program.startEpochSeconds)
                        put("stop", program.stopEpochSeconds)
                    }
                    db.insert("epg_programs", null, values)
                }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    // What's airing right now on each channel, keyed by epg_channel_id.
    fun programsAiringAt(epgIds: Collection<String>, nowEpochSeconds: Long): Map<String, EpgProgram> {
        if (epgIds.isEmpty()) return emptyMap()
        val placeholders = epgIds.joinToString(",") { "?" }
        val args = epgIds.toList() + listOf(nowEpochSeconds.toString(), nowEpochSeconds.toString())
        val airing = HashMap<String, EpgProgram>()
        readableDatabase.rawQuery(
            "SELECT epg_channel_id, title, description, start, stop FROM epg_programs " +
                "WHERE epg_channel_id IN ($placeholders) AND start <= ? AND stop > ?",
            args.toTypedArray()
        ).use { cursor ->
            while (cursor.moveToNext()) {
                airing[cursor.getString(0)] = EpgProgram(
                    title = cursor.getString(1) ?: "",
                    description = cursor.getString(2) ?: "",
                    startEpochSeconds = cursor.getLong(3),
                    stopEpochSeconds = cursor.getLong(4)
                )
            }
        }
        return airing
    }

    fun isEmpty(): Boolean {
        readableDatabase.rawQuery("SELECT COUNT(*) FROM live_channels", null).use { cursor ->
            cursor.moveToFirst()
            return cursor.getInt(0) == 0
        }
    }

    // Streamed inserts (see XtreamApi.streamVodStreams), one transaction
    // per table. Providers sometimes answer with an empty list while they
    // rebuild their catalog - that throws, rolling back, so it never wipes
    // what we have.
    fun replaceLiveChannels(channels: Sequence<LiveChannel>) =
        replaceTable(
            "live_channels",
            "INSERT OR REPLACE INTO live_channels (stream_id, name, category_id, stream_icon, epg_channel_id, name_normalized) VALUES (?, ?, ?, ?, ?, ?)",
            channels,
            "channel"
        ) { st, c ->
            st.bindLong(1, c.streamId.toLong())
            st.bindString(2, c.name)
            st.bindString(3, c.categoryId)
            st.bindString(4, c.streamIcon)
            st.bindString(5, c.epgChannelId)
            st.bindString(6, normalizeForSearch(c.name))
        }

    fun replaceVodStreams(streams: Sequence<VodStream>) =
        replaceTable(
            "vod_streams",
            "INSERT OR REPLACE INTO vod_streams (stream_id, name, category_id, stream_icon, container_extension, name_normalized, release_year, tmdb) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
            streams,
            "movie"
        ) { st, m ->
            st.bindLong(1, m.streamId.toLong())
            st.bindString(2, m.name)
            st.bindString(3, m.categoryId)
            st.bindString(4, m.streamIcon)
            st.bindString(5, m.containerExtension)
            st.bindString(6, normalizeForSearch(m.name))
            st.bindLong(7, m.releaseYear.toLong())
            st.bindLong(8, m.tmdb.toLong())
        }

    fun replaceSeries(series: Sequence<SeriesShow>) =
        replaceTable(
            "series",
            "INSERT OR REPLACE INTO series (series_id, name, category_id, cover, name_normalized, release_year, tmdb) VALUES (?, ?, ?, ?, ?, ?, ?)",
            series,
            "series"
        ) { st, s ->
            st.bindLong(1, s.seriesId.toLong())
            st.bindString(2, s.name)
            st.bindString(3, s.categoryId)
            st.bindString(4, s.cover)
            st.bindString(5, normalizeForSearch(s.name))
            st.bindLong(6, s.releaseYear.toLong())
            st.bindLong(7, s.tmdb.toLong())
        }

    private fun <T> replaceTable(
        table: String,
        insertSql: String,
        rows: Sequence<T>,
        noun: String,
        bind: (SQLiteStatement, T) -> Unit
    ) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete(table, null, null)
            val statement = db.compileStatement(insertSql)
            var count = 0
            for (row in rows) {
                statement.clearBindings()
                bind(statement, row)
                statement.executeInsert()
                count++
            }
            if (count == 0) throw XtreamApiException("Provider sent an empty $noun list - kept the saved one")
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    // ---- All (the provider's raw list) ------------------------------
    // Categories in the provider's own order (categories has no order column,
    // but rows come back in insert order), each with how many items it holds.
    // Empty categories are left out.
    fun categoriesWithCounts(kind: CatalogKind): List<CategoryCount> {
        val table = catalogTable(kind)
        val results = mutableListOf<CategoryCount>()
        readableDatabase.rawQuery(
            "SELECT c.category_id, c.name, COUNT(t.category_id) FROM categories c " +
                "JOIN $table t ON t.category_id = c.category_id " +
                "WHERE c.kind = ? GROUP BY c.category_id ORDER BY c.rowid",
            arrayOf(kind.key)
        ).use { cursor ->
            while (cursor.moveToNext()) {
                results.add(CategoryCount(cursor.getString(0), cursor.getString(1) ?: "", cursor.getInt(2)))
            }
        }
        return results
    }

    // Provider order within the category (rows keep their download order).
    fun liveChannelsIn(categoryId: String): List<LiveChannel> {
        val results = mutableListOf<LiveChannel>()
        readableDatabase.rawQuery(
            "SELECT stream_id, name, category_id, stream_icon, epg_channel_id FROM live_channels WHERE category_id = ? ORDER BY rowid",
            arrayOf(categoryId)
        ).use { cursor ->
            while (cursor.moveToNext()) {
                results.add(LiveChannel(cursor.getInt(0), cursor.getString(1), cursor.getString(2), cursor.getString(3) ?: "", cursor.getString(4) ?: ""))
            }
        }
        return results
    }

    fun vodStreamsIn(categoryId: String): List<VodStream> {
        val results = mutableListOf<VodStream>()
        readableDatabase.rawQuery(
            "SELECT stream_id, name, category_id, stream_icon, container_extension FROM vod_streams WHERE category_id = ? ORDER BY rowid",
            arrayOf(categoryId)
        ).use { cursor ->
            while (cursor.moveToNext()) {
                results.add(VodStream(cursor.getInt(0), cursor.getString(1), cursor.getString(2), cursor.getString(3) ?: "", cursor.getString(4) ?: "mp4"))
            }
        }
        return results
    }

    fun seriesIn(categoryId: String): List<SeriesShow> {
        val results = mutableListOf<SeriesShow>()
        readableDatabase.rawQuery(
            "SELECT series_id, name, category_id, cover FROM series WHERE category_id = ? ORDER BY rowid",
            arrayOf(categoryId)
        ).use { cursor ->
            while (cursor.moveToNext()) {
                results.add(SeriesShow(cursor.getInt(0), cursor.getString(1), cursor.getString(2), cursor.getString(3) ?: ""))
            }
        }
        return results
    }

    private fun catalogTable(kind: CatalogKind): String = when (kind) {
        CatalogKind.LIVE -> "live_channels"
        CatalogKind.VOD -> "vod_streams"
        CatalogKind.SERIES -> "series"
    }

    // ---- What's New (same queries as the TV app) --------------------
    // An empty download (server file missing/broken) is ignored so it can
    // never blank out the scores we already have.
    fun replaceImdbRatings(movies: Map<Int, ImdbScore>, series: Map<Int, ImdbScore>): Boolean {
        if (movies.isEmpty() && series.isEmpty()) return false
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("imdb_ratings", null, null)
            val statement = db.compileStatement("INSERT OR REPLACE INTO imdb_ratings (kind, tmdb_id, rating, votes, lang) VALUES (?, ?, ?, ?, ?)")
            for ((kind, map) in listOf("movie" to movies, "tv" to series)) {
                for ((tmdbId, score) in map) {
                    statement.clearBindings()
                    statement.bindString(1, kind)
                    statement.bindLong(2, tmdbId.toLong())
                    statement.bindDouble(3, score.rating)
                    statement.bindLong(4, score.votes.toLong())
                    statement.bindString(5, score.language)
                    statement.executeInsert()
                }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return true
    }

    fun hasImdbRatings(): Boolean = countRows("imdb_ratings") > 0

    // Recent releases (minYear and later) in the given language, best IMDb
    // score first, at least MIN_IMDB_VOTES votes. Titles the provider lists
    // more than once (same tmdb id) show once.
    fun topMovies(language: WhatsNewLanguage, minYear: Int, limit: Int = 10): List<VodStream> {
        val results = mutableListOf<VodStream>()
        readableDatabase.rawQuery(
            "SELECT v.stream_id, v.name, v.category_id, v.stream_icon, v.container_extension, v.release_year, v.tmdb, r.rating " +
                "FROM vod_streams v JOIN imdb_ratings r ON r.kind = 'movie' AND r.tmdb_id = v.tmdb " +
                "WHERE ${language.nameFilter("v.name")} AND r.lang = ? AND r.votes >= $MIN_IMDB_VOTES AND v.release_year >= ? " +
                "GROUP BY v.tmdb ORDER BY r.rating DESC, r.votes DESC LIMIT ?",
            arrayOf(language.originalLanguage, minYear.toString(), limit.toString())
        ).use { cursor ->
            while (cursor.moveToNext()) {
                results.add(
                    VodStream(
                        streamId = cursor.getInt(0),
                        name = cursor.getString(1),
                        categoryId = cursor.getString(2),
                        streamIcon = cursor.getString(3) ?: "",
                        containerExtension = cursor.getString(4) ?: "mp4",
                        releaseYear = cursor.getInt(5),
                        tmdb = cursor.getInt(6),
                        imdbRating = cursor.getDouble(7)
                    )
                )
            }
        }
        return results
    }

    fun topSeries(language: WhatsNewLanguage, minYear: Int, limit: Int = 10): List<SeriesShow> {
        val results = mutableListOf<SeriesShow>()
        readableDatabase.rawQuery(
            "SELECT v.series_id, v.name, v.category_id, v.cover, v.release_year, v.tmdb, r.rating " +
                "FROM series v JOIN imdb_ratings r ON r.kind = 'tv' AND r.tmdb_id = v.tmdb " +
                "WHERE ${language.nameFilter("v.name")} AND r.lang = ? AND r.votes >= $MIN_IMDB_VOTES AND v.release_year >= ? " +
                "GROUP BY v.tmdb ORDER BY r.rating DESC, r.votes DESC LIMIT ?",
            arrayOf(language.originalLanguage, minYear.toString(), limit.toString())
        ).use { cursor ->
            while (cursor.moveToNext()) {
                results.add(
                    SeriesShow(
                        seriesId = cursor.getInt(0),
                        name = cursor.getString(1),
                        categoryId = cursor.getString(2),
                        cover = cursor.getString(3) ?: "",
                        releaseYear = cursor.getInt(4),
                        tmdb = cursor.getInt(5),
                        imdbRating = cursor.getDouble(6)
                    )
                )
            }
        }
        return results
    }

    fun searchAll(query: String, limit: Int = 50): List<SearchResult> {
        val tokens = normalizeForSearch(query).split(Regex("\\s+")).filter { it.isNotBlank() }
        if (tokens.isEmpty()) return emptyList()
        val whereClause = tokens.joinToString(" AND ") { "name_normalized LIKE ?" }
        val whereArgs = tokens.map { "%$it%" }.toTypedArray()

        val results = mutableListOf<SearchResult>()

        readableDatabase.rawQuery(
            "SELECT stream_id, name, category_id, stream_icon, epg_channel_id FROM live_channels WHERE $whereClause LIMIT $limit",
            whereArgs
        ).use { cursor ->
            while (cursor.moveToNext()) {
                results.add(
                    SearchResult.Live(
                        LiveChannel(
                            streamId = cursor.getInt(0),
                            name = cursor.getString(1),
                            categoryId = cursor.getString(2),
                            streamIcon = cursor.getString(3) ?: "",
                            epgChannelId = cursor.getString(4) ?: ""
                        )
                    )
                )
            }
        }

        readableDatabase.rawQuery(
            "SELECT stream_id, name, category_id, stream_icon, container_extension FROM vod_streams WHERE $whereClause LIMIT $limit",
            whereArgs
        ).use { cursor ->
            while (cursor.moveToNext()) {
                results.add(
                    SearchResult.Vod(
                        VodStream(
                            streamId = cursor.getInt(0),
                            name = cursor.getString(1),
                            categoryId = cursor.getString(2),
                            streamIcon = cursor.getString(3) ?: "",
                            containerExtension = cursor.getString(4) ?: "mp4"
                        )
                    )
                )
            }
        }

        readableDatabase.rawQuery(
            "SELECT series_id, name, category_id, cover FROM series WHERE $whereClause LIMIT $limit",
            whereArgs
        ).use { cursor ->
            while (cursor.moveToNext()) {
                results.add(
                    SearchResult.Series(
                        SeriesShow(
                            seriesId = cursor.getInt(0),
                            name = cursor.getString(1),
                            categoryId = cursor.getString(2),
                            cover = cursor.getString(3) ?: ""
                        )
                    )
                )
            }
        }

        return results
    }
}
