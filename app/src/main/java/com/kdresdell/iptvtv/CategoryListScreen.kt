package com.kdresdell.iptvtv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Card
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.kdresdell.iptvtv.theme.RailIcons
import com.kdresdell.iptvtv.theme.appCardBorder
import com.kdresdell.iptvtv.theme.appCardGlow
import com.kdresdell.iptvtv.theme.appRowCardColors
import com.kdresdell.iptvtv.theme.appRowCardScale

// STYLE_GUIDE.md §6.5 "All" screen - single-column list of the provider's
// live categories, reached from the side nav's All item. Deviations from the
// spec's original proposal, both to avoid fabricating data the Xtream API
// doesn't return here: no synthetic "Favorites"/"All channels" pinned rows
// (the side rail's own My TV item already covers "favorites", and there's no
// bulk "all channels" endpoint to back a flattened row), and no quality tag
// line (LiveCategory carries no such field - only the category name).
@Composable
fun CategoryListScreen(
    state: LoadState<List<LiveCategory>>,
    onSelectCategory: (LiveCategory) -> Unit
) {
    val firstItemFocus = remember { FocusRequester() }
    // Item 0 of the LazyColumn is the title, so the rows start at lazy index 1.
    val wrap = rememberListWrap(count = (state as? LoadState.Success)?.data?.size ?: 0, headerCount = 1)

    // Without this, D-pad focus lands nowhere when this screen opens - the
    // first press anywhere jumps to the side rail's Search item instead of
    // this list, a real bug hit on real hardware across every screen that
    // didn't explicitly claim initial focus like this.
    LaunchedEffect(state) {
        if (state is LoadState.Success && state.data.isNotEmpty()) {
            firstItemFocus.requestFocus()
        }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        when (state) {
            is LoadState.Loading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "Loading categories...",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            is LoadState.Error -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "Could not load channels: ${state.message}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 48.dp)
                )
            }
            is LoadState.Success -> {
                if (state.data.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "This provider returned no live categories.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        state = wrap.listState,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(horizontal = 48.dp, vertical = 24.dp).then(wrap.keys)
                    ) {
                        item {
                            Text(
                                text = "Live categories",
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }
                        itemsIndexed(state.data) { index, category ->
                            CategoryRow(
                                category = category,
                                onClick = { onSelectCategory(category) },
                                modifier = (if (index == 0) Modifier.focusRequester(firstItemFocus) else Modifier)
                                    .then(wrap.itemModifier(index))
                            )
                        }
                    }
                }
            }
        }
    }
}

// §6 "Lists" row pattern, full width, no scale-up on focus (same row
// exception every other full-width list row in the app uses) - a chevron on
// the right signals "this row drills into another screen", matching the
// spec's distinction from channel rows (which play/toggle instead).
@Composable
private fun CategoryRow(
    category: LiveCategory,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        scale = appRowCardScale(),
        colors = appRowCardColors(),
        border = appCardBorder(shape = RoundedCornerShape(8.dp)),
        glow = appCardGlow()
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Text(
                text = category.categoryName,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = RailIcons.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
