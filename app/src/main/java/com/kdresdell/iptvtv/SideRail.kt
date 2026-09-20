package com.kdresdell.iptvtv

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.kdresdell.iptvtv.theme.LocalAppColors
import com.kdresdell.iptvtv.theme.RailIcons
import com.kdresdell.iptvtv.theme.appCardBorder
import com.kdresdell.iptvtv.theme.appCardGlow
import com.kdresdell.iptvtv.theme.appCardScale

enum class RailItem(val icon: ImageVector, val label: String) {
    Search(RailIcons.Search, "Search"),
    MyChannel(RailIcons.MyTv, "My TV"),
    MyVod(RailIcons.MyLibrairie, "My Librairie"),
    WhatsNew(RailIcons.WhatsNew, "What's New"),
    Categories(RailIcons.All, "All"),
    Settings(RailIcons.Settings, "Settings"),
    Help(RailIcons.Help, "Help")
}

// Persistent left-side navigation, always visible next to whatever screen
// is currently shown (except the full-screen player).
// STYLE_GUIDE.md §6.2 - approved recipe: round logo, no wordmark, rail one
// surface tone lighter than the content behind it, "selected" is a neutral
// elevated fill (never a green fill), Search never shows the selected
// treatment since it opens a screen rather than a persistent section.
@Composable
fun SideRail(
    selected: RailItem?,
    onSelect: (RailItem) -> Unit,
    hasSettingsAlert: Boolean = false,
    // The persistent app-level rail (WithRail below) never needs this - the
    // content pane next to it grabs initial focus instead. The player's
    // Left-key quick-browse overlay is the one caller that pops this rail up
    // on its own, with nothing else to focus, so it needs to claim focus for
    // itself when it appears.
    requestInitialFocus: Boolean = false,
    // WithRail passes its own instance so a Left press from content can call
    // requestFocus() on it directly and reliably, instead of depending on
    // Compose's implicit directional focus search (which real screens with
    // their own Left/Right handling - a text field's cursor keys, the EPG
    // grid's timeline scroll - could otherwise swallow before it ever
    // reaches the rail).
    firstItemFocusRequester: FocusRequester = remember { FocusRequester() }
) {
    val appColors = LocalAppColors.current
    LaunchedEffect(requestInitialFocus) {
        if (requestInitialFocus) {
            firstItemFocusRequester.requestFocus()
        }
    }
    // focusGroup() keeps Up/Down D-pad presses contained within this
    // column while focus is inside it, instead of Compose's default
    // nearest-neighbor focus search occasionally jumping sideways into
    // the content pane next to it - a real bug hit on real hardware where
    // Down from a rail item sometimes landed on a channel card instead of
    // the next rail item.
    val lastItemFocusRequester = remember { FocusRequester() }
    Row(modifier = Modifier.fillMaxHeight()) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                // Widened from 160dp so the extra left/right padding below is
                // real breathing room, not a shrink of the item pills - content
                // width (176 - 2*16 = 144dp) matches the original 160dp rail's
                // content width exactly, so labels like "My Librairie" still
                // fit on one line. Explicit user request (2026-09-17): the
                // focus pill was touching the rail edges with barely any gap.
                .width(176.dp)
                .background(appColors.surfaceContainerLow)
                .padding(vertical = 24.dp, horizontal = 16.dp)
                .focusGroup(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(R.drawable.app_logo),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    // -10% from 80dp, explicit user request (2026-09-17).
                    .size(72.dp)
                    .clip(CircleShape)
                    .border(2.dp, MaterialTheme.colorScheme.border, CircleShape)
            )
            Spacer(modifier = Modifier.padding(top = 20.dp))
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                listOf(RailItem.Search, RailItem.MyChannel, RailItem.MyVod, RailItem.WhatsNew, RailItem.Categories).forEachIndexed { index, item ->
                    RailRow(
                        item = item,
                        selected = selected,
                        onSelect = onSelect,
                        // Wrap-around: Up from the first item lands on the last.
                        modifier = if (index == 0) {
                            Modifier.focusRequester(firstItemFocusRequester)
                                .wrapKey(Key.DirectionUp) { lastItemFocusRequester.requestFocus() }
                        } else {
                            Modifier
                        }
                    )
                }
            }
            Spacer(modifier = Modifier.weight(1f))
            RailRow(item = RailItem.Help, selected = selected, onSelect = onSelect)
            RailRow(
                item = RailItem.Settings,
                selected = selected,
                onSelect = onSelect,
                showAlert = hasSettingsAlert,
                // Wrap-around: Down from the last item lands on the first.
                modifier = Modifier.focusRequester(lastItemFocusRequester)
                    .wrapKey(Key.DirectionDown) { firstItemFocusRequester.requestFocus() }
            )
        }
        // A physically-simulated elevation shadow (tried first) was barely
        // visible against bright video - confirmed on real hardware
        // (2026-09-17). Real overlay panels on video (YouTube, Netflix) use
        // an explicit gradient scrim instead, which reads clearly regardless
        // of what's playing behind it. Drawn as its own strip rather than a
        // shadow modifier so opacity/width are under direct control.
        // Narrowed and darkened - explicit user request (2026-09-17): the
        // first version (28dp, linear fade from 60% alpha) read as too wide
        // and too washed-out on real hardware. Front-loading the opacity
        // (most of the darkness in the first third, not a plain linear
        // fade) keeps it looking like a deliberate edge, not a haze.
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(16.dp)
                .background(
                    Brush.horizontalGradient(
                        colorStops = arrayOf(
                            0.0f to Color.Black.copy(alpha = 0.85f),
                            0.4f to Color.Black.copy(alpha = 0.45f),
                            1.0f to Color.Transparent
                        )
                    )
                )
        )
    }
}

