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

val MenuTextColor = Color(0xFFE4E7E4)
val MenuDestructiveTextColor = Color(0xFFF2B8B5) // §2.7 Error tone

// One item in a long-press action menu. `isDestructive` tints the label in
// the app's Error tone instead of the plain menu text color - used for
// actions like "Remove from Recordings" that delete rather than toggle.
data class MenuAction(
    val label: String,
    val onClick: () -> Unit,
    val isDestructive: Boolean = false
)

// Long-press context menu shared by every list row in the app (Search,
// Favorites, Channels-in-category, My VOD). Card's own onLongClick (native
// to androidx.tv.material3, not a custom hold-duration hack) opens this;
// short press still plays/opens the item as before.
@Composable
fun RowActionsMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    actions: List<MenuAction>
) {
    if (!expanded) return

    BackHandler(enabled = expanded) { onDismiss() }

    val firstItemFocus = remember { FocusRequester() }
    // Two separate problems, two separate fixes - don't conflate them:
    // 1) Focus must move into the popup IMMEDIATELY (no delay), or a fast
    //    real-remote Down press arrives before focus has entered it and
    //    goes nowhere (confirmed on real hardware).
    // 2) But moving focus immediately means it happens while the physical
    //    long-press button is still literally held down. When the user
    //    then releases it, that key-up lands on the now-focused menu item
    //    (which never saw its own key-down) and gets misread as a click -
    //    confirmed on real hardware: releasing the long-press instantly
    //    "selected" whatever item ended up focused, with no chance to
    //    navigate first. A delay here just moves *which* input this
    //    happens to (still breaks either navigation or the release).
    //    Fix: focus moves immediately (so Down always works), but clicks
    //    are ignored for a short grace window after opening - long enough
    //    to absorb the stray release, short enough that a deliberate
    //    press after actually navigating still registers normally.
    var readyToSelect by remember { mutableStateOf(false) }
    LaunchedEffect(expanded) {
        if (expanded) {
            readyToSelect = false
            firstItemFocus.requestFocus()
            delay(500)
            readyToSelect = true
        }
    }

    // Popup defaults to focusable = false, which left D-pad input routed to
    // the row underneath - Center on the (visually focused) first menu item
    // was actually still hitting the row's own onClick (play) behind it.
    // Confirmed on real hardware before this fix: the menu could open, but
    // selecting an item played the row instead of acting on the menu.
    // dismissOnClickOutside is also turned off - dismissal is entirely
    // explicit (Back, or picking a menu item) so no stray input during the
    // long-press release gets misread as an "outside" dismissal.
    //
    // alignment = Center (rather than the previous default, unset position)
    // means the menu always lands in the same predictable spot regardless
    // of which row triggered it, or how close to a screen edge that row
    // was - it no longer depends on where in the layout tree Popup happens
    // to place an unaligned popup.
    Popup(
        alignment = Alignment.Center,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true, dismissOnClickOutside = false)
    ) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)), contentAlignment = Alignment.Center) {
            Column(
                // A fixed width, not widthIn(min=...): a fillMaxWidth() Card
                // inside an otherwise-unconstrained Column forces the Column
                // itself to claim the full incoming max width (the whole
                // screen, via the scrim Box above) instead of shrinking to
                // its content - confirmed on-device (the menu stretched
                // edge-to-edge instead of appearing as a small centered
                // dialog). A fixed width sidesteps that Compose sizing
                // interaction entirely.
                //
                // No container background here on purpose - each item Card
                // already has its own surface-container fill/border/glow, so
                // a second background behind them just read as an ugly grey
                // box padded around already-styled buttons. The items float
                // directly on the scrim, spaced apart.
                modifier = Modifier.width(340.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                actions.forEachIndexed { index, action ->
                    MenuItemCard(
                        action = action,
                        readyToSelect = { readyToSelect },
                        onDismiss = onDismiss,
                        modifier = if (index == 0) Modifier.focusRequester(firstItemFocus) else Modifier
                    )
                }
            }
        }
    }
}

// Outline + glow only, no scale - same as ChannelRow.kt/VodTiles.kt's rows
// (appRowCardScale()). A focused item growing larger than an unfocused one
// is exactly the bug already fixed for list rows; menu items get the same
// fix so "selected" and "not selected" stay the same size. Every item
// renders at the same fillMaxWidth() size regardless of label length,
// fixing the previous "two different-size grey buttons" look.
@Composable
private fun MenuItemCard(
    action: MenuAction,
    readyToSelect: () -> Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = {
            if (readyToSelect()) {
                action.onClick()
                onDismiss()
            }
        },
        modifier = modifier.fillMaxWidth(),
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
            text = action.label,
            color = if (action.isDestructive) MenuDestructiveTextColor else MenuTextColor,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp)
        )
    }
}
