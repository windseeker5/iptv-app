package com.kdresdell.iptvtv.phone

// TODO: duplicated/trimmed from the TV app's LiveChannelDatabase.kt - the
// 3 catalog tables + search, plus category names, a description cache and a
// guide cache for the My TV / My Library rows. No IMDb ratings (TV-only).

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
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

class CatalogDatabase(context: Context) : SQLiteOpenHelper(context, "catalog.db", null, 2) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE live_channels (stream_id INTEGER PRIMARY KEY, name TEXT, category_id TEXT, stream_icon TEXT, epg_channel_id TEXT, name_normalized TEXT)"
        )
        db.execSQL(
            "CREATE TABLE vod_streams (stream_id INTEGER PRIMARY KEY, name TEXT, category_id TEXT, stream_icon TEXT, container_extension TEXT, name_normalized TEXT)"
        )
        db.execSQL(
            "CREATE TABLE series (series_id INTEGER PRIMARY KEY, name TEXT, category_id TEXT, cover TEXT, name_normalized TEXT)"
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
    }

    // Everything here is a cache of the provider's data - dropping it just
    // means the next launch re-syncs (isEmpty() is true again).
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        for (table in listOf("live_channels", "vod_streams", "series", "categories", "descriptions", "epg_programs")) {
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

    fun replaceLiveChannels(channels: List<LiveChannel>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("live_channels", null, null)
            for (channel in channels) {
                val values = ContentValues().apply {
                    put("stream_id", channel.streamId)
                    put("name", channel.name)
                    put("category_id", channel.categoryId)
                    put("stream_icon", channel.streamIcon)
                    put("epg_channel_id", channel.epgChannelId)
                    put("name_normalized", normalizeForSearch(channel.name))
                }
                db.insert("live_channels", null, values)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun replaceVodStreams(streams: List<VodStream>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("vod_streams", null, null)
            for (stream in streams) {
                val values = ContentValues().apply {
                    put("stream_id", stream.streamId)
                    put("name", stream.name)
                    put("category_id", stream.categoryId)
                    put("stream_icon", stream.streamIcon)
                    put("container_extension", stream.containerExtension)
                    put("name_normalized", normalizeForSearch(stream.name))
                }
                db.insert("vod_streams", null, values)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun replaceSeries(series: List<SeriesShow>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("series", null, null)
            for (show in series) {
                val values = ContentValues().apply {
                    put("series_id", show.seriesId)
                    put("name", show.name)
                    put("category_id", show.categoryId)
                    put("cover", show.cover)
                    put("name_normalized", normalizeForSearch(show.name))
                }
                db.insert("series", null, values)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
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
