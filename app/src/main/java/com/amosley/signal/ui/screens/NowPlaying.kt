package com.amosley.signal.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amosley.signal.core.Fmt
import com.amosley.signal.core.Lrc
import com.amosley.signal.core.Lyrics
import com.amosley.signal.core.Origin
import com.amosley.signal.core.Quality
import com.amosley.signal.core.Track
import com.amosley.signal.data.LyricMode
import com.amosley.signal.ui.Ctx
import com.amosley.signal.ui.Sheet
import com.amosley.signal.ui.components.Art
import com.amosley.signal.ui.components.Mono
import com.amosley.signal.ui.theme.C
import com.amosley.signal.ui.theme.T
import java.util.Locale

@Composable
fun MiniPlayer(c: Ctx) {
    val t = c.player.current ?: return
    val progress = if (c.player.durationMs > 0) c.player.positionMs.toFloat() / c.player.durationMs else 0f
    Row(
        Modifier
            .fillMaxWidth()
            .height(64.dp)
            .background(C.Surface)
            .drawBehind {
                drawRect(C.Hair, Offset.Zero, Size(size.width, 1.dp.toPx()))
                drawRect(C.Amber, Offset.Zero, Size(size.width * progress.coerceIn(0f, 1f), 2.dp.toPx()))
            }
            .clickable { c.st.sheet = Sheet.NowPlaying }
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Art(t.albumKey + t.title, c.art(t), Modifier.size(44.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(t.title, style = T.ui(14.5.sp, 600), maxLines = 1, overflow = TextOverflow.Ellipsis)
            val prefix = c.player.output?.let { "▸ $it · " } ?: ""
            Mono("$prefix${t.artist ?: "Unknown artist"} · ${Fmt.dur(c.player.positionMs)} / ${Fmt.dur(c.player.durationMs)}", style = T.metaMono, color = C.Muted)
        }
        Box(Modifier.size(44.dp).background(C.Amber).clickable { c.hub.toggle() }, contentAlignment = Alignment.Center) {
            Icon(if (c.player.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, "Play/pause", tint = C.OnAmber)
        }
        Box(Modifier.size(44.dp).clickable { c.hub.next() }, contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.SkipNext, "Next", tint = C.Fg)
        }
    }
}

@Composable
private fun HeaderToggle(text: String, active: Boolean, onClick: () -> Unit, filled: Boolean = false) {
    Box(
        Modifier
            .height(28.dp)
            .background(if (filled) C.Amber else Color.Transparent)
            .border(1.dp, if (active || filled) C.Amber else C.HairStrong)
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text.uppercase(), style = T.mono(10.sp, 600, 0.08.sp), color = if (filled) C.OnAmber else if (active) C.AmberText else C.Fg, maxLines = 1) }
}

