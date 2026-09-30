package com.amosley.signal.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amosley.signal.core.Fmt
import com.amosley.signal.core.MusicVideo
import com.amosley.signal.core.Origin
import com.amosley.signal.ui.components.OutlineBtn
import com.amosley.signal.ui.components.CardBox
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.foundation.border
import com.amosley.signal.core.Track
import com.amosley.signal.data.DlState
import com.amosley.signal.ui.Ctx
import com.amosley.signal.ui.Screen
import com.amosley.signal.ui.Sheet
import com.amosley.signal.ui.VideoKind
import com.amosley.signal.ui.components.Art
import com.amosley.signal.ui.components.FilledBtn
import com.amosley.signal.ui.components.FormatChip
import com.amosley.signal.ui.components.Mono
import com.amosley.signal.ui.components.PlayDisc
import com.amosley.signal.ui.components.PlayingBars
import com.amosley.signal.ui.components.QualityBadge
import com.amosley.signal.ui.components.SectionLabel
import com.amosley.signal.ui.components.SongRow
import com.amosley.signal.ui.components.SourceDot
import com.amosley.signal.ui.components.Spinner
import com.amosley.signal.ui.components.SquareBtn
import com.amosley.signal.ui.components.artBrush
import com.amosley.signal.ui.theme.C
import com.amosley.signal.ui.theme.T

@Composable
fun BackButton(c: Ctx, modifier: Modifier = Modifier) {
    SquareBtn(Icons.AutoMirrored.Filled.ArrowBack, modifier, size = 38.dp, bg = Color.Black.copy(alpha = 0.35f), desc = "Back") { c.st.back() }
}

