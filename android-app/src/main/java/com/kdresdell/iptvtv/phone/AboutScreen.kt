package com.kdresdell.iptvtv.phone

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

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

@Composable
fun AboutScreen(
    updateState: UpdateState,
    onCheckForUpdate: () -> Unit,
    onInstallUpdate: (UpdateInfo) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("KDTV Phone", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            "Version ${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})",
            style = MaterialTheme.typography.bodyLarge
        )

        Text("Updates", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
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
