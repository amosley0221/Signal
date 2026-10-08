package com.amosley.signal.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.remember
import com.amosley.signal.core.SortKey
import com.amosley.signal.ui.components.AlphaRail
import com.amosley.signal.ui.components.JumpIndex
import com.amosley.signal.ui.components.RailMode
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
import com.amosley.signal.core.SortPref
import com.amosley.signal.core.SortTab
import com.amosley.signal.core.Sorting
import com.amosley.signal.core.WatchItem
import com.amosley.signal.core.Watching
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import com.amosley.signal.core.Track
import com.amosley.signal.data.DlState
import com.amosley.signal.ui.Ctx
import com.amosley.signal.ui.MusicTab
import com.amosley.signal.ui.Screen
import com.amosley.signal.ui.Section
import com.amosley.signal.ui.VideoTab
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
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

private const val POSTER_COLS = 3
private const val ALBUM_COLS = 2

@Composable
fun LibraryPane(c: Ctx) {
    val st = c.st
    val section = if (c.unfolded && st.section == Section.SETTINGS) Section.MUSIC else st.section
    val listState = rememberLazyListState()
    val jump = remember { JumpIndex() }
    // The store is a web page with its own scrolling: it fills the pane under the tabs instead of joining the list.
    if (section == Section.MUSIC && st.tab == MusicTab.STORE) {
        Column(Modifier.fillMaxSize()) {
            LibraryHeader(c, section)
            SubTabs(c)
            androidx.compose.runtime.key(st.storeNonce) {
                StoreBrowser(c, st.storeUrl ?: com.amosley.signal.core.StoreFiles.QOBUZ_HOME, Modifier.weight(1f), onPage = { st.storeUrl = it })
            }
        }
        return
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // More columns when the pane is wide (unfolded, full width).
        val posterCols = (maxWidth / 150.dp).toInt().coerceIn(POSTER_COLS, 8)
        val albumCols = (maxWidth / 190.dp).toInt().coerceIn(ALBUM_COLS, 6)
        LazyColumn(Modifier.fillMaxSize(), state = listState) {
            jump.reset()
            jump.posterCols = posterCols
            jump.albumCols = albumCols
            counted(jump) { LibraryHeader(c, section) }
            when (section) {
                Section.MUSIC -> {
                    counted(jump) { SubTabs(c) }
                    when (st.tab) {
                        MusicTab.RECENT -> recent(c, jump.albumCols)
                        MusicTab.SONGS -> songs(c, jump)
                        MusicTab.ALBUMS -> albums(c, jump)
                        MusicTab.ARTISTS -> artists(c, jump)
                        MusicTab.PLAYLISTS -> playlists(c)
                        MusicTab.VIDEOS -> musicVideos(c)
                        MusicTab.STORE -> Unit
                    }
                }
                Section.MOVIES -> {
                    counted(jump) { TabRow(VideoTab.entries.map { it.label }, st.movieTab.ordinal) { st.movieTab = VideoTab.entries[it] } }
                    when (st.movieTab) {
                        VideoTab.RECOMMENDED -> moviesHome(c)
                        VideoTab.BROWSE -> movies(c, jump, c.lib.movies, "All movies")
                        VideoTab.CATEGORIES -> {
                            val genres = genreCounts(c.lib.movies.map { it.genres })
                            counted(jump) { GenrePicker(genres, st.movieGenre) { st.movieGenre = it } }
                            st.movieGenre?.let { g -> movies(c, jump, c.lib.movies.filter { g in it.genres }, g) }
                        }
                    }
                }
                Section.TV -> {
                    counted(jump) { TabRow(VideoTab.entries.map { it.label }, st.tvTab.ordinal) { st.tvTab = VideoTab.entries[it] } }
                    when (st.tvTab) {
                        VideoTab.RECOMMENDED -> showsHome(c)
                        VideoTab.BROWSE -> shows(c, jump, c.lib.shows, "All shows")
                        VideoTab.CATEGORIES -> {
                            val genres = genreCounts(c.lib.shows.map { it.genres })
                            counted(jump) { GenrePicker(genres, st.tvGenre) { st.tvGenre = it } }
                            st.tvGenre?.let { g -> shows(c, jump, c.lib.shows.filter { g in it.genres }, g) }
                        }
                    }
                }
                Section.SETTINGS -> Unit
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
        railMode(c, section)?.let { AlphaRail(listState, jump, it) }
    }
}

/** Section title (or picker), the "+N new" button, search and settings. */
@Composable
private fun LibraryHeader(c: Ctx, section: Section) {
    val st = c.st
    Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Box(Modifier.weight(1f)) {
                if (c.unfolded) SectionPicker(c, section) else Text(section.label, style = T.sectionTitle, color = C.Fg)
            }
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
        // PC status lives in Settings; here only a problem is shown (songs can't stream while the PC is unreachable).
        val s = c.status
        if (c.settings.pc != null && (c.settings.offline || (!s.reachable && !s.checking))) {
            Spacer(Modifier.height(8.dp))
            StatusLine(c)
        }
    }
}

/** The right-edge strip for long lists: A–Z letters when sorted by a name, a scrub bar for other sorts. */
private fun railMode(c: Ctx, section: Section): RailMode? {
    val names = setOf(SortKey.TITLE, SortKey.ARTIST, SortKey.ALBUM)
    fun mode(tab: SortTab, size: Int, min: Int) = when {
        size <= min -> null
        sortPref(c, tab).key in names -> RailMode.LETTERS
        else -> RailMode.SCRUB
    }
    return when (section) {
        Section.MUSIC -> when (c.st.tab) {
            MusicTab.SONGS -> mode(SortTab.SONGS, c.lib.tracks.size, 40)
            MusicTab.ALBUMS -> mode(SortTab.ALBUMS, c.lib.albums.size, 24)
            MusicTab.ARTISTS -> mode(SortTab.ARTISTS, c.lib.artists.size, 30)
            else -> null
        }
        // Only the grids have a strip (not the Recommended rows or the list of genres).
        Section.MOVIES -> when (c.st.movieTab) {
            VideoTab.BROWSE -> mode(SortTab.MOVIES, c.lib.movies.size, 30)
            VideoTab.CATEGORIES -> c.st.movieGenre?.let { g -> mode(SortTab.MOVIES, c.lib.movies.count { g in it.genres }, 30) }
            VideoTab.RECOMMENDED -> null
        }
        Section.TV -> when (c.st.tvTab) {
            VideoTab.BROWSE -> mode(SortTab.SHOWS, c.lib.shows.size, 30)
            VideoTab.CATEGORIES -> c.st.tvGenre?.let { g -> mode(SortTab.SHOWS, c.lib.shows.count { g in it.genres }, 30) }
            VideoTab.RECOMMENDED -> null
        }
        Section.SETTINGS -> null
    }
}

private val monthFmt = java.text.SimpleDateFormat("MMM yyyy", java.util.Locale.getDefault())

/** Scrub-bar bubble for one item under a non-name sort. */
private fun bubbleFor(key: SortKey, addedAt: Long, year: Int?, rating: Double?, durationMs: Long, songs: Int? = null): String? = when (key) {
    SortKey.ADDED -> if (addedAt > 0) monthFmt.format(java.util.Date(addedAt)) else null
    SortKey.YEAR -> year?.toString() ?: "No year"
    SortKey.RATING -> rating?.let { String.format(java.util.Locale.ROOT, "%.1f", it) } ?: "Not rated"
    SortKey.DURATION -> if (durationMs > 0) Fmt.runtime(durationMs) else null
    SortKey.SONGS -> songs?.let { "$it song${if (it != 1) "s" else ""}" }
    else -> null
}

/** An item that comes before the sorted rows, counted so the A–Z strip knows where the rows start. */
private fun LazyListScope.counted(j: JumpIndex, content: @Composable LazyItemScope.() -> Unit) {
    j.count++
    item(content = content)
}

@Composable
private fun SubTabs(c: Ctx) = TabRow(MusicTab.entries.map { it.label }, c.st.tab.ordinal) { c.st.tab = MusicTab.entries[it] }

/** Underlined text tabs (Music: Recently Added, Songs…; Movies/TV: Recommended, Browse, Categories). */
@Composable
private fun TabRow(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, top = 16.dp)
            .drawBehind { drawRect(C.Hair, Offset(0f, size.height - 1.dp.toPx()), Size(size.width, 1.dp.toPx())) },
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        labels.forEachIndexed { i, label ->
            val on = i == selected
            Box(
                Modifier.clickable { onSelect(i) }.padding(bottom = 10.dp)
                    .drawBehind { if (on) drawRect(C.Amber, Offset(0f, size.height + 8.dp.toPx()), Size(size.width, 2.dp.toPx())) },
            ) { Text(label, style = T.ui(14.sp, if (on) 600 else 500), color = if (on) C.Fg else C.Muted) }
        }
    }
}