/** Now Playing — full-screen sheet when folded, persistent right pane when unfolded. */
@Composable
fun NowPlayingContent(c: Ctx, pane: Boolean) {
    val t = c.player.current
    if (t == null) {
        Column(Modifier.fillMaxSize().padding(28.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Mono("Nothing playing", color = C.Faint)
            Spacer(Modifier.height(8.dp))
            Text("Pick a song from the library", style = T.ui(15.sp), color = C.Muted)
        }
        return
    }
    val loaded by c.repo.lyricsLoaded.collectAsState()
    val cachedNow = loaded[t.id]
    // Re-fetch when the song changes or its lyrics are cleared (e.g. after removing your own lyrics).
    val fetched by produceState<Lyrics?>(cachedNow, t.id, cachedNow == null) { value = cachedNow ?: c.repo.lyrics(t) }
    val lyrics = cachedNow ?: fetched
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        // Header row
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(34.dp).clickable { if (pane) c.st.nowPlayingPane = false else c.st.sheet = null }, contentAlignment = Alignment.CenterStart) {
                Icon(Icons.Filled.KeyboardArrowDown, "Close", tint = C.Fg)
            }
            Mono(if (c.player.output != null) "Playing on ${c.player.output}" else "Now playing · on device", color = C.Muted, modifier = Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                HeaderToggle(if (c.st.showLyrics) "Queue" else "Lyrics", c.st.showLyrics, { c.st.showLyrics = !c.st.showLyrics })
                val casting = c.player.output != null
                Box(
                    Modifier.height(28.dp).background(if (casting) C.Amber else Color.Transparent).border(1.dp, if (casting) C.Amber else C.HairStrong)
                        .clickable { c.st.sheet = Sheet.Cast() }.padding(horizontal = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Cast, null, tint = if (casting) C.OnAmber else C.Fg, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text((c.player.output ?: "Cast").uppercase(), style = T.mono(10.sp, 600, 0.08.sp), color = if (casting) C.OnAmber else C.Fg, maxLines = 1)
                    }
                }
                HeaderToggle("Edit", false, { c.st.sheet = Sheet.Edit(t.id) })
            }
        }
        Spacer(Modifier.height(16.dp))
        // Art + readout
        Row(verticalAlignment = Alignment.Top) {
            Art(t.albumKey + t.title, c.art(t), Modifier.size(116.dp))
            Spacer(Modifier.width(14.dp))
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                if (t.quality != Quality.LOSSY) Mono("▯ ${t.quality.label}", color = C.AmberText, style = T.metaMono)
                t.bitsLabel?.let { Mono(it, style = T.metaMono, color = C.Muted) }
                Mono(listOfNotNull(t.container, t.genres.firstOrNull(), Fmt.dur(t.durationMs)).joinToString(" · "), style = T.metaMono, color = C.Muted)
                t.album?.let { Mono(it, style = T.metaMono, color = C.Faint, maxLines = 2) }
                Mono(c.srcLabel(t), style = T.metaMono, color = C.Faint)
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.Top) {
            Text(t.title, style = if (pane) T.npTitleUnfolded else T.npTitleFolded, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            val fav = c.isFavorite(t.id)
            Box(Modifier.size(40.dp).clickable { c.toggleFavorite(t) }, contentAlignment = Alignment.Center) {
                Icon(if (fav) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, if (fav) "Remove from Favorites" else "Add to Favorites",
                    tint = if (fav) C.Amber else C.Muted, modifier = Modifier.size(24.dp))
            }
        }
        Text(
            t.artist ?: "Unknown artist", style = T.ui(16.sp, 400), color = if (t.artist == null) C.AmberText else C.Muted,
            modifier = Modifier.padding(top = 3.dp).clickable { if (t.artist != null) c.st.openArtist(t.artist) else c.st.sheet = Sheet.Edit(t.id) },
        )
        Spacer(Modifier.height(12.dp))
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (c.st.showLyrics) LyricsView(c, t, lyrics, pane) else UpNextList(c)
        }
        Spacer(Modifier.height(10.dp))
        Waveform(c.player.positionMs, c.player.durationMs, t.id) { c.hub.seekTo(it) }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
            Mono(Fmt.dur(c.player.positionMs), style = T.metaMono, color = C.Muted)
            Spacer(Modifier.weight(1f))
            Mono("-" + Fmt.dur((c.player.durationMs - c.player.positionMs).coerceAtLeast(0)), style = T.metaMono, color = C.Muted)
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(60.dp)) {
                if (t.origin == Origin.PC) DlButton(c, t.id, t.origin, 40) { c.repo.download(t) }
            }
            Spacer(Modifier.weight(1f))
            Box(Modifier.size(52.dp).clickable { c.hub.prev() }, contentAlignment = Alignment.Center) { Icon(Icons.Filled.SkipPrevious, "Previous", Modifier.size(30.dp)) }
            Spacer(Modifier.width(14.dp))
            Box(Modifier.size(60.dp).background(C.Amber).clickable { c.hub.toggle() }, contentAlignment = Alignment.Center) {
                Icon(if (c.player.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, "Play/pause", tint = C.OnAmber, modifier = Modifier.size(32.dp))
            }
            Spacer(Modifier.width(14.dp))
            Box(Modifier.size(52.dp).clickable { c.hub.next() }, contentAlignment = Alignment.Center) { Icon(Icons.Filled.SkipNext, "Next", Modifier.size(30.dp)) }
            Spacer(Modifier.weight(1f))
            Box(Modifier.width(60.dp), contentAlignment = Alignment.CenterEnd) {
                Mono("Queue", color = C.Muted, modifier = Modifier.clickable { c.st.sheet = Sheet.Queue })
            }
        }
    }
}

