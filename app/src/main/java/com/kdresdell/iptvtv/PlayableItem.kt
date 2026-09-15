package com.kdresdell.iptvtv

// What's currently loaded in the player - unifies live channels, VOD
// movies, and series episodes under one type so MainActivity can have a
// single "now playing" screen instead of three separate ones. That
// unification is what lets switching content (e.g. picking something new
// from the in-player browse overlay) update in place rather than tearing
// down and rebuilding the whole player screen.
sealed class PlayableItem {
    data class Live(val channel: LiveChannel) : PlayableItem()
    data class Vod(val movie: VodStream) : PlayableItem()
    data class Episode(val episode: SeriesEpisode, val seriesName: String, val cover: String) : PlayableItem()
}
