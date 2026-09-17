package com.kdresdell.iptvtv

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.kdresdell.iptvtv.theme.FocusDefaults
import com.kdresdell.iptvtv.theme.LocalAppColors
import com.kdresdell.iptvtv.theme.ScreenColors
import com.kdresdell.iptvtv.theme.appCardBorder
import com.kdresdell.iptvtv.theme.appCardGlow
import com.kdresdell.iptvtv.theme.appCardScale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// STYLE_GUIDE.md §6.7 - text input, hand-rolled on BasicTextField exactly
// like SearchScreen.kt's SearchField (not the mobile-material3
// OutlinedTextField the previous version of this screen used). That
// mismatch was the real cause of a real bug: OutlinedTextField pulls
// defaults from an ambient mobile Material3 theme this app never provides
// (IptvTvTheme only sets up the tv-material3 one) - only the parts this
// screen explicitly overrode rendered correctly, everything else (like the
// label) fell back to unthemed mobile-Material defaults, unreadable against
// this app's dark background. A field label lives as a separate static
// Text above the field instead of OutlinedTextField's floating label, both
// to sidestep that ambient entirely and to match this app's existing
// pattern (every other labeled control in the app is "label text, then the
// control", not a floating label).
@Composable
private fun SettingsField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    focusRequester: FocusRequester,
    imeAction: ImeAction,
    onImeAction: () -> Unit,
    onDirectionDown: () -> Boolean,
    isPassword: Boolean = false
) {
    val appColors = LocalAppColors.current
    var isFocused by remember { mutableStateOf(false) }
    val borderColor = if (isFocused) appColors.vividAccent else MaterialTheme.colorScheme.border
    val fieldShape = RoundedCornerShape(8.dp)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = label,
            // Was Title Small (20sp) - cut ~20% to Label Medium (16sp),
            // explicit request.
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(appColors.surfaceContainerLow, fieldShape)
                .border(FocusDefaults.OutlineWidth, borderColor, fieldShape)
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(appColors.vividAccent),
                visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
                keyboardOptions = KeyboardOptions(imeAction = imeAction),
                keyboardActions = KeyboardActions(
                    onNext = { onImeAction() },
                    onDone = { onImeAction() }
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .onFocusChanged { isFocused = it.isFocused }
                    // Same BasicTextField-swallows-Down issue as
                    // SearchScreen.kt's SearchField - intercepted here and
                    // handed to the caller instead of doing nothing.
                    .onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown) {
                            onDirectionDown()
                        } else {
                            false
                        }
                    }
            )
        }
    }
}

// §5/§6.7 primary "commit" action (Update, Save) - vivid-accent fill (the
// one "terminal green" used everywhere else - focus rings, progress bars,
// and the player overlay's own play/pause circle, which is the exact
// precedent this reuses: vivid accent fill + black content), not the pastel
// tone-80 swatch a previous pass used, which read as a second, unrelated
// green (or washed-out grey on real hardware). Pill-shaped to match this
// app's existing button-like controls (the search field, the player
// overlay's "OK" hint pill, HistoryPill) rather than the 8dp-corner card
// shape used for content rows - a real button reads as a button, not
// another row.
@Composable
private fun PrimaryActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pillShape = RoundedCornerShape(50)
    val accent = LocalAppColors.current.vividAccent
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = CardDefaults.shape(pillShape),
        scale = appCardScale(),
        colors = CardDefaults.colors(
            containerColor = accent,
            contentColor = Color.Black,
            focusedContainerColor = accent,
            focusedContentColor = Color.Black,
            pressedContainerColor = accent,
            pressedContentColor = Color.Black
        ),
        border = appCardBorder(shape = pillShape),
        glow = appCardGlow()
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 28.dp, vertical = 14.dp)
        )
    }
}

