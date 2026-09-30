package com.amosley.signal.playback

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.amosley.signal.core.Origin
import com.amosley.signal.core.Track
import com.amosley.signal.data.Repository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PlayerUi(
    val current: Track? = null,
    val playing: Boolean = false,
    val buffering: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val upNext: List<Track> = emptyList(),
    val shuffle: Boolean = false,
    val output: String? = null,
    val outputKind: OutputKind = OutputKind.PHONE,
    val error: String? = null,
)

enum class OutputKind { PHONE, CAST, SONOS }

/** A speaker / TV that plays one item at a time while the phone keeps the queue. */
interface RemoteOutput {
    val name: String
    val kind: OutputKind
    val state: StateFlow<RemoteState>
    fun load(item: RemoteItem, startMs: Long, play: Boolean)
    fun play()
    fun pause()
    fun seek(ms: Long)
    fun release()
}

data class RemoteItem(val url: String, val title: String, val artist: String?, val album: String?, val artUrl: String?, val mime: String, val durationMs: Long, val isVideo: Boolean = false)
data class RemoteState(val positionMs: Long = 0, val durationMs: Long = 0, val playing: Boolean = false, val ended: Boolean = false, val error: String? = null)

/**
 * Owns the music ExoPlayer and the queue. Queue semantics follow Apple Music: playing from a list
 * replaces the queue with the rest of that list; "Play next" inserts after the current song;
 * "Add to queue" appends; Previous restarts if more than 3 s in, otherwise goes back.
 */
