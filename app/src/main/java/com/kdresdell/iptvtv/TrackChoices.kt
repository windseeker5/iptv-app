package com.kdresdell.iptvtv

// TODO: duplicated in the phone app's TrackChoices.kt (the UI differs, the
// track logic and the saved preference are the same).

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import java.util.Locale

// One audio or subtitle track the stream offers. Only what is embedded in
// the stream shows up - the provider (Xtream) sends no separate subtitle
// files, so a title with a single audio track and no subtitles has nothing
// to pick from.
data class TrackOption(
    val label: String,
    // ISO language code from the stream, null when it doesn't say.
    val language: String?,
    val group: Tracks.Group,
    val trackIndex: Int,
    val selected: Boolean
)

data class TrackChoices(
    val audio: List<TrackOption> = emptyList(),
    val subtitles: List<TrackOption> = emptyList()
) {
    val subtitlesOff: Boolean get() = subtitles.none { it.selected }
    val isEmpty: Boolean get() = audio.size < 2 && subtitles.isEmpty()
}

fun Player.trackChoices(): TrackChoices {
    val audio = mutableListOf<TrackOption>()
    val subtitles = mutableListOf<TrackOption>()
    for (group in currentTracks.groups) {
        val target = when (group.type) {
            C.TRACK_TYPE_AUDIO -> audio
            C.TRACK_TYPE_TEXT -> subtitles
            else -> continue
        }
        for (i in 0 until group.length) {
            if (!group.isTrackSupported(i)) continue
            val format = group.getTrackFormat(i)
            val language = format.language?.takeIf { it.isNotBlank() && it != "und" }
            val name = language
                ?.let { Locale.forLanguageTag(it).getDisplayLanguage(Locale.getDefault()) }
                ?.takeIf { it.isNotBlank() && !it.equals(language, ignoreCase = true) }
                ?.replaceFirstChar { it.uppercase() }
                ?: format.label
                ?: language
                ?: "Track ${target.size + 1}"
            val surround = if (group.type == C.TRACK_TYPE_AUDIO && format.channelCount >= 6) " 5.1" else ""
            target += TrackOption(name + surround, language, group, i, group.isTrackSelected(i))
        }
    }
    return TrackChoices(numberDuplicates(audio), numberDuplicates(subtitles))
}

// Two "English" tracks (e.g. stereo + commentary) would be indistinguishable.
private fun numberDuplicates(options: List<TrackOption>): List<TrackOption> {
    val seen = mutableMapOf<String, Int>()
    return options.map { option ->
        if (options.count { it.label == option.label } < 2) {
            option
        } else {
            val n = (seen[option.label] ?: 0) + 1
            seen[option.label] = n
            option.copy(label = "${option.label} ($n)")
        }
    }
}

// Re-reads the available tracks whenever the player finds or switches them
// (they only appear once the stream has started loading).
@Composable
fun rememberTrackChoices(player: Player): TrackChoices {
    var choices by remember(player) { mutableStateOf(player.trackChoices()) }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onTracksChanged(tracks: Tracks) {
                choices = player.trackChoices()
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }
    return choices
}

// The choice is remembered by language (not by track number, which differs
// between titles) and applied to the next movie/episode that starts.
class TrackPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("track_prefs", Context.MODE_PRIVATE)

    val audioLanguage: String? get() = prefs.getString(KEY_AUDIO, null)

    // null = never chosen, SUBTITLES_OFF = turned off, else a language code.
    val subtitleLanguage: String? get() = prefs.getString(KEY_SUBTITLES, null)

    fun saveAudio(language: String?) {
        if (language != null) prefs.edit().putString(KEY_AUDIO, language).apply()
    }

    fun saveSubtitles(language: String?) {
        if (language != null) prefs.edit().putString(KEY_SUBTITLES, language).apply()
    }

    companion object {
        const val SUBTITLES_OFF = "off"
        private const val KEY_AUDIO = "audio_language"
        private const val KEY_SUBTITLES = "subtitle_language"
    }
}

// Call right after building a movie/episode player. Languages the stream
// doesn't have simply fall back to the stream's own defaults.
fun Player.applyTrackPrefs(prefs: TrackPrefs) {
    val builder = trackSelectionParameters.buildUpon()
    prefs.audioLanguage?.let { builder.setPreferredAudioLanguage(it) }
    when (val subtitles = prefs.subtitleLanguage) {
        null -> {}
        TrackPrefs.SUBTITLES_OFF -> builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
        else -> builder.setPreferredTextLanguage(subtitles)
    }
    trackSelectionParameters = builder.build()
}

fun Player.selectTrack(option: TrackOption, prefs: TrackPrefs) {
    trackSelectionParameters = trackSelectionParameters.buildUpon()
        .setTrackTypeDisabled(option.group.type, false)
        .setOverrideForType(TrackSelectionOverride(option.group.mediaTrackGroup, option.trackIndex))
        .build()
    if (option.group.type == C.TRACK_TYPE_AUDIO) {
        prefs.saveAudio(option.language)
    } else {
        prefs.saveSubtitles(option.language)
    }
}

fun Player.turnSubtitlesOff(prefs: TrackPrefs) {
    trackSelectionParameters = trackSelectionParameters.buildUpon()
        .clearOverridesOfType(C.TRACK_TYPE_TEXT)
        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
        .build()
    prefs.saveSubtitles(TrackPrefs.SUBTITLES_OFF)
}
