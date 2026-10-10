package com.kdresdell.iptvtv.phone

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Where the phone app's self-update stands. The check/download/install
// calls live in MainActivity so the launch-time check and this screen
// share one state.
sealed class UpdateState {
    data object Idle : UpdateState()
    data object Checking : UpdateState()
    data object UpToDate : UpdateState()
    data class Available(val info: UpdateInfo) : UpdateState()
    data class Downloading(val info: UpdateInfo) : UpdateState()
    data class Failed(val message: String) : UpdateState()
}

// Settings: the provider login, the provider list (when it was last
// downloaded + refresh), and About & updates - one scrolling page of cards.
@Composable
fun SettingsScreen(
    credentials: ProviderCredentials,
    onSaveCredentials: (ProviderCredentials) -> Unit,
    stats: CatalogStats,
    lastRefreshLabel: String,
    onRefresh: () -> Unit,
    updateState: UpdateState,
    onCheckForUpdate: () -> Unit,
    onInstallUpdate: (UpdateInfo) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        ProviderCard(credentials, onSaveCredentials)
        ProviderListCard(stats, lastRefreshLabel, onRefresh)
        UpdatesCard(updateState, onCheckForUpdate, onInstallUpdate)
    }
}

// Editing the login and saving re-downloads the whole list from the new
// details, so Save only lights up once something actually changed.
@Composable
private fun ProviderCard(credentials: ProviderCredentials, onSave: (ProviderCredentials) -> Unit) {
    var serverUrl by rememberSaveable(credentials) { mutableStateOf(credentials.serverUrl) }
    var username by rememberSaveable(credentials) { mutableStateOf(credentials.username) }
    var password by rememberSaveable(credentials) { mutableStateOf(credentials.password) }
    var showPassword by rememberSaveable { mutableStateOf(false) }
    val edited = ProviderCredentials(serverUrl.trim(), username.trim(), password)
    val changed = edited != credentials

    SettingsCard("Provider") {
        OutlinedTextField(
            value = serverUrl,
            onValueChange = { serverUrl = it },
            label = { Text("Server URL") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            label = { Text("Username") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            singleLine = true,
            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            trailingIcon = {
                TextButton(onClick = { showPassword = !showPassword }) {
                    Text(if (showPassword) "Hide" else "Show")
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
        if (changed) {
            Text(
                "Saving will download the list again from these details.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Button(onClick = { onSave(edited) }, enabled = changed && edited.isComplete) {
            Text("Save")
        }
    }
}

@Composable
private fun ProviderListCard(stats: CatalogStats, lastRefreshLabel: String, onRefresh: () -> Unit) {
    SettingsCard("Provider list") {
        InfoLine("Last refresh", lastRefreshLabel)
        InfoLine("Live channels", stats.liveCount.toString())
        InfoLine("Movies", stats.vodCount.toString())
        InfoLine("TV shows", stats.seriesCount.toString())
        stats.account.expDateEpochSeconds?.let { expires ->
            InfoLine("Subscription expires", SimpleDateFormat("dd/MM/yyyy", Locale.US).format(Date(expires * 1000)))
        }
        Text(
            "Live events and new titles are added by the provider during the day - refresh to get them.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Button(onClick = onRefresh) {
            Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
            Text("Refresh now")
        }
    }
}

@Composable
private fun UpdatesCard(
    updateState: UpdateState,
    onCheckForUpdate: () -> Unit,
    onInstallUpdate: (UpdateInfo) -> Unit
) {
    SettingsCard("About & updates") {
        InfoLine("Version", "${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})")
        when (updateState) {
            is UpdateState.Idle -> OutlinedButton(onClick = onCheckForUpdate) { Text("Check for updates") }
            is UpdateState.Checking -> Text("Checking for updates...")
            is UpdateState.UpToDate -> {
                Text("You're on the latest version.")
                OutlinedButton(onClick = onCheckForUpdate) { Text("Check again") }
            }
            is UpdateState.Available -> {
                Text(
                    "Version ${updateState.info.versionName} is available.",
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                if (updateState.info.notes.isNotBlank()) {
                    Text(updateState.info.notes, style = MaterialTheme.typography.bodyMedium)
                }
                Button(onClick = { onInstallUpdate(updateState.info) }) { Text("Download and install") }
            }
            is UpdateState.Downloading -> Text("Downloading version ${updateState.info.versionName}...")
            is UpdateState.Failed -> {
                Text("Update failed: ${updateState.message}")
                OutlinedButton(onClick = onCheckForUpdate) { Text("Try again") }
            }
        }
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        content()
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}
