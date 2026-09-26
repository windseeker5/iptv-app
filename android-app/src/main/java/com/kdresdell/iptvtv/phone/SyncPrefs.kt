package com.kdresdell.iptvtv.phone

import android.content.Context

// When the catalog / guide were last pulled from the provider. Same
// SharedPreferences file name as the TV app's LiveChannelDatabase.syncPrefs.
class SyncPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("catalog_sync", Context.MODE_PRIVATE)

    // Epoch millis of the last successful catalog sync, or null if never.
    fun catalogSyncedAt(): Long? = prefs.getLong(KEY_CATALOG, 0L).takeIf { it > 0L }

    fun markCatalogSynced(atMillis: Long = System.currentTimeMillis()) {
        prefs.edit().putLong(KEY_CATALOG, atMillis).apply()
    }

    // The guide cache only covers the channels that were saved when it was
    // fetched, so it's stale if it's old OR a channel was added since.
    fun isEpgStale(wantedEpgIds: Set<String>, nowMillis: Long = System.currentTimeMillis()): Boolean {
        val syncedAt = prefs.getLong(KEY_EPG, 0L)
        val syncedIds = prefs.getStringSet(KEY_EPG_IDS, emptySet()) ?: emptySet()
        return nowMillis - syncedAt > EPG_MAX_AGE_MILLIS || !syncedIds.containsAll(wantedEpgIds)
    }

    fun markEpgSynced(epgIds: Set<String>, atMillis: Long = System.currentTimeMillis()) {
        prefs.edit().putLong(KEY_EPG, atMillis).putStringSet(KEY_EPG_IDS, epgIds).apply()
    }

    companion object {
        private const val KEY_CATALOG = "catalog_synced_at"
        private const val KEY_EPG = "epg_synced_at"
        private const val KEY_EPG_IDS = "epg_synced_ids"

        // Same max age as the TV app's EpgSync. xmltv.php covers ~2 days.
        private const val EPG_MAX_AGE_MILLIS = 12L * 60 * 60 * 1000
    }
}
