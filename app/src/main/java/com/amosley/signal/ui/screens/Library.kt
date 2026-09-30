package com.amosley.signal.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amosley.signal.core.Album
import com.amosley.signal.core.Fmt
import com.amosley.signal.core.Movie
import com.amosley.signal.core.MusicVideo
import com.amosley.signal.core.Origin
import com.amosley.signal.core.Show
import com.amosley.signal.core.Track
import com.amosley.signal.data.DlState
import com.amosley.signal.ui.Ctx
import com.amosley.signal.ui.MusicTab
import com.amosley.signal.ui.Screen
import com.amosley.signal.ui.Section
import com.amosley.signal.ui.Sheet
import com.amosley.signal.ui.VideoKind
import com.amosley.signal.ui.components.Art
import com.amosley.signal.ui.components.CardBox
import com.amosley.signal.ui.components.Chip
import com.amosley.signal.ui.components.FilledBtn
import com.amosley.signal.ui.components.Mono
import com.amosley.signal.ui.components.OutlineBtn
import com.amosley.signal.ui.components.PlayDisc
import com.amosley.signal.ui.components.SectionLabel
import com.amosley.signal.ui.components.SongRow
import com.amosley.signal.ui.components.SquareBtn
import com.amosley.signal.ui.theme.C
import com.amosley.signal.ui.theme.T

/** Tracks the PC found recently that still need an artist (drives the "+N NEW" chip / Import review). */
fun importCandidates(c: Ctx): List<Track> =
    c.lib.tracks.filter { it.origin == Origin.PC && it.artist == null && it.addedAt > c.settings.importReviewedAt }
        .sortedByDescending { it.addedAt }

fun untagged(c: Ctx): List<Track> = c.lib.tracks.filter { it.artist == null }

@Composable
fun StatusLine(c: Ctx, modifier: Modifier = Modifier) {
    val s = c.status
    val (color, text) = when {
        c.settings.pc == null -> C.Faint to "No PC paired · tap to pair"
        c.settings.offline -> C.AmberText to "Offline · showing downloads only"
        s.checking && !s.reachable -> C.Faint to "Connecting to ${c.pcName}…"
        !s.reachable -> C.AmberText to "${c.pcName} unreachable · last synced ${Fmt.ago(System.currentTimeMillis(), s.lastSync)}"
        else -> C.Green to "${c.pcName}${if (s.viaRemote) " · via tailnet" else ""} · synced ${Fmt.ago(System.currentTimeMillis(), s.lastSync)}"
    }
    Row(modifier.clickable { if (c.settings.pc == null) c.st.push(Screen.Pair) else c.st.push(Screen.Sync) }, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(7.dp).background(color))
        Spacer(Modifier.width(7.dp))
        Mono(text, color = if (color == C.Green) C.Muted else color)
    }
}

