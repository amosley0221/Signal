package com.amosley.signal.ui.screens

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.net.Uri
import android.util.Rational
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.C as MC
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.amosley.signal.core.Chapter
import com.amosley.signal.core.Episode
import com.amosley.signal.core.Fmt
import com.amosley.signal.core.Movie
import com.amosley.signal.core.Origin
import com.amosley.signal.core.Show
import com.amosley.signal.core.Subtitle
import com.amosley.signal.core.resolutionLabel
import com.amosley.signal.data.DlState
import com.amosley.signal.playback.RemoteItem
import com.amosley.signal.playback.RemoteState
import kotlinx.coroutines.flow.MutableStateFlow
import androidx.compose.runtime.collectAsState
import com.amosley.signal.ui.Ctx
import com.amosley.signal.ui.Screen
import com.amosley.signal.ui.Sheet
import com.amosley.signal.ui.VideoKind
import com.amosley.signal.ui.components.Art
import com.amosley.signal.ui.components.Chip
import com.amosley.signal.ui.components.FilledBtn
import com.amosley.signal.ui.components.Mono
import com.amosley.signal.ui.components.SquareBtn
import com.amosley.signal.ui.theme.C
import com.amosley.signal.ui.theme.T
import kotlinx.coroutines.delay

fun Context.findActivity(): Activity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

// ---- Movie detail -------------------------------------------------------------------------------

@Composable
private fun MetaRow(c: Ctx, certificate: String?, rating: Double?, matchedBy: String?, phoneFix: (() -> Unit)? = null) {
    Row(Modifier.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        certificate?.let { Chip(it, bg = Color.Transparent, fg = C.Fg, border = C.HairStrong) }
        rating?.let { Chip("★ ${"%.1f".format(it)}", bg = C.AmberTint, fg = C.AmberText) }
        val source = when (matchedBy) {
            "ON THIS PHONE" -> null
            "ITUNES" -> "Details from Apple TV"
            "TVMAZE" -> "Details from TVmaze"
            null -> null
            else -> "Matched by $matchedBy"
        }
        source?.let { Mono(it, style = T.metaMono, color = C.Faint) }
        when {
            phoneFix != null -> Mono(if (matchedBy == "ON THIS PHONE") "Find details" else "Wrong title? Fix match", style = T.metaMono, color = C.AmberText,
                modifier = Modifier.clickable(onClick = phoneFix))
            matchedBy != null && matchedBy.startsWith("PLEX") -> Mono("Wrong title? Fix match", style = T.metaMono, color = C.AmberText, modifier = Modifier.clickable {
                c.toast("Use Fix Match on this title in Plex, then Sync now in Signal")
            })
        }
    }
}

@Composable
private fun Backdrop(c: Ctx, key: String, url: String?, height: Int) {
    Box(Modifier.fillMaxWidth().height(height.dp)) {
        Art(key, url?.let { c.repo.remoteUrl(it) }, Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.2f), Color.Transparent, C.Bg))))
        BackButton(c, Modifier.padding(16.dp))
    }
}