/** Download-state button for a PC item: ↓ / spinner / ✓ (tap ✓ to remove the download). */
@Composable
fun DlButton(c: Ctx, id: String, origin: Origin, size: Int = 34, onDownload: () -> Unit) {
    if (origin == Origin.PHONE) return
    Box(
        Modifier.size(size.dp).clickable {
            when (c.dlState(id)) {
                is DlState.Done -> { c.repo.downloads.remove(id); c.toast("Removed download") }
                is DlState.Running, is DlState.Queued -> c.repo.downloads.cancel(id)
                is DlState.Failed -> c.repo.downloads.retry(id)
                else -> onDownload()
            }
        },
        contentAlignment = Alignment.Center,
    ) {
        when (val d = c.dlState(id)) {
            is DlState.Done -> SourceDot(origin, d, Modifier.size(16.dp))
            is DlState.Running -> Spinner(Modifier.size(16.dp), d.progress)
            is DlState.Queued -> Spinner(Modifier.size(16.dp))
            else -> Icon(Icons.Filled.Download, "Download", tint = if (d is DlState.Failed) C.Amber else C.Muted, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun DetailHeader(
    c: Ctx,
    key: String,
    artModel: Any?,
    title: String,
    subtitle: String?,
    onSubtitle: (() -> Unit)?,
    meta: String,
    tracks: List<Track>,
    round: Boolean = false,
    changeArt: String? = null,
    onChangeArt: (() -> Unit)? = null,
    extra: @Composable () -> Unit = {},
) {
    Box(Modifier.fillMaxWidth()) {
        // Blurred glow of the art behind the header.
        Box(
            Modifier.fillMaxWidth().height(420.dp).alpha(0.55f).blur(60.dp)
                .background(Brush.verticalGradient(listOf(Color.Transparent, C.Bg)))
                .background(artBrush(key, 0.3f)),
        )
        Box(Modifier.fillMaxWidth().height(420.dp).background(Brush.verticalGradient(listOf(Color.Transparent, C.Bg))))
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                BackButton(c)
                Spacer(Modifier.weight(1f))
                val allDown = tracks.isNotEmpty() && tracks.all { it.origin == Origin.PHONE || c.dlState(it.id) is DlState.Done }
                if (allDown) {
                    Row(Modifier.background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(7.dp).background(C.Green, CircleShape))
                        Spacer(Modifier.width(6.dp))
                        Text("Available offline", style = T.ui(12.sp, 500))
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Art(key, artModel, Modifier.size(220.dp), shape = if (round) CircleShape else androidx.compose.ui.graphics.RectangleShape)
            Spacer(Modifier.height(16.dp))
            Text(title, style = T.albumTitle, textAlign = TextAlign.Center)
            if (subtitle != null) {
                Text(
                    subtitle + if (onSubtitle != null) " ›" else "", style = T.ui(16.sp, 500), color = C.AmberText,
                    modifier = Modifier.padding(top = 3.dp).then(if (onSubtitle != null) Modifier.clickable(onClick = onSubtitle) else Modifier),
                )
            }
            Spacer(Modifier.height(6.dp))
            Mono(meta, color = C.Faint, style = T.metaMono)
            if (changeArt != null && onChangeArt != null) {
                Mono(changeArt, color = C.AmberText, style = T.metaMono, modifier = Modifier.clickable(onClick = onChangeArt).padding(top = 8.dp, bottom = 2.dp, start = 8.dp, end = 8.dp))
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                FilledBtn("Play", icon = Icons.Filled.PlayArrow, modifier = Modifier.width(120.dp)) { c.hub.playList(tracks, 0) }
                FilledBtn("Shuffle", icon = Icons.Filled.Shuffle, bg = Color.White.copy(alpha = 0.12f), fg = C.Fg, modifier = Modifier.width(120.dp)) { c.hub.shuffleAll(tracks) }
                if (tracks.any { it.origin == Origin.PC }) {
                    SquareBtn(Icons.Filled.Download, size = 40.dp, desc = "Download all") {
                        tracks.forEach { c.repo.download(it) }
                        c.toast("Downloading ${tracks.count { it.origin == Origin.PC }} songs")
                    }
                }
            }
            extra()
        }
    }
}

@Composable
fun TrackRowNumbered(c: Ctx, t: Track, index: Int, list: List<Track>) {
    val current = c.player.current?.id == t.id
    Row(
        Modifier.fillMaxWidth().alpha(if (c.unavailable(t)) 0.35f else 1f).clickable { c.st.playFrom(c.app, list, t) }.padding(start = 20.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(26.dp)) {
            if (current) PlayingBars(playing = c.player.playing) else Text("${t.track ?: index + 1}", style = T.mono(13.sp, 500, 0.sp), color = C.Faint)
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(t.title, style = T.row, color = if (current) C.AmberText else C.Fg, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (c.isFavorite(t.id)) {
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.Filled.Favorite, "Favorite", tint = C.Amber, modifier = Modifier.size(12.dp))
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (t.quality != com.amosley.signal.core.Quality.LOSSY) {
                    QualityBadge(t.quality.label)
                    Spacer(Modifier.width(5.dp))
                }
                t.container?.let { FormatChip(it); Spacer(Modifier.width(6.dp)) }
                Text(
                    listOfNotNull(t.bitsLabel, c.srcLabel(t)).joinToString(" · "), style = T.metaMono, color = C.Faint,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Text(Fmt.dur(t.durationMs), style = T.mono(11.sp, 500, 0.sp), color = C.Faint)
        DlButton(c, t.id, t.origin) { c.repo.download(t) }
        Box(Modifier.size(34.dp).clickable { c.st.sheet = Sheet.Actions(t.id) }, contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.MoreHoriz, "More", tint = C.Muted)
        }
    }
}

@Composable
fun MusicVideoRow(c: Ctx, v: MusicVideo) {
    Row(Modifier.fillMaxWidth().clickable { c.st.push(Screen.Video(v.id, VideoKind.MUSIC_VIDEO)) }.padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Art(v.title, if (v.hasArt) c.repo.artUrl(v.id, "thumb") else null, Modifier.size(150.dp, 84.dp)) {
            PlayDisc(30.dp, Modifier.align(Alignment.Center))
            Box(Modifier.align(Alignment.BottomEnd).padding(4.dp).background(Color.Black.copy(alpha = 0.7f)).padding(horizontal = 4.dp, vertical = 1.dp)) {
                Text(Fmt.dur(v.durationMs), style = T.badge, color = C.Fg)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(v.title, style = T.rowSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                FormatChip(v.container ?: "MP4")
                Spacer(Modifier.width(6.dp))
                Mono(listOfNotNull(v.resLabel, if (v.hdr) "HDR" else null, Fmt.dur(v.durationMs)).joinToString(" · "), style = T.metaMono, color = C.Faint)
            }
            Mono(if (v.origin == Origin.PHONE) "On this phone" else if (c.dlState(v.id) is DlState.Done) "Downloaded" else "Stream from ${c.pcName}", style = T.metaMono, color = C.Faint)
        }
        DlButton(c, v.id, v.origin) { c.repo.download(v) }
    }
}

@Composable
fun AlbumScreen(c: Ctx, key: String) {
    val album = c.lib.albums.firstOrNull { it.key == key }
    if (album == null) return Missing(c)
    val tracks = album.tracks
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            DetailHeader(
                c, album.key, c.albumArt(album), album.title,
                subtitle = listOfNotNull(album.artist ?: "Unknown artist", album.year?.toString()).joinToString(" · "),
                onSubtitle = album.artist?.let { a -> { c.st.openArtist(a) } },
                meta = listOfNotNull("Album", "${tracks.size} songs", if (album.videos.isNotEmpty()) "${album.videos.size} video${if (album.videos.size > 1) "s" else ""}" else null, Fmt.dur(album.durationMs)).joinToString(" · "),
                tracks = tracks,
                changeArt = "Change album art",
                onChangeArt = { c.st.sheet = Sheet.Art(album = album.key, artist = null) },
            )
        }
        // Same album title under other artists (a soundtrack or compilation): offer to combine them.
        val sameTitle = if (album.title == "Singles") emptyList() else c.lib.albums.filter { it.key != album.key && it.title.trim().equals(album.title.trim(), ignoreCase = true) }
        if (sameTitle.isNotEmpty()) item { CombineAlbumsCard(c, album, sameTitle) }
        item { Spacer(Modifier.height(10.dp)) }
        itemsIndexed(tracks, key = { _, t -> "at-${t.id}" }) { i, t -> TrackRowNumbered(c, t, i, tracks) }
        if (album.videos.isNotEmpty()) {
            item { SectionLabel("Music videos", Modifier.padding(horizontal = 20.dp), trailing = "${album.videos.size}") }
            itemsIndexed(album.videos, key = { _, v -> "av-${v.id}" }) { _, v -> MusicVideoRow(c, v) }
        }
        item {
            Text(
                "Videos join an album when their ALBUM tag matches, or by dropping them in the album folder on ${c.pcName}.",
                style = T.ui(12.5.sp), color = C.Faint, modifier = Modifier.padding(20.dp),
            )
        }
    }
}

@Composable
fun PlaylistScreen(c: Ctx, id: String) {
    val (name, tracks, user) = when (id) {
        Screen.NEEDS_ARTIST -> Triple("Needs an artist", c.lib.tracks.filter { it.artist == null }, false)
        Screen.RECENT_PLAYLIST -> Triple("Recently added", c.lib.tracks.sortedByDescending { it.addedAt }.take(50), false)
        Screen.FAVORITES -> Triple("Favorites", c.lib.tracks.filter { c.isFavorite(it.id) }.sortedBy { com.amosley.signal.core.Sorting.titleKey(it.title) }, false)
        else -> {
            val pl = c.playlists.firstOrNull { it.id == id } ?: return Missing(c)
            Triple(pl.name, pl.trackIds.mapNotNull { c.track(it) }, true)
        }
    }
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            DetailHeader(
                c, name, tracks.firstOrNull()?.let { c.art(it) }, name,
                subtitle = if (user) "Playlist" else "Smart playlist", onSubtitle = null,
                meta = "Playlist · ${tracks.size} songs · ${Fmt.dur(tracks.sumOf { it.durationMs })}", tracks = tracks,
            ) {
                if (id == Screen.NEEDS_ARTIST && tracks.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    FilledBtn("Suggest names for all", bg = C.Amber, fg = C.OnAmber) { c.st.sheet = Sheet.Batch }
                }
                if (user) {
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.clickable { c.repo.deletePlaylist(id); c.st.back(); c.toast("Deleted $name") }.padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Delete, null, tint = C.Faint, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Mono("Delete playlist", color = C.Faint)
                    }
                }
            }
        }
        item { Spacer(Modifier.height(10.dp)) }
        itemsIndexed(tracks, key = { i, t -> "pt-$i-${t.id}" }) { _, t ->
            SongRow(
                t, c.art(t), c.dlState(t.id), c.player.current?.id == t.id, c.player.playing, c.unavailable(t),
                onPlay = { c.st.playFrom(c.app, tracks, t) }, onArtist = { c.st.openArtist(it) },
                onMore = { c.st.sheet = if (id == Screen.NEEDS_ARTIST) Sheet.Edit(t.id) else Sheet.Actions(t.id) },
            )
        }
        if (tracks.isEmpty()) item {
            Box(Modifier.padding(20.dp)) {
                Mono(if (id == Screen.FAVORITES) "Tap ♥ on Now Playing, or ⋯ → Add to Favorites on any song" else "Add songs with ⋯ → Add to playlist", color = C.Faint, maxLines = 2)
            }
        }
    }
}

@Composable
fun ArtistScreen(c: Ctx, name: String) {
    val tracks = c.lib.artistTracks(name).sortedWith(compareBy<Track>({ it.album ?: "~" }).then(com.amosley.signal.core.trackOrder))
    val albums = com.amosley.signal.core.Sorting.albums(
        c.lib.albums.filter { a -> a.artist == name || (a.tracks.isNotEmpty() && a.tracks.all { c.lib.artistOf[it.id] == name }) }, sortPref(c, com.amosley.signal.core.SortTab.ARTIST_ALBUMS),
    )
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            DetailHeader(
                c, name, c.artistArt(name), name, subtitle = "Artist", onSubtitle = null,
                meta = "Artist · ${tracks.size} songs · ${albums.size} album${if (albums.size != 1) "s" else ""}", tracks = tracks, round = true,
                changeArt = "Change artist picture",
                onChangeArt = { c.st.sheet = Sheet.Art(album = null, artist = name) },
            )
        }
        if (albums.isNotEmpty()) {
            item { SectionLabel("Albums", Modifier.padding(horizontal = 20.dp)) }
            if (albums.size > 1) item { SortBar(c, com.amosley.signal.core.SortTab.ARTIST_ALBUMS) }
            albumGrid(c, albums, "artist-albums")
        }
        item { SectionLabel("Songs", Modifier.padding(horizontal = 20.dp)) }
        songRows(c, tracks, "artist")
    }
}

