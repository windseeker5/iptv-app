package com.kdresdell.iptvtv

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.compose.runtime.mutableStateMapOf
import java.util.concurrent.Executors

// Where you stopped in each movie and episode, and whether you finished it.
// Only titles you actually started are stored, so it stays tiny. Its own
// small database file, separate from the catalog one, so a catalog rebuild
// or schema change can never wipe what you've watched.
//
// Kept in memory too (mutableStateMapOf), so the ✓ marks and progress bars
// update on screen as soon as something is saved, without any screen
// having to reload.
data class WatchEntry(val positionMs: Long, val durationMs: Long, val watched: Boolean, val updatedAt: Long) {
    val fraction: Float get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
}

object WatchHistory {
    const val MOVIE = "movie"
    const val EPISODE = "episode"

    // Counts as watched from 90% on, so skipping the end credits still does.
    private const val WATCHED_FRACTION = 0.9f
    // Less than this in, it isn't worth resuming - start from the top.
    private const val MIN_RESUME_MS = 30_000L

    private val entries = mutableStateMapOf<String, WatchEntry>()
    private var helper: Helper? = null
    private val writer = Executors.newSingleThreadExecutor()

    fun init(context: Context): Unit = synchronized(this) {
        if (helper != null) return
        val h = Helper(context.applicationContext)
        helper = h
        try {
            h.readableDatabase.rawQuery(
                "SELECT kind, content_id, position_ms, duration_ms, watched, updated_at FROM progress", null
            ).use { c ->
                while (c.moveToNext()) {
                    entries[key(c.getString(0), c.getInt(1))] =
                        WatchEntry(c.getLong(2), c.getLong(3), c.getInt(4) != 0, c.getLong(5))
                }
            }
        } catch (e: Exception) {
            AppLog.log("Watch history could not be read: ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    fun get(kind: String, id: Int): WatchEntry? = entries[key(kind, id)]

    // Where to start playback: the saved spot if you stopped part-way,
    // otherwise the beginning (never started, barely started, or finished).
    fun resumePositionMs(kind: String, id: Int): Long {
        val e = get(kind, id) ?: return 0L
        if (e.durationMs <= 0 || e.positionMs < MIN_RESUME_MS) return 0L
        if (e.positionMs >= e.durationMs * WATCHED_FRACTION) return 0L
        return e.positionMs
    }

    // Called every few seconds while playing, on pause and on leaving.
    // Once watched, stays watched - rewatching the start doesn't unmark it.
    fun save(kind: String, id: Int, positionMs: Long, durationMs: Long) {
        if (durationMs <= 0) return
        val old = get(kind, id)
        val watched = (old?.watched == true) || positionMs >= durationMs * WATCHED_FRACTION
        put(kind, id, WatchEntry(positionMs, durationMs, watched, System.currentTimeMillis()))
    }

    // Played to the very end.
    fun markWatched(kind: String, id: Int, durationMs: Long) {
        val d = if (durationMs > 0) durationMs else get(kind, id)?.durationMs ?: 0L
        put(kind, id, WatchEntry(d, d, true, System.currentTimeMillis()))
    }

    private fun put(kind: String, id: Int, entry: WatchEntry) {
        entries[key(kind, id)] = entry
        val db = helper ?: return
        writer.execute {
            try {
                db.writableDatabase.insertWithOnConflict("progress", null, ContentValues().apply {
                    put("kind", kind)
                    put("content_id", id)
                    put("position_ms", entry.positionMs)
                    put("duration_ms", entry.durationMs)
                    put("watched", if (entry.watched) 1 else 0)
                    put("updated_at", entry.updatedAt)
                }, SQLiteDatabase.CONFLICT_REPLACE)
            } catch (e: Exception) {
                AppLog.log("Watch history could not be saved: ${e.javaClass.simpleName}: ${e.message}")
            }
        }
    }

    private fun key(kind: String, id: Int) = "$kind:$id"

    private class Helper(context: Context) : SQLiteOpenHelper(context, "watch_history.db", null, 1) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE progress (" +
                    "kind TEXT NOT NULL, content_id INTEGER NOT NULL, " +
                    "position_ms INTEGER NOT NULL, duration_ms INTEGER NOT NULL, " +
                    "watched INTEGER NOT NULL, updated_at INTEGER NOT NULL, " +
                    "PRIMARY KEY (kind, content_id))"
            )
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}
    }
}