@Composable
fun MovieScreen(c: Ctx, id: String) {
    val m = c.lib.movies.firstOrNull { it.id == id } ?: return Missing(c)
    LazyColumn(Modifier.fillMaxSize()) {
        item { Backdrop(c, m.title + "bd", m.backdropUrl, 300) }
        item {
            Column(Modifier.padding(horizontal = 20.dp)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Art(m.title, m.posterUrl?.let { c.repo.remoteUrl(it) }, Modifier.width(110.dp).aspectRatio(2f / 3f))
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(m.title, style = T.videoTitle, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(4.dp))
                        Text(listOfNotNull(m.year?.toString(), if (m.durationMs > 0) Fmt.runtime(m.durationMs) else null, m.genres.firstOrNull()).joinToString(" · "), style = T.meta, color = C.Muted)
                        Text(
                            listOfNotNull(m.director?.let { "Directed by $it" }, if (m.origin == Origin.PHONE) "On this phone" else if (c.dlState(m.id) is DlState.Done) "Downloaded" else "Stream from ${c.pcName}").joinToString(" · "),
                            style = T.ui(12.5.sp), color = C.Faint,
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val label = when {
                        m.watched -> "Watch again"
                        m.viewOffsetMs > 60_000 -> "Resume · ${Fmt.dur(m.durationMs - m.viewOffsetMs)} left"
                        else -> "Play"
                    }
                    FilledBtn(label, icon = Icons.Filled.PlayArrow, modifier = Modifier.weight(1f)) { c.st.push(Screen.Video(m.id, VideoKind.MOVIE)) }
                    if (m.origin == Origin.PC) {
                        Spacer(Modifier.width(10.dp))
                        Box(Modifier.size(40.dp).border(1.dp, C.HairStrong), contentAlignment = Alignment.Center) {
                            DlButton(c, m.id, m.origin, 40) { c.repo.download(m); c.toast("Downloading ${m.title}") }
                        }
                    }
                }
                if (m.viewOffsetMs > 0 && m.durationMs > 0 && !m.watched) {
                    Spacer(Modifier.height(8.dp))
                    Box(Modifier.fillMaxWidth().height(2.dp).background(C.Hair)) {
                        Box(Modifier.fillMaxWidth(m.viewOffsetMs.toFloat() / m.durationMs).height(2.dp).background(C.Amber))
                    }
                }
                Spacer(Modifier.height(16.dp))
                MetaRow(c, m.certificate, m.rating, m.matchedBy,
                    phoneFix = if (m.id.startsWith("locv:")) ({ c.st.sheet = Sheet.FixMatch(movieId = m.id, showKey = null) }) else null)
                m.synopsis?.let {
                    Spacer(Modifier.height(14.dp))
                    Text(it, style = T.ui(14.5.sp, lineHeight = 22.sp), color = C.Fg.copy(alpha = 0.85f))
                }
                if (m.cast.isNotEmpty()) {
                    Spacer(Modifier.height(14.dp))
                    Mono("Cast", color = C.Muted)
                    Text(m.cast.joinToString(", "), style = T.ui(13.5.sp), color = C.Muted, modifier = Modifier.padding(top = 4.dp))
                }
                Spacer(Modifier.height(30.dp))
            }
        }
    }
}

// ---- Show page ----------------------------------------------------------------------------------