// Section headers ("Provider Setup", "App Update", "Error Log") at ~80% of
// the standard Headline Small (34sp -> ~27sp) - explicit request, the full
// headline size read as too heavy for a settings form. Applied uniformly to
// every section header on this screen rather than singling one out, so they
// stay visually consistent with each other.
@Composable
private fun sectionHeaderStyle() = MaterialTheme.typography.headlineSmall.let {
    it.copy(fontSize = it.fontSize * 0.8f, lineHeight = it.lineHeight * 0.8f)
}

@Composable
private fun SectionDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(ScreenColors.SectionDivider)
    )
}

// Error log entries are otherwise-inert text, but need to be real focus
// targets - a LazyColumn only auto-scrolls a focused child into view, and
// with no focusable content here at all, Down from Save had nowhere to go
// and the log was simply unreachable by D-pad (confirmed bug, not "no
// errors logged": the section was there, just impossible to scroll to).
@Composable
private fun ErrorLogRow(entry: String) {
    var isFocused by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (isFocused) LocalAppColors.current.surfaceContainer else Color.Transparent,
                RoundedCornerShape(6.dp)
            )
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(
            text = entry,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.error
        )
    }
}

// OutlinedTextField swallowed DirectionDown too, same underlying
// BasicTextField issue - see SettingsField's onPreviewKeyEvent above, which
// replaced this. Kept only as the shared 300ms hide-keyboard-after-nav
// delay every Down handler below needs (confirmed on real hardware: the
// newly-focused field/button reopens the software keyboard asynchronously,
// a frame or two after requestFocus(), so hide() has to run after that
// auto-show actually happens or the keyboard wins the race).
private fun CoroutineScope.hideKeyboardAfterNav(keyboardController: SoftwareKeyboardController?) {
    launch {
        delay(300)
        keyboardController?.hide()
    }
}

