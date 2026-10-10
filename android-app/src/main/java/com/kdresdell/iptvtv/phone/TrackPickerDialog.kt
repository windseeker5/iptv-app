package com.kdresdell.iptvtv.phone

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

// Audio language + subtitle picker for movies/episodes. Picking applies
// immediately; the dialog stays open so audio and subtitles can both be set.
@Composable
fun TrackPickerDialog(
    choices: TrackChoices,
    onSelectTrack: (TrackOption) -> Unit,
    onSubtitlesOff: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
        title = { Text("Audio & subtitles") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                if (choices.isEmpty) {
                    Text("This video has only one audio track and no subtitles.")
                    return@Column
                }
                if (choices.audio.isNotEmpty()) {
                    SectionLabel("Audio")
                    choices.audio.forEach { option ->
                        OptionRow(option.label, option.selected) { onSelectTrack(option) }
                    }
                }
                SectionLabel("Subtitles")
                OptionRow("Off", choices.subtitlesOff, onSubtitlesOff)
                choices.subtitles.forEach { option ->
                    OptionRow(option.label, option.selected) { onSelectTrack(option) }
                }
            }
        }
    )
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
    )
}

@Composable
private fun OptionRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}