/** Unfolded: the big section title is a menu (Music / Movies / TV Shows) instead of a separate bar. */
@Composable
private fun SectionPicker(c: Ctx, section: Section) {
    var open by remember { mutableStateOf(false) }
    Box {
        Row(Modifier.clickable { open = true }, verticalAlignment = Alignment.CenterVertically) {
            Text(section.label, style = T.sectionTitle, color = C.Fg)
            Spacer(Modifier.width(6.dp))
            Icon(Icons.Filled.KeyboardArrowDown, "Change section", tint = C.Muted, modifier = Modifier.size(30.dp))
        }
        DropdownMenu(open, onDismissRequest = { open = false }, containerColor = C.Surface) {
            listOf(Section.MUSIC, Section.MOVIES, Section.TV).forEach { s ->
                DropdownMenuItem(
                    text = { Text(s.label, style = T.ui(16.sp, if (s == section) 700 else 500), color = if (s == section) C.AmberText else C.Fg) },
                    onClick = { open = false; c.st.goSection(s) },
                )
            }
        }
    }
}

/** Genre → number of titles, most common first. */
private fun genreCounts(genres: List<List<String>>): List<Pair<String, Int>> =
    genres.flatten().filter { it.isNotBlank() }.groupingBy { it }.eachCount().toList().sortedWith(compareByDescending<Pair<String, Int>> { it.second }.thenBy { it.first })

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun GenrePicker(genres: List<Pair<String, Int>>, selected: String?, onPick: (String?) -> Unit) {
    Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp)) {
        if (genres.isEmpty()) {
            Mono("No genres yet · they come from Plex or online details", color = C.Faint)
            return@Column
        }
        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            genres.forEach { (g, n) ->
                val on = g == selected
                Box(
                    Modifier.background(if (on) C.Amber else Color.Transparent).border(1.dp, if (on) C.Amber else C.HairStrong)
                        .clickable { onPick(if (on) null else g) }.padding(horizontal = 12.dp, vertical = 7.dp),
                ) { Text("$g · $n", style = T.ui(13.sp, 600), color = if (on) C.OnAmber else C.Fg) }
            }
        }
        if (selected == null) Mono("Pick a genre", color = C.Faint, modifier = Modifier.padding(top = 12.dp))
    }
}