@Composable
fun Missing(c: Ctx) {
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        BackButton(c)
        Spacer(Modifier.height(20.dp))
        Mono("Not available", color = C.Faint)
    }
}

/** "3 more albums are called Vacancy: The Soundtrack" → combine them under one album artist. */
@Composable
private fun CombineAlbumsCard(c: Ctx, album: com.amosley.signal.core.Album, others: List<com.amosley.signal.core.Album>) {
    var name by remember(album.key) { mutableStateOf(album.tracks.firstNotNullOfOrNull { it.albumArtist } ?: "Various Artists") }
    Box(Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp)) {
        CardBox {
            Column {
                val n = others.size
                Text("$n more album${if (n > 1) "s are" else " is"} called ${album.title}", style = T.rowSecondary)
                Spacer(Modifier.height(3.dp))
                Text(
                    "By " + others.mapNotNull { it.artist }.distinct().take(4).joinToString(", ") +
                        ". Combine them into one album (like a soundtrack) by giving every song the same album artist. Your album art is kept.",
                    style = T.ui(12.5.sp), color = C.Muted,
                )
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.foundation.text.BasicTextField(
                        name, { name = it }, singleLine = true,
                        textStyle = T.ui(14.sp).copy(color = C.Fg), cursorBrush = androidx.compose.ui.graphics.SolidColor(C.Amber),
                        modifier = Modifier.weight(1f).border(1.dp, C.HairStrong).padding(horizontal = 10.dp, vertical = 9.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    OutlineBtn("Combine", color = C.AmberText, border = C.Amber) {
                        if (name.isBlank()) return@OutlineBtn
                        c.repo.combineAlbums(listOf(album) + others, name)
                        c.st.stack.removeAt(c.st.stack.lastIndex)
                        c.st.push(com.amosley.signal.ui.Screen.Album(com.amosley.signal.core.albumKeyOf(album.title, name.trim())))
                        c.toast(if ((listOf(album) + others).any { a -> a.tracks.any { it.origin == Origin.PC } }) "Combined · writing album artist on ${c.pcName}" else "Combined")
                    }
                }
                Text("Album artist", style = T.ui(11.5.sp), color = C.Faint, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}
