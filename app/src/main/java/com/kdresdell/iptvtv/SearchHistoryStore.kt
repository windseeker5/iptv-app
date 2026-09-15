package com.kdresdell.iptvtv

import android.content.Context

// Local-only list of past search terms, most recent first, shown as
// re-clickable pills under the search box. Re-searching a term already in
// history moves it to the front instead of duplicating it (case-insensitive).
class SearchHistoryStore(context: Context) {
    private val prefs = context.getSharedPreferences("search_history", Context.MODE_PRIVATE)

    fun load(): List<String> {
        val raw = prefs.getString(KEY_HISTORY, null) ?: return emptyList()
        return raw.split(DELIMITER).filter { it.isNotBlank() }
    }

    fun add(query: String): List<String> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return load()
        val updated = listOf(trimmed) + load().filterNot { it.equals(trimmed, ignoreCase = true) }
        val capped = updated.take(MAX_ENTRIES)
        prefs.edit().putString(KEY_HISTORY, capped.joinToString(DELIMITER)).apply()
        return capped
    }

    fun clear(): List<String> {
        prefs.edit().remove(KEY_HISTORY).apply()
        return emptyList()
    }

    companion object {
        private const val KEY_HISTORY = "queries"
        private const val DELIMITER = ""
        private const val MAX_ENTRIES = 10
    }
}
