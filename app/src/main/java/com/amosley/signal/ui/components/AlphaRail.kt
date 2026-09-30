package com.amosley.signal.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
 * [start] = LazyColumn index of the first row, [perRow] = items per row (grids), [letters] = letter → item position.
 */
class JumpIndex {
    var count = 0
    var start = -1
    var perRow = 1
    var letters: Map<Char, Int> = emptyMap()

    fun reset() { count = 0; start = -1; perRow = 1; letters = emptyMap() }

    /** Call right before emitting the sorted rows. */
    fun mark(labels: List<String?>, perRow: Int = 1) {
        start = count
        this.perRow = perRow
        letters = AlphaIndex.build(labels)
    }

    fun itemFor(letter: Char): Int? {
        if (start < 0) return null
        val pos = AlphaIndex.positionFor(letters, letter) ?: return null
        return start + pos / perRow
    }
}

/** Apple-style A–Z strip on the right edge: tap or drag to jump; a large letter shows while dragging. */
@Composable
fun AlphaRail(state: LazyListState, jump: JumpIndex, modifier: Modifier = Modifier) {
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
