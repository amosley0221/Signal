package com.amosley.signal.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.amosley.signal.SignalApp
import com.amosley.signal.core.Track

enum class Section(val label: String) { MUSIC("Music"), MOVIES("Movies"), TV("TV Shows"), SETTINGS("Settings") }

enum class MusicTab(val label: String) {
    RECENT("Recently Added"), SONGS("Songs"), ALBUMS("Albums"), ARTISTS("Artists"), PLAYLISTS("Playlists"), VIDEOS("Music Videos")
}

enum class VideoKind { MOVIE, EPISODE, MUSIC_VIDEO }

sealed interface Screen {
    data object Library : Screen
    data class Album(val key: String) : Screen
    /** A user playlist id, or [NEEDS_ARTIST] for the smart playlist. */
    data class Playlist(val id: String) : Screen
    data class Artist(val name: String) : Screen
    data class ShowPage(val id: String) : Screen
    data class MoviePage(val id: String) : Screen
    data class Video(val id: String, val kind: VideoKind, val showId: String? = null) : Screen
    data object Sync : Screen
    data object Activity : Screen
    data object Pair : Screen
    data object Search : Screen
    data object PhoneFolders : Screen
    data object Settings : Screen

    companion object {
        const val NEEDS_ARTIST = "smart:needs-artist"
        const val RECENT_PLAYLIST = "smart:recent"
    }
}

sealed interface Sheet {
    data object NowPlaying : Sheet
    data object Queue : Sheet
    data class Edit(val trackId: String) : Sheet
    data object Batch : Sheet
    data object Import : Sheet
    data class Cast(val video: Boolean = false) : Sheet
    data class Actions(val trackId: String) : Sheet
    data class AddTo(val trackId: String) : Sheet
    data class NewPlaylist(val trackId: String? = null) : Sheet
    data class LyricsEditor(val trackId: String) : Sheet
    /** Pick or generate art for an album (key) or an artist (name). */
    data class Art(val album: String?, val artist: String?) : Sheet
}

/** UI navigation state. Library data lives in the Repository; playback in PlayerHub. */
class AppState : ViewModel() {
    var section by mutableStateOf(Section.MUSIC)
    var tab by mutableStateOf(MusicTab.RECENT)
    val stack = mutableStateListOf<Screen>(Screen.Library)
    var sheet by mutableStateOf<Sheet?>(null)
    var showLyrics by mutableStateOf(false)
    var query by mutableStateOf("")
    var toast by mutableStateOf<String?>(null)
    var season by mutableStateOf<Int?>(null)
    var stylePrompt by mutableStateOf("")
    var batchIdx = mutableStateOf(mapOf<String, Int>())
    var importIdx = mutableStateOf(mapOf<String, Int>())
    var pip by mutableStateOf(false)
    /** Sonos rooms (uuid) grouped with the active Sonos output. */
    var sonosGroup by mutableStateOf(setOf<String>())
    var sonosVolume by mutableStateOf(30)
    /** Position to resume a video at after returning from the cast picker. */
    var videoPositionMs by mutableStateOf(0L)

    val screen: Screen get() = stack.last()

    fun push(s: Screen) {
        if (stack.last() != s) stack.add(s)
    }

    fun back(): Boolean {
        if (sheet != null) {
            sheet = null
            return true
        }
        if (stack.size > 1) {
            stack.removeAt(stack.lastIndex)
            return true
        }
        if (section != Section.MUSIC) {
            section = Section.MUSIC
            return true
        }
        return false
    }

    fun goSection(s: Section) {
        section = s
        stack.clear()
        stack.add(if (s == Section.SETTINGS) Screen.Settings else Screen.Library)
    }

    fun openArtist(name: String?) {
        if (name == null) return
        sheet = null
        push(Screen.Artist(name))
    }

    fun playFrom(app: SignalApp, list: List<Track>, t: Track) {
        app.hub.playList(list, list.indexOfFirst { it.id == t.id }.coerceAtLeast(0))
    }
}