@Composable
fun LibraryPane(c: Ctx) {
    val st = c.st
    val section = if (c.unfolded && st.section == Section.SETTINGS) Section.MUSIC else st.section
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp)) {
                Row(verticalAlignment = Alignment.Top) {
                    Text(section.label, style = T.sectionTitle, color = C.Fg, modifier = Modifier.weight(1f))
                    val newCount = importCandidates(c).size
                    if (newCount > 0 && section == Section.MUSIC) {
                        OutlineBtn("+$newCount new", color = C.AmberText, border = C.Amber) { st.sheet = Sheet.Import }
                        Spacer(Modifier.width(8.dp))
                    }
                    SquareBtn(Icons.Filled.Search, size = 32.dp, desc = "Search") { st.push(Screen.Search) }
                    if (c.unfolded) {
                        Spacer(Modifier.width(8.dp))
                        SquareBtn(Icons.Filled.Settings, size = 32.dp, desc = "Settings") { st.push(Screen.Settings) }
                    }
                }
                Spacer(Modifier.height(8.dp))
                StatusLine(c)
                if (c.unfolded) {
                    Spacer(Modifier.height(14.dp))
                    Row(Modifier.fillMaxWidth().border(1.dp, C.HairStrong)) {
                        listOf(Section.MUSIC, Section.MOVIES, Section.TV).forEach { s ->
                            val on = section == s
                            Box(
                                Modifier.weight(1f).height(32.dp).background(if (on) C.Fg else Color.Transparent).clickable { st.goSection(s) },
                                contentAlignment = Alignment.Center,
                            ) { Mono(s.label, color = if (on) C.Bg else C.Muted, style = T.mono(10.5.sp, 600)) }
                        }
                    }
                }
            }
        }
        when (section) {
            Section.MUSIC -> {
                item { SubTabs(c) }
                when (st.tab) {
                    MusicTab.RECENT -> recent(c)
                    MusicTab.SONGS -> songs(c)
                    MusicTab.ALBUMS -> albums(c)
                    MusicTab.ARTISTS -> artists(c)
                    MusicTab.PLAYLISTS -> playlists(c)
                    MusicTab.VIDEOS -> musicVideos(c)
                }
            }
            Section.MOVIES -> movies(c)
            Section.TV -> shows(c)
            Section.SETTINGS -> Unit
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun SubTabs(c: Ctx) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, top = 16.dp)
            .drawBehind { drawRect(C.Hair, Offset(0f, size.height - 1.dp.toPx()), Size(size.width, 1.dp.toPx())) },
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        MusicTab.entries.forEach { t ->
            val on = c.st.tab == t
            Box(
                Modifier.clickable { c.st.tab = t }.padding(bottom = 10.dp)
                    .drawBehind { if (on) drawRect(C.Amber, Offset(0f, size.height + 8.dp.toPx()), Size(size.width, 2.dp.toPx())) },
            ) { Text(t.label, style = T.ui(14.sp, if (on) 600 else 500), color = if (on) C.Fg else C.Muted) }
        }
    }
}

fun LazyListScope.songRows(c: Ctx, list: List<Track>, key: String) {
    items(list, key = { "$key-${it.id}" }) { t ->
        SongRow(
            t = t, artModel = c.art(t), dl = c.dlState(t.id),
            isCurrent = c.player.current?.id == t.id, playing = c.player.playing, unavailable = c.unavailable(t),
            onPlay = { c.st.playFrom(c.app, list, t) },
            onArtist = { c.st.openArtist(it) },
            onMore = { c.st.sheet = Sheet.Actions(t.id) },
        )
    }
}

private fun LazyListScope.recent(c: Ctx) {
    val cand = importCandidates(c)
    if (cand.isNotEmpty()) item {
        Box(Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp)) {
            CardBox {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${cand.size} new file${if (cand.size > 1) "s" else ""} on ${c.pcName}", style = T.rowSecondary)
                        Spacer(Modifier.height(3.dp))
                        Text("Review artist names, then add", style = T.meta, color = C.Muted)
                    }
                    OutlineBtn("Review", color = C.AmberText, border = C.Amber) { c.st.sheet = Sheet.Import }
                }
            }
        }
    }
    if (!c.settings.phoneFoldersChosen && c.repo.phoneFolders.value.isNotEmpty()) item {
        Box(Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp)) {
            CardBox {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Choose folders on this phone", style = T.rowSecondary)
                        Spacer(Modifier.height(3.dp))
                        Text("Pick which folders hold your music, music videos, movies and TV", style = T.meta, color = C.Muted)
                    }
                    OutlineBtn("Choose", color = C.AmberText, border = C.Amber) { c.st.goSection(Section.SETTINGS) }
                }
            }
        }
    }
    if (c.lib.tracks.isEmpty()) {
        item { EmptyLibrary(c) }
        return
    }
    val weekAgo = System.currentTimeMillis() - 7 * 24 * 3600_000L
    val recent = c.lib.tracks.sortedByDescending { it.addedAt }
    val thisWeek = recent.filter { it.addedAt > weekAgo }
    item { SectionLabel(if (thisWeek.isNotEmpty()) "Added this week" else "Recently added", Modifier.padding(horizontal = 20.dp)) }
    songRows(c, (thisWeek.ifEmpty { recent }).take(if (thisWeek.isEmpty()) 12 else 50), "recent")
    val recentAlbums = c.lib.albums.filter { it.title != "Singles" }.sortedByDescending { a -> a.tracks.maxOf { it.addedAt } }.take(6)
    if (recentAlbums.isNotEmpty()) {
        item { SectionLabel("Recent albums", Modifier.padding(horizontal = 20.dp)) }
        albumGrid(c, recentAlbums, "recent-albums")
    }
}

