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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock

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
    /** Index in [upNext] where songs added by Autoplay begin (-1: none). */
    val autoplayStart: Int = -1,
)

/** Volume change per press of the phone's volume buttons while playing on a speaker (0–100 scale). */
const val VOLUME_STEP = 3

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
    /** Speakers whose volume the phone's volume buttons can control (Sonos: the whole group). */
    val supportsVolume: Boolean get() = false
    suspend fun setVolume(volume: Int) {}
    suspend fun volume(): Int? = null
    /** Relative change (volume buttons); returns the new volume if known. */
    suspend fun adjustVolume(delta: Int): Int? { val v = (volume() ?: return null) + delta; setVolume(v.coerceIn(0, 100)); return v.coerceIn(0, 100) }
}

data class RemoteItem(val url: String, val title: String, val artist: String?, val album: String?, val artUrl: String?, val mime: String, val durationMs: Long, val isVideo: Boolean = false,
    /** The same song served from the phone, tried when the speaker can't fetch [url] from the PC. */
    val fallback: RemoteItem? = null,
)
/** What was playing, saved so it comes back after the app is closed or updated. */
@kotlinx.serialization.Serializable
data class SavedQueue(
    val tracks: List<Track> = emptyList(),
    val index: Int = 0,
    val positionMs: Long = 0,
    val shuffle: Boolean = false,
    val unshuffled: List<String> = emptyList(),
    val autoplay: List<String> = emptyList(),
    val played: List<String> = emptyList(),
)

data class RemoteState(val positionMs: Long = 0, val durationMs: Long = 0, val playing: Boolean = false, val ended: Boolean = false, val error: String? = null)

/**
 * Owns the music ExoPlayer and the queue. Queue semantics follow Apple Music: playing from a list
 * replaces the queue with the rest of that list; "Play next" inserts after the current song;
 * "Add to queue" appends; Previous restarts if more than 3 s in, otherwise goes back.
 */