fun sortPref(c: Ctx, tab: SortTab): SortPref = c.settings.sorts[tab] ?: tab.default

/** "SORT  A–Z ↑  Artist  Date added …" — tap an option to sort by it, tap it again to flip the order. */
@Composable
fun SortBar(c: Ctx, tab: SortTab, modifier: Modifier = Modifier) {
    val cur = sortPref(c, tab)
    Row(
        modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Mono("Sort", color = C.Faint, style = T.metaMono)
        tab.options.forEach { k ->
            val on = cur.key == k
            Box(
                Modifier
                    .background(if (on) C.Amber else Color.Transparent)
                    .border(1.dp, if (on) C.Amber else C.HairStrong)
                    .clickable {
                        val next = if (on) cur.copy(descending = !cur.descending) else SortPref(k, k.defaultDescending)
                        c.repo.updateSettings { it.copy(sorts = it.sorts + (tab to next)) }
                    }
                    .padding(horizontal = 9.dp, vertical = 5.dp),
            ) {
                Text(
                    k.label + if (on) (if (cur.descending) " ↓" else " ↑") else "",
                    style = T.ui(12.sp, 600), color = if (on) C.OnAmber else C.Fg, maxLines = 1,
                )
            }
        }
    }
}

fun LazyListScope.songRows(c: Ctx, list: List<Track>, key: String) {
    items(list, key = { "$key-${it.id}" }) { t ->
        SongRow(
            t = t, artModel = c.art(t), dl = c.dlState(t.id),
            isCurrent = c.player.current?.id == t.id, playing = c.player.playing, unavailable = c.unavailable(t),
            onPlay = {
                if (c.unavailable(t)) c.toast(if (c.settings.offline) "Offline mode is on · only downloaded songs play" else "Can't reach ${c.pcName} · only downloaded songs play until it's back")
                else c.st.playFrom(c.app, list, t)
            },
            onArtist = { c.st.openArtist(c.lib.artistOf[t.id] ?: it) },
            onMore = { c.st.sheet = Sheet.Actions(t.id) },
            favorite = c.isFavorite(t.id),
        )
    }
}

