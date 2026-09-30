package com.amosley.signal.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amosley.signal.core.Fmt
import com.amosley.signal.core.Lrc
import com.amosley.signal.core.LyricLine
import com.amosley.signal.core.PastedLyrics
import com.amosley.signal.ui.Ctx
import com.amosley.signal.ui.components.FilledBtn
import com.amosley.signal.ui.components.Mono
import com.amosley.signal.ui.components.OutlineBtn
import com.amosley.signal.ui.theme.C
import com.amosley.signal.ui.theme.T
import kotlinx.coroutines.launch

/**
 * Paste lyrics (e.g. copied from Suno), then tap along with the song to time each line.
 * Saved lyrics auto-scroll like an .lrc file; for PC songs an .lrc is also written next to the file.
 */
@Composable
fun LyricsEditor(c: Ctx, trackId: String) {
    val t = c.track(trackId) ?: return
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val existing = remember(trackId) { c.repo.cachedLyrics(trackId) }
    var text by remember(trackId) { mutableStateOf(existing?.lines?.joinToString("\n") { it.text }.orEmpty()) }
    var syncing by remember { mutableStateOf(false) }
    val lines = remember { mutableStateListOf<String>() }
    val stamps = remember { mutableStateListOf<Double>() }
    var saving by remember { mutableStateOf(false) }

    fun save(result: List<LyricLine>, synced: Boolean) {
        if (saving) return
        saving = true
        scope.launch {
            val msg = c.repo.saveUserLyrics(t, result, synced)
            c.toast(msg)
            c.st.showLyrics = true
            c.st.sheet = null
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp).imePadding()) {
        Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(34.dp).clickable { if (syncing) syncing = false else c.st.sheet = null }, contentAlignment = Alignment.CenterStart) {
                Icon(Icons.Filled.Close, "Close", tint = C.Fg)
            }
            Column(Modifier.weight(1f)) {
                Mono(if (syncing) "Tap-to-sync · ${stamps.size} of ${lines.size}" else "Lyrics", color = C.Muted)
                Text(t.title, style = T.rowSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }

        if (!syncing) {
            Text(
                "Paste the lyrics (for example, copied from the Suno app). Section labels like [Verse] and [Chorus] are removed automatically. " +
                    "Then tap along with the song to time each line.",
                style = T.ui(13.sp), color = C.Muted,
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlineBtn("Paste") {
                    val clip = clipboard.getText()?.text
                    if (clip.isNullOrBlank()) c.toast("Clipboard is empty — copy the lyrics in Suno first") else text = clip
                }
                if (text.isNotEmpty()) OutlineBtn("Clear") { text = "" }
                if (existing?.source == "user") OutlineBtn("Remove my lyrics", color = C.AmberText) {
                    c.repo.deleteUserLyrics(t)
                    c.toast("Removed your lyrics for ${t.title}")
                    c.st.sheet = null
                }
            }
            Spacer(Modifier.height(10.dp))
            Box(Modifier.weight(1f).fillMaxWidth().border(1.dp, C.HairStrong).padding(12.dp)) {
                if (text.isEmpty()) Text("Lyrics go here, one line per line…", style = T.ui(15.sp), color = C.Faint)
                BasicTextField(
                    text, { text = it }, textStyle = T.ui(15.sp, color = C.Fg, lineHeight = 22.sp), cursorBrush = SolidColor(C.Amber),
                    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                )
            }
            val cleaned = PastedLyrics.clean(text)
            val isLrc = PastedLyrics.isLrc(text)
            Mono(
                if (isLrc) "Already has timestamps · ${Lrc.parse(text).lines.size} lines" else "${cleaned.size} lines to sync",
                style = T.metaMono, color = C.Faint, modifier = Modifier.padding(vertical = 8.dp),
            )
            Row(Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (isLrc) {
                    FilledBtn("Save", bg = C.Amber, fg = C.OnAmber, modifier = Modifier.weight(1f), enabled = !saving) {
                        val p = Lrc.parse(text)
                        save(p.lines, p.synced)
                    }
                } else {
                    OutlineBtn("Save without timing", modifier = Modifier.weight(1f)) { if (cleaned.isNotEmpty()) save(PastedLyrics.spread(cleaned, t.durationMs / 1000.0), false) }
                    FilledBtn("Sync with song", bg = C.Amber, fg = C.OnAmber, modifier = Modifier.weight(1f), enabled = cleaned.isNotEmpty()) {
                        lines.clear(); lines.addAll(cleaned)
                        stamps.clear()
                        if (c.player.current?.id != t.id) c.hub.playList(listOf(t))
                        c.hub.seekTo(0)
                        if (!c.player.playing) c.hub.toggle()
                        syncing = true
                    }
                }
            }
        } else {
            SyncStep(c, t.durationMs, lines, stamps, saving,
                onSave = { save(lines.mapIndexed { i, s -> LyricLine(stamps[i], s) }, true) },
                onFinishSpread = {
                    // Spread the lines that weren't tapped between the last tap and the end of the song.
                    val done = stamps.size
                    val from = stamps.lastOrNull() ?: 0.0
                    val end = (t.durationMs / 1000.0).coerceAtLeast(from + 1)
                    val left = lines.size - done
                    val step = (end - from) / (left + 1)
                    val result = lines.mapIndexed { i, s -> LyricLine(if (i < done) stamps[i] else from + step * (i - done + 1), s) }
                    save(result, true)
                },
            )
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.SyncStep(
    c: Ctx,
    durationMs: Long,
    lines: List<String>,
    stamps: MutableList<Double>,
    saving: Boolean,
    onSave: () -> Unit,
    onFinishSpread: () -> Unit,
) {
    val next = stamps.size
    val listState = rememberLazyListState()
    LaunchedEffect(next) { listState.animateScrollToItem((next - 2).coerceAtLeast(0)) }
    Text(
        "Tap the big button right as each line starts. Late? Tap Undo. It jumps back a few seconds so you can try that line again.",
        style = T.ui(13.sp), color = C.Muted,
    )
    Spacer(Modifier.height(8.dp))
    LazyColumn(state = listState, modifier = Modifier.weight(1f).fillMaxWidth()) {
        itemsIndexed(lines, key = { i, _ -> "sl-$i" }) { i, line ->
            val state = when {
                i < next -> 0
                i == next -> 1
                else -> 2
            }
            Row(
                Modifier.fillMaxWidth().background(if (state == 1) C.Card else Color.Transparent).padding(vertical = 9.dp, horizontal = 8.dp).alpha(if (state == 2) 0.45f else 1f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (i < stamps.size) Fmt.dur((stamps[i] * 1000).toLong()) else "–:––",
                    style = T.mono(11.sp, 500, 0.sp), color = if (state == 0) C.AmberText else C.Faint, modifier = Modifier.width(52.dp),
                )
                Text(line, style = if (state == 1) T.ui(18.sp, 600) else T.ui(15.sp, 500), color = if (state == 1) C.Fg else C.Fg.copy(alpha = 0.8f))
            }
        }
    }
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Mono("${Fmt.dur(c.player.positionMs)} / ${Fmt.dur(durationMs)}", style = T.metaMono, color = C.Muted)
        Spacer(Modifier.weight(1f))
        Box(Modifier.size(40.dp).clickable { c.hub.toggle() }, contentAlignment = Alignment.Center) {
            Icon(if (c.player.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, "Play/pause", tint = C.Fg)
        }
    }
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlineBtn("Undo", modifier = Modifier.weight(1f)) {
            if (stamps.isNotEmpty()) {
                val t = stamps.removeAt(stamps.lastIndex)
                c.hub.seekTo(((t - 3.0).coerceAtLeast(0.0) * 1000).toLong())
            }
        }
        OutlineBtn("Restart", modifier = Modifier.weight(1f)) {
            stamps.clear()
            c.hub.seekTo(0)
        }
    }
    if (next < lines.size) {
        Box(
            Modifier.fillMaxWidth().height(96.dp).background(C.Amber).clickable {
                val pos = (c.player.positionMs / 1000.0 - 0.15).coerceAtLeast(0.0)
                stamps.add(maxOf(pos, (stamps.lastOrNull() ?: -0.01) + 0.01))
            },
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("TAP", style = T.ui(26.sp, 700), color = C.OnAmber)
                Text("when “${lines[next].take(32)}${if (lines[next].length > 32) "…" else ""}” starts", style = T.ui(13.sp, 500), color = C.OnAmber, maxLines = 1)
            }
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.Center) {
            if (next > 0) Mono("Finish now · spread the rest", color = C.Muted, modifier = Modifier.clickable(onClick = onFinishSpread).padding(6.dp))
        }
    } else {
        FilledBtn("Save synced lyrics", bg = C.Amber, fg = C.OnAmber, enabled = !saving, modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), onClick = onSave)
    }
}