/** Waveform-style scrubber: 2 px bars every 5 px, played part amber; tap or drag to seek. */
@Composable
fun Waveform(positionMs: Long, durationMs: Long, seed: String, onSeek: (Long) -> Unit) {
    var dragFrac by remember { mutableStateOf<Float?>(null) }
    val heights = remember(seed) {
        val r = java.util.Random(seed.hashCode().toLong())
        FloatArray(400) { 0.3f + r.nextFloat() * 0.7f }
    }
    val frac = dragFrac ?: if (durationMs > 0) positionMs.toFloat() / durationMs else 0f
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(36.dp)
            .pointerInput(durationMs) {
                detectTapGestures { o -> if (durationMs > 0) onSeek((o.x / size.width * durationMs).toLong().coerceIn(0, durationMs)) }
            }
            .pointerInput(durationMs) {
                detectHorizontalDragGestures(
                    onDragEnd = { dragFrac?.let { f -> onSeek((f * durationMs).toLong()) }; dragFrac = null },
                    onDragCancel = { dragFrac = null },
                ) { change, _ -> dragFrac = (change.position.x / size.width).coerceIn(0f, 1f) }
            },
    ) {
        val step = 5.dp.toPx()
        val w = 2.dp.toPx()
        var x = 0f
        var i = 0
        while (x < size.width) {
            val h = size.height * heights[i % heights.size]
            val played = x / size.width <= frac
            drawRect(if (played) C.Amber else C.Fg.copy(alpha = 0.22f), Offset(x, (size.height - h) / 2), Size(w, h))
            x += step
            i++
        }
    }
}

@Composable
private fun UpNextList(c: Ctx) {
    val next = c.player.upNext
    if (next.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Mono("Queue is empty · use ⋯ on any song → play next", color = C.Faint) }
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Row(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
                Mono("Up next", color = C.Muted)
                Spacer(Modifier.weight(1f))
                Mono(if (c.player.shuffle) "Shuffle on" else "", color = C.AmberText)
            }
        }
        itemsIndexed(next.take(50), key = { i, t -> "un-$i-${t.id}" }) { i, t ->
            Row(Modifier.fillMaxWidth().clickable { c.hub.jumpTo(i) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("%02d".format(i + 1), style = T.mono(11.sp, 500, 0.sp), color = C.Faint, modifier = Modifier.width(28.dp))
                Column(Modifier.weight(1f)) {
                    Text(t.title, style = T.ui(14.5.sp, 500), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Mono("${t.artist ?: "Unknown artist"} · ${t.quality.label}", style = T.metaMono, color = C.Faint)
                }
                Text(Fmt.dur(t.durationMs), style = T.mono(11.sp, 500, 0.sp), color = C.Faint)
            }
        }
    }
}

private fun langName(code: String?): String = code?.let { Locale(it).getDisplayLanguage(Locale.ENGLISH).ifEmpty { it } } ?: "Original"

