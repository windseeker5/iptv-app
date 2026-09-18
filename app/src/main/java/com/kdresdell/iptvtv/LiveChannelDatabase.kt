package com.kdresdell.iptvtv

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.text.Normalizer

// Search needs to match "elections" against a stored "Élections" and
// "ncaa hockey" against "Hockey - NCAA Men's" - plain SQLite
// `LIKE ... COLLATE NOCASE` only folds ASCII case, it does not fold
// accents, and a raw multi-word query is one contiguous substring, not an
// AND of its words. NFD-decomposing and stripping combining marks (the
// diacritics) before lowercasing turns "Élections" into "elections", and
// the same normalization is applied to the query at search time so both
// sides compare on equal footing.
private fun normalizeForSearch(text: String): String =
    Normalizer.normalize(text, Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .lowercase()

// Local cache of the full live-channel catalog. Search needs this because
// a real provider's catalog can be huge (tens of thousands of channels) -
// holding that as an in-memory Kotlin List and re-filtering it on every
// keystroke caused real ANRs on real hardware. SQLite's LIKE table scan
// stays fast at that scale; a giant List.filter() in the JVM does not.
class LiveChannelDatabase(context: Context) :
    SQLiteOpenHelper(context, "live_channels.db", null, 11) {

    init {
        // WAL mode lets concurrent readers proceed without blocking behind
        // a writer, using a real connection pool instead of one shared
        // connection - confirmed necessary on real hardware (2026-09-17):
        // once the My TV guide loop started fetching/caching up to ~20
        // channels' EPG concurrently (see MainActivity's coroutineScope +
        // async), plus the player reading/writing this same database at the
        // same time, the default single-connection journal mode threw
        // SQLiteDatabaseLockedException ("database is locked") and crashed
        // the app under that contention. Must be set before the database is
        // first opened, hence here in init rather than onConfigure/onOpen.
        setWriteAheadLoggingEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        createLiveChannelsTable(db)
        createVodStreamsTable(db)
        createSeriesTable(db)
        createEpgWindowTable(db)
        createEpgSyncTables(db)
        createVodDetailsTable(db)
        createSeriesDetailsTables(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 4) {
            db.execSQL("ALTER TABLE live_channels ADD COLUMN stream_icon TEXT NOT NULL DEFAULT ''")
        }
        if (oldVersion < 5) {
            createVodStreamsTable(db)
        }
        if (oldVersion < 6) {
            createSeriesTable(db)
        }
        if (oldVersion < 7) {
            createEpgWindowTable(db)
        }
        if (oldVersion < 8) {
            createVodDetailsTable(db)
            createSeriesDetailsTables(db)
        }
        if (oldVersion < 9) {
            db.execSQL("ALTER TABLE live_channels ADD COLUMN name_normalized TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE vod_streams ADD COLUMN name_normalized TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE series ADD COLUMN name_normalized TEXT NOT NULL DEFAULT ''")
            // The catalog only re-syncs from the network when its table is
            // empty (see MainActivity's isEmpty()/isVodEmpty()/isSeriesEmpty()
            // checks) - on an upgrade the tables are already full, so that
            // sync never runs and every row would be stuck with the blank
            // default above (search would return nothing for anyone
            // upgrading in place). Backfill in SQL from the name already on
            // disk instead of waiting for a resync.
            backfillNormalizedNames(db, "live_channels", "stream_id")
            backfillNormalizedNames(db, "vod_streams", "stream_id")
            backfillNormalizedNames(db, "series", "series_id")
        }
        if (oldVersion < 10) {
            // v9 shipped without this backfill on some installs (this
            // device included) - re-run it unconditionally so name_normalized
            // is never left blank regardless of which version an install is
            // coming from.
            backfillNormalizedNames(db, "live_channels", "stream_id")
            backfillNormalizedNames(db, "vod_streams", "stream_id")
            backfillNormalizedNames(db, "series", "series_id")
        }
        if (oldVersion < 11) {
            // Everything cached so far came from get_short_epg /
            // get_simple_data_table, which this provider serves 2h late
            // (see XtreamApi.fetchXmltvPrograms) - drop it all rather than
            // keep showing wrong times until it expires. Cache-only data.
            db.execSQL("DROP TABLE IF EXISTS channel_epg")
            db.execSQL("DELETE FROM channel_epg_window")
            createEpgSyncTables(db)
        }
    }

    // Without an explicit transaction, SQLite commits (fsyncs) after every
    // single UPDATE - confirmed on real hardware (2026-09-17) that this
    // made backfilling ~190k rows across the three tables grind for
    // minutes, indistinguishable from a hang. Wrapping the whole backfill
    // in one transaction is the same fix replaceAll()/replaceAllVod()/
    // replaceAllSeries() already use for their bulk inserts.
    private fun backfillNormalizedNames(db: SQLiteDatabase, table: String, idColumn: String) {
        db.beginTransaction()
        try {
            val updateStatement = db.compileStatement("UPDATE $table SET name_normalized = ? WHERE $idColumn = ?")
            db.rawQuery("SELECT $idColumn, name FROM $table", null).use { cursor ->
                while (cursor.moveToNext()) {
                    updateStatement.clearBindings()
                    updateStatement.bindString(1, normalizeForSearch(cursor.getString(1)))
                    updateStatement.bindLong(2, cursor.getLong(0))
                    updateStatement.executeUpdateDelete()
                }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private fun createLiveChannelsTable(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS live_channels (
                stream_id INTEGER PRIMARY KEY,
                name TEXT NOT NULL,
                category_id TEXT NOT NULL,
                stream_icon TEXT NOT NULL DEFAULT '',
                name_normalized TEXT NOT NULL DEFAULT ''
            )
            """.trimIndent()
        )
    }

    private fun createVodStreamsTable(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS vod_streams (
                stream_id INTEGER PRIMARY KEY,
                name TEXT NOT NULL,
                category_id TEXT NOT NULL,
                stream_icon TEXT NOT NULL DEFAULT '',
                container_extension TEXT NOT NULL DEFAULT 'mp4',
                name_normalized TEXT NOT NULL DEFAULT ''
            )
            """.trimIndent()
        )
    }

    private fun createSeriesTable(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS series (
                series_id INTEGER PRIMARY KEY,
                name TEXT NOT NULL,
                category_id TEXT NOT NULL,
                cover TEXT NOT NULL DEFAULT '',
                name_normalized TEXT NOT NULL DEFAULT ''
            )
            """.trimIndent()
        )
    }

    // Multi-program window per channel: the one guide cache, read by the My
    // TV grid, the player overlay and Search's "Now:" line (STYLE_GUIDE.md
    // §6.3). Filled only by EpgSync, from xmltv.php.
    private fun createEpgWindowTable(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS channel_epg_window (
                stream_id INTEGER NOT NULL,
                start_epoch INTEGER NOT NULL,
                stop_epoch INTEGER NOT NULL,
                title TEXT NOT NULL,
                description TEXT NOT NULL DEFAULT '',
                fetched_at INTEGER NOT NULL,
                PRIMARY KEY (stream_id, start_epoch)
            )
            """.trimIndent()
        )
    }

    // Guide sync bookkeeping (see EpgSync). The guide is downloaded for whole
    // categories - the ones favorites/played channels belong to - because
    // one small get_live_streams call per category gives every channel's
    // epg_channel_id, and it lets channel surfing and Search find guide
    // data without another 74MB download.
    private fun createEpgSyncTables(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS epg_categories (
                category_id TEXT PRIMARY KEY,
                synced_at INTEGER NOT NULL DEFAULT 0,
                requested_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS channel_epg_ids (
                stream_id INTEGER PRIMARY KEY,
                category_id TEXT NOT NULL,
                epg_channel_id TEXT NOT NULL
            )
            """.trimIndent()
        )
    }

    // Replaces the cached programs of every given channel in ONE
    // transaction (a per-channel transaction each meant an fsync per
    // channel - see backfillNormalizedNames for why that matters here).
    fun setEpgWindows(windows: Map<Int, List<EpgProgram>>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val statement = db.compileStatement(
                "INSERT OR REPLACE INTO channel_epg_window " +
                    "(stream_id, start_epoch, stop_epoch, title, description, fetched_at) VALUES (?, ?, ?, ?, ?, ?)"
            )
            val fetchedAt = System.currentTimeMillis()
            windows.forEach { (streamId, programs) ->
                db.delete("channel_epg_window", "stream_id = ?", arrayOf(streamId.toString()))
                programs.forEach { program ->
                    statement.clearBindings()
                    statement.bindLong(1, streamId.toLong())
                    statement.bindLong(2, program.startEpochSeconds)
                    statement.bindLong(3, program.stopEpochSeconds)
                    statement.bindString(4, program.title)
                    statement.bindString(5, program.description)
                    statement.bindLong(6, fetchedAt)
                    statement.executeInsert()
                }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun getCachedEpgWindow(streamId: Int): List<EpgProgram> {
        val results = mutableListOf<EpgProgram>()
        readableDatabase.rawQuery(
            "SELECT start_epoch, stop_epoch, title, description FROM channel_epg_window " +
                "WHERE stream_id = ? ORDER BY start_epoch",
            arrayOf(streamId.toString())
        ).use { cursor ->
            while (cursor.moveToNext()) {
                results.add(
                    EpgProgram(
                        title = cursor.getString(2),
                        description = cursor.getString(3),
                        startEpochSeconds = cursor.getLong(0),
                        stopEpochSeconds = cursor.getLong(1)
                    )
                )
            }
        }
        return results
    }

    // Marks these categories as wanted right now (inserting unseen ones as
    // never-synced), so they are kept fresh by every later sync too.
    fun touchEpgCategories(categoryIds: Collection<String>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val now = System.currentTimeMillis()
            categoryIds.forEach { id ->
                db.execSQL(
                    "INSERT OR IGNORE INTO epg_categories (category_id, synced_at, requested_at) VALUES (?, 0, ?)",
                    arrayOf(id, now)
                )
                db.execSQL("UPDATE epg_categories SET requested_at = ? WHERE category_id = ?", arrayOf(now, id))
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun staleEpgCategories(categoryIds: Collection<String>, maxAgeMillis: Long): List<String> {
        val cutoff = System.currentTimeMillis() - maxAgeMillis
        return categoryIds.filter { id ->
            readableDatabase.rawQuery(
                "SELECT synced_at FROM epg_categories WHERE category_id = ?",
                arrayOf(id)
            ).use { cursor -> !cursor.moveToFirst() || cursor.getLong(0) < cutoff }
        }
    }

    fun activeEpgCategories(requestedWithinMillis: Long): List<String> {
        val cutoff = System.currentTimeMillis() - requestedWithinMillis
        val result = mutableListOf<String>()
        readableDatabase.rawQuery(
            "SELECT category_id FROM epg_categories WHERE requested_at >= ?",
            arrayOf(cutoff.toString())
        ).use { cursor -> while (cursor.moveToNext()) result.add(cursor.getString(0)) }
        return result
    }

    fun replaceEpgChannelIds(categoryId: String, ids: Map<Int, String>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("channel_epg_ids", "category_id = ?", arrayOf(categoryId))
            val statement = db.compileStatement(
                "INSERT OR REPLACE INTO channel_epg_ids (stream_id, category_id, epg_channel_id) VALUES (?, ?, ?)"
            )
            ids.forEach { (streamId, epgId) ->
                statement.clearBindings()
                statement.bindLong(1, streamId.toLong())
                statement.bindString(2, categoryId)
                statement.bindString(3, epgId)
                statement.executeInsert()
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    // epg_channel_id -> every stream_id sharing it (HD/SD variants of one
    // channel share one guide entry).
    fun streamIdsByEpgChannelId(categoryIds: Collection<String>): Map<String, List<Int>> {
        val result = HashMap<String, MutableList<Int>>()
        categoryIds.forEach { id ->
            readableDatabase.rawQuery(
                "SELECT epg_channel_id, stream_id FROM channel_epg_ids WHERE category_id = ?",
                arrayOf(id)
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    result.getOrPut(cursor.getString(0)) { mutableListOf() }.add(cursor.getInt(1))
                }
            }
        }
        return result
    }

    fun markEpgCategoriesSynced(categoryIds: Collection<String>) {
        val now = System.currentTimeMillis()
        val db = writableDatabase
        db.beginTransaction()
        try {
            categoryIds.forEach { id ->
                db.execSQL("UPDATE epg_categories SET synced_at = ? WHERE category_id = ?", arrayOf(now, id))
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    // Categories nobody asked for in a while stop being refreshed and are
    // dropped, so browsing around once doesn't grow the sync forever.
    fun pruneEpg(requestedWithinMillis: Long) {
        val cutoff = (System.currentTimeMillis() - requestedWithinMillis).toString()
        val db = writableDatabase
        db.beginTransaction()
        try {
            val args = arrayOf(cutoff)
            db.execSQL(
                "DELETE FROM channel_epg_window WHERE stream_id IN (SELECT stream_id FROM channel_epg_ids " +
                    "WHERE category_id IN (SELECT category_id FROM epg_categories WHERE requested_at < ?))",
                args
            )
            db.execSQL(
                "DELETE FROM channel_epg_ids WHERE category_id IN " +
                    "(SELECT category_id FROM epg_categories WHERE requested_at < ?)",
                args
            )
            db.execSQL("DELETE FROM epg_categories WHERE requested_at < ?", args)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    // Movie synopsis/rating (get_vod_info), cached on-demand: fetched once
    // on first open, then instant on every later open - no TTL, since a
    // catalog title's plot/rating essentially never changes once published.
    // fetched_at is still stored for consistency with the rest of this
    // file's caches and as a future-diagnostic hook, not for expiry.
    private fun createVodDetailsTable(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS vod_details (
                stream_id INTEGER PRIMARY KEY,
                description TEXT NOT NULL DEFAULT '',
                rating TEXT NOT NULL DEFAULT '',
                fetched_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }

    fun getCachedVodDetails(streamId: Int): VodDetails? {
        readableDatabase.rawQuery(
            "SELECT description, rating FROM vod_details WHERE stream_id = ?",
            arrayOf(streamId.toString())
        ).use { cursor ->
            if (!cursor.moveToFirst()) return null
            return VodDetails(description = cursor.getString(0), rating = cursor.getString(1))
        }
    }

    fun setVodDetails(streamId: Int, details: VodDetails) {
        writableDatabase.execSQL(
            "INSERT OR REPLACE INTO vod_details (stream_id, description, rating, fetched_at) VALUES (?, ?, ?, ?)",
            arrayOf(streamId, details.description, details.rating, System.currentTimeMillis())
        )
    }

    // Series synopsis/rating + full episode list (get_series_info), cached
    // on-demand the same way as vod_details above - same no-TTL rationale.
    private fun createSeriesDetailsTables(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS series_details (
                series_id INTEGER PRIMARY KEY,
                description TEXT NOT NULL DEFAULT '',
                rating TEXT NOT NULL DEFAULT '',
                fetched_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS series_episodes (
                series_id INTEGER NOT NULL,
                episode_id INTEGER NOT NULL,
                season INTEGER NOT NULL,
                episode_num INTEGER NOT NULL,
                title TEXT NOT NULL DEFAULT '',
                container_extension TEXT NOT NULL DEFAULT 'mp4',
                description TEXT NOT NULL DEFAULT '',
                fetched_at INTEGER NOT NULL,
                PRIMARY KEY (series_id, episode_id)
            )
            """.trimIndent()
        )
    }

    fun getCachedSeriesDetails(seriesId: Int): SeriesDetails? {
        val (description, rating) = readableDatabase.rawQuery(
            "SELECT description, rating FROM series_details WHERE series_id = ?",
            arrayOf(seriesId.toString())
        ).use { cursor ->
            if (!cursor.moveToFirst()) return null
            cursor.getString(0) to cursor.getString(1)
        }
        val episodes = mutableListOf<SeriesEpisode>()
        readableDatabase.rawQuery(
            "SELECT episode_id, episode_num, season, title, container_extension, description " +
                "FROM series_episodes WHERE series_id = ? ORDER BY season, episode_num",
            arrayOf(seriesId.toString())
        ).use { cursor ->
            while (cursor.moveToNext()) {
                episodes.add(
                    SeriesEpisode(
                        episodeId = cursor.getInt(0),
                        episodeNum = cursor.getInt(1),
                        season = cursor.getInt(2),
                        title = cursor.getString(3),
                        containerExtension = cursor.getString(4),
                        description = cursor.getString(5)
                    )
                )
            }
        }
        return SeriesDetails(description = description, rating = rating, episodes = episodes)
    }

    fun setSeriesDetails(seriesId: Int, details: SeriesDetails) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.execSQL(
                "INSERT OR REPLACE INTO series_details (series_id, description, rating, fetched_at) VALUES (?, ?, ?, ?)",
                arrayOf(seriesId, details.description, details.rating, System.currentTimeMillis())
            )
            db.delete("series_episodes", "series_id = ?", arrayOf(seriesId.toString()))
            val statement = db.compileStatement(
                "INSERT OR REPLACE INTO series_episodes " +
                    "(series_id, episode_id, season, episode_num, title, container_extension, description, fetched_at) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?)"
            )
            val fetchedAt = System.currentTimeMillis()
            details.episodes.forEach { episode ->
                statement.clearBindings()
                statement.bindLong(1, seriesId.toLong())
                statement.bindLong(2, episode.episodeId.toLong())
                statement.bindLong(3, episode.season.toLong())
                statement.bindLong(4, episode.episodeNum.toLong())
                statement.bindString(5, episode.title)
                statement.bindString(6, episode.containerExtension)
                statement.bindString(7, episode.description)
                statement.bindLong(8, fetchedAt)
                statement.executeInsert()
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    // What's on right now, read from the same guide cache the My TV grid and
    // the player use - so Search can never disagree with them, and never
    // makes its own network call. Null when this channel's category isn't
    // synced (see EpgSync) or nothing is airing.
    fun getCachedNowPlaying(streamId: Int): NowPlayingInfo? {
        val current = XtreamApi.currentAndNextFrom(getCachedEpgWindow(streamId)).first ?: return null
        return NowPlayingInfo(
            title = current.title,
            description = current.description,
            startEpochSeconds = current.startEpochSeconds,
            stopEpochSeconds = current.stopEpochSeconds
        )
    }

    fun isEmpty(): Boolean {
        readableDatabase.rawQuery("SELECT COUNT(*) FROM live_channels", null).use { cursor ->
            cursor.moveToFirst()
            return cursor.getInt(0) == 0
        }
    }

    fun isVodEmpty(): Boolean {
        readableDatabase.rawQuery("SELECT COUNT(*) FROM vod_streams", null).use { cursor ->
            cursor.moveToFirst()
            return cursor.getInt(0) == 0
        }
    }

    fun isSeriesEmpty(): Boolean {
        readableDatabase.rawQuery("SELECT COUNT(*) FROM series", null).use { cursor ->
            cursor.moveToFirst()
            return cursor.getInt(0) == 0
        }
    }

    // §6.4 search KPI row - the real catalog totals, not just the running
    // counter a sync-in-progress callback reports (which stays 0 forever on
    // a returning session where the sync is skipped because the table is
    // already populated).
    private fun countRows(table: String): Int {
        readableDatabase.rawQuery("SELECT COUNT(*) FROM $table", null).use { cursor ->
            cursor.moveToFirst()
            return cursor.getInt(0)
        }
    }

    fun countLiveChannels(): Int = countRows("live_channels")
    fun countVodStreams(): Int = countRows("vod_streams")
    fun countSeries(): Int = countRows("series")

    // channels is consumed lazily and inserted in one transaction, so the
    // full catalog never needs to exist as a Kotlin List at any point.
    // onProgress is called periodically (not on every row) with the running
    // count, so the UI can show something other than a frozen message.
    fun replaceAll(channels: Sequence<LiveChannel>, onProgress: (Int) -> Unit = {}) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.execSQL("DELETE FROM live_channels")
            val statement = db.compileStatement(
                "INSERT OR REPLACE INTO live_channels (stream_id, name, category_id, stream_icon, name_normalized) VALUES (?, ?, ?, ?, ?)"
            )
            var count = 0
            for (channel in channels) {
                statement.clearBindings()
                statement.bindLong(1, channel.streamId.toLong())
                statement.bindString(2, channel.name)
                statement.bindString(3, channel.categoryId)
                statement.bindString(4, channel.streamIcon)
                statement.bindString(5, normalizeForSearch(channel.name))
                statement.executeInsert()
                count++
                if (count % 250 == 0) {
                    onProgress(count)
                }
            }
            onProgress(count)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    // Same "streamed insert into SQLite" reasoning as replaceAll, for the
    // VOD catalog.
    fun replaceAllVod(streams: Sequence<VodStream>, onProgress: (Int) -> Unit = {}) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.execSQL("DELETE FROM vod_streams")
            val statement = db.compileStatement(
                "INSERT OR REPLACE INTO vod_streams (stream_id, name, category_id, stream_icon, container_extension, name_normalized) VALUES (?, ?, ?, ?, ?, ?)"
            )
            var count = 0
            for (stream in streams) {
                statement.clearBindings()
                statement.bindLong(1, stream.streamId.toLong())
                statement.bindString(2, stream.name)
                statement.bindString(3, stream.categoryId)
                statement.bindString(4, stream.streamIcon)
                statement.bindString(5, stream.containerExtension)
                statement.bindString(6, normalizeForSearch(stream.name))
                statement.executeInsert()
                count++
                if (count % 250 == 0) {
                    onProgress(count)
                }
            }
            onProgress(count)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    // Same "streamed insert into SQLite" reasoning as replaceAll, for the
    // series catalog.
    fun replaceAllSeries(series: Sequence<SeriesShow>, onProgress: (Int) -> Unit = {}) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.execSQL("DELETE FROM series")
            val statement = db.compileStatement(
                "INSERT OR REPLACE INTO series (series_id, name, category_id, cover, name_normalized) VALUES (?, ?, ?, ?, ?)"
            )
            var count = 0
            for (show in series) {
                statement.clearBindings()
                statement.bindLong(1, show.seriesId.toLong())
                statement.bindString(2, show.name)
                statement.bindString(3, show.categoryId)
                statement.bindString(4, show.cover)
                statement.bindString(5, normalizeForSearch(show.name))
                statement.executeInsert()
                count++
                if (count % 250 == 0) {
                    onProgress(count)
                }
            }
            onProgress(count)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    // Searches all three indexes and returns a combined, type-tagged
    // result list - live channels, then VOD movies, then series. Each
    // whitespace-separated word in the query is its own AND'd condition
    // against the accent/case-folded name_normalized column, so "ncaa
    // hockey" matches "Hockey - NCAA Men's Division" (both words present,
    // any order, any position) rather than requiring that exact contiguous
    // phrase - and "elections" matches a stored "Élections" the same way.
    fun searchAll(query: String, limit: Int = 100): List<SearchResult> {
        val tokens = normalizeForSearch(query).split(Regex("\\s+")).filter { it.isNotBlank() }
        if (tokens.isEmpty()) return emptyList()
        val whereClause = tokens.joinToString(" AND ") { "name_normalized LIKE ?" }
        val tokenArgs = tokens.map { "%$it%" }

        val results = mutableListOf<SearchResult>()
        readableDatabase.rawQuery(
            "SELECT stream_id, name, category_id, stream_icon FROM live_channels WHERE $whereClause LIMIT ?",
            (tokenArgs + limit.toString()).toTypedArray()
        ).use { cursor ->
            while (cursor.moveToNext()) {
                results.add(
                    SearchResult.Live(
                        LiveChannel(
                            streamId = cursor.getInt(0),
                            name = cursor.getString(1),
                            categoryId = cursor.getString(2),
                            streamIcon = cursor.getString(3)
                        )
                    )
                )
            }
        }
        readableDatabase.rawQuery(
            "SELECT stream_id, name, category_id, stream_icon, container_extension FROM vod_streams WHERE $whereClause LIMIT ?",
            (tokenArgs + limit.toString()).toTypedArray()
        ).use { cursor ->
            while (cursor.moveToNext()) {
                results.add(
                    SearchResult.Vod(
                        VodStream(
                            streamId = cursor.getInt(0),
                            name = cursor.getString(1),
                            categoryId = cursor.getString(2),
                            streamIcon = cursor.getString(3),
                            containerExtension = cursor.getString(4)
                        )
                    )
                )
            }
        }
        readableDatabase.rawQuery(
            "SELECT series_id, name, category_id, cover FROM series WHERE $whereClause LIMIT ?",
            (tokenArgs + limit.toString()).toTypedArray()
        ).use { cursor ->
            while (cursor.moveToNext()) {
                results.add(
                    SearchResult.Series(
                        SeriesShow(
                            seriesId = cursor.getInt(0),
                            name = cursor.getString(1),
                            categoryId = cursor.getString(2),
                            cover = cursor.getString(3)
                        )
                    )
                )
            }
        }
        return results
    }
}
