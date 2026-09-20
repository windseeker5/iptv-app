package com.kdresdell.iptvtv

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

// TiViMate-style wrap-around for a vertical list: Down on the last row jumps
// to the first, Up on the first row jumps to the last. Up/Down only - Left
// still opens the side rail, so nothing sideways wraps.
//
// Usage in a screen:
//   val wrap = rememberListWrap(count = items.size, headerCount = <non-row items before the rows>)
//   LazyColumn(state = wrap.listState, modifier = Modifier.then(wrap.keys)) {
//       itemsIndexed(items) { i, it -> Row(modifier = wrap.itemModifier(i)) }
//   }
//
// Which row has focus is tracked by index (each row reports its own focus)
// rather than asking "did moveFocus fail?": a Card can swallow Down on real
// hardware (see SettingsScreen), and a false "nothing below" would wrap in
// the middle of a list. The handler is a preview one, so it runs before the
// row's own key handling.
//
// Wrapping only happens on a fresh key press. Holding Down runs to the end
// and stops there; it never loops around on its own.
@Stable
class ListWrap internal constructor(
    val listState: LazyListState,
    private val scope: CoroutineScope,
    // LazyColumn items that come before the first row (a title, say), so
    // list index N is lazy index N + headerCount.
    private val headerCount: Int,
    // Off where something focusable sits above the first row (a Back
    // button) - Up from the first row must reach it, as before.
    private val wrapUpFromFirst: Boolean
) {
    var count: Int = 0
    private var focusedIndex = -1
    private val firstFocus = FocusRequester()
    private val lastFocus = FocusRequester()

    // Put on each row's focusable (or a container around it): reports its
    // focus, and lets the first/last rows be targeted after a jump.
    fun itemModifier(index: Int): Modifier {
        var m: Modifier = Modifier.onFocusChanged { state ->
            if (state.hasFocus) {
                focusedIndex = index
            } else if (focusedIndex == index) {
                focusedIndex = -1
            }
        }
        if (count > 1) {
            if (index == 0) m = m.focusRequester(firstFocus)
            if (index == count - 1) m = m.focusRequester(lastFocus)
        }
        return m
    }

    // Put on the LazyColumn.
    val keys: Modifier = Modifier.onPreviewKeyEvent { handleKey(it) }

    // For a control above the list (e.g. a Back button): Up from it wraps to
    // the last row.
    val upFromAbove: Modifier = Modifier.onPreviewKeyEvent { event ->
        if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionUp && count > 1) {
            if (event.nativeKeyEvent.repeatCount == 0) jumpToLast()
            true
        } else {
            false
        }
    }

    fun jumpToFirst() = jump(toLast = false)

    fun jumpToLast() = jump(toLast = true)

    private fun handleKey(event: KeyEvent): Boolean {
        if (event.type != KeyEventType.KeyDown || count < 2 || focusedIndex < 0) return false
        val fresh = event.nativeKeyEvent.repeatCount == 0
        return when {
            event.key == Key.DirectionDown && focusedIndex == count - 1 -> {
                if (fresh) jumpToFirst()
                true
            }
            event.key == Key.DirectionUp && focusedIndex == 0 && wrapUpFromFirst -> {
                if (fresh) jumpToLast()
                true
            }
            else -> false
        }
    }

    private fun jump(toLast: Boolean) {
        scope.launch {
            // To the very top (not the first row) so a title above it comes
            // back into view too.
            listState.scrollToItem(if (toLast) headerCount + count - 1 else 0)
            val target = if (toLast) lastFocus else firstFocus
            // The target row is composed by the scroll above but only
            // attaches its FocusRequester a frame later - retry briefly.
            repeat(3) {
                withFrameNanos { }
                val focused = try {
                    target.requestFocus()
                    true
                } catch (e: IllegalStateException) {
                    false
                }
                if (focused) return@launch
            }
            AppLog.log("List wrap: could not focus the ${if (toLast) "last" else "first"} row")
        }
    }
}

@Composable
fun rememberListWrap(
    count: Int,
    headerCount: Int = 0,
    wrapUpFromFirst: Boolean = true,
    listState: LazyListState = rememberLazyListState()
): ListWrap {
    val scope = rememberCoroutineScope()
    val wrap = remember(listState, headerCount, wrapUpFromFirst) {
        ListWrap(listState, scope, headerCount, wrapUpFromFirst)
    }
    wrap.count = count
    return wrap
}
