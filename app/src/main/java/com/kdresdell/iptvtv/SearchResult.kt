package com.kdresdell.iptvtv

// A single search hit, tagged by content type so the results list can tell
// live channels and VOD movies apart even though they come from two
// separate SQLite tables (see LiveChannelDatabase.searchAll).
sealed class SearchResult {
    data class Live(val channel: LiveChannel) : SearchResult()
    data class Vod(val movie: VodStream) : SearchResult()
    data class Series(val series: SeriesShow) : SearchResult()
}