class PlayerHub(private val context: Context, private val repo: Repository, private val scope: CoroutineScope) {
    val exo: ExoPlayer = ExoPlayer.Builder(
        context,
        // The phone's own decoders first; FFmpeg for what it can't decode (Apple Lossless, …).
        androidx.media3.exoplayer.DefaultRenderersFactory(context)
            .setExtensionRendererMode(androidx.media3.exoplayer.DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON),
    )
        .setMediaSourceFactory(DefaultMediaSourceFactory(DefaultDataSource.Factory(context, OkHttpDataSource.Factory(repo.http))))
        .setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), true)
        .setHandleAudioBecomingNoisy(true)
        // Keep Wi-Fi awake while streaming from the PC (otherwise it naps with the screen off and audio drops out).
        .setWakeMode(C.WAKE_MODE_NETWORK)
        // Read further ahead than the default 50 s, so a busy PC or a Wi-Fi hiccup doesn't interrupt the song.
        .setLoadControl(
            androidx.media3.exoplayer.DefaultLoadControl.Builder()
                .setBufferDurationsMs(120_000, 300_000, 2_500, 5_000)
                .setTargetBufferBytes(64 * 1024 * 1024)
                .setPrioritizeTimeOverSizeThresholds(false)
                .build(),
        )
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
    /** True while a song plays from the PC (on the phone, or on Sonos/Cast, which also pull it from the PC). Read from any thread. */
    @Volatile var streamingFromPc = false
        private set

    /** Equalizer on the phone's own playback (Sonos and Cast play the file themselves, so it doesn't reach them). */
    private val eqEngine = EqEngine()

    /** Applies [s] right away without saving it (while a slider is being dragged). */
    fun previewEq(s: com.amosley.signal.core.EqSettings) = eqEngine.apply(exo.audioSessionId, s)

    private val queueStore = com.amosley.signal.data.JsonStore(java.io.File(context.filesDir, "queue.json"), SavedQueue.serializer()) { SavedQueue() }
    /** Something changed that should be saved (the queue, the song, play/pause). */
    @Volatile private var queueDirty = false
    private var lastQueueSave = 0L

    private fun saveQueue() {
        queueDirty = false
        lastQueueSave = System.currentTimeMillis()
        val n = exo.mediaItemCount
        val ids = (0 until n).map { exo.getMediaItemAt(it).mediaId }
        val state = SavedQueue(
            tracks = ids.mapNotNull { tracks[it] },
            index = exo.currentMediaItemIndex.coerceAtLeast(0),
            positionMs = (if (remote != null) remote?.state?.value?.positionMs else exo.currentPosition)?.coerceAtLeast(0) ?: 0,
            shuffle = _ui.value.shuffle,
            unshuffled = unshuffled,
            autoplay = autoplayIds.toList(),
            played = played.toList(),
        )
        scope.launch(kotlinx.coroutines.Dispatchers.IO) { queueStore.save(state) }
    }

    /** Puts back what was playing last time, paused at the same spot (only if nothing else was started). */
    private fun restoreQueue() {
        val s = queueStore.load()
        if (s.tracks.isEmpty() || exo.mediaItemCount > 0) return
        val kept = s.tracks.filter { repo.isAvailable(it.id, it.origin) || it.origin == Origin.PC }
        val items = kept.mapNotNull { t -> item(t)?.also { tracks[t.id] = t } }
        if (items.isEmpty()) return
        val current = s.tracks.getOrNull(s.index)?.id
        val index = kept.indexOfFirst { it.id == current }.takeIf { it >= 0 && it < items.size } ?: 0
        unshuffled = s.unshuffled
        autoplayIds += s.autoplay
        s.played.forEach { played.addLast(it) }
        _ui.update { it.copy(shuffle = s.shuffle) }
        exo.setMediaItems(items, index, if (index == kept.indexOfFirst { it.id == current }) s.positionMs else 0)
        exo.prepare()
        publish()
    }

    init {
        scope.launch(kotlinx.coroutines.Dispatchers.Main) { restoreQueue() }
        scope.launch(kotlinx.coroutines.Dispatchers.Main) {
            repo.settings.map { it.eq }.distinctUntilChanged().collect { eqEngine.apply(exo.audioSessionId, it) }
        }
        exo.addListener(object : Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) = eqEngine.apply(audioSessionId, repo.settings.value.eq)
            override fun onEvents(player: Player, events: Player.Events) {
                publish()
                if (events.containsAny(Player.EVENT_TIMELINE_CHANGED, Player.EVENT_MEDIA_ITEM_TRANSITION, Player.EVENT_IS_PLAYING_CHANGED, Player.EVENT_POSITION_DISCONTINUITY)) queueDirty = true
            }
            override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
                // A song whose audio no decoder can play would otherwise run silently: say so instead.
                val audio = tracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
                if (audio.isNotEmpty() && audio.none { it.isSelected }) {
                    val codec = audio.first().getTrackFormat(0).sampleMimeType?.substringAfter('/')?.uppercase() ?: "this format"
                    onToast("This phone can't play $codec audio · skipping")
                    if (exo.hasNextMediaItem()) exo.seekToNextMediaItem()
                }
            }
            override fun onPlayerError(error: PlaybackException) {
                _ui.update { it.copy(error = error.message) }
                onToast("Can't play this — ${error.errorCodeName.removePrefix("ERROR_CODE_").lowercase().replace('_', ' ')}")
            }
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                mediaItem?.mediaId?.let { id -> played.remove(id); played.addLast(id); while (played.size > 200) played.removeFirst() }
                topUpAutoplay()
                if (remote != null && mediaItem != null && reason != Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED) {
                    sendCurrentToRemote(play = true, startMs = 0)
                }
            }
        })
        scope.launch {
            while (true) {
                delay(500)
                val r = remote
                streamingFromPc = if (r != null) r.state.value.playing
                else exo.isPlaying && exo.currentMediaItem?.localConfiguration?.uri?.scheme?.startsWith("http") == true
                if (remote == null) {
                    _ui.update { it.copy(positionMs = exo.currentPosition.coerceAtLeast(0), durationMs = durationOf(exo)) }
                }
                // Save right after changes, and every 10 s while playing so the spot in the song is kept.
                val playing = _ui.value.playing
                if (queueDirty || (playing && System.currentTimeMillis() - lastQueueSave > 10_000)) saveQueue()
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
        val autoplayStart = upNext.indexOfFirst { it.id in autoplayIds }
        _ui.update {
            it.copy(
                current = current,
                playing = if (remote != null) it.playing else exo.isPlaying || (exo.playWhenReady && exo.playbackState == Player.STATE_BUFFERING),
                buffering = exo.playbackState == Player.STATE_BUFFERING,
                positionMs = if (remote != null) it.positionMs else exo.currentPosition.coerceAtLeast(0),
                durationMs = if (remote != null) it.durationMs else durationOf(exo),
                upNext = upNext,
                autoplayStart = autoplayStart,
            )
        }
    }

    private fun item(t: Track): MediaItem? {
        val uri = repo.playUri(t.id, t.origin, t.uri) ?: return null
        val art: Uri? = when {
            t.origin == Origin.PC -> (repo.albumArtFile(t.albumKey) ?: repo.savedCover(t.albumKey))?.let(Uri::fromFile) ?: repo.artUrl(t.id)?.let(Uri::parse)
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
        autoplayIds.clear()
        exo.setMediaItems(items, 0, 0)
        exo.prepare()
        topUpAutoplay()
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

    // ---- Autoplay --------------------------------------------------------------------------------

    /** Recently played song ids, oldest first (Autoplay's idea of what you're listening to; never repeated soon). */
    private val played = ArrayDeque<String>()
    /** Songs Autoplay added to the queue. */
    private val autoplayIds = HashSet<String>()
    private var autoplayJob: Job? = null

    /** When two or fewer songs are left, add ten more like what's been playing. */
    fun topUpAutoplay() {
        if (!repo.settings.value.autoplay || autoplayJob?.isActive == true) return
        val idx = exo.currentMediaItemIndex
        if (idx < 0 || exo.mediaItemCount - idx - 1 > 2) return
        val queued = (0 until exo.mediaItemCount).map { exo.getMediaItemAt(it).mediaId }
        val seeds = (played.toList() - queued.toSet() + queued.take(idx + 1)).takeLast(25).mapNotNull { tracks[it] } +
            queued.drop(idx + 1).mapNotNull { tracks[it] }
        val exclude = queued.toSet() + played
        val library = repo.library.value.tracks
        val favorites = repo.favorites.value
        autoplayJob = scope.launch {
            val picks = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                com.amosley.signal.core.Autoplay.pick(seeds, library.filter { repo.isAvailable(it.id, it.origin) }, exclude, favorites)
            }
            if (!repo.settings.value.autoplay || exo.mediaItemCount == 0) return@launch
            val items = picks.mapNotNull { t -> item(t)?.also { tracks[t.id] = t } }
            if (items.isEmpty()) return@launch
            autoplayIds += picks.map { it.id }
            exo.addMediaItems(items)
            unshuffled = unshuffled + picks.map { it.id }
            publish()
        }
    }

    /** Turning Autoplay off removes the songs it queued but hasn't played yet. */
    fun setAutoplay(on: Boolean) {
        repo.updateSettings { it.copy(autoplay = on) }
        if (on) { topUpAutoplay(); return }
        autoplayJob?.cancel()
        for (i in exo.mediaItemCount - 1 downTo exo.currentMediaItemIndex + 1) {
            if (exo.getMediaItemAt(i).mediaId in autoplayIds) exo.removeMediaItem(i)
        }
        autoplayIds.clear()
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

    private var groupExtra = 0
    /** Rooms grouped with the speaker being played on: shown as "Office + 2" everywhere the output is named. */
    fun setGroupExtra(n: Int) {
        val r = remote ?: return
        if (n == groupExtra) return
        groupExtra = n
        _ui.update { it.copy(output = if (n > 0) "${r.name} + $n" else r.name) }
    }

    // ---- Phone volume buttons → speaker volume ----------------------------------------------------

    private val _remoteVolume = MutableStateFlow(30)
    /** Volume (0–100) of the speaker or Sonos group being played on. */
    val remoteVolume: StateFlow<Int> = _remoteVolume
    private val sessionListeners = java.util.concurrent.CopyOnWriteArraySet<Player.Listener>()
    private val remoteDevice = androidx.media3.common.DeviceInfo.Builder(androidx.media3.common.DeviceInfo.PLAYBACK_TYPE_REMOTE)
        .setMinVolume(0).setMaxVolume(100).build()
    private val volumeCommands = listOf(
        Player.COMMAND_GET_DEVICE_VOLUME, Player.COMMAND_SET_DEVICE_VOLUME, Player.COMMAND_SET_DEVICE_VOLUME_WITH_FLAGS,
        Player.COMMAND_ADJUST_DEVICE_VOLUME, Player.COMMAND_ADJUST_DEVICE_VOLUME_WITH_FLAGS,
    )
    private fun remoteVolumeActive() = remote?.supportsVolume == true

    /** Volume requests go to the speaker one at a time (fast button presses would otherwise race). */
    private val volumeLock = kotlinx.coroutines.sync.Mutex()

    private fun showRemoteVolume(v: Int) {
        _remoteVolume.value = v.coerceIn(0, 100)
        sessionListeners.forEach { it.onDeviceVolumeChanged(_remoteVolume.value, false) }
    }

    /** Sets the speaker volume (from the Play on slider) and tells Android's volume panel. */
    fun setRemoteVolume(volume: Int, send: Boolean = true) {
        val v = volume.coerceIn(0, 100)
        showRemoteVolume(v)
        val r = remote ?: return
        if (send && r.supportsVolume) scope.launch { volumeLock.withLock { runCatching { r.setVolume(v) } } }
    }

    /** Volume buttons: nudge the speaker (a Sonos group keeps each room's share), then show its real new volume. */
    fun adjustRemoteVolume(delta: Int, announceVolume: Boolean = false) {
        val r = remote ?: return
        if (!r.supportsVolume) return
        showRemoteVolume(_remoteVolume.value + delta)
        scope.launch {
            volumeLock.withLock {
                runCatching { r.adjustVolume(delta) }.getOrNull()?.let {
                    if (remote === r) {
                        showRemoteVolume(it)
                        // Report the speaker's real new level (not a guess from a possibly stale number).
                        if (announceVolume) onToast("${_ui.value.output ?: r.name} volume · $it")
                    }
                }
            }
        }
    }

    /** Re-read the speaker's volume (after grouping changes, rooms' levels combine differently). */
    fun refreshRemoteVolume() {
        val r = remote ?: return
        if (!r.supportsVolume) return
        scope.launch { volumeLock.withLock { runCatching { r.volume() }.getOrNull()?.let { if (remote === r) showRemoteVolume(it) } } }
    }

    private fun notifyDeviceChanged() {
        val info = sessionPlayer.deviceInfo
        val cmds = sessionPlayer.availableCommands
        sessionListeners.forEach {
            it.onDeviceInfoChanged(info)
            it.onAvailableCommandsChanged(cmds)
            it.onDeviceVolumeChanged(sessionPlayer.deviceVolume, false)
        }
        notifyPlayState()
    }

    /** Tells the MediaSession whether we're playing (on a speaker the phone's own player is paused). */
    private fun notifyPlayState() {
        val playing = sessionPlayer.isPlaying
        val state = sessionPlayer.playbackState
        sessionListeners.forEach {
            it.onPlaybackStateChanged(state)
            it.onPlayWhenReadyChanged(sessionPlayer.playWhenReady, Player.PLAY_WHEN_READY_CHANGE_REASON_REMOTE)
            it.onIsPlayingChanged(playing)
        }
    }

    /**
     * The player the MediaSession (notification, lock screen, volume keys) sees. While playing on a Sonos speaker it
     * reports a remote device, so Android sends the phone's volume buttons to the speaker instead of the phone.
     */
    val sessionPlayer: Player = object : androidx.media3.common.ForwardingPlayer(exo) {
        override fun addListener(listener: Player.Listener) { sessionListeners += listener; super.addListener(listener) }
        override fun removeListener(listener: Player.Listener) { sessionListeners -= listener; super.removeListener(listener) }
        override fun getDeviceInfo() = if (remoteVolumeActive()) remoteDevice else super.getDeviceInfo()
        // While a speaker plays, report *its* state: Android only gives the volume buttons to a session that's playing,
        // and the notification's play/pause should control the speaker.
        override fun isPlaying() = remote?.state?.value?.playing ?: super.isPlaying()
        override fun getPlayWhenReady() = remote?.state?.value?.playing ?: super.getPlayWhenReady()
        override fun getPlaybackState() = if (remote != null && super.getMediaItemCount() > 0) Player.STATE_READY else super.getPlaybackState()
        override fun getCurrentPosition() = remote?.state?.value?.positionMs ?: super.getCurrentPosition()
        override fun getContentPosition() = remote?.state?.value?.positionMs ?: super.getContentPosition()
        override fun play() { val r = remote; if (r != null) r.play() else super.play() }
        override fun pause() { val r = remote; if (r != null) r.pause() else super.pause() }
        override fun setPlayWhenReady(playWhenReady: Boolean) { val r = remote; if (r != null) { if (playWhenReady) r.play() else r.pause() } else super.setPlayWhenReady(playWhenReady) }
        override fun getDeviceVolume() = if (remoteVolumeActive()) _remoteVolume.value else super.getDeviceVolume()
        override fun isDeviceMuted() = if (remoteVolumeActive()) false else super.isDeviceMuted()
        override fun getAvailableCommands(): Player.Commands =
            if (!remoteVolumeActive()) super.getAvailableCommands()
            else super.getAvailableCommands().buildUpon().addAll(*volumeCommands.toIntArray()).build()
        override fun isCommandAvailable(command: Int) =
            if (remoteVolumeActive() && command in volumeCommands) true else super.isCommandAvailable(command)
        override fun setDeviceVolume(volume: Int, flags: Int) { if (remoteVolumeActive()) setRemoteVolume(volume) else super.setDeviceVolume(volume, flags) }
        @Deprecated("Deprecated in Java")
        @Suppress("DEPRECATION")
        override fun setDeviceVolume(volume: Int) { if (remoteVolumeActive()) setRemoteVolume(volume) else super.setDeviceVolume(volume) }
        override fun increaseDeviceVolume(flags: Int) { if (remoteVolumeActive()) adjustRemoteVolume(VOLUME_STEP) else super.increaseDeviceVolume(flags) }
        @Deprecated("Deprecated in Java")
        @Suppress("DEPRECATION")
        override fun increaseDeviceVolume() { if (remoteVolumeActive()) adjustRemoteVolume(VOLUME_STEP) else super.increaseDeviceVolume() }
        override fun decreaseDeviceVolume(flags: Int) { if (remoteVolumeActive()) adjustRemoteVolume(-VOLUME_STEP) else super.decreaseDeviceVolume(flags) }
        @Deprecated("Deprecated in Java")
        @Suppress("DEPRECATION")
        override fun decreaseDeviceVolume() { if (remoteVolumeActive()) adjustRemoteVolume(-VOLUME_STEP) else super.decreaseDeviceVolume() }
    }

    fun setRemote(output: RemoteOutput?) {
        if (output === remote) return
        val pos = if (remote != null) _ui.value.positionMs else exo.currentPosition
        val wasPlaying = _ui.value.playing
        remoteJob?.cancel()
        remote?.release()
        remote = output
        notifyDeviceChanged()
        // Start from the speaker's current volume, so the first button press doesn't jump.
        if (output?.supportsVolume == true) scope.launch { runCatching { output.volume() }.getOrNull()?.let { if (remote === output) setRemoteVolume(it, send = false) } }
        if (output == null) {
            exo.seekTo(pos)
            if (wasPlaying) exo.play()
            _ui.update { it.copy(output = null, outputKind = OutputKind.PHONE) }
            return
        }
        exo.pause()
        groupExtra = 0
        _ui.update { it.copy(output = output.name, outputKind = output.kind) }
        sendCurrentToRemote(play = wasPlaying || exo.mediaItemCount > 0, startMs = pos)
        var lastPlaying: Boolean? = null
        remoteJob = scope.launch {
            output.state.collect { st ->
                if (st.playing != lastPlaying) { lastPlaying = st.playing; notifyPlayState() }
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
            val url = (if (transcode) repo.speakerTranscodedUrl(t.id) else repo.speakerStreamUrl(t.id)) ?: return null
            return RemoteItem(url, t.title, t.artist, t.album, if (t.hasArt) repo.speakerArtUrl(t.id) else null, if (transcode) "audio/flac" else mimeOf(t.container), t.durationMs,
                fallback = phoneItem(t))
        }
        val item = phoneItem(t) ?: return null
        if (forSonos && hiRes) onToast("${t.title} is hi-res; Sonos may not play files above 48 kHz from the phone")
        return item
    }

    /** The song served to the speaker from the phone itself (a download or a phone file), or null if it isn't on the phone. */
    private fun phoneItem(t: Track): RemoteItem? {
        val file = repo.downloads.fileFor(t.id)
        val uri = when {
            file != null -> Uri.fromFile(file)
            t.origin == Origin.PHONE -> Uri.parse(t.uri)
            else -> return null
        }
        val mime = if (file != null && file.extension.equals("flac", true)) "audio/flac" else mimeOf(t.container)
        val url = localServer.urlFor(t.id, uri, mime) ?: return null
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
