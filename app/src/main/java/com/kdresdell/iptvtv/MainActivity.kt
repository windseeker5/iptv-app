package com.kdresdell.iptvtv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme as PhoneMaterialTheme
import androidx.compose.material3.darkColorScheme as phoneDarkColorScheme
import androidx.tv.material3.Card
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.tv.material3.darkColorScheme as tvDarkColorScheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val context = LocalContext.current
            val prefs = remember { ProviderPrefs(context) }
            var credentials by remember { mutableStateOf(prefs.load()) }
            var showSettings by remember { mutableStateOf(!credentials.isComplete) }

            if (showSettings) {
                PhoneMaterialTheme(colorScheme = phoneDarkColorScheme()) {
                    SettingsScreen(
                        initial = credentials,
                        onSave = { saved ->
                            prefs.save(saved)
                            credentials = saved
                            showSettings = false
                        }
                    )
                }
            } else {
                MaterialTheme(colorScheme = tvDarkColorScheme()) {
                    HelloTvScreen(
                        loggedInAs = credentials.username,
                        onEditSettings = { showSettings = true }
                    )
                }
            }
        }
    }
}

// Placeholder screen for validating D-pad focus navigation on real Google TV
// hardware before any real channel/EPG logic is wired in.
@Composable
fun HelloTvScreen(loggedInAs: String, onEditSettings: () -> Unit) {
    val placeholderChannels = listOf("Channel A", "Channel B", "Channel C", "Channel D", "Channel E")
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(24.dp)
        ) {
            item {
                Text(text = "Signed in as $loggedInAs")
            }
            items(placeholderChannels) { label ->
                Card(onClick = {}) {
                    Text(text = label, modifier = Modifier.padding(24.dp))
                }
            }
            item {
                Card(onClick = onEditSettings) {
                    Text(text = "Edit provider settings", modifier = Modifier.padding(24.dp))
                }
            }
        }
    }
}
