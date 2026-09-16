package com.kdresdell.iptvtv

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.material3.darkColorScheme
import androidx.tv.material3.Card
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// OutlinedTextField swallows DirectionDown itself (cursor-movement key
// handling inside BasicTextField) before it ever reaches Compose's normal
// focus-search - confirmed on real hardware where Down did nothing at all
// while a field was focused, keyboard open or closed. onPreviewKeyEvent
// intercepts it on the way in, ahead of the field's own handling, and
// moves focus explicitly instead. Also confirmed on real hardware: the
// newly-focused field reopens the software keyboard asynchronously (a
// frame or two after requestFocus(), not in the same call), which then
// owns all further D-pad input (its own on-screen grid) until dismissed -
// hide() has to run *after* that auto-show actually happens, not right
// after requestFocus(), or the keyboard wins the race and reopens anyway.
private fun Modifier.advanceFocusOnDown(
    next: FocusRequester,
    keyboardController: SoftwareKeyboardController?,
    scope: CoroutineScope
): Modifier =
    onPreviewKeyEvent { event ->
        if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown) {
            next.requestFocus()
            scope.launch {
                delay(300)
                keyboardController?.hide()
            }
            true
        } else {
            false
        }
    }

// Self-contained theming: this screen is shown both standalone (first
// run, no credentials yet) and nested inside the side rail's TV-themed
// layout (editing existing settings) - wrapping its own theme here means
// it renders correctly with readable contrast either way.
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

    // App Update comes first on screen and gets initial focus whenever
    // there's an update - that's the one thing people keep coming to
    // Settings to check/reach, and burying it below three text fields
    // meant it was only reachable by fighting the software keyboard's
    // own D-pad handling first. When there's nothing to update, initial
    // focus falls back to the first provider field as before.
    LaunchedEffect(Unit) {
        if (availableUpdate != null) {
            updateFocus.requestFocus()
        } else {
            serverUrlFocus.requestFocus()
            delay(300)
            keyboardController?.hide()
        }
    }

    MaterialTheme(colorScheme = darkColorScheme()) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(48.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Update - first thing on screen, so it's reachable
            // immediately without ever touching a text field.
            Text(text = "App Update", style = MaterialTheme.typography.headlineSmall)
            Text(text = "Version $currentVersionName", style = MaterialTheme.typography.bodyMedium)
            if (availableUpdate != null) {
                Text(
                    text = "What's new in ${availableUpdate.versionName}: ${availableUpdate.notes}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Card(
                    onClick = onUpdateClick,
                    modifier = Modifier.focusRequester(updateFocus)
                ) {
                    Text("Update to ${availableUpdate.versionName}", modifier = Modifier.padding(12.dp))
                }
            } else {
                Text(text = "Up to date", style = MaterialTheme.typography.bodyMedium)
            }

            HorizontalDivider()

            // 2. Provider
            Text(text = "IPTV Provider Setup", style = MaterialTheme.typography.headlineMedium)

            OutlinedTextField(
                value = serverUrl,
                onValueChange = { serverUrl = it },
                label = { Text("Server URL (e.g. http://host:port)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { usernameFocus.requestFocus() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(serverUrlFocus)
                    .advanceFocusOnDown(usernameFocus, keyboardController, coroutineScope)
            )
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("Username") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { passwordFocus.requestFocus() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(usernameFocus)
                    .advanceFocusOnDown(passwordFocus, keyboardController, coroutineScope)
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    keyboardController?.hide()
                    saveFocus.requestFocus()
                }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(passwordFocus)
                    .advanceFocusOnDown(saveFocus, keyboardController, coroutineScope)
            )

            Card(
                onClick = { onSave(ProviderCredentials(serverUrl.trim(), username.trim(), password)) },
                modifier = Modifier.focusRequester(saveFocus)
            ) {
                Text("Save", modifier = Modifier.padding(12.dp))
            }

            HorizontalDivider()

            // 3. Error log (prototype-stage debugging - no adb/logcat access
            // for the people actually testing this on real TVs).
            Text(text = "Error Log", style = MaterialTheme.typography.headlineSmall)
            if (AppLog.entries.isEmpty()) {
                Text(text = "No errors logged", style = MaterialTheme.typography.bodySmall)
            } else {
                AppLog.entries.forEach { entry ->
                    Text(text = entry, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
    }
}