@Composable
fun LyricsView(c: Ctx, t: Track, lyrics: Lyrics?, pane: Boolean) {
    if (lyrics == null || lyrics.lines.isEmpty()) {
        val msg = when {
            lyrics == null && t.origin == Origin.PC && !c.status.reachable -> "Lyrics load when ${c.pcName} is reachable"
            lyrics?.source == "lrclib-instrumental" || t.genres.any { it.equals("instrumental", true) } -> "Instrumental · no lyrics"
            t.artist == null -> "No lyrics · add an artist so Signal can look them up online"
            !c.settings.onlineLyrics -> "No lyrics found · online lookup is off in Settings"
            else -> "No lyrics found for this song"
        }
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Mono(msg, color = C.Fg.copy(alpha = 0.45f), maxLines = 3)
            Spacer(Modifier.height(14.dp))
            Box(
                Modifier.border(1.dp, C.Amber).clickable { c.st.sheet = Sheet.LyricsEditor(t.id) }.padding(horizontal = 14.dp, vertical = 8.dp),
            ) { Text("ADD LYRICS", style = T.mono(11.sp, 600, 0.1.sp), color = C.AmberText) }
            Spacer(Modifier.height(6.dp))
            Mono("Paste from Suno · tap along to sync", style = T.metaMono, color = C.Faint)
        }
        return
    }
    val mode = c.settings.lyricMode
    val foreign = lyrics.hasTranslation && lyrics.lang != null && lyrics.lang != "en"
    val active = if (lyrics.synced) Lrc.activeIndex(lyrics.lines, c.player.positionMs / 1000.0) else -1
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    Column(Modifier.fillMaxSize()) {
        if (foreign) {
            val code = lyrics.lang!!.uppercase()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.border(1.dp, C.HairStrong)) {
                    listOf(LyricMode.ORIG to code, LyricMode.BOTH to "$code + EN", LyricMode.TRANS to "EN").forEach { (m, label) ->
                        Box(
                            Modifier.background(if (mode == m) C.Amber else Color.Transparent).clickable { c.repo.updateSettings { it.copy(lyricMode = m) } }
                                .padding(horizontal = 9.dp, vertical = 5.dp),
                        ) { Text(label, style = T.mono(10.sp, 600, 0.08.sp), color = if (mode == m) C.OnAmber else C.Muted) }
                    }
                }
                Spacer(Modifier.width(10.dp))
                Mono("${langName(lyrics.lang)} · translated on ${if (lyrics.source == "pc") c.pcName else "device"}", style = T.metaMono, color = C.Faint)
            }
            Spacer(Modifier.height(8.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Mono(
                listOfNotNull(
                    when (lyrics.source) { "lrclib" -> "Lyrics from LRCLIB"; "user" -> "Your lyrics"; else -> null },
                    if (!lyrics.synced) "Not time-synced" else null,
                ).joinToString(" · "),
                style = T.metaMono, color = C.Faint, modifier = Modifier.weight(1f),
            )
            Mono(if (lyrics.synced) "Edit" else "Sync", style = T.metaMono, color = C.AmberText,
                modifier = Modifier.clickable { c.st.sheet = Sheet.LyricsEditor(t.id) }.padding(4.dp))
        }
        Spacer(Modifier.height(4.dp))
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val h = maxHeight
            LaunchedEffect(active, h) {
                if (active >= 0) {
                    val offset = with(density) { (h * 0.38f).roundToPx() }
                    listState.animateScrollToItem(active, -offset)
                }
            }
            LazyColumn(state = listState, contentPadding = PaddingValues(top = h * 0.38f, bottom = h * 0.6f), modifier = Modifier.fillMaxSize()) {
                itemsIndexed(lyrics.lines, key = { i, _ -> "ly-$i" }) { i, line ->
                    val target = when {
                        !lyrics.synced -> 0.9f
                        i == active -> 1f
                        i < active -> 0.28f
                        else -> 0.42f
                    }
                    val a by animateFloatAsState(target, tween(400), label = "ly")
                    val showOrig = !foreign || mode != LyricMode.TRANS
                    val showTr = foreign && mode != LyricMode.ORIG && line.tr != null
                    Column(
                        Modifier.fillMaxWidth().alpha(a).clickable(enabled = lyrics.synced) {
                            c.hub.seekTo((line.t * 1000).toLong())
                            if (!c.player.playing) c.hub.toggle()
                        }.padding(vertical = 11.dp),
                    ) {
                        if (showOrig) Text(line.text, style = if (pane) T.lyric.copy(fontSize = 22.sp, lineHeight = 26.sp) else T.lyric)
                        if (showTr) Text(line.tr!!, style = if (showOrig) T.lyricTr else T.lyric, color = if (showOrig) C.Fg.copy(alpha = 0.6f) else C.Fg, modifier = Modifier.padding(top = 3.dp))
                    }
                }
            }
        }
    }
}
