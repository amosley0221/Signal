package com.amosley.signal.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amosley.signal.core.VideoNames
import com.amosley.signal.data.MovieInfo
import com.amosley.signal.data.ShowInfo
import com.amosley.signal.ui.Ctx
import com.amosley.signal.ui.components.Art
import com.amosley.signal.ui.components.Hairline
import com.amosley.signal.ui.components.Mono
import com.amosley.signal.ui.components.OutlineBtn
import com.amosley.signal.ui.components.Spinner
import com.amosley.signal.ui.theme.C
import com.amosley.signal.ui.theme.T
import kotlinx.coroutines.launch

/** Search Apple's movie catalog / TVmaze for a video that's only on the phone, and pick the right title. */
@Composable
fun ColumnScope.FixMatchSheet(c: Ctx, movieId: String?, showId: String?) {
    val scope = rememberCoroutineScope()
    val show = showId?.let { id -> c.lib.shows.firstOrNull { it.id == id } }
    // Search with the file's own name, not the (possibly wrong) online title.
    val original = movieId?.let { c.repo.phoneVideoName(it) }
    val showKey = showId?.removePrefix("locs:")
    var query by remember { mutableStateOf(original?.let { (t, y) -> if (y != null) "$t $y" else t } ?: show?.title.orEmpty()) }
    var movies by remember { mutableStateOf<List<MovieInfo>>(emptyList()) }
    var shows by remember { mutableStateOf<List<ShowInfo>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }

    fun search() {
        busy = true
        scope.launch {
            if (movieId != null) movies = runCatching { c.repo.searchMovies(query) }.getOrDefault(emptyList())
            else shows = runCatching { c.repo.searchShows(query) }.getOrDefault(emptyList())
            busy = false
        }
    }
    LaunchedEffect(Unit) { if (query.isNotBlank()) search() }

    Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f)) {
            Text(if (movieId != null) "Find the movie" else "Find the show", style = T.sheetTitle)
            Mono(if (movieId != null) "Movie details from Apple TV" else "Show details from TVmaze", style = T.metaMono, color = C.Faint)
        }
        Mono("Cancel", color = C.Fg, style = T.mono(11.sp, 600), modifier = Modifier.clickable { c.st.sheet = null }.padding(4.dp))
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Field(query, { query = it }, "Title", Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        OutlineBtn("Search") { search() }
    }
    Spacer(Modifier.height(8.dp))
    Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
        if (busy) Spinner(Modifier.padding(20.dp).size(20.dp))
        if (!busy && movies.isEmpty() && shows.isEmpty()) Mono("No results · try a shorter title", color = C.Faint, modifier = Modifier.padding(vertical = 16.dp))
        movies.forEach { m ->
            ResultRow(m.title, listOfNotNull(m.year?.toString(), m.genres.firstOrNull(), m.director).joinToString(" · "), m.posterUrl) {
                c.repo.setMovieInfo(movieId!!, m)
                c.toast("Using ${m.title}")
                c.st.sheet = null
            }
        }
        shows.forEach { s ->
            ResultRow(s.title, listOfNotNull(s.year?.toString(), s.genres.firstOrNull()).joinToString(" · "), s.posterUrl) {
                scope.launch {
                    val full = c.repo.showDetails(s)
                    // The phone show is stored under its own (file-name) title.
                    val key = VideoNames.norm(show?.let { c.repo.phoneShowName(it.id) } ?: showKey)
                    c.repo.setShowInfo(key, full)
                    c.toast("Using ${full.title}")
                    c.st.sheet = null
                }
            }
        }
    }
    Spacer(Modifier.height(10.dp))
    Mono("Use the file name instead", color = C.Muted, modifier = Modifier.clickable {
        if (movieId != null) c.repo.setMovieInfo(movieId, null)
        else show?.let { c.repo.setShowInfo(VideoNames.norm(c.repo.phoneShowName(it.id) ?: it.title), null) }
        c.st.sheet = null
    }.padding(6.dp))
}

@Composable
private fun ResultRow(title: String, sub: String, poster: String?, onPick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onPick).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Art(title, poster, Modifier.width(48.dp).aspectRatio(2f / 3f))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = T.row, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Mono(sub, style = T.metaMono, color = C.Faint)
        }
    }
    Hairline()
}
