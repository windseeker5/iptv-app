package com.kdresdell.iptvtv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Card
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.tv.material3.darkColorScheme as tvDarkColorScheme

enum class RailItem(val icon: String, val label: String) {
    Search("🔍", "Search"),
    MyChannel("📺", "My Channel"),
    MyVod("🎬", "My VOD"),
    Categories("☰", "Categories"),
    Settings("⚙", "Settings")
}

// Persistent left-side navigation, always visible next to whatever screen
// is currently shown (except the full-screen player). "My Channel" is the
// app's home/default content (still launched into by default / on cold
// start) but also has its own rail entry now, same as everything else.
@Composable
fun SideRail(
    selected: RailItem?,
    onSelect: (RailItem) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(160.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(vertical = 24.dp, horizontal = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        RailItem.entries.forEach { item ->
            Card(onClick = { onSelect(item) }) {
                Text(
                    text = "${item.icon}  ${item.label}",
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
    }
}

// Wraps a screen's content with the persistent rail, applying the TV dark
// theme once for both.
@Composable
fun WithRail(
    selected: RailItem?,
    onSelectRail: (RailItem) -> Unit,
    content: @Composable () -> Unit
) {
    MaterialTheme(colorScheme = tvDarkColorScheme()) {
        Row(modifier = Modifier.fillMaxSize()) {
            SideRail(selected = selected, onSelect = onSelectRail)
            Box(modifier = Modifier.weight(1f)) {
                content()
            }
        }
    }
}