@Composable
private fun EmptyLibrary(c: Ctx) {
    Column(Modifier.fillMaxWidth().padding(20.dp)) {
        Spacer(Modifier.height(30.dp))
        Mono("Nothing here yet", color = C.Muted)
        Spacer(Modifier.height(10.dp))
        Text(
            if (c.settings.pc == null) "Pair your PC to stream and sync your library, or allow access to music on this phone."
            else "Your library will appear once ${c.pcName} finishes scanning. Music already on this phone shows up here too.",
            style = T.ui(14.5.sp), color = C.Muted,
        )
        Spacer(Modifier.height(16.dp))
        if (c.settings.pc == null) FilledBtn("Pair a PC") { c.st.push(Screen.Pair) }
    }
}

private fun LazyListScope.songs(c: Ctx) {
    val all = c.lib.tracks.sortedBy { it.title.lowercase() }
    val missing = untagged(c)
    item {
        Row(Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FilledBtn("Play", icon = Icons.Filled.PlayArrow, modifier = Modifier.weight(1f)) { c.hub.playList(all, 0) }
            FilledBtn("Shuffle", icon = Icons.Filled.Shuffle, bg = Color.White.copy(alpha = 0.1f), fg = C.Fg, modifier = Modifier.weight(1f)) { c.hub.shuffleAll(all) }
        }
    }
    if (missing.isNotEmpty()) item {
        Box(Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp)) {
            CardBox {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(30.dp).background(C.Amber), contentAlignment = Alignment.Center) {
                        Text("?", style = T.ui(16.sp, 700), color = C.OnAmber)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("${missing.size} song${if (missing.size > 1) "s" else ""} have no artist", style = T.rowSecondary)
                        Text("Suggest fictional names from title, lyrics and mood", style = T.ui(12.5.sp), color = C.Muted)
                    }
                    OutlineBtn("Fix", color = C.AmberText, border = C.Amber) { c.st.sheet = Sheet.Batch }
                }
            }
        }
    }
    item { Spacer(Modifier.height(8.dp)) }
    songRows(c, all, "songs")
}

