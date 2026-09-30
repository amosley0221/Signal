package com.amosley.signal.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amosley.signal.SignalApp
import com.amosley.signal.core.Origin
import com.amosley.signal.core.Track
import com.amosley.signal.core.UserPlaylist
import com.amosley.signal.data.DlState
import com.amosley.signal.data.LibraryView
import com.amosley.signal.data.PcStatus
import com.amosley.signal.data.Settings
import com.amosley.signal.playback.PlayerUi
import com.amosley.signal.ui.components.Mono
import com.amosley.signal.ui.screens.ActivityScreen
import com.amosley.signal.ui.screens.AlbumScreen
import com.amosley.signal.ui.screens.ArtistScreen
import com.amosley.signal.ui.screens.LibraryPane
import com.amosley.signal.ui.screens.MiniPlayer
import com.amosley.signal.ui.screens.MovieScreen
import com.amosley.signal.ui.screens.NowPlayingContent
import com.amosley.signal.ui.screens.PairScreen
import com.amosley.signal.ui.screens.PhoneFoldersScreen
import com.amosley.signal.ui.screens.SettingsScreen
import com.amosley.signal.ui.screens.PlaylistScreen
import com.amosley.signal.ui.screens.SearchScreen
import com.amosley.signal.ui.screens.SheetHost
import com.amosley.signal.ui.screens.ShowScreen
import com.amosley.signal.ui.screens.SyncScreen
import com.amosley.signal.ui.screens.VideoScreen
import com.amosley.signal.ui.theme.C
import com.amosley.signal.ui.theme.T
import kotlinx.coroutines.delay

/** Everything a screen needs, snapshotted once per recomposition. */
class Ctx(
    val app: SignalApp,
    val st: AppState,
    val lib: LibraryView,
    val player: PlayerUi,
    val settings: Settings,
    val status: PcStatus,
    val dl: Map<String, DlState>,
    val unfolded: Boolean,
    val playlists: List<UserPlaylist>,
    /** Bumps when custom art changes; read so art recomposes. */
    val artVersion: Int = 0,
    val favorites: Set<String> = emptySet(),
) {
    val repo get() = app.repo
    val hub get() = app.hub
    val pcName: String get() = settings.pc?.name ?: "PC"

    /** Song art: custom album art first, then the file's own cover. */
    fun art(t: Track): Any? = repo.albumArtFile(t.albumKey) ?: fileArt(t)

    fun albumArt(a: com.amosley.signal.core.Album): Any? = repo.albumArtFile(a.key) ?: a.artTrack?.let { fileArt(it) }

    /** Artist picture: one you set or generated, else the art of one of their songs. */
    fun artistArt(name: String): Any? = repo.artistArtFile(name) ?: lib.artistTracks(name).firstOrNull()?.let { art(it) }

    private fun fileArt(t: Track): Any? = when (t.origin) {
        Origin.PC -> if (t.hasArt) repo.artUrl(t.id, version = t.mtime) else null
        Origin.PHONE -> t.uri.takeIf { it.startsWith("content://") }?.let { "$it/albumart" }
    }

    fun dlState(id: String): DlState = dl[id] ?: DlState.None
    fun unavailable(t: Track): Boolean = t.origin == Origin.PC && dlState(t.id) !is DlState.Done && (settings.offline || !status.reachable)
    fun track(id: String): Track? = lib.tracks.firstOrNull { it.id == id }
    fun toast(msg: String) = app.toast(msg)
    fun isFavorite(id: String) = id in favorites
    fun toggleFavorite(t: Track) {
        val now = repo.toggleFavorite(t.id)
        toast(if (now) "Added to Favorites" else "Removed from Favorites")
    }

    fun srcLabel(t: Track): String = when {
        player.current?.id == t.id && player.output != null -> "Playing on ${player.output}"
        t.origin == Origin.PHONE -> "On device"
        else -> when (val d = dlState(t.id)) {
            is DlState.Done -> "Downloaded"
            is DlState.Running -> "Downloading ${(d.progress * 100).toInt()}%"
            is DlState.Queued -> "Queued"
            else -> "Stream from $pcName"
        }
    }
}