@Composable
fun SettingsScreen(
    initial: ProviderCredentials,
    onSave: (ProviderCredentials) -> Unit,
    currentVersionName: String = BuildConfig.VERSION_NAME,
    availableUpdate: UpdateInfo? = null,
    onUpdateClick: () -> Unit = {}
) {
    var serverUrl by remember { mutableStateOf(initial.serverUrl) }
    var username by remember { mutableStateOf(initial.username) }
    var password by remember { mutableStateOf(initial.password) }
    val keyboardController = LocalSoftwareKeyboardController.current

    val updateFocus = remember { FocusRequester() }
    val serverUrlFocus = remember { FocusRequester() }
    val usernameFocus = remember { FocusRequester() }
    val passwordFocus = remember { FocusRequester() }
    val saveFocus = remember { FocusRequester() }
    val coroutineScope = rememberCoroutineScope()
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()

    // Provider Setup is first on screen now (Update moved below it - see
    // the LazyColumn body), so initial focus always goes to the first
    // provider field regardless of whether an update is available. Update,
    // when present, is reached the same way the error log rows are: normal
    // Down-navigation from Save, not a separate initial-focus branch.
    LaunchedEffect(Unit) {
        serverUrlFocus.requestFocus()
        delay(300)
        keyboardController?.hide()
        listState.scrollToItem(0)
    }

    // LazyColumn (not Column + verticalScroll) so a focused item - the
    // whole point of the ErrorLogRow fix above - actually gets scrolled
    // into view; verticalScroll has no such behavior on its own. Matches
    // the list pattern already used elsewhere (CategoryListScreen.kt,
    // ChannelListScreen.kt) rather than inventing a different one here.
    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 48.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item {
            // 1. Provider - first on screen. This is a settings form
            // someone opens to actually change something; a passive
            // "you're up to date" status doesn't belong ahead of it, and an
            // available update is an action, not routine status, so it
            // moved below (see the end of this list) - both explicit
            // requests.
            Text(text = "Provider Setup", style = sectionHeaderStyle(), color = MaterialTheme.colorScheme.onSurface)
        }
        item {
            SettingsField(
                label = "Server URL (e.g. http://host:port)",
                value = serverUrl,
                onValueChange = { serverUrl = it },
                focusRequester = serverUrlFocus,
                imeAction = ImeAction.Next,
                onImeAction = { usernameFocus.requestFocus() },
                onDirectionDown = {
                    usernameFocus.requestFocus()
                    coroutineScope.hideKeyboardAfterNav(keyboardController)
                    true
                }
            )
        }
        item {
            SettingsField(
                label = "Username",
                value = username,
                onValueChange = { username = it },
                focusRequester = usernameFocus,
                imeAction = ImeAction.Next,
                onImeAction = { passwordFocus.requestFocus() },
                onDirectionDown = {
                    passwordFocus.requestFocus()
                    coroutineScope.hideKeyboardAfterNav(keyboardController)
                    true
                }
            )
        }
        item {
            SettingsField(
                label = "Password",
                value = password,
                onValueChange = { password = it },
                focusRequester = passwordFocus,
                imeAction = ImeAction.Done,
                onImeAction = {
                    keyboardController?.hide()
                    saveFocus.requestFocus()
                },
                onDirectionDown = {
                    saveFocus.requestFocus()
                    coroutineScope.hideKeyboardAfterNav(keyboardController)
                    true
                },
                isPassword = true
            )
        }
        item {
            PrimaryActionButton(
                text = "Save",
                onClick = { onSave(ProviderCredentials(serverUrl.trim(), username.trim(), password)) },
                modifier = Modifier.focusRequester(saveFocus)
            )
        }

        // 2. App Update - moved below Provider Setup (was above it) and,
        // like the error log below, only rendered at all when there's
        // something to act on: no more permanent "Up to date" status line
        // cluttering the screen when there's nothing to do. Reachable the
        // same way the error log is - plain Down-navigation from Save, no
        // special-cased initial focus needed since Card (unlike the text
        // fields) doesn't swallow Down.
        if (availableUpdate != null) {
            item { SectionDivider() }
            item {
                Text(text = "App Update", style = sectionHeaderStyle(), color = MaterialTheme.colorScheme.onSurface)
            }
            item {
                Text(text = "Version $currentVersionName", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item {
                // §6.7: passive callout, plain gray outline - not green,
                // since green is reserved for focus/action.
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(LocalAppColors.current.surfaceContainer, RoundedCornerShape(8.dp))
                        .border(1.dp, MaterialTheme.colorScheme.border, RoundedCornerShape(8.dp))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "What's new in ${availableUpdate.versionName}",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = availableUpdate.notes,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            item {
                PrimaryActionButton(
                    text = "Update to ${availableUpdate.versionName}",
                    onClick = onUpdateClick,
                    modifier = Modifier.focusRequester(updateFocus)
                )
            }
        }

        // 3. Error log (prototype-stage debugging - no adb/logcat access for
        // the people actually testing this on real TVs). Section - divider,
        // header, and all - only exists when there's actually something to
        // show: an empty "Error Log / No errors logged" state had nothing
        // focusable below Save, so Down had nowhere to go and the section
        // was structurally unreachable (confirmed bug, not "no errors").
        // Note this log is in-memory only (AppLog.kt) - it resets on every
        // app restart, so "empty" only ever means "nothing since the last
        // launch," never "this app has never errored." As of this pass,
        // most of the app's actual error paths (XtreamApi network/parsing
        // failures surfaced through MainActivity.kt's LoadState.Error
        // branches, corrupt local favorites/saved-VOD data) now actually
        // report here - previously only update-check and recording failures
        // did, which is the real reason this so often looked suspiciously
        // empty.
        if (AppLog.entries.isNotEmpty()) {
            item { SectionDivider() }
            item {
                // Standard M3 error tone - never green, so this never reads
                // as "green = good".
                Text(text = "Error Log", style = sectionHeaderStyle(), color = MaterialTheme.colorScheme.onSurface)
            }
            items(AppLog.entries) { entry ->
                ErrorLogRow(entry)
            }
        }
    }
}