@Composable
private fun RailRow(
    item: RailItem,
    selected: RailItem?,
    onSelect: (RailItem) -> Unit,
    showAlert: Boolean = false,
    modifier: Modifier = Modifier
) {
    val appColors = LocalAppColors.current
    // Search opens a screen rather than marking a persistent section, so it
    // never takes the "selected" treatment even while the Search screen is
    // current - per §6.2.
    val isSelected = item == selected && item != RailItem.Search
    val contentColor = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
    val iconColor = if (isSelected) MaterialTheme.colorScheme.primary else contentColor
    Card(
        onClick = { onSelect(item) },
        modifier = Modifier.fillMaxWidth().then(modifier),
        colors = CardDefaults.colors(
            containerColor = if (isSelected) appColors.selectedSurface else androidx.compose.ui.graphics.Color.Transparent,
            contentColor = contentColor,
            focusedContainerColor = if (isSelected) appColors.selectedSurface else appColors.surfaceContainerHigh,
            focusedContentColor = MaterialTheme.colorScheme.onSurface,
            pressedContainerColor = if (isSelected) appColors.selectedSurface else appColors.surfaceContainerHigh,
            pressedContentColor = MaterialTheme.colorScheme.onSurface
        ),
        scale = appCardScale(),
        border = appCardBorder(),
        glow = appCardGlow()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp)
        ) {
            Icon(imageVector = item.icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(24.dp))
            Text(
                text = item.label,
                style = MaterialTheme.typography.labelMedium.let {
                    if (isSelected) it.copy(fontWeight = FontWeight.Bold) else it
                }
            )
            if (showAlert) {
                Spacer(modifier = Modifier.weight(1f))
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.error)
                ) {
                    Text(
                        text = "!",
                        color = MaterialTheme.colorScheme.onError,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
        }
    }
}

// Wraps a screen's content with the rail. The app theme itself is applied
// once, at the root, in MainActivity's IptvTvTheme.
//
// The rail auto-hides: content always gets the full screen width, and the
// rail slides fully off-screen to the left the moment it loses focus,
// reappearing on top of content when the user presses Left back into it -
// per explicit direction, it should disappear entirely rather than collapse
// to an icon strip. It stays laid out (just translated off-canvas) so
// Compose's directional focus search can still find it from a Left press.
// Rail is 176dp + a 28dp gradient scrim strip = 204dp total - offset must
// clear that fully or a sliver of the scrim peeks in from the left edge.
private val HiddenRailOffset = (-210).dp

