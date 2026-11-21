package com.kdresdell.iptvtv

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

// Keeps the local guide cache (channel_epg_window) filled from the
// provider's xmltv.php - the only EPG source on this provider with correct
// times (see XtreamApi.fetchXmltvPrograms). Screens never call this to get
// data: they read the cache, which is why the guide stays instant. This
// only ever runs in the background, and only when something is missing or
// older than MAX_AGE_MILLIS, so a normal launch does no network work at all.
//
// The guide is synced per CATEGORY, not per channel: the categories your
// favorites and played channels belong to are small (a few hundred
// channels), and covering all of them means zapping around or searching
// finds guide data without another ~74MB download.
class EpgSync(private val api: XtreamApi, private val db: LiveChannelDatabase) {
    // One download at a time, app-wide: the boot refresh, a favorites
    // change and the player asking for a channel must never stack up.
    private val mutex = Mutex()
    @Volatile private var lastRunFinishedAt = 0L

    // Bumped after every successful download, whoever asked for it. The My
    // TV guide reloads on this - it used to reload only when its own call
    // did the download, so when the player's call (the app opens straight
    // into the last channel) got there first, the guide stayed empty until
    // the app was restarted while OK showed the show fine (2026-10-27).
    private val _updates = MutableStateFlow(0)
    val updates: StateFlow<Int> = _updates

    // Returns true when the cache was actually refreshed. Throws on network
    // or parse failure, leaving the existing cache untouched. minGapMillis
    // rate-limits callers that fire on user actions (channel surfing) so
    // hopping through several new categories costs one download, not one
    // per hop; the categories are remembered and picked up by that download.
    // force (Settings' "Refresh list now") downloads even when fresh.
    suspend fun refreshIfStale(channels: List<LiveChannel>, minGapMillis: Long = 0L, force: Boolean = false): Boolean =
        withContext(Dispatchers.IO) {
            val categories = channels.map { it.categoryId }.filter { it.isNotBlank() }.distinct()
            if (categories.isEmpty()) return@withContext false
            db.touchEpgCategories(categories)
            if (!force && db.staleEpgCategories(categories, MAX_AGE_MILLIS).isEmpty()) return@withContext false

            mutex.withLock {
                // Someone else may have refreshed while this call waited.
                val stale = if (force) categories else db.staleEpgCategories(categories, MAX_AGE_MILLIS)
                if (stale.isEmpty()) return@withLock false
                if (!force && System.currentTimeMillis() - lastRunFinishedAt < minGapMillis) return@withLock false

                // Every recently-wanted category is refreshed in the same
                // download, not just the stale one - it's the same file.
                val active = db.activeEpgCategories(RETAIN_MILLIS)
                stale.forEach { categoryId -> db.replaceEpgChannelIds(categoryId, api.getEpgChannelIds(categoryId)) }
                val streamsByEpgId = db.streamIdsByEpgChannelId(active)

                val startedAt = System.currentTimeMillis()
                AppLog.log("Guide: downloading for ${active.size} categories (${streamsByEpgId.size} guide channels)")
                if (streamsByEpgId.isNotEmpty()) {
                    val programs = api.fetchXmltvPrograms(streamsByEpgId.keys)
                    // A provider hiccup that returns an empty guide must
                    // not wipe a good cache.
                    if (programs.isEmpty()) {
                        throw XtreamApiException("Guide file had no programmes for the requested channels")
                    }
                    val windows = HashMap<Int, List<EpgProgram>>()
                    streamsByEpgId.forEach { (epgId, streamIds) ->
                        streamIds.forEach { streamId -> windows[streamId] = programs[epgId].orEmpty() }
                    }
                    db.setEpgWindows(windows)
                    AppLog.log(
                        "Guide updated: ${programs.size} of ${streamsByEpgId.size} guide channels have shows, " +
                            "took ${(System.currentTimeMillis() - startedAt) / 1000}s"
                    )
                }
                db.markEpgCategoriesSynced(active)
                db.pruneEpg(RETAIN_MILLIS)
                lastRunFinishedAt = System.currentTimeMillis()
                _updates.value++
                true
            }
        }

    companion object {
        // The provider's guide covers ~2 days ahead; refreshing twice a day
        // keeps "now/next" accurate with a wide margin.
        const val MAX_AGE_MILLIS = 12 * 60 * 60 * 1000L

        // A category nobody has asked for in a week stops being refreshed.
        const val RETAIN_MILLIS = 7 * 24 * 60 * 60 * 1000L
    }
}
