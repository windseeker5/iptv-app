package com.kdresdell.iptvtv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme as PhoneMaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text as PhoneText
import androidx.compose.material3.darkColorScheme as phoneDarkColorScheme
import androidx.tv.material3.Card
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.delay

// Xtream has no search endpoint. The full catalog is synced once into a
// local SQLite table (see LiveChannelDatabase/XtreamApi.syncAllLiveChannelsInto)
// and this screen queries that with SQL LIKE instead of holding/filtering
// a big Kotlin list - a real provider's catalog can be huge (tens of
// thousands of channels), and filtering that in the JVM on every keystroke
// caused real ANRs on real hardware.
@Composable
fun SearchScreen(
    syncState: LoadState<Unit>,
    syncedCount: Int,
    onSearch: suspend (String) -> List<LiveChannel>,
    isFavorite: (Int) -> Boolean,
    onPlay: (LiveChannel) -> Unit,
    onToggleFavorite: (LiveChannel) -> Unit,
    onBack: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<LiveChannel>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    val onBackground = MaterialTheme.colorScheme.onBackground
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    // As soon as the local index is ready, jump straight into the text
    // field and pop the keyboard - no reason to make the user navigate to
    // it and press select first.
    LaunchedEffect(syncState) {
        if (syncState is LoadState.Success) {
            focusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    LaunchedEffect(query, syncState) {
        if (syncState !is LoadState.Success || query.isBlank()) {
            isSearching = false
            results = emptyList()
            return@LaunchedEffect
        }
        isSearching = true
        delay(300) // debounce - only search once typing pauses
        results = onSearch(query)
        isSearching = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        PhoneMaterialTheme(colorScheme = phoneDarkColorScheme()) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { PhoneText("Search channels") },
                singleLine = true,
                enabled = syncState is LoadState.Success,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
            )
        }

        when (syncState) {
            is LoadState.Loading -> Text(
                text = "Preparing search - indexed $syncedCount channels so far...",
                color = onBackground
            )
            is LoadState.Error -> Text(text = "Could not load channels: ${syncState.message}", color = onBackground)
            is LoadState.Success -> {
                if (isSearching) {
                    Text(text = "Searching...", color = onBackground)
                } else if (query.isNotBlank() && results.isEmpty()) {
                    Text(text = "No matches", color = onBackground)
                }
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(results) { channel ->
                        ChannelRow(
                            channel = channel,
                            isFavorite = isFavorite(channel.streamId),
                            onPlay = { onPlay(channel) },
                            onToggleFavorite = { onToggleFavorite(channel) }
                        )
                    }
                }
            }
        }

        Card(onClick = onBack) {
            Text(text = "Back", modifier = Modifier.padding(24.dp))
        }
    }
}