fun LazyListScope.albumGrid(c: Ctx, albums: List<Album>, key: String) {
    val cols = if (c.unfolded) 2 else 2
    items(albums.chunked(cols), key = { row -> "$key-${row.first().key}" }) { row ->
        Row(Modifier.padding(horizontal = 20.dp, vertical = 9.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            row.forEach { a ->
                Column(Modifier.weight(1f).clickable { c.st.push(Screen.Album(a.key)) }) {
                    Art(a.key, a.artTrack?.let { c.art(it) }, Modifier.fillMaxWidth().aspectRatio(1f))
                    Spacer(Modifier.height(8.dp))
                    Text(a.title, style = T.rowSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(a.artist ?: "Unknown artist", style = T.meta, color = if (a.artist == null) C.AmberText else C.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

private fun LazyListScope.albums(c: Ctx) {
    item {
        Box(Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 6.dp)) {
            OutlineBtn("Shuffle all albums", icon = Icons.Filled.Shuffle) { c.hub.shuffleAll(c.lib.albums.flatMap { it.tracks }) }
        }
    }
    albumGrid(c, c.lib.albums, "albums")
}

private fun LazyListScope.artists(c: Ctx) {
    item { Spacer(Modifier.height(10.dp)) }
    items(c.lib.artists, key = { "artist-${it.first}" }) { (name, n) ->
        val sample = c.lib.tracks.firstOrNull { it.artist == name }
        Row(Modifier.fillMaxWidth().clickable { c.st.openArtist(name) }.padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Art(name, sample?.let { c.art(it) }, Modifier.size(56.dp), shape = CircleShape)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(name, style = T.row)
                Text("$n song${if (n > 1) "s" else ""}", style = T.meta, color = C.Muted)
            }
            Icon(Icons.Filled.ChevronRight, null, tint = C.Faint)
        }
    }
}

private fun LazyListScope.playlists(c: Ctx) {
    item {
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 6.dp).clickable { c.st.sheet = Sheet.NewPlaylist() }
                .drawBehind {
                    drawRect(C.HairStrong, style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))))
                }.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Add, null, tint = C.AmberText)
            Spacer(Modifier.width(10.dp))
            Text("New playlist", style = T.row)
        }
    }
    val user = c.playlists
    items(user, key = { "pl-${it.id}" }) { pl ->
        val first = pl.trackIds.firstNotNullOfOrNull { c.track(it) }
        PlaylistRow(c, pl.name, "${pl.trackIds.size} songs", pl.id, first)
    }
    item {
        val missing = untagged(c)
        PlaylistRow(c, "Needs an artist", "Smart playlist · ${missing.size} songs", Screen.NEEDS_ARTIST, missing.firstOrNull())
    }
    item {
        PlaylistRow(c, "Recently added", "Smart playlist · last 50", Screen.RECENT_PLAYLIST, c.lib.tracks.maxByOrNull { it.addedAt })
    }
}

@Composable
private fun PlaylistRow(c: Ctx, name: String, sub: String, id: String, art: Track?) {
    Row(Modifier.fillMaxWidth().clickable { c.st.push(Screen.Playlist(id)) }.padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Art(name, art?.let { c.art(it) }, Modifier.size(64.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(name, style = T.row)
            Text(sub, style = T.meta, color = C.Muted)
        }
        Icon(Icons.Filled.ChevronRight, null, tint = C.Faint)
    }
}

@Composable
fun DownloadChip(c: Ctx, id: String, onDownload: () -> Unit) {
    when (val d = c.dlState(id)) {
        is DlState.Done -> Chip("✓ Downloaded", bg = C.Green.copy(alpha = 0.18f), fg = C.Green)
        is DlState.Running -> Chip("${(d.progress * 100).toInt()}%", bg = C.AmberTint, fg = C.AmberText)
        is DlState.Queued -> Chip(d.reason ?: "Queued", bg = C.AmberTint, fg = C.AmberText)
        else -> Box(Modifier.clickable(onClick = onDownload)) { Chip("↓ Download", bg = Color.Black.copy(alpha = 0.55f)) }
    }
}

private fun LazyListScope.musicVideos(c: Ctx) {
    if (c.lib.videos.isEmpty()) item {
        Box(Modifier.padding(20.dp)) { Mono("No music videos on ${c.pcName} yet", color = C.Faint) }
    }
    items(c.lib.videos, key = { "mv-${it.id}" }) { v -> MusicVideoCard(c, v) }
}

@Composable
fun MusicVideoCard(c: Ctx, v: MusicVideo) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp).clickable { c.st.push(Screen.Video(v.id, VideoKind.MUSIC_VIDEO)) }) {
        Art(v.title, if (v.hasArt) c.repo.artUrl(v.id, "thumb") else null, Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
            PlayDisc(modifier = Modifier.align(Alignment.Center))
            if (v.origin == Origin.PC) Box(Modifier.align(Alignment.TopEnd).padding(8.dp)) { DownloadChip(c, v.id) { c.repo.download(v) } }
        }
        Spacer(Modifier.height(8.dp))
        Text(v.title, style = T.rowSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Mono(listOfNotNull(v.resLabel, if (v.hdr) "HDR" else null, Fmt.dur(v.durationMs), if (v.origin == Origin.PHONE) "On this phone" else if (c.dlState(v.id) is DlState.Done) "Downloaded" else "Stream from ${c.pcName}").joinToString(" · "), style = T.metaMono, color = C.Faint)
    }
}