private fun LazyListScope.recent(c: Ctx, albumCols: Int) {
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
    // Albums (and songs with no album, as "Single" tiles), newest first, like the Albums tab.
    val weekAgo = System.currentTimeMillis() - 7 * 24 * 3600_000L
    val tiles = (c.lib.albums.filter { it.title != "Singles" && it.tracks.isNotEmpty() }.map { RecentTile(it, null, it.tracks.maxOf { t -> t.addedAt }) } +
        c.lib.albums.filter { it.title == "Singles" }.flatMap { a -> a.tracks.map { RecentTile(null, it, it.addedAt) } })
        .sortedByDescending { it.at }.take(albumCols * 20)
    val (week, earlier) = tiles.partition { it.at > weekAgo }
    if (week.isNotEmpty()) {
        item { SectionLabel("Added this week", Modifier.padding(horizontal = 20.dp)) }
        recentGrid(c, week, "rw", albumCols)
    }
    if (earlier.isNotEmpty()) {
        item { SectionLabel(if (week.isEmpty()) "Recently added" else "Earlier", Modifier.padding(horizontal = 20.dp)) }
        recentGrid(c, earlier, "re", albumCols)
    }
}

/** One Recently Added tile: an album, or a song with no album. */
private data class RecentTile(val album: Album?, val track: Track?, val at: Long) {
    val key: String get() = album?.key ?: "t-${track!!.id}"
}