class PlayerHub(private val context: Context, private val repo: Repository, private val scope: CoroutineScope) {
    val exo: ExoPlayer = ExoPlayer.Builder(context)
        .setMediaSourceFactory(DefaultMediaSourceFactory(DefaultDataSource.Factory(context, OkHttpDataSource.Factory(repo.http))))
        .setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), true)
        .setHandleAudioBecomingNoisy(true)
        .setSeekBackIncrementMs(10_000)
        .setSeekForwardIncrementMs(10_000)
        .build()

    private val tracks = HashMap<String, Track>()
    /** Remaining queue in its pre-shuffle order, used to un-shuffle. */
    private var unshuffled: List<String> = emptyList()
    private val _ui = MutableStateFlow(PlayerUi())
    val ui: StateFlow<PlayerUi> = _ui
    private var remote: RemoteOutput? = null
    private var remoteJob: Job? = null
    private var controller: MediaController? = null
    var onToast: (String) -> Unit = {}

    init {
        exo.addListener(object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) = publish()
            override fun onPlayerError(error: PlaybackException) {
                _ui.update { it.copy(error = error.message) }
                onToast("Can't play this — ${error.errorCodeName.removePrefix("ERROR_CODE_").lowercase().replace('_', ' ')}")
            }
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (remote != null && mediaItem != null && reason != Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED) {
                    sendCurrentToRemote(play = true, startMs = 0)
                }
            }
        })
        scope.launch {
            while (true) {
                delay(500)
                if (remote == null) {
                    _ui.update { it.copy(positionMs = exo.currentPosition.coerceAtLeast(0), durationMs = durationOf(exo)) }
                }
            }
        }
    }

    /** Binds the MediaSessionService so playback survives the UI and shows the media notification. */
    fun connectSession() {
        if (controller != null) return
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener({ controller = runCatching { future.get() }.getOrNull() }, androidx.core.content.ContextCompat.getMainExecutor(context))
    }

    private fun durationOf(p: Player): Long = p.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: (_ui.value.current?.durationMs ?: 0)

    private fun publish() {
        val idx = exo.currentMediaItemIndex
        val ids = (0 until exo.mediaItemCount).map { exo.getMediaItemAt(it).mediaId }
        val current = ids.getOrNull(idx)?.let { tracks[it] }
        val upNext = if (idx >= 0) ids.drop(idx + 1).mapNotNull { tracks[it] } else emptyList()
        _ui.update {
            it.copy(
                current = current,
                playing = if (remote != null) it.playing else exo.isPlaying || (exo.playWhenReady && exo.playbackState == Player.STATE_BUFFERING),
                buffering = exo.playbackState == Player.STATE_BUFFERING,
                positionMs = if (remote != null) it.positionMs else exo.currentPosition.coerceAtLeast(0),
                durationMs = if (remote != null) it.durationMs else durationOf(exo),
                upNext = upNext,
            )
        }
    }

    private fun item(t: Track): MediaItem? {
        val uri = repo.playUri(t.id, t.origin, t.uri) ?: return null
        val art: Uri? = when {
            t.origin == Origin.PC -> repo.artUrl(t.id)?.let(Uri::parse)
            else -> null
        }
        return MediaItem.Builder()
            .setMediaId(t.id)
            .setUri(uri)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(t.title)
                    .setArtist(t.artist ?: "Unknown artist")
                    .setAlbumTitle(t.album)
                    .setArtworkUri(art)
                    .setIsPlayable(true)
                    .setIsBrowsable(false)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                    .build(),
            )
            .build()
    }

    // ---- Queue operations -----------------------------------------------------------------------

    fun playList(list: List<Track>, startIndex: Int = 0, shuffle: Boolean = false) {
        if (list.isEmpty()) return
        val start = list.getOrNull(startIndex) ?: list.first()
        val playable = list.filter { repo.isAvailable(it.id, it.origin) }
        if (!repo.isAvailable(start.id, start.origin)) {
            onToast(if (repo.settings.value.offline) "Not downloaded — you're offline" else "${repo.pcName} isn't reachable")
            return
        }
        val rest = playable.dropWhile { it.id != start.id }.drop(1).let { after ->
            if (shuffle) (playable.filter { it.id != start.id }).shuffled() else after
        }
        val ordered = listOf(start) + rest
        ordered.forEach { tracks[it.id] = it }
        unshuffled = if (shuffle) playable.dropWhile { it.id != start.id }.drop(1).map { it.id } else rest.map { it.id }
        _ui.update { it.copy(shuffle = shuffle, error = null) }
        val items = ordered.mapNotNull(::item)
        exo.setMediaItems(items, 0, 0)
        exo.prepare()
        if (remote != null) sendCurrentToRemote(play = true, startMs = 0) else exo.play()
        connectSession()
    }

    fun shuffleAll(list: List<Track>) {
        if (list.isEmpty()) return
        playList(list, list.indices.random(), shuffle = true)
    }

    fun playNext(t: Track) {
        if (exo.mediaItemCount == 0) return playList(listOf(t))
        val mi = item(t) ?: return onToast("Not available offline")
        tracks[t.id] = t
        exo.addMediaItem(exo.currentMediaItemIndex + 1, mi)
        unshuffled = listOf(t.id) + unshuffled
        onToast("Playing next · ${t.title}")
    }

    fun addToQueue(t: Track) {
        if (exo.mediaItemCount == 0) return playList(listOf(t))
        val mi = item(t) ?: return onToast("Not available offline")
        tracks[t.id] = t
        exo.addMediaItem(mi)
        unshuffled = unshuffled + t.id
        onToast("Added to queue · ${t.title}")
    }

    /** [index] is relative to the up-next list. */
    fun removeFromQueue(index: Int) {
        val abs = exo.currentMediaItemIndex + 1 + index
        if (abs < exo.mediaItemCount) {
            val id = exo.getMediaItemAt(abs).mediaId
            exo.removeMediaItem(abs)
            unshuffled = unshuffled - id
        }
    }

    fun moveInQueue(from: Int, to: Int) {
        val base = exo.currentMediaItemIndex + 1
        if (base + from < exo.mediaItemCount && base + to < exo.mediaItemCount) exo.moveMediaItem(base + from, base + to)
    }

    fun clearQueue() {
        val cur = exo.currentMediaItemIndex
        if (cur + 1 < exo.mediaItemCount) exo.removeMediaItems(cur + 1, exo.mediaItemCount)
        unshuffled = emptyList()
    }

    fun setShuffle(on: Boolean) {
        val base = exo.currentMediaItemIndex + 1
        val remaining = (base until exo.mediaItemCount).map { exo.getMediaItemAt(it) }
        if (remaining.isNotEmpty()) {
            val reordered = if (on) remaining.shuffled() else {
                val order = unshuffled.withIndex().associate { it.value to it.index }
                remaining.sortedBy { order[it.mediaId] ?: Int.MAX_VALUE }
            }
            exo.replaceMediaItems(base, exo.mediaItemCount, reordered)
        }
        _ui.update { it.copy(shuffle = on) }
    }

    // ---- Transport ------------------------------------------------------------------------------

    fun toggle() {
        val r = remote
        if (r != null) {
            if (r.state.value.playing) r.pause() else r.play()
            return
        }
        if (exo.playbackState == Player.STATE_IDLE) exo.prepare()
        if (exo.playbackState == Player.STATE_ENDED) exo.seekTo(0)
        exo.playWhenReady = !exo.playWhenReady
    }

    fun pause() { remote?.pause() ?: exo.pause() }

    fun next() {
        if (exo.hasNextMediaItem()) exo.seekToNextMediaItem()
    }

    fun prev() {
        val r = remote
        if (r != null) {
            if (r.state.value.positionMs > 3000 || !exo.hasPreviousMediaItem()) r.seek(0) else exo.seekToPreviousMediaItem()
            return
        }
        exo.seekToPrevious()
    }

    fun seekTo(ms: Long) {
        remote?.seek(ms) ?: exo.seekTo(ms)
        _ui.update { it.copy(positionMs = ms) }
    }

    fun jumpTo(upNextIndex: Int) {
        val abs = exo.currentMediaItemIndex + 1 + upNextIndex
        if (abs < exo.mediaItemCount) {
            exo.seekTo(abs, 0)
            if (remote == null) exo.play()
        }
    }

    // ---- Outputs --------------------------------------------------------------------------------

    val remoteOutput: RemoteOutput? get() = remote

    fun setRemote(output: RemoteOutput?) {
        if (output === remote) return
        val pos = if (remote != null) _ui.value.positionMs else exo.currentPosition
        val wasPlaying = _ui.value.playing
        remoteJob?.cancel()
        remote?.release()
        remote = output
        if (output == null) {
            exo.seekTo(pos)
            if (wasPlaying) exo.play()
            _ui.update { it.copy(output = null, outputKind = OutputKind.PHONE) }
            return
        }
        exo.pause()
        _ui.update { it.copy(output = output.name, outputKind = output.kind) }
        sendCurrentToRemote(play = wasPlaying || exo.mediaItemCount > 0, startMs = pos)
        remoteJob = scope.launch {
            output.state.collect { st ->
                _ui.update { it.copy(positionMs = st.positionMs, durationMs = if (st.durationMs > 0) st.durationMs else it.durationMs, playing = st.playing) }
                if (st.ended) {
                    if (exo.hasNextMediaItem()) exo.seekToNextMediaItem() else _ui.update { it.copy(playing = false) }
                }
            }
        }
    }

    /** URL a speaker/TV can fetch itself: always the PC endpoint (phone-only songs can't be cast). */
    /** Serves phone files to speakers/TVs on the Wi-Fi. */
    val localServer = LocalMediaServer(context)

    private fun mimeOf(container: String?): String = when (container?.uppercase()) {
        "FLAC" -> "audio/flac"; "WAV" -> "audio/wav"; "MP3" -> "audio/mpeg"; "M4A", "AAC", "ALAC" -> "audio/mp4"
        "OGG", "OPUS" -> "audio/ogg"; "AIFF" -> "audio/aiff"
        else -> "audio/mpeg"
    }

    /**
     * A URL a speaker or TV can fetch itself: the PC's stream for PC songs (transcoded to 16/44 FLAC for
     * Sonos when the file is hi-res), otherwise the phone's built-in server for phone songs and downloads.
     */
    fun remoteItemFor(t: Track, forSonos: Boolean): RemoteItem? {
        val hiRes = (t.sampleRate ?: 0) > 48000 || (t.bitDepth ?: 0) > 24
        val pcUp = t.origin == Origin.PC && repo.status.value.reachable && !repo.settings.value.offline
        if (pcUp) {
            val transcode = forSonos && hiRes
            val url = (if (transcode) repo.transcodedUrl(t.id) else repo.streamUrl(t.id)) ?: return null
            return RemoteItem(url, t.title, t.artist, t.album, if (t.hasArt) repo.artUrl(t.id) else null, if (transcode) "audio/flac" else mimeOf(t.container), t.durationMs)
        }
        val file = repo.downloads.fileFor(t.id)
        val uri = when {
            file != null -> Uri.fromFile(file)
            t.origin == Origin.PHONE -> Uri.parse(t.uri)
            else -> return null
        }
        val mime = if (file != null && file.extension.equals("flac", true)) "audio/flac" else mimeOf(t.container)
        val url = localServer.urlFor(t.id, uri, mime) ?: return null
        if (forSonos && hiRes) onToast("${t.title} is hi-res; Sonos may not play files above 48 kHz from the phone")
        return RemoteItem(url, t.title, t.artist, t.album, null, mime, t.durationMs)
    }

    private fun sendCurrentToRemote(play: Boolean, startMs: Long) {
        val r = remote ?: return
        val t = exo.currentMediaItem?.mediaId?.let { tracks[it] } ?: return
        val ri = remoteItemFor(t, r.kind == OutputKind.SONOS)
        if (ri == null) {
            onToast("Connect to Wi-Fi to play on ${r.name}")
            return
        }
        r.load(ri, startMs, play)
    }
}
