package com.amosley.signal

import android.app.Application
import android.content.Intent
import androidx.core.content.ContextCompat
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.amosley.signal.cast.CastManager
import com.amosley.signal.cast.CastOutput
import com.amosley.signal.playback.RemoteItem
import com.amosley.signal.cast.SonosController
import com.amosley.signal.data.Repository
import com.amosley.signal.playback.PlayerHub
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

class SignalApp : Application(), ImageLoaderFactory {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    lateinit var repo: Repository
        private set
    lateinit var hub: PlayerHub
        private set
    lateinit var cast: CastManager
        private set
    lateinit var sonos: SonosController
        private set
    private val _toasts = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val toasts: SharedFlow<String> = _toasts

    fun toast(msg: String) { _toasts.tryEmit(msg) }

    /** Video casting is separate from the music queue: the TV plays one video, the phone is the remote. */
    var videoCast by mutableStateOf<CastOutput?>(null)
    var pendingVideoCast = false
    var currentVideoItem: RemoteItem? = null
    var videoPosition: () -> Long = { 0L }

    fun startVideoCast() {
        val out = cast.currentOutput() ?: return
        pendingVideoCast = false
        hub.setRemote(null)
        videoCast = out
        currentVideoItem?.let { out.load(it, videoPosition(), true) }
    }

    override fun onCreate() {
        super.onCreate()
        repo = Repository(this, scope)
        hub = PlayerHub(this, repo, scope)
        hub.onToast = ::toast
        sonos = SonosController(this, repo.http, scope)
        cast = CastManager(this) { output ->
            when {
                output != null && pendingVideoCast -> {
                    pendingVideoCast = false
                    hub.setRemote(null)
                    videoCast = output
                    currentVideoItem?.let { output.load(it, videoPosition(), true) }
                }
                output == null -> {
                    videoCast = null
                    hub.setRemote(null)
                }
                else -> hub.setRemote(output)
            }
        }
        repo.downloads.onQueueActive = { active ->
            val intent = Intent(this, DownloadService::class.java)
            if (active) runCatching { ContextCompat.startForegroundService(this, intent) } else stopService(intent)
        }
    }

    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .okHttpClient { repo.http }
        .diskCache { DiskCache.Builder().directory(cacheDir.resolve("art")).maxSizeBytes(256L * 1024 * 1024).build() }
        .crossfade(true)
        .build()
}
