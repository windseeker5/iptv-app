package com.kdresdell.iptvtv

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Text
import com.kdresdell.iptvtv.theme.LocalAppColors
import com.kdresdell.iptvtv.theme.appCardBorder
import com.kdresdell.iptvtv.theme.appCardGlow
import com.kdresdell.iptvtv.theme.appRowCardScale

// Audio language + subtitle picker for movies/episodes. Same Popup shape as
// RecordingDurationMenu.kt (focusable popup, Cards, Back dismisses). The
// current choice is ticked; picking one applies it immediately and closes.
@Composable
fun TrackPickerMenu(
    expanded: Boolean,
    choices: TrackChoices,
    onDismiss: () -> Unit,
    onSelectTrack: (TrackOption) -> Unit,
    onSubtitlesOff: () -> Unit
) {
    if (!expanded) return

    BackHandler(enabled = expanded) { onDismiss() }

    val firstItemFocus = remember { FocusRequester() }
    LaunchedEffect(expanded) { firstItemFocus.requestFocus() }

    Popup(
        alignment = Alignment.Center,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true, dismissOnClickOutside = false)
    ) {
        Box(
            modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier.width(380.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (choices.isEmpty) {
                    Text(
                        text = "This video has only one audio track and no subtitles.",
                        color = MenuTextColor,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
                    )
                    MenuCard("Close", Modifier.focusRequester(firstItemFocus), onDismiss)
                } else {
                    var firstPlaced = false
                    fun focusIfFirst(): Modifier =
                        if (!firstPlaced) {
                            firstPlaced = true
                            Modifier.focusRequester(firstItemFocus)
                        } else {
                            Modifier
                        }

                    if (choices.audio.isNotEmpty()) {
                        SectionLabel("Audio")
                        choices.audio.forEach { option ->
                            MenuCard(
                                label = (if (option.selected) "✓  " else "") + option.label,
                                modifier = focusIfFirst(),
                                onClick = { onSelectTrack(option) }
                            )
                        }
                    }
                    SectionLabel("Subtitles")
                    MenuCard(
                        label = (if (choices.subtitlesOff) "✓  " else "") + "Off",
                        modifier = focusIfFirst(),
                        onClick = onSubtitlesOff
                    )
                    choices.subtitles.forEach { option ->
                        MenuCard(
                            label = (if (option.selected) "✓  " else "") + option.label,
                            modifier = focusIfFirst(),
                            onClick = { onSelectTrack(option) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        color = LocalAppColors.current.vividAccent,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp)
    )
}

@Composable
private fun MenuCard(label: String, modifier: Modifier, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        // No scale - same size focused/unfocused, see RowActionsMenu.kt's MenuItemCard.
        scale = appRowCardScale(),
        border = appCardBorder(),
        glow = appCardGlow(),
        colors = CardDefaults.colors(
            containerColor = LocalAppColors.current.surfaceContainer,
            focusedContainerColor = LocalAppColors.current.selectedSurface,
            pressedContainerColor = LocalAppColors.current.selectedSurface
        )
    ) {
        Text(
            text = label,
            color = MenuTextColor,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp)
        )
    }
}
