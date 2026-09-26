package com.kdresdell.iptvtv.phone

// TODO: duplicated from the TV app's TitleFormat.kt.

// Cleans provider-supplied title prefixes like "CA FR:", "CA EN:", "US:"
// for display. Applied at render time only, never at parse/ingest -
// live_channels/vod_streams/series only re-sync when their table is empty,
// so baking this into ingest would leave already-cached names wrong until
// a manual data clear; cleaning at display time means a regex fix applies
// immediately, and search (LiveChannelDatabase.searchAll) keeps matching
// the real raw provider string.
object TitleFormat {
    // One or two ALL-CAPS tokens of 2-3 letters, then a colon and optional
    // space - deliberately narrow so a legitimate title containing a colon
    // ("Breaking News: Storm Update") is never touched: "Breaking" and
    // "News" aren't 2-3 letter all-caps tokens.
    private val PREFIX_REGEX = Regex("^\\s*[A-Z]{2,3}(?:[ \\-][A-Z]{2,3})?:\\s*")

    fun clean(raw: String): String {
        if (raw.isBlank()) return raw
        val stripped = PREFIX_REGEX.replace(raw, "")
        return stripped.ifBlank { raw }
    }
}
