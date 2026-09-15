package com.kdresdell.iptvtv

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

// Local cache of the full live-channel catalog. Search needs this because
// a real provider's catalog can be huge (tens of thousands of channels) -
// holding that as an in-memory Kotlin List and re-filtering it on every
// keystroke caused real ANRs on real hardware. SQLite's LIKE table scan
// stays fast at that scale; a giant List.filter() in the JVM does not.
class LiveChannelDatabase(context: Context) :
    SQLiteOpenHelper(context, "live_channels.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE live_channels (
                stream_id INTEGER PRIMARY KEY,
                name TEXT NOT NULL,
                category_id TEXT NOT NULL
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS live_channels")
        onCreate(db)
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
                "INSERT OR REPLACE INTO live_channels (stream_id, name, category_id) VALUES (?, ?, ?)"
            )
            var count = 0
            for (channel in channels) {
                statement.clearBindings()
                statement.bindLong(1, channel.streamId.toLong())
                statement.bindString(2, channel.name)
                statement.bindString(3, channel.categoryId)
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
            "SELECT stream_id, name, category_id FROM live_channels WHERE name LIKE ? COLLATE NOCASE LIMIT ?",
            arrayOf("%$query%", limit.toString())
        ).use { cursor ->
            while (cursor.moveToNext()) {
                results.add(
                    LiveChannel(
                        streamId = cursor.getInt(0),
                        name = cursor.getString(1),
                        categoryId = cursor.getString(2)
                    )
                )
            }
        }
        return results
    }
}
