package com.amosley.signal.cast

import android.content.Context
import android.net.Uri
import androidx.mediarouter.media.MediaRouteSelector
import androidx.mediarouter.media.MediaRouter
import com.amosley.signal.playback.OutputKind
import com.amosley.signal.playback.RemoteItem
import com.amosley.signal.playback.RemoteOutput
import com.amosley.signal.playback.RemoteState
import com.google.android.gms.cast.CastMediaControlIntent
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.cast.MediaSeekOptions
import com.google.android.gms.cast.MediaStatus
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastOptions
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.OptionsProvider
import com.google.android.gms.cast.framework.SessionManagerListener
import com.google.android.gms.cast.framework.SessionProvider
import com.google.android.gms.cast.framework.media.RemoteMediaClient
import com.google.android.gms.common.images.WebImage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Uses Google's Default Media Receiver, so no receiver app registration is needed. */
class CastOptionsProvider : OptionsProvider {
    override fun getCastOptions(context: Context): CastOptions =
        CastOptions.Builder()
            .setReceiverApplicationId(CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID)
            .setStopReceiverApplicationWhenEndingSession(true)
            .build()

    override fun getAdditionalSessionProviders(context: Context): List<SessionProvider>? = null
}

data class CastDevice(val id: String, val name: String, val description: String, val selected: Boolean)

/** Discovers Cast devices (Chromecast / Google TV / Cast-enabled speakers) and manages the session. */
class CastManager(private val context: Context, private val onSession: (CastOutput?) -> Unit) {
    private val castContext: CastContext? = runCatching { CastContext.getSharedInstance(context) }.getOrNull()
    val available: Boolean get() = castContext != null
    private val router = MediaRouter.getInstance(context)
    private val selector: MediaRouteSelector? = castContext?.mergedSelector
    private val _devices = MutableStateFlow<List<CastDevice>>(emptyList())
    val devices: StateFlow<List<CastDevice>> = _devices
    private var scanning = false

    private val routerCallback = object : MediaRouter.Callback() {
        override fun onRouteAdded(router: MediaRouter, route: MediaRouter.RouteInfo) = refresh()
        override fun onRouteRemoved(router: MediaRouter, route: MediaRouter.RouteInfo) = refresh()
        override fun onRouteChanged(router: MediaRouter, route: MediaRouter.RouteInfo) = refresh()
        override fun onRouteSelected(router: MediaRouter, route: MediaRouter.RouteInfo, reason: Int) = refresh()
        override fun onRouteUnselected(router: MediaRouter, route: MediaRouter.RouteInfo, reason: Int) = refresh()
    }

    private val sessionListener = object : SessionManagerListener<CastSession> {
        override fun onSessionStarted(session: CastSession, sessionId: String) = onSession(CastOutput(session))
        override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) = onSession(CastOutput(session))
        override fun onSessionEnded(session: CastSession, error: Int) = onSession(null)
        override fun onSessionStarting(session: CastSession) {}
        override fun onSessionStartFailed(session: CastSession, error: Int) = onSession(null)
        override fun onSessionEnding(session: CastSession) {}
        override fun onSessionResuming(session: CastSession, sessionId: String) {}
        override fun onSessionResumeFailed(session: CastSession, error: Int) {}
        override fun onSessionSuspended(session: CastSession, reason: Int) {}
    }

    init {
        castContext?.sessionManager?.addSessionManagerListener(sessionListener, CastSession::class.java)
    }

    fun startScan() {
        val sel = selector ?: return
        if (scanning) return
        scanning = true
        router.addCallback(sel, routerCallback, MediaRouter.CALLBACK_FLAG_PERFORM_ACTIVE_SCAN)
        refresh()
    }

    fun stopScan() {
        if (!scanning) return
        scanning = false
        router.removeCallback(routerCallback)
    }

    private fun refresh() {
        val sel = selector ?: return
        _devices.value = router.routes
            .filter { !it.isDefault && it.isEnabled && it.matchesSelector(sel) }
            .map { CastDevice(it.id, it.name, it.description ?: "Google Cast", it.isSelected) }
    }

    fun select(id: String) {
        router.routes.firstOrNull { it.id == id }?.let { router.selectRoute(it) }
    }

    fun disconnect() {
        castContext?.sessionManager?.endCurrentSession(true)
        router.unselect(MediaRouter.UNSELECT_REASON_STOPPED)
    }

    fun currentOutput(): CastOutput? = castContext?.sessionManager?.currentCastSession?.takeIf { it.isConnected }?.let { CastOutput(it) }

    val currentName: String? get() = castContext?.sessionManager?.currentCastSession?.castDevice?.friendlyName
}

class CastOutput(private val session: CastSession) : RemoteOutput {
    override val name: String = session.castDevice?.friendlyName ?: "TV"
    override val kind = OutputKind.CAST
    private val _state = MutableStateFlow(RemoteState())
    override val state: StateFlow<RemoteState> = _state
    private val client: RemoteMediaClient? get() = session.remoteMediaClient
    private var sawPlaying = false

    private val callback = object : RemoteMediaClient.Callback() {
        override fun onStatusUpdated() = update()
    }
    private val progress = RemoteMediaClient.ProgressListener { pos, dur ->
        _state.value = _state.value.copy(positionMs = pos, durationMs = dur)
    }

    init {
        client?.registerCallback(callback)
        client?.addProgressListener(progress, 500)
    }

    private fun update() {
        val c = client ?: return
        val playing = c.isPlaying || c.isBuffering
        if (playing) sawPlaying = true
        val ended = sawPlaying && c.playerState == MediaStatus.PLAYER_STATE_IDLE && c.idleReason == MediaStatus.IDLE_REASON_FINISHED
        if (ended) sawPlaying = false
        _state.value = _state.value.copy(playing = playing, ended = ended, positionMs = c.approximateStreamPosition, durationMs = c.streamDuration)
    }

    override fun load(item: RemoteItem, startMs: Long, play: Boolean) {
        val md = com.google.android.gms.cast.MediaMetadata(
            if (item.isVideo) com.google.android.gms.cast.MediaMetadata.MEDIA_TYPE_MOVIE else com.google.android.gms.cast.MediaMetadata.MEDIA_TYPE_MUSIC_TRACK,
        ).apply {
            putString(com.google.android.gms.cast.MediaMetadata.KEY_TITLE, item.title)
            item.artist?.let { putString(com.google.android.gms.cast.MediaMetadata.KEY_ARTIST, it) }
            item.album?.let { putString(com.google.android.gms.cast.MediaMetadata.KEY_ALBUM_TITLE, it) }
            item.artUrl?.let { addImage(WebImage(Uri.parse(it))) }
        }
        val info = MediaInfo.Builder(item.url)
            .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
            .setContentType(item.mime)
            .setMetadata(md)
            .apply { if (item.durationMs > 0) setStreamDuration(item.durationMs) }
            .build()
        sawPlaying = false
        _state.value = RemoteState(positionMs = startMs, durationMs = item.durationMs, playing = play)
        client?.load(MediaLoadRequestData.Builder().setMediaInfo(info).setAutoplay(play).setCurrentTime(startMs).build())
    }

    override fun play() { client?.play() }
    override fun pause() { client?.pause() }
    override fun seek(ms: Long) { client?.seek(MediaSeekOptions.Builder().setPosition(ms).build()) }

    override fun release() {
        client?.unregisterCallback(callback)
        client?.removeProgressListener(progress)
    }
}
