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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import kotlinx.coroutines.delay

// Shown when long-press starts a *new* recording (stopping one already in
// progress skips this entirely - see PlayerScreen). Same Popup shape and
// focus/grace-window handling as RowActionsMenu.kt: focus must move into
// the popup immediately (so a fast Down after the long-press still works),
// but clicks are ignored for a short window to absorb the stray key-up
// from releasing the long-press itself, which otherwise gets misread as an
// immediate selection of whatever ended up focused.
@Composable
fun RecordingDurationMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onSelect: (durationMinutes: Int?) -> Unit
) {
    if (!expanded) return

    BackHandler(enabled = expanded) { onDismiss() }

    val firstItemFocus = remember { FocusRequester() }
    var readyToSelect by remember { mutableStateOf(false) }
    LaunchedEffect(expanded) {
        if (expanded) {
            readyToSelect = false
            firstItemFocus.requestFocus()
            delay(500)
            readyToSelect = true
        }
    }

    val options = listOf(
        "30 min" to 30,
        "60 min" to 60,
        "90 min" to 90,
        "120 min" to 120,
        "Until I stop it" to null
    )

    Popup(
        alignment = Alignment.Center,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true, dismissOnClickOutside = false)
    ) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)), contentAlignment = Alignment.Center) {
            Column(
                // Fixed width, no container background - see RowActionsMenu.kt.
                modifier = Modifier.width(340.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                options.forEachIndexed { index, (label, minutes) ->
                    Card(
                        onClick = {
                            if (readyToSelect) onSelect(minutes)
                        },
                        modifier = (if (index == 0) Modifier.focusRequester(firstItemFocus) else Modifier).fillMaxWidth(),
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
            }
        }
    }
}