@Composable
fun ShowScreen(c: Ctx, id: String) {
    val show = c.lib.shows.firstOrNull { it.id == id } ?: return Missing(c)
    val eps = show.allEpisodes
    // Same rules as the Continue watching / Up next rows.
    val next = com.amosley.signal.core.Watching.continueWatching(emptyList(), listOf(show)).firstOrNull()?.episode
        ?: com.amosley.signal.core.Watching.upNext(listOf(show)).firstOrNull()?.episode
        ?: eps.firstOrNull { !it.watched } ?: eps.firstOrNull()
    val seasonNo = c.st.season ?: next?.season ?: show.seasons.firstOrNull()?.number ?: 1
    val season = show.seasons.firstOrNull { it.number == seasonNo }
    LazyColumn(Modifier.fillMaxSize()) {
        item { Backdrop(c, show.title + "bd", show.backdropUrl, 280) }
        item {
            Column(Modifier.padding(horizontal = 20.dp)) {
                Text(show.title, style = T.videoTitle)
                Spacer(Modifier.height(4.dp))
                Text(
                    listOfNotNull("${show.seasons.size} season${if (show.seasons.size != 1) "s" else ""}", "${show.episodeCount} episodes", show.genres.firstOrNull(), c.pcName).joinToString(" · "),
                    style = T.meta, color = C.Muted,
                )
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (next != null) {
                        FilledBtn("${if (next.viewOffsetMs > 0) "Continue" else "Play"} · E${next.episode} ${next.title}", icon = Icons.Filled.PlayArrow, modifier = Modifier.weight(1f)) {
                            c.st.push(Screen.Video(next.id, VideoKind.EPISODE, show.id))
                        }
                    }
                    if (!show.id.startsWith("locs:")) {
                    Spacer(Modifier.width(10.dp))
                    SquareBtn(Icons.Filled.Download, size = 40.dp, desc = "Download season") {
                        season?.episodes?.forEach { c.repo.download(it, show) }
                        c.toast("Downloading season $seasonNo")
                    }
                    }
                }
                Spacer(Modifier.height(16.dp))
                MetaRow(c, show.certificate, show.rating, show.matchedBy,
                    phoneFix = if (show.id.startsWith("locs:")) ({ c.st.sheet = Sheet.FixMatch(movieId = null, showKey = show.id) }) else null)
                show.synopsis?.let {
                    Spacer(Modifier.height(12.dp))
                    Text(it, style = T.ui(14.5.sp, lineHeight = 22.sp), color = C.Fg.copy(alpha = 0.85f))
                }
                if (show.cast.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Mono("Cast", color = C.Muted)
                    Text(show.cast.joinToString(", "), style = T.ui(13.5.sp), color = C.Muted, modifier = Modifier.padding(top = 4.dp))
                }
                Spacer(Modifier.height(16.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    show.seasons.forEach { s ->
                        val on = s.number == seasonNo
                        Box(
                            Modifier.height(32.dp).background(if (on) C.Fg else Color.Transparent).border(1.dp, if (on) C.Fg else C.HairStrong)
                                .clickable { c.st.season = s.number }.padding(horizontal = 12.dp),
                            contentAlignment = Alignment.Center,
                        ) { Text(if (on) "Season ${s.number}" else "Season ${s.number} · ${s.episodes.size} ep", style = T.ui(13.sp, 600), color = if (on) C.Bg else C.Fg) }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
        items(season?.episodes.orEmpty(), key = { "ep-${it.id}" }) { ep -> EpisodeRow(c, show, ep) }
        item { Spacer(Modifier.height(30.dp)) }
    }
}

@Composable
private fun EpisodeRow(c: Ctx, show: Show, ep: Episode) {
    Row(Modifier.fillMaxWidth().clickable { c.st.push(Screen.Video(ep.id, VideoKind.EPISODE, show.id)) }.padding(horizontal = 20.dp, vertical = 10.dp)) {
        Art(ep.title, ep.thumbUrl?.let { c.repo.remoteUrl(it) }, Modifier.size(128.dp, 72.dp)) {
            if (ep.viewOffsetMs > 0 && ep.durationMs > 0 && !ep.watched) {
                Box(Modifier.align(Alignment.BottomStart).fillMaxWidth(ep.viewOffsetMs.toFloat() / ep.durationMs).height(3.dp).background(C.Amber))
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Mono("E${ep.episode} · ${Fmt.runtime(ep.durationMs)}", style = T.metaMono, color = C.Faint, modifier = Modifier.weight(1f))
                if (ep.watched) Icon(Icons.Filled.Check, "Watched", tint = C.Green, modifier = Modifier.size(14.dp))
                DlButton(c, ep.id, if (ep.uri.isNotEmpty()) Origin.PHONE else Origin.PC, 28) { c.repo.download(ep, show) }
            }
            Text(ep.title, style = T.ui(14.5.sp, 600), maxLines = 1, overflow = TextOverflow.Ellipsis)
            ep.summary?.let { Text(it, style = T.ui(12.5.sp), color = C.Muted, maxLines = 2, overflow = TextOverflow.Ellipsis) }
        }
    }
}

// ---- Video player -------------------------------------------------------------------------------

private data class VideoSource(
    val id: String,
    val title: String,
    val subtitle: String,
    val uri: Uri?,
    val remoteUrl: String?,
    val startMs: Long,
    val durationMs: Long,
    val subtitles: List<Subtitle>,
    val chapters: List<Chapter>,
    val width: Int,
    val height: Int,
    val origin: Origin,
    val next: Screen.Video?,
)

private fun resolve(c: Ctx, s: Screen.Video): VideoSource? {
    fun uri(id: String, origin: Origin, phoneUri: String) = c.repo.playUri(id, origin, phoneUri)
    return when (s.kind) {
        VideoKind.MOVIE -> c.lib.movies.firstOrNull { it.id == s.id }?.let { m: Movie ->
            VideoSource(m.id, m.title, listOfNotNull(m.year?.toString(), m.certificate).joinToString(" · "), uri(m.id, m.origin, m.uri),
                if (m.origin == Origin.PC) c.repo.streamUrl(m.id) else null, if (m.watched) 0 else m.viewOffsetMs, m.durationMs, m.subtitles, m.chapters, m.width, m.height, m.origin, null)
        }
        VideoKind.EPISODE -> {
            val show = c.lib.shows.firstOrNull { it.id == s.showId } ?: c.lib.shows.firstOrNull { sh -> sh.allEpisodes.any { it.id == s.id } }
            val eps = show?.allEpisodes.orEmpty()
            val i = eps.indexOfFirst { it.id == s.id }
            val ep = eps.getOrNull(i) ?: return null
            val nxt = eps.getOrNull(i + 1)?.let { Screen.Video(it.id, VideoKind.EPISODE, show!!.id) }
            val epOrigin = if (ep.uri.isNotEmpty()) Origin.PHONE else Origin.PC
            VideoSource(ep.id, "${show!!.title} · S${ep.season}E${ep.episode} ${ep.title}", Fmt.runtime(ep.durationMs), uri(ep.id, epOrigin, ep.uri),
                if (epOrigin == Origin.PC) c.repo.streamUrl(ep.id) else null, if (ep.watched) 0 else ep.viewOffsetMs, ep.durationMs, ep.subtitles, ep.chapters, ep.width, ep.height, epOrigin, nxt)
        }
        VideoKind.MUSIC_VIDEO -> c.lib.videos.firstOrNull { it.id == s.id }?.let { v ->
            VideoSource(v.id, v.title, listOfNotNull(v.artist, v.album).joinToString(" · "), uri(v.id, v.origin, v.uri),
                if (v.origin == Origin.PC) c.repo.streamUrl(v.id) else null, 0, v.durationMs,
                emptyList(), emptyList(), v.width, v.height, v.origin, null)
        }
    }
}

private val SPEEDS = listOf(1f, 1.25f, 1.5f, 2f, 0.75f)

@Composable
fun VideoScreen(c: Ctx, screen: Screen.Video) {
    val src = remember(screen) { resolve(c, screen) }
    if (src == null || src.uri == null) {
        Column(Modifier.fillMaxSize().background(Color.Black).windowInsetsPadding(WindowInsets.statusBars).padding(20.dp)) {
            BackButton(c)
            Spacer(Modifier.height(20.dp))
            Mono(if (c.settings.offline) "Not downloaded — you're offline" else "Can't reach ${c.pcName}", color = C.AmberText)
        }
        return
    }
    val context = LocalContext.current
    val activity = context.findActivity()
    val app = c.app
    val player = remember(screen) {
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(DefaultDataSource.Factory(context, OkHttpDataSource.Factory(app.repo.http))))
            .setSeekBackIncrementMs(10_000).setSeekForwardIncrementMs(10_000)
            .build()
    }
    var full by remember { mutableStateOf(false) }
    var ui by remember { mutableStateOf(true) }
    var subs by remember { mutableStateOf(true) }
    var speedIdx by remember { mutableStateOf(0) }
    var pos by remember { mutableLongStateOf(src.startMs) }
    var dur by remember { mutableLongStateOf(src.durationMs) }
    var playing by remember { mutableStateOf(false) }
    var hint by remember { mutableStateOf<String?>(null) }
    val casting = app.videoCast

    DisposableEffect(screen) {
        app.hub.pause()
        val item = MediaItem.Builder().setUri(src.uri).setMediaId(src.id)
            .setSubtitleConfigurations(src.subtitles.mapNotNull { s ->
                val url = c.repo.remoteUrl(s.url) ?: return@mapNotNull null
                MediaItem.SubtitleConfiguration.Builder(Uri.parse(url))
                    .setMimeType(if (s.format == "vtt" || s.url.endsWith(".vtt")) MimeTypes.TEXT_VTT else MimeTypes.APPLICATION_SUBRIP)
                    .setLanguage(s.language).setLabel(s.label)
                    .setSelectionFlags(if (s == src.subtitles.first()) MC.SELECTION_FLAG_DEFAULT else 0)
                    .build()
            })
            .build()
        player.setMediaItem(item, src.startMs)
        player.prepare()
        player.play()
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) {
                    app.repo.reportProgress(src.id, dur, dur)
                    src.next?.let { c.st.stack[c.st.stack.lastIndex] = it }
                }
            }
        }
        player.addListener(listener)
        val castUrl = src.remoteUrl ?: app.hub.localServer.urlFor(src.id, src.uri, "video/mp4")
        app.currentVideoItem = castUrl?.let { RemoteItem(it, src.title, null, null, null, "video/mp4", src.durationMs, isVideo = true) }
        app.videoPosition = { player.currentPosition }
        onDispose {
            app.repo.reportProgress(src.id, player.currentPosition, player.duration.coerceAtLeast(dur))
            player.removeListener(listener)
            player.release()
            app.currentVideoItem = null
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            activity?.window?.let { w -> WindowCompat.getInsetsController(w, w.decorView).show(WindowInsetsCompat.Type.systemBars()) }
        }
    }
    LaunchedEffect(casting) { if (casting != null) player.pause() }
    LaunchedEffect(subs) {
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(MC.TRACK_TYPE_TEXT, !subs).build()
    }
    LaunchedEffect(speedIdx) { player.playbackParameters = PlaybackParameters(SPEEDS[speedIdx]) }
    LaunchedEffect(screen) {
        var tick = 0
        while (true) {
            delay(500)
            if (app.videoCast == null) {
                pos = player.currentPosition
                if (player.duration > 0) dur = player.duration
            }
            if (++tick % 30 == 0 && player.isPlaying) app.repo.reportProgress(src.id, pos, dur)
        }
    }
    LaunchedEffect(ui, playing) {
        if (ui && playing) {
            delay(3500)
            ui = false
        }
    }
    LaunchedEffect(hint) { if (hint != null) { delay(900); hint = null } }
    LaunchedEffect(full) {
        val a = activity ?: return@LaunchedEffect
        val ctl = WindowCompat.getInsetsController(a.window, a.window.decorView)
        if (full) {
            ctl.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            ctl.hide(WindowInsetsCompat.Type.systemBars())
            if (!c.unfolded) a.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            ctl.show(WindowInsetsCompat.Type.systemBars())
            a.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }
    val idleCast = remember { MutableStateFlow(RemoteState()) }
    val castState by (casting?.state ?: idleCast).collectAsState()
    val shownPos = if (casting != null) castState.positionMs else pos
    val shownPlaying = if (casting != null) castState.playing else playing
    fun seekBy(d: Long) {
        val to = (shownPos + d).coerceIn(0, dur.coerceAtLeast(0))
        if (casting != null) casting.seek(to) else player.seekTo(to)
    }
    fun toggle() {
        if (casting != null) { if (castState.playing) casting.pause() else casting.play() } else if (player.isPlaying) player.pause() else player.play()
    }
    val chapter = src.chapters.lastOrNull { it.startMs <= shownPos }

    Column(Modifier.fillMaxSize().background(Color.Black).then(if (full || c.st.pip) Modifier else Modifier.windowInsetsPadding(WindowInsets.statusBars).windowInsetsPadding(WindowInsets.navigationBars))) {
        BoxWithConstraints(
            (if (full || c.st.pip) Modifier.fillMaxSize() else Modifier.fillMaxWidth().aspectRatio(16f / 9f)).background(Color.Black)
                .pointerInput(screen) {
                    detectTapGestures(
                        onTap = { ui = !ui },
                        onDoubleTap = { o -> if (o.x < size.width / 2) { seekBy(-10_000); hint = "-10 s" } else { seekBy(10_000); hint = "+10 s" } },
                    )
                }
                .pointerInput(screen) {
                    var startX = 0f
                    var mode = 0 // 1 = seek, 2 = brightness, 3 = volume
                    var acc = Offset.Zero
                    val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                    detectDragGestures(
                        onDragStart = { o -> startX = o.x; mode = 0; acc = Offset.Zero },
                        onDragEnd = { if (mode == 1) seekBy((acc.x / size.width * 90_000).toLong()); mode = 0 },
                    ) { change, amount ->
                        change.consume()
                        acc += amount
                        if (mode == 0 && acc.getDistance() > 24f) mode = if (kotlin.math.abs(acc.x) > kotlin.math.abs(acc.y)) 1 else if (startX < size.width / 2) 2 else 3
                        when (mode) {
                            1 -> hint = "${if (acc.x >= 0) "+" else "-"}${Fmt.dur(kotlin.math.abs((acc.x / size.width * 90_000).toLong()))}"
                            2 -> activity?.window?.let { w ->
                                val lp = w.attributes
                                val cur = if (lp.screenBrightness < 0) 0.5f else lp.screenBrightness
                                lp.screenBrightness = (cur - amount.y / size.height).coerceIn(0.02f, 1f)
                                w.attributes = lp
                                hint = "Brightness ${(lp.screenBrightness * 100).toInt()}%"
                            }
                            3 -> {
                                val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                                if (kotlin.math.abs(acc.y) > size.height / max / 1.5f) {
                                    audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, if (acc.y < 0) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER, 0)
                                    acc = Offset(acc.x, 0f)
                                }
                                hint = "Volume ${audio.getStreamVolume(AudioManager.STREAM_MUSIC) * 100 / max}%"
                            }
                        }
                    }
                },
        ) {
            if (casting == null) {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            useController = false
                            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                            setShutterBackgroundColor(android.graphics.Color.BLACK)
                            this.player = player
                        }
                    },
                    update = { it.player = player },
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.Cast, null, tint = C.Amber, modifier = Modifier.size(40.dp))
                    Spacer(Modifier.height(8.dp))
                    Mono("Playing on ${casting.name}", color = C.Fg)
                }
            }
            hint?.let {
                Box(Modifier.align(Alignment.Center).background(Color.Black.copy(alpha = 0.6f)).padding(horizontal = 12.dp, vertical = 6.dp)) {
                    Text(it, style = T.mono(13.sp, 600), color = C.Fg)
                }
            }
            if (ui && !c.st.pip) VideoOverlay(
                c, src, shownPos, dur, shownPlaying, full, subs, SPEEDS[speedIdx], chapter,
                onBack = { if (full) full = false else c.st.back() },
                onCast = { c.st.sheet = Sheet.Cast(video = true) },
                onSubs = { subs = !subs },
                onSpeed = { speedIdx = (speedIdx + 1) % SPEEDS.size },
                onToggle = { toggle() },
                onSeekBy = { seekBy(it) },
                onSeek = { to -> if (casting != null) casting.seek(to) else player.seekTo(to) },
                onPip = {
                    activity?.enterPictureInPictureMode(PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build())
                },
                onFull = { full = !full },
                onNext = src.next?.let { n -> { c.st.stack[c.st.stack.lastIndex] = n } },
            )
        }
        if (!full && !c.st.pip) {
            LazyColumn(Modifier.fillMaxSize().background(C.Bg)) {
                item {
                    Column(Modifier.padding(20.dp)) {
                        Text(src.title, style = T.ui(17.sp, 600))
                        Spacer(Modifier.height(4.dp))
                        Mono(
                            listOfNotNull(resolutionLabel(src.width, src.height), if (src.subtitles.isNotEmpty()) "Subtitles" else null, "Direct play",
                                if (src.origin == Origin.PHONE) "On this phone" else if (c.dlState(src.id) is DlState.Done) "Downloaded" else c.pcName).joinToString(" · "),
                            style = T.metaMono, color = C.Faint,
                        )
                        Spacer(Modifier.height(6.dp))
                        Mono("Double-tap sides ±10 s · swipe left edge for brightness, right for volume", style = T.metaMono, color = C.Faint, maxLines = 2)
                    }
                }
                if (src.chapters.isNotEmpty()) {
                    item { Mono("Chapters", color = C.Muted, modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) }
                    items(src.chapters, key = { "ch-${it.startMs}" }) { ch ->
                        val on = ch == chapter
                        Row(
                            Modifier.fillMaxWidth().background(if (on) C.Card else Color.Transparent)
                                .clickable { if (casting != null) casting.seek(ch.startMs) else player.seekTo(ch.startMs) }.padding(horizontal = 20.dp, vertical = 11.dp),
                        ) {
                            Text(Fmt.dur(ch.startMs), style = T.mono(12.sp, 500, 0.sp), color = if (on) C.AmberText else C.Faint, modifier = Modifier.width(70.dp))
                            Text(ch.title, style = T.ui(14.sp, 500))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OverlayIcon(icon: ImageVector, desc: String, size: Int = 40, onClick: () -> Unit) {
    Box(Modifier.size(size.dp).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, desc, tint = Color.White, modifier = Modifier.size((size * 0.6).dp))
    }
}

@Composable
private fun VideoOverlay(
    c: Ctx, src: VideoSource, pos: Long, dur: Long, playing: Boolean, full: Boolean, subs: Boolean, speed: Float, chapter: Chapter?,
    onBack: () -> Unit, onCast: () -> Unit, onSubs: () -> Unit, onSpeed: () -> Unit, onToggle: () -> Unit, onSeekBy: (Long) -> Unit,
    onSeek: (Long) -> Unit, onPip: () -> Unit, onFull: () -> Unit, onNext: (() -> Unit)?,
) {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f))) {
        Row(Modifier.fillMaxWidth().padding(8.dp).align(Alignment.TopStart), verticalAlignment = Alignment.CenterVertically) {
            OverlayIcon(Icons.AutoMirrored.Filled.ArrowBack, "Back", onClick = onBack)
            Column(Modifier.weight(1f).padding(horizontal = 6.dp)) {
                Text(src.title, style = T.ui(14.sp, 600), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Mono(listOfNotNull(resolutionLabel(src.width, src.height), if (src.subtitles.isNotEmpty()) "Subtitles" else null, "Direct play", c.pcName).joinToString(" · "), style = T.metaMono, color = Color.White.copy(alpha = 0.7f))
            }
            OverlayIcon(Icons.Filled.Cast, "Cast", onClick = onCast)
            if (src.subtitles.isNotEmpty()) Box(Modifier.clickable(onClick = onSubs).padding(8.dp)) { Mono(if (subs) "CC on" else "CC off", color = Color.White) }
            Box(Modifier.clickable(onClick = onSpeed).padding(8.dp)) { Mono("${if (speed % 1f == 0f) speed.toInt() else speed}×", color = Color.White) }
        }
        Row(Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            OverlayIcon(Icons.Filled.Replay10, "Back 10 seconds", 48) { onSeekBy(-10_000) }
            Box(Modifier.size(72.dp).background(Color.White, CircleShape).clickable(onClick = onToggle), contentAlignment = Alignment.Center) {
                Icon(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, "Play/pause", tint = Color.Black, modifier = Modifier.size(40.dp))
            }
            OverlayIcon(Icons.Filled.Forward10, "Forward 10 seconds", 48) { onSeekBy(10_000) }
        }
        Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp)) {
            Row {
                Mono(Fmt.dur(pos), color = Color.White, style = T.metaMono)
                chapter?.let { Mono(" · ${it.title}", color = Color.White.copy(alpha = 0.7f), style = T.metaMono, modifier = Modifier.weight(1f)) } ?: Spacer(Modifier.weight(1f))
                Mono(Fmt.dur(dur), color = Color.White, style = T.metaMono)
            }
            Box(
                Modifier.fillMaxWidth().height(22.dp)
                    .pointerInput(dur) { detectTapGestures { o -> if (dur > 0) onSeek((o.x / size.width * dur).toLong()) } }
                    .drawBehind {
                        val y = size.height / 2
                        drawRect(Color.White.copy(alpha = 0.3f), Offset(0f, y - 1.5f), Size(size.width, 3f))
                        val f = if (dur > 0) pos.toFloat() / dur else 0f
                        drawRect(C.Amber, Offset(0f, y - 1.5f), Size(size.width * f.coerceIn(0f, 1f), 3f))
                        src.chapters.forEach { ch -> if (dur > 0) drawRect(Color.White, Offset(size.width * ch.startMs / dur, y - 4f), Size(2f, 8f)) }
                        drawCircle(C.Amber, 6.dp.toPx(), Offset(size.width * f.coerceIn(0f, 1f), y))
                    },
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                OverlayIcon(Icons.Filled.PictureInPictureAlt, "Picture in picture", onClick = onPip)
                if (onNext != null) OverlayIcon(Icons.Filled.SkipNext, "Next episode", onClick = onNext)
                Spacer(Modifier.weight(1f))
                OverlayIcon(if (full) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen, if (full) "Exit full screen" else "Full screen", onClick = onFull)
            }
        }
    }
}