@Composable
fun PosterBadge(c: Ctx, id: String, origin: Origin, modifier: Modifier = Modifier) {
    when (c.dlState(id)) {
        is DlState.Done -> Box(modifier.size(18.dp).background(C.Green), contentAlignment = Alignment.Center) { Icon(Icons.Filled.Check, null, tint = C.OnGreen, modifier = Modifier.size(13.dp)) }
        is DlState.Running, is DlState.Queued -> Box(modifier.size(18.dp).background(Color.Black.copy(alpha = 0.6f)), contentAlignment = Alignment.Center) { Icon(Icons.Filled.Download, null, tint = C.Amber, modifier = Modifier.size(12.dp)) }
        else -> if (origin == Origin.PC) {
            Box(modifier.background(Color.Black.copy(alpha = 0.6f)).padding(horizontal = 4.dp, vertical = 1.dp)) { Text("PC", style = T.badge, color = C.Fg) }
        }
    }
}

private fun LazyListScope.posterGrid(c: Ctx, count: Int, key: (Int) -> String, cell: @Composable (Int, Modifier) -> Unit) {
    val cols = if (c.unfolded) 3 else 3
    val rows = (0 until count).chunked(cols)
    items(rows, key = { "$it-${key(it.first())}" }) { row ->
        Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            row.forEach { i -> cell(i, Modifier.weight(1f)) }
            repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

private fun LazyListScope.movies(c: Ctx) {
    val list: List<Movie> = c.lib.movies.sortedByDescending { it.addedAt }
    item { Spacer(Modifier.height(14.dp)) }
    if (list.isEmpty()) item { Box(Modifier.padding(20.dp)) { Mono("No movies yet", color = C.Faint) } }
    posterGrid(c, list.size, { list[it].id }) { i, m ->
        val mv = list[i]
        Column(m.clickable { c.st.push(Screen.MoviePage(mv.id)) }) {
            Art(mv.title, mv.posterUrl?.let { c.repo.remoteUrl(it) }, Modifier.fillMaxWidth().aspectRatio(2f / 3f)) {
                PosterBadge(c, mv.id, mv.origin, Modifier.align(Alignment.TopEnd).padding(5.dp))
                if (mv.viewOffsetMs > 0 && mv.durationMs > 0 && !mv.watched) {
                    Box(Modifier.align(Alignment.BottomStart).fillMaxWidth(mv.viewOffsetMs.toFloat() / mv.durationMs).height(3.dp).background(C.Amber))
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(mv.title, style = T.ui(13.sp, 600), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(mv.year?.toString() ?: mv.matchedBy ?: "", style = T.ui(12.sp), color = C.Muted, maxLines = 1)
        }
    }
}

private fun LazyListScope.shows(c: Ctx) {
    val list: List<Show> = c.lib.shows.sortedBy { it.title.lowercase() }
    item { Spacer(Modifier.height(14.dp)) }
    if (list.isEmpty()) item { Box(Modifier.padding(20.dp)) { Mono("No TV shows yet", color = C.Faint) } }
    posterGrid(c, list.size, { list[it].id }) { i, m ->
        val sh = list[i]
        Column(m.clickable { c.st.season = null; c.st.push(Screen.ShowPage(sh.id)) }) {
            Art(sh.title, sh.posterUrl?.let { c.repo.remoteUrl(it) }, Modifier.fillMaxWidth().aspectRatio(2f / 3f)) {
                if (!sh.id.startsWith("locs:")) Box(Modifier.align(Alignment.TopEnd).padding(5.dp).background(Color.Black.copy(alpha = 0.6f)).padding(horizontal = 4.dp, vertical = 1.dp)) {
                    Text("PC", style = T.badge, color = C.Fg)
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(sh.title, style = T.ui(13.sp, 600), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${sh.seasons.size} season${if (sh.seasons.size != 1) "s" else ""}", style = T.ui(12.sp), color = C.Muted)
        }
    }
}