@Composable
fun WithRail(
    selected: RailItem?,
    onSelectRail: (RailItem) -> Unit,
    hasSettingsAlert: Boolean = false,
    // Back while the rail itself has focus is the app's one, uniform exit
    // gesture everywhere outside the player (see [[navigation_model_spec]]).
    // Previously this was left unconsumed to fall through to the OS's
    // undocumented default back action, which is exactly why exiting left
    // audio playing on Fire TV: an Activity that's only backgrounded (not
    // destroyed) never runs Compose's DisposableEffect release() calls.
    // Calling this explicitly instead makes exit deterministic on every
    // device - the caller is expected to release any live playback and then
    // actually finish() the Activity.
    onExitApp: () -> Unit,
    content: @Composable () -> Unit
) {
    var railFocused by remember { mutableStateOf(false) }
    val railOffset by animateDpAsState(targetValue = if (railFocused) 0.dp else HiddenRailOffset, label = "railOffset")
    val railFirstItemFocusRequester = remember { FocusRequester() }
    val contentFocusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .focusRequester(contentFocusRequester)
                // Remembers/restores whichever descendant was last focused
                // (a grid row, a search chip, whatever) so returning from
                // the rail lands back where the user left off, not at some
                // arbitrary default.
                .focusRestorer()
                .focusGroup()
                // onPreviewKeyEvent (root-to-leaf) fires before any
                // descendant's own onKeyEvent (leaf-to-root) - so this is
                // the only place that can decide Left's meaning before a
                // text field or a horizontal chip row consumes it first.
                // Try a normal local focus move first (so a horizontal row
                // - e.g. search history pills - can be navigated leftward
                // between its own items); only fall back to summoning the
                // rail when there's truly no focusable neighbor to the
                // left (every single-column screen in the app, or the
                // leftmost item of a row).
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (event.key) {
                        Key.DirectionLeft -> {
                            if (!focusManager.moveFocus(FocusDirection.Left)) {
                                railFirstItemFocusRequester.requestFocus()
                            }
                            true
                        }
                        // Back always opens the menu outright, unlike Left -
                        // it never tries a local focus move first, since
                        // Back should never double as sideways navigation
                        // (see [[navigation_model_spec]] memory: Back and
                        // Left do the same thing everywhere outside the
                        // player, uniformly, no exceptions).
                        Key.Back -> {
                            railFirstItemFocusRequester.requestFocus()
                            true
                        }
                        else -> false
                    }
                }
        ) {
            content()
        }
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .offset(x = railOffset)
                .onFocusChanged { railFocused = it.hasFocus }
                .onKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                    when (event.key) {
                        Key.DirectionRight -> {
                            contentFocusRequester.requestFocus()
                            true
                        }
                        // Back while the menu itself has focus is the app's
                        // only exit gesture: Back opens the menu (above),
                        // Back again while it's focused exits - now via an
                        // explicit onExitApp() call instead of falling
                        // through to the OS default (see the param doc above
                        // for why that mattered).
                        Key.Back -> {
                            onExitApp()
                            true
                        }
                        else -> false
                    }
                }
        ) {
            SideRail(
                selected = selected,
                // Picking an item only actually changes `screen` state (and
                // so only actually closes the rail) when it navigates to a
                // *different* screen - selecting the current screen's own
                // rail item (e.g. "Search" while already on Search) leaves
                // `screen` unchanged, no recomposition happens, and nothing
                // ever moved focus back out of the rail. Confirmed bug:
                // pressing OK on the current screen's rail item did nothing
                // visible - only the dedicated DirectionRight handler above
                // actually returned focus to content. Explicitly requesting
                // content focus here on every selection (not just Right)
                // makes OK behave the same way regardless of whether the
                // screen itself changes.
                onSelect = { item ->
                    onSelectRail(item)
                    contentFocusRequester.requestFocus()
                },
                hasSettingsAlert = hasSettingsAlert,
                firstItemFocusRequester = railFirstItemFocusRequester
            )
        }
    }
}

// Runs onWrap on a fresh press of `wrapKey` (a held key is swallowed, so it
// never loops around by itself). A preview handler: it runs before the
// row's own key handling, which can swallow Down on real hardware.
private fun Modifier.wrapKey(wrapKey: Key, onWrap: () -> Unit): Modifier = onPreviewKeyEvent { event ->
    if (event.type == KeyEventType.KeyDown && event.key == wrapKey) {
        if (event.nativeKeyEvent.repeatCount == 0) onWrap()
        true
    } else {
        false
    }
}
