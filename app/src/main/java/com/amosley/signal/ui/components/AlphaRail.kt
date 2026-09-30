package com.amosley.signal.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amosley.signal.core.AlphaIndex
import com.amosley.signal.ui.theme.C
import com.amosley.signal.ui.theme.T
import kotlinx.coroutines.launch

/**
 * Where the sorted items of the current tab sit in the LazyColumn, filled in while the list content is built.
 * [start] = LazyColumn index of the first row, [perRow] = items per row (grids), [letters] = letter → item position,
 * [bubbles] = label per item for the scrub bar (sorts other than by name: date, year, rating…).
 */
class JumpIndex {
    var count = 0
    var start = -1
    var perRow = 1
    var size = 0
    var letters: Map<Char, Int> = emptyMap()
    var bubbles: List<String?> = emptyList()

    fun reset() { count = 0; start = -1; perRow = 1; size = 0; letters = emptyMap(); bubbles = emptyList() }

    /** Call right before emitting the sorted rows. */
    fun mark(labels: List<String?>, perRow: Int = 1, bubbles: List<String?> = emptyList()) {
        start = count
        this.perRow = perRow
        size = labels.size
        letters = AlphaIndex.build(labels)
        this.bubbles = bubbles
    }

    fun itemFor(letter: Char): Int? {
        if (start < 0) return null
        val pos = AlphaIndex.positionFor(letters, letter) ?: return null
        return start + pos / perRow
    }

    /** Item position at [fraction] (0..1) of the list. */
    fun positionAt(fraction: Float): Int? = if (start < 0 || size == 0) null else (fraction * size).toInt().coerceIn(0, size - 1)
    fun itemForPosition(pos: Int): Int = start + pos / perRow
}

/** How the right-edge strip works: letters for lists sorted by name, a scrub bar for the other sorts. */
enum class RailMode { LETTERS, SCRUB }

@Composable
fun AlphaRail(state: LazyListState, jump: JumpIndex, mode: RailMode, modifier: Modifier = Modifier) {
    if (mode == RailMode.LETTERS) LetterRail(state, jump, modifier) else ScrubRail(state, jump, modifier)
}

/** Apple-style A–Z strip on the right edge: tap or drag to jump; a large letter shows while dragging. */
@Composable
private fun LetterRail(state: LazyListState, jump: JumpIndex, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    var active by remember { mutableStateOf<Char?>(null) }
    val letters = AlphaIndex.LETTERS

    Box(modifier.fillMaxSize()) {
        Column(
            Modifier.align(Alignment.CenterEnd).width(22.dp).fillMaxHeight(0.9f)
                .pointerInput(Unit) {
                    fun pick(y: Float) {
                        val i = (y / size.height * letters.size).toInt().coerceIn(0, letters.lastIndex)
                        val l = letters[i]
                        if (l == active) return
                        active = l
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        jump.itemFor(l)?.let { idx -> scope.launch { state.scrollToItem(idx) } }
                    }
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        down.consume()
                        pick(down.position.y)
                        while (true) {
                            val ev = awaitPointerEvent()
                            val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                            if (!ch.pressed) break
                            ch.consume()
                            pick(ch.position.y)
                        }
                        active = null
                    }
                },
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceEvenly,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            letters.forEach { l ->
                Text(
                    l.toString(), style = T.ui(10.sp, 700), textAlign = TextAlign.Center,
                    color = if (l == active) C.Fg else C.AmberText, modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        active?.let { l ->
            Box(
                Modifier.align(Alignment.Center).size(84.dp).background(C.Bg.copy(alpha = 0.92f)),
                contentAlignment = Alignment.Center,
            ) { Text(l.toString(), style = T.ui(44.sp, 700), color = C.Fg) }
        }
    }
}

/** Drag along the right edge to move through a long list; a bubble shows where you are (e.g. "Mar 2025", "2019"). */
@Composable
private fun ScrubRail(state: LazyListState, jump: JumpIndex, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    var bubble by remember { mutableStateOf<String?>(null) }
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    var trackPx by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current
    // Where the list is now, as 0..1 of the sorted rows (for the thumb when not dragging).
    val scrolled by remember {
        derivedStateOf {
            val rows = if (jump.size == 0) 0 else (jump.size + jump.perRow - 1) / jump.perRow
            if (rows <= 1 || jump.start < 0) 0f else ((state.firstVisibleItemIndex - jump.start).toFloat() / (rows - 1)).coerceIn(0f, 1f)
        }
    }
    Box(modifier.fillMaxSize()) {
        Box(
            Modifier.align(Alignment.CenterEnd).width(26.dp).fillMaxHeight(0.9f)
                .onSizeChanged { trackPx = it }
                .pointerInput(Unit) {
                    var lastPos = -1
                    fun pick(y: Float) {
                        val f = (y / size.height).coerceIn(0f, 1f)
                        dragFraction = f
                        val pos = jump.positionAt(f) ?: return
                        bubble = jump.bubbles.getOrNull(pos)
                        if (pos == lastPos) return
                        if (lastPos >= 0 && jump.bubbles.getOrNull(lastPos) != bubble) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        lastPos = pos
                        scope.launch { state.scrollToItem(jump.itemForPosition(pos)) }
                    }
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        down.consume()
                        lastPos = -1
                        pick(down.position.y)
                        while (true) {
                            val ev = awaitPointerEvent()
                            val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                            if (!ch.pressed) break
                            ch.consume()
                            pick(ch.position.y)
                        }
                        dragFraction = null
                        bubble = null
                    }
                },
        ) {
            // Track and thumb
            Box(Modifier.align(Alignment.TopCenter).width(2.dp).fillMaxHeight().background(C.Faint.copy(alpha = 0.35f)))
            val f = dragFraction ?: scrolled
            val thumbH = 36.dp
            val travel = with(density) { (trackPx.height.toDp() - thumbH).coerceAtLeast(0.dp) }
            Box(Modifier.align(Alignment.TopCenter).offset(y = travel * f).width(6.dp).height(thumbH).background(C.Amber))
        }
        bubble?.let { label ->
            Box(
                Modifier.align(Alignment.Center).widthIn(min = 120.dp).background(C.Bg.copy(alpha = 0.92f)).padding(horizontal = 18.dp, vertical = 14.dp),
                contentAlignment = Alignment.Center,
            ) { Text(label, style = T.ui(26.sp, 700), color = C.Fg, textAlign = TextAlign.Center) }
        }
    }
}