@Composable
fun SignalRoot(st: AppState) {
    val app = LocalContext.current.applicationContext as SignalApp
    val lib by app.repo.library.collectAsState()
    val player by app.hub.ui.collectAsState()
    val settings by app.repo.settings.collectAsState()
    val status by app.repo.status.collectAsState()
    val dl by app.repo.downloads.states.collectAsState()
    val playlists by app.repo.playlists.collectAsState()
    val artVersion by app.repo.artVersion.collectAsState()
    val favorites by app.repo.favorites.collectAsState()

    LaunchedEffect(Unit) {
        app.toasts.collect { msg ->
            st.toast = msg
        }
    }
    LaunchedEffect(st.toast) {
        if (st.toast != null) {
            delay(2600)
            st.toast = null
        }
    }
    LaunchedEffect(settings.pc == null, settings.skippedPairing) {
        if (settings.pc == null && !settings.skippedPairing && st.screen != Screen.Pair) st.push(Screen.Pair)
    }

    BackHandler(enabled = st.sheet != null || st.stack.size > 1 || st.section != Section.MUSIC) { st.back() }

    BoxWithConstraints(Modifier.fillMaxSize().background(C.Bg)) {
        val unfolded = maxWidth >= 600.dp
        val c = Ctx(app, st, lib, player, settings, status, dl, unfolded, playlists, artVersion, favorites)
        val screen = st.screen
        when {
            screen is Screen.Video -> VideoScreen(c, screen)
            screen is Screen.Pair -> Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars).windowInsetsPadding(WindowInsets.navigationBars)) { PairScreen(c) }
            unfolded -> Unfolded(c)
            else -> Folded(c)
        }
        if (screen !is Screen.Video) SheetHost(c)
        AnimatedVisibility(
            visible = st.toast != null, enter = fadeIn(), exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 56.dp),
        ) {
            Box(Modifier.background(C.Fg).padding(horizontal = 14.dp, vertical = 9.dp)) {
                Text(st.toast ?: "", style = T.ui(13.5.sp, 500), color = C.Bg)
            }
        }
    }
}

@Composable
private fun ScreenContent(c: Ctx, screen: Screen) {
    when (screen) {
        is Screen.Library -> LibraryPane(c)
        is Screen.Album -> AlbumScreen(c, screen.key)
        is Screen.Playlist -> PlaylistScreen(c, screen.id)
        is Screen.Artist -> ArtistScreen(c, screen.name)
        is Screen.ShowPage -> ShowScreen(c, screen.id)
        is Screen.MoviePage -> MovieScreen(c, screen.id)
        is Screen.Sync -> SyncScreen(c)
        is Screen.Activity -> ActivityScreen(c)
        is Screen.Search -> SearchScreen(c)
        is Screen.Pair -> PairScreen(c)
        is Screen.PhoneFolders -> PhoneFoldersScreen(c)
        is Screen.Settings -> SettingsScreen(c)
        is Screen.Video -> Unit
    }
}

@Composable
private fun Folded(c: Ctx) {
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.navigationBars)) {
        Box(Modifier.weight(1f).fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars)) {
            ScreenContent(c, c.st.screen)
        }
        if (c.player.current != null) MiniPlayer(c)
        BottomNav(c)
    }
}

@Composable
private fun Unfolded(c: Ctx) {
    val screen = c.st.screen
    Row(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars).windowInsetsPadding(WindowInsets.navigationBars)) {
        if (screen is Screen.Search) {
            Box(Modifier.weight(1f).fillMaxHeight()) { SearchScreen(c) }
            return@Row
        }
        Box(Modifier.width(316.dp).fillMaxHeight().drawBehind {
            drawRect(C.Hair, Offset(size.width - 1.dp.toPx(), 0f), Size(1.dp.toPx(), size.height))
        }) { LibraryPane(c) }
        Box(Modifier.weight(1f).fillMaxHeight()) {
            if (screen is Screen.Library) NowPlayingContent(c, pane = true) else ScreenContent(c, screen)
        }
    }
}

@Composable
private fun BottomNav(c: Ctx) {
    Row(Modifier.fillMaxWidth().height(56.dp).background(C.Bg).drawBehind {
        drawRect(C.Hair, Offset.Zero, Size(size.width, 1.dp.toPx()))
    }) {
        Section.entries.forEach { s ->
            val active = c.st.section == s
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable { c.st.goSection(s) }
                    .drawBehind { if (active) drawRect(C.Amber, Offset.Zero, Size(size.width, 2.dp.toPx())) },
                contentAlignment = Alignment.Center,
            ) {
                Mono(s.label, color = if (active) C.Fg else C.Faint, style = T.mono(10.5.sp, 600))
            }
        }
    }
}

@Composable
fun Gap(h: Int) = Spacer(Modifier.height(h.dp))