private fun LazyListScope.recentGrid(c: Ctx, tiles: List<RecentTile>, key: String, cols: Int) {
    items(tiles.chunked(cols), key = { row -> "$key-${row.first().key}" }) { row ->
        Row(Modifier.padding(horizontal = 20.dp, vertical = 9.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            row.forEach { tile ->
                val a = tile.album
                val t = tile.track
                if (a != null) {
                    Column(Modifier.weight(1f).clickable { c.st.push(Screen.Album(a.key)) }) {
                        Art(a.key, c.albumArt(a), Modifier.fillMaxWidth().aspectRatio(1f))
                        Spacer(Modifier.height(8.dp))
                        Text(a.title, style = T.rowSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(a.artist ?: "Unknown artist", style = T.meta, color = if (a.artist == null) C.AmberText else C.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                } else if (t != null) {
                    Column(Modifier.weight(1f).clickable { c.st.playFrom(c.app, listOf(t), t) }) {
                        Art(t.albumKey, c.art(t), Modifier.fillMaxWidth().aspectRatio(1f)) {
                            Box(Modifier.align(Alignment.TopStart).padding(6.dp).background(Color.Black.copy(alpha = 0.6f)).padding(horizontal = 5.dp, vertical = 2.dp)) {
                                Text("SINGLE", style = T.badge, color = C.Fg)
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(t.title, style = T.rowSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(t.artist ?: "Unknown artist", style = T.meta, color = if (t.artist == null) C.AmberText else C.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
        }
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

private fun LazyListScope.songs(c: Ctx, jump: JumpIndex) {
    val pref = sortPref(c, SortTab.SONGS)
    val all = Sorting.songs(c.lib.tracks, pref)
    counted(jump) {
        Row(Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FilledBtn("Play", icon = Icons.Filled.PlayArrow, modifier = Modifier.weight(1f)) { c.hub.playList(all, 0) }
            FilledBtn("Shuffle", icon = Icons.Filled.Shuffle, bg = Color.White.copy(alpha = 0.1f), fg = C.Fg, modifier = Modifier.weight(1f)) { c.hub.shuffleAll(all) }
        }
    }
    counted(jump) { SortBar(c, SortTab.SONGS, Modifier.padding(top = 8.dp)) }
    jump.mark(
        all.map { t -> when (pref.key) { SortKey.ARTIST -> t.artist; SortKey.ALBUM -> t.album; else -> t.title } },
        bubbles = all.map { bubbleFor(pref.key, it.addedAt, it.year, null, it.durationMs) },
    )
    songRows(c, all, "songs")
}

fun LazyListScope.albumGrid(c: Ctx, albums: List<Album>, key: String, cols: Int = 2) {
    items(albums.chunked(cols), key = { row -> "$key-${row.first().key}" }) { row ->
        Row(Modifier.padding(horizontal = 20.dp, vertical = 9.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            row.forEach { a ->
                Column(Modifier.weight(1f).clickable { c.st.push(Screen.Album(a.key)) }) {
                    Art(a.key, c.albumArt(a), Modifier.fillMaxWidth().aspectRatio(1f))
                    Spacer(Modifier.height(8.dp))
                    Text(a.title, style = T.rowSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(a.artist ?: "Unknown artist", style = T.meta, color = if (a.artist == null) C.AmberText else C.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

private fun LazyListScope.albums(c: Ctx, jump: JumpIndex) {
    counted(jump) {
        Box(Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 6.dp)) {
            OutlineBtn("Shuffle all albums", icon = Icons.Filled.Shuffle) { c.hub.shuffleAll(c.lib.albums.flatMap { it.tracks }) }
        }
    }
    counted(jump) { SortBar(c, SortTab.ALBUMS) }
    val pref = sortPref(c, SortTab.ALBUMS)
    val list = Sorting.albums(c.lib.albums, pref)
    jump.mark(
        list.map { if (pref.key == SortKey.ARTIST) it.artist else it.title }, perRow = jump.albumCols,
        bubbles = list.map { a -> bubbleFor(pref.key, a.tracks.maxOfOrNull { it.addedAt } ?: 0, a.year, null, 0) },
    )
    albumGrid(c, list, "albums", jump.albumCols)
}

private fun LazyListScope.artists(c: Ctx, jump: JumpIndex) {
    counted(jump) { SortBar(c, SortTab.ARTISTS, Modifier.padding(top = 10.dp)) }
    val newest = HashMap<String, Long>().apply { c.lib.tracks.forEach { t -> (c.lib.artistOf[t.id] ?: t.artist)?.let { a -> if (t.addedAt > (this[a] ?: 0)) this[a] = t.addedAt } } }
    val apref = sortPref(c, SortTab.ARTISTS)
    val list = Sorting.artists(c.lib.artists, newest, apref)
    jump.mark(list.map { it.first }, bubbles = list.map { (name, n) -> bubbleFor(apref.key, newest[name] ?: 0, null, null, 0, n) })
    items(list, key = { "artist-${it.first}" }) { (name, n) ->
        Row(Modifier.fillMaxWidth().clickable { c.st.openArtist(name) }.padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Art(name, c.artistArt(name), Modifier.size(56.dp), shape = CircleShape)
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
    item {
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 6.dp).clickable { c.st.sheet = Sheet.ImportPlaylist() }
                .drawBehind {
                    drawRect(C.HairStrong, style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))))
                }.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Add, null, tint = C.AmberText)
            Spacer(Modifier.width(10.dp))
            Text("Import from Apple Music", style = T.row)
        }
    }
    item {
        val favs = c.lib.tracks.filter { c.isFavorite(it.id) }
        PlaylistRow(c, "Favorites", "${favs.size} song${if (favs.size != 1) "s" else ""} · tap ♥ on any song", Screen.FAVORITES, favs.firstOrNull())
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

private fun LazyListScope.posterGrid(c: Ctx, cols: Int, count: Int, key: (Int) -> String, cell: @Composable (Int, Modifier) -> Unit) {
    val rows = (0 until count).chunked(cols)
    items(rows, key = { "$it-${key(it.first())}" }) { row ->
        Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            row.forEach { i -> cell(i, Modifier.weight(1f)) }
            repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

/** Movies › Recommended: Plex-style rows. */
private fun LazyListScope.moviesHome(c: Ctx) {
    val all = c.lib.movies
    if (all.isEmpty()) { item { Box(Modifier.padding(20.dp)) { Mono("No movies yet", color = C.Faint) } }; return }
    val cont = Watching.continueWatching(all, emptyList(), c.lib.plexContinue, mine = c.lib.watchedInSignal)
    if (cont.isNotEmpty()) item { WatchRow(c, "Continue watching", cont) }
    val released = all.filter { it.year != null }.sortedWith(compareByDescending<Movie> { it.year }.thenByDescending { it.addedAt }).take(20)
    if (released.isNotEmpty()) item { MovieRow(c, "Recently released", released) }
    val recent = Watching.recentMovies(all)
    if (recent.isNotEmpty()) item { MovieRow(c, "Recently added", recent) }
    val unwatched = all.filter { !it.watched && it.viewOffsetMs == 0L }.sortedByDescending { it.addedAt }
    genreCounts(all.map { it.genres }).take(5).forEach { (g, _) ->
        val inGenre = unwatched.filter { g in it.genres }.take(20)
        if (inGenre.size >= 3) item(key = "mg-$g") {
            MovieRow(c, g, inGenre) { c.st.movieTab = VideoTab.CATEGORIES; c.st.movieGenre = g }
        }
    }
}

/** TV Shows › Recommended: Plex-style rows. */
private fun LazyListScope.showsHome(c: Ctx) {
    val all = c.lib.shows
    if (all.isEmpty()) { item { Box(Modifier.padding(20.dp)) { Mono("No TV shows yet", color = C.Faint) } }; return }
    val cont = Watching.continueWatching(emptyList(), all, c.lib.plexContinue, mine = c.lib.watchedInSignal)
    if (cont.isNotEmpty()) item { WatchRow(c, "Continue watching", cont) }
    val next = Watching.upNext(all, c.lib.plexContinue, mine = c.lib.watchedInSignal)
    if (next.isNotEmpty()) item { WatchRow(c, "Up next", next) }
    val recent = Watching.recentEpisodes(all)
    if (recent.isNotEmpty()) item { WatchRow(c, "Recently added", recent, recentStyle = true) }
    // Shows you haven't started (nothing watched in Signal or Plex), newest first.
    val start = all.filter { sh -> sh.seasons.all { se -> se.episodes.all { !it.watched && it.viewOffsetMs == 0L && it.id !in c.lib.watchedInSignal } } }
        .sortedByDescending { it.addedAt }.take(20)
    if (start.isNotEmpty()) item { ShowRow(c, "Start watching", start) }
    genreCounts(all.map { it.genres }).take(5).forEach { (g, _) ->
        val inGenre = all.filter { g in it.genres }.sortedByDescending { it.addedAt }.take(20)
        if (inGenre.size >= 3) item(key = "sg-$g") {
            ShowRow(c, g, inGenre) { c.st.tvTab = VideoTab.CATEGORIES; c.st.tvGenre = g }
        }
    }
}

private fun LazyListScope.movies(c: Ctx, jump: JumpIndex, source: List<Movie>, label: String) {
    val list: List<Movie> = Sorting.movies(source, sortPref(c, SortTab.MOVIES))
    counted(jump) { SectionLabel(label, Modifier.padding(horizontal = 20.dp), trailing = "${list.size}") }
    counted(jump) { SortBar(c, SortTab.MOVIES) }
    if (list.isEmpty()) counted(jump) { Box(Modifier.padding(20.dp)) { Mono("No movies yet", color = C.Faint) } }
    val mpref = sortPref(c, SortTab.MOVIES)
    jump.mark(list.map { it.title }, perRow = jump.posterCols, bubbles = list.map { bubbleFor(mpref.key, it.addedAt, it.year, it.rating, it.durationMs) })
    posterGrid(c, jump.posterCols, list.size, { list[it].id }) { i, m ->
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

private fun LazyListScope.shows(c: Ctx, jump: JumpIndex, source: List<Show>, label: String) {
    val list: List<Show> = Sorting.shows(source, sortPref(c, SortTab.SHOWS))
    counted(jump) { SectionLabel(label, Modifier.padding(horizontal = 20.dp), trailing = "${list.size}") }
    counted(jump) { SortBar(c, SortTab.SHOWS) }
    if (list.isEmpty()) counted(jump) { Box(Modifier.padding(20.dp)) { Mono("No TV shows yet", color = C.Faint) } }
    val spref = sortPref(c, SortTab.SHOWS)
    jump.mark(list.map { it.title }, perRow = jump.posterCols, bubbles = list.map { bubbleFor(spref.key, it.addedAt, it.year, it.rating, 0) })
    posterGrid(c, jump.posterCols, list.size, { list[it].id }) { i, m ->
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

/** A Plex-style horizontal row of 16:9 cards (Continue watching / Up next / Recently added). */
@Composable
private fun WatchRow(c: Ctx, title: String, cards: List<WatchItem>, recentStyle: Boolean = false) {
    Column(Modifier.padding(top = 14.dp)) {
        Mono(title, color = C.Muted, modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(cards, key = { "$title-${it.id}" }) { w -> WatchCard(c, w, recentStyle) }
        }
    }
}

@Composable
private fun WatchCard(c: Ctx, w: WatchItem, recentStyle: Boolean) {
    val ep = w.episode
    val show = w.show
    val mv = w.movie
    val art: Any? = when {
        ep != null -> (ep.thumbUrl ?: show?.backdropUrl)?.let { c.repo.remoteUrl(it) }
        else -> (mv?.backdropUrl ?: mv?.posterUrl)?.let { c.repo.remoteUrl(it) }
    }
    val heading = show?.title ?: mv?.title ?: ""
    val left = (w.durationMs - w.offsetMs).coerceAtLeast(0)
    val sub = when {
        ep != null && recentStyle -> "S${ep.season} · E${ep.episode} · ${ep.title}"
        ep != null && w.offsetMs > 0 -> "S${ep.season} · E${ep.episode} · ${Fmt.runtime(left)} left"
        ep != null -> "S${ep.season} · E${ep.episode} · ${ep.title}"
        mv != null && w.offsetMs > 0 -> "${Fmt.runtime(left)} left"
        else -> mv?.year?.toString() ?: ""
    }
    Column(
        Modifier.width(220.dp).clickable {
            if (ep != null) c.st.push(Screen.Video(ep.id, VideoKind.EPISODE, show?.id)) else if (mv != null) c.st.push(Screen.Video(mv.id, VideoKind.MOVIE))
        },
    ) {
        Art(heading + (ep?.id ?: ""), art, Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
            PlayDisc(34.dp, Modifier.align(Alignment.Center))
            if (w.offsetMs > 0) {
                Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(3.dp).background(Color.Black.copy(alpha = 0.5f))) {
                    Box(Modifier.fillMaxWidth(w.progress).height(3.dp).background(C.Amber))
                }
            }
            if (recentStyle && ep != null && !ep.watched) {
                Box(Modifier.align(Alignment.TopStart).padding(6.dp).background(C.Amber).padding(horizontal = 5.dp, vertical = 1.dp)) {
                    Text("NEW", style = T.badge, color = C.OnAmber)
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(heading, style = T.ui(13.5.sp, 600), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(sub, style = T.ui(12.sp), color = C.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun RowTitle(title: String, onSeeAll: (() -> Unit)?) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Mono(title, color = C.Muted, modifier = Modifier.weight(1f))
        if (onSeeAll != null) Mono("See all ›", color = C.AmberText, modifier = Modifier.clickable(onClick = onSeeAll))
    }
}

@Composable
private fun ShowRow(c: Ctx, title: String, shows: List<Show>, onSeeAll: (() -> Unit)? = null) {
    val w = if (c.unfolded) 150.dp else 112.dp
    Column(Modifier.padding(top = 14.dp)) {
        RowTitle(title, onSeeAll)
        LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(shows, key = { "$title-${it.id}" }) { sh ->
                Column(Modifier.width(w).clickable { c.st.season = null; c.st.push(Screen.ShowPage(sh.id)) }) {
                    Art(sh.title, sh.posterUrl?.let { c.repo.remoteUrl(it) }, Modifier.fillMaxWidth().aspectRatio(2f / 3f))
                    Spacer(Modifier.height(6.dp))
                    Text(sh.title, style = T.ui(12.5.sp, 600), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${sh.seasons.size} season${if (sh.seasons.size != 1) "s" else ""}", style = T.ui(11.5.sp), color = C.Muted, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun MovieRow(c: Ctx, title: String, movies: List<Movie>, onSeeAll: (() -> Unit)? = null) {
    val w = if (c.unfolded) 150.dp else 112.dp
    Column(Modifier.padding(top = 14.dp)) {
        RowTitle(title, onSeeAll)
        LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(movies, key = { "$title-${it.id}" }) { mv ->
                Column(Modifier.width(w).clickable { c.st.push(Screen.MoviePage(mv.id)) }) {
                    Art(mv.title, mv.posterUrl?.let { c.repo.remoteUrl(it) }, Modifier.fillMaxWidth().aspectRatio(2f / 3f)) {
                        PosterBadge(c, mv.id, mv.origin, Modifier.align(Alignment.TopEnd).padding(5.dp))
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(mv.title, style = T.ui(12.5.sp, 600), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(mv.year?.toString() ?: "", style = T.ui(11.5.sp), color = C.Muted, maxLines = 1)
                }
            }
        }
    }
}
