package com.kdresdell.iptvtv

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

// Local cache of the full live-channel catalog. Search needs this because
// a real provider's catalog can be huge (tens of thousands of channels) -
// holding that as an in-memory Kotlin List and re-filtering it on every
// keystroke caused real ANRs on real hardware. SQLite's LIKE table scan
// stays fast at that scale; a giant List.filter() in the JVM does not.
class LiveChannelDatabase(context: Context) :
    SQLiteOpenHelper(context, "live_channels.db", null, 4) {

    override fun onCreate(db: SQLiteDatabase) {
        createLiveChannelsTable(db)
        createEpgTable(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            createEpgTable(db)
        }
        if (oldVersion < 3) {
            // Cache-only data, safe to drop and let it repopulate.
            db.execSQL("DROP TABLE IF EXISTS channel_epg")
            createEpgTable(db)
        }
        if (oldVersion < 4) {
            db.execSQL("ALTER TABLE live_channels ADD COLUMN stream_icon TEXT NOT NULL DEFAULT ''")
        }
    }

    private fun createLiveChannelsTable(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS live_channels (
                stream_id INTEGER PRIMARY KEY,
                name TEXT NOT NULL,
                category_id TEXT NOT NULL,
                stream_icon TEXT NOT NULL DEFAULT ''
            )
            """.trimIndent()
        )
    }

    private fun createEpgTable(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS channel_epg (
                stream_id INTEGER PRIMARY KEY,
                now_playing_title TEXT NOT NULL,
                description TEXT NOT NULL DEFAULT '',
                start_epoch INTEGER,
                stop_epoch INTEGER,
                fetched_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }

    // Only ever called for favorited channels (a handful, not the whole
    // catalog) so a plain per-channel fetch/cache is fine here - this is
    // deliberately not the same "index everything" approach search needed.
    fun getCachedNowPlaying(streamId: Int): NowPlayingInfo? {
        readableDatabase.rawQuery(
            "SELECT now_playing_title, description, start_epoch, stop_epoch FROM channel_epg WHERE stream_id = ?",
            arrayOf(streamId.toString())
        ).use { cursor ->
            if (!cursor.moveToFirst()) return null
            return NowPlayingInfo(
                title = cursor.getString(0),
                description = cursor.getString(1),
                startEpochSeconds = if (cursor.isNull(2)) null else cursor.getLong(2),
                stopEpochSeconds = if (cursor.isNull(3)) null else cursor.getLong(3)
            )
        }
    }

    fun isEpgStale(streamId: Int, maxAgeMillis: Long): Boolean {
        readableDatabase.rawQuery(
            "SELECT fetched_at FROM channel_epg WHERE stream_id = ?",
            arrayOf(streamId.toString())
        ).use { cursor ->
            if (!cursor.moveToFirst()) return true
            val fetchedAt = cursor.getLong(0)
            return System.currentTimeMillis() - fetchedAt > maxAgeMillis
        }
    }

    fun setNowPlaying(streamId: Int, info: NowPlayingInfo) {
        val values = ContentValues().apply {
            put("stream_id", streamId)
            put("now_playing_title", info.title)
            put("description", info.description)
            if (info.startEpochSeconds != null) put("start_epoch", info.startEpochSeconds) else putNull("start_epoch")
            if (info.stopEpochSeconds != null) put("stop_epoch", info.stopEpochSeconds) else putNull("stop_epoch")
            put("fetched_at", System.currentTimeMillis())
        }
        writableDatabase.insertWithOnConflict(
            "channel_epg", null, values, SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    fun isEmpty(): Boolean {
        readableDatabase.rawQuery("SELECT COUNT(*) FROM live_channels", null).use { cursor ->
            cursor.moveToFirst()
            return cursor.getInt(0) == 0
        }
    }

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
                "INSERT OR REPLACE INTO live_channels (stream_id, name, category_id, stream_icon) VALUES (?, ?, ?, ?)"
            )
            var count = 0
            for (channel in channels) {
                statement.clearBindings()
                statement.bindLong(1, channel.streamId.toLong())
                statement.bindString(2, channel.name)
                statement.bindString(3, channel.categoryId)
                statement.bindString(4, channel.streamIcon)
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

    fun search(query: String, limit: Int = 200): List<LiveChannel> {
        val results = mutableListOf<LiveChannel>()
        readableDatabase.rawQuery(
            "SELECT stream_id, name, category_id, stream_icon FROM live_channels WHERE name LIKE ? COLLATE NOCASE LIMIT ?",
            arrayOf("%$query%", limit.toString())
        ).use { cursor ->
            while (cursor.moveToNext()) {
                results.add(
                    LiveChannel(
                        streamId = cursor.getInt(0),
                        name = cursor.getString(1),
                        categoryId = cursor.getString(2),
                        streamIcon = cursor.getString(3)
                    )
                )
            }
        }
        return results
    }
}
