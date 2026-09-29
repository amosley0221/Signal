package com.amosley.signal.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amosley.signal.core.Search
import com.amosley.signal.ui.Ctx
import com.amosley.signal.ui.Screen
import com.amosley.signal.ui.Sheet
import com.amosley.signal.ui.VideoKind
import com.amosley.signal.ui.components.Art
import com.amosley.signal.ui.components.Mono
import com.amosley.signal.ui.components.PlayDisc
import com.amosley.signal.ui.theme.C
import com.amosley.signal.ui.theme.T

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(c: Ctx) {
    val focus = remember { FocusRequester() }
    val lyrics by c.repo.lyricsLoaded.collectAsState()
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val q = c.st.query
    val results = remember(q, c.lib, c.playlists, lyrics) {
        Search.run(q, c.lib.tracks, c.lib.albums, c.playlists, c.lib.videos, c.lib.movies, c.lib.shows, lyrics.mapValues { it.value.lines })
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            BackButton(c)
            Spacer(Modifier.width(10.dp))
            Row(Modifier.weight(1f).height(44.dp).border(1.dp, C.HairStrong).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    if (q.isEmpty()) Text("Songs, artists, lyrics, movies, episodes…", style = T.ui(14.5.sp), color = C.Faint, maxLines = 1)
                    BasicTextField(
                        q, { c.st.query = it }, singleLine = true, textStyle = T.ui(14.5.sp, color = C.Fg), cursorBrush = SolidColor(C.Amber),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { c.repo.addRecentSearch(q) }),
                        modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    )
                }
                if (q.isNotEmpty()) Icon(Icons.Filled.Close, "Clear", tint = C.Muted, modifier = Modifier.size(18.dp).clickable { c.st.query = "" })
            }
        }
        if (q.isBlank()) {
            Column(Modifier.padding(horizontal = 20.dp)) {
                if (c.settings.recentSearches.isNotEmpty()) {
                    Mono("Recent", color = C.Muted)
                    Spacer(Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        c.settings.recentSearches.forEach { r ->
                            Box(Modifier.border(1.dp, C.HairStrong).clickable { c.st.query = r }.padding(horizontal = 10.dp, vertical = 6.dp)) { Text(r, style = T.ui(13.sp)) }
                        }
                    }
                    Spacer(Modifier.height(18.dp))
                }
                Mono(
                    "Searching ${c.lib.tracks.size} songs, ${lyrics.size} synced lyrics, ${c.lib.movies.size} movies and ${c.lib.shows.sumOf { it.episodeCount }} episodes" +
                        if (c.settings.offline) " · offline: downloads only" else "",
                    style = T.metaMono, color = C.Faint, maxLines = 3,
                )
            }
            return
        }
        LazyColumn(Modifier.fillMaxSize()) {
            item { Mono("${results.total} results for \"$q\"", color = C.Muted, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) }
            if (results.total == 0) item { Mono("Nothing on the phone or ${c.pcName} matches", color = C.Faint, modifier = Modifier.padding(20.dp)) }
            group("Songs", results.songs.isNotEmpty())
            items(results.songs, key = { "s-${it.id}" }) { t ->
                Row(Modifier.fillMaxWidth().clickable { c.repo.addRecentSearch(q); c.hub.playList(results.songs, results.songs.indexOf(t)) }.padding(horizontal = 20.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                    Art(t.albumKey + t.title, c.art(t), Modifier.size(44.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(t.title, style = T.row, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Mono("${t.artist ?: "Unknown artist"} · ${t.quality.label} · ${c.srcLabel(t)}", style = T.metaMono, color = C.Faint)
                    }
                    Icon(Icons.Filled.MoreHoriz, "More", tint = C.Muted, modifier = Modifier.size(32.dp).clickable { c.st.sheet = Sheet.Actions(t.id) }.padding(6.dp))
                }
            }
            group("In lyrics", results.lyrics.isNotEmpty())
            items(results.lyrics, key = { "l-${it.track.id}" }) { h ->
                Column(Modifier.fillMaxWidth().clickable { c.hub.playList(listOf(h.track)); c.st.showLyrics = true }.padding(horizontal = 20.dp, vertical = 8.dp)) {
                    Text("“${h.line}”", style = T.ui(14.5.sp).copy(fontStyle = FontStyle.Italic), maxLines = 2)
                    Mono("${h.track.title} · ${h.track.artist ?: "Unknown artist"}", style = T.metaMono, color = C.Faint)
                }
            }
            group("Artists", results.artists.isNotEmpty())
            items(results.artists, key = { "a-$it" }) { a ->
                Row(Modifier.fillMaxWidth().clickable { c.st.openArtist(a) }.padding(horizontal = 20.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                    Art(a, c.lib.tracks.firstOrNull { it.artist == a }?.let { c.art(it) }, Modifier.size(44.dp), shape = CircleShape)
                    Spacer(Modifier.width(12.dp))
                    Text(a, style = T.row)
                }
            }
            group("Albums", results.albums.isNotEmpty())
            items(results.albums, key = { "al-${it.key}" }) { al ->
                Row(Modifier.fillMaxWidth().clickable { c.st.push(Screen.Album(al.key)) }.padding(horizontal = 20.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                    Art(al.key, al.artTrack?.let { c.art(it) }, Modifier.size(44.dp))
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(al.title, style = T.row)
                        Mono(al.artist ?: "Unknown artist", style = T.metaMono, color = C.Faint)
                    }
                }
            }
            group("Playlists", results.playlists.isNotEmpty())
            items(results.playlists, key = { "p-${it.id}" }) { pl ->
                Text(pl.name, style = T.row, modifier = Modifier.fillMaxWidth().clickable { c.st.push(Screen.Playlist(pl.id)) }.padding(horizontal = 20.dp, vertical = 10.dp))
            }
            group("Music videos", results.videos.isNotEmpty())
            items(results.videos, key = { "v-${it.id}" }) { v ->
                Row(Modifier.fillMaxWidth().clickable { c.st.push(Screen.Video(v.id, VideoKind.MUSIC_VIDEO)) }.padding(horizontal = 20.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                    Art(v.title, if (v.hasArt) c.repo.artUrl(v.id, "thumb") else null, Modifier.width(112.dp).aspectRatio(16f / 9f)) { PlayDisc(24.dp, Modifier.align(Alignment.Center)) }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(v.title, style = T.row, maxLines = 2)
                        Mono(v.artist ?: "", style = T.metaMono, color = C.Faint)
                    }
                }
            }
            group("Movies", results.movies.isNotEmpty())
            items(results.movies, key = { "m-${it.id}" }) { m ->
                PosterResult(c, m.title, m.posterUrl, listOfNotNull(m.year?.toString(), m.director).joinToString(" · ")) { c.st.push(Screen.MoviePage(m.id)) }
            }
            group("TV shows", results.shows.isNotEmpty())
            items(results.shows, key = { "sh-${it.id}" }) { s ->
                PosterResult(c, s.title, s.posterUrl, "${s.seasons.size} seasons") { c.st.season = null; c.st.push(Screen.ShowPage(s.id)) }
            }
            group("Episodes", results.episodes.isNotEmpty())
            items(results.episodes, key = { "e-${it.episode.id}" }) { h ->
                Column(Modifier.fillMaxWidth().clickable { c.st.push(Screen.Video(h.episode.id, VideoKind.EPISODE, h.show.id)) }.padding(horizontal = 20.dp, vertical = 8.dp)) {
                    Text(h.episode.title, style = T.row)
                    Mono("${h.show.title} · S${h.episode.season}E${h.episode.episode} · ${h.episode.durationMs / 60000} min", style = T.metaMono, color = C.Faint)
                }
            }
            item { Spacer(Modifier.height(30.dp)) }
        }
    }
}

private fun LazyListScope.group(title: String, show: Boolean) {
    if (show) item { Mono(title, color = C.Muted, modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 6.dp)) }
}

@Composable
private fun PosterResult(c: Ctx, title: String, poster: String?, sub: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        Art(title, poster?.let { c.repo.remoteUrl(it) }, Modifier.width(44.dp).aspectRatio(2f / 3f))
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, style = T.row)
            Mono(sub, style = T.metaMono, color = C.Faint)
        }
    }
}
