package com.amosley.signal.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.StatFs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.RandomAccessFile

@Serializable
data class DownloadedFile(val id: String, val path: String, val size: Long, val quality: String, val kind: String, val finishedAt: Long)

sealed interface DlState {
    data object None : DlState
    data class Queued(val reason: String? = null) : DlState
    data class Running(val progress: Float) : DlState
    data class Failed(val message: String, val attempts: Int) : DlState
    data class Done(val file: DownloadedFile) : DlState
}

/** [auto] = queued by a library's Download new / Download all rule (cancelled again if the rule changes). */
data class DlRequest(val id: String, val kind: String, val title: String, val ext: String, val size: Long, val auto: Boolean = false)

/**
 * Downloads PC items into app-private storage. Resumable (HTTP Range), retries 3× with back-off,
 * honours Wi-Fi-only and the low-storage floor.
 */
class Downloads(
    private val context: Context,
    private val http: OkHttpClient,
    private val scope: CoroutineScope,
    private val urlFor: (id: String, quality: String) -> String?,
    private val settings: () -> Settings,
) {
    private val dir = File(context.filesDir, "downloads").apply { mkdirs() }
    private val store = JsonStore(File(context.filesDir, "downloads.json"), MapSerializer(String.serializer(), DownloadedFile.serializer())) { emptyMap() }
    private val _states = MutableStateFlow<Map<String, DlState>>(store.load().filterValues { File(it.path).exists() }.mapValues { DlState.Done(it.value) })
    val states: StateFlow<Map<String, DlState>> = _states
    private val pending = ArrayDeque<DlRequest>()
    private val requests = mutableMapOf<String, DlRequest>()
    private val workers = mutableListOf<Job>()
    private val _titles = MutableStateFlow<Map<String, String>>(emptyMap())
    val titles: StateFlow<Map<String, String>> = _titles
    var onQueueActive: (Boolean) -> Unit = {}

    fun state(id: String): DlState = _states.value[id] ?: DlState.None
    fun fileFor(id: String): File? = (state(id) as? DlState.Done)?.file?.path?.let(::File)?.takeIf { it.exists() }
    fun isDownloaded(id: String) = fileFor(id) != null

    fun usedBytes(): Long = _states.value.values.sumOf { (it as? DlState.Done)?.file?.size ?: 0L }
    fun freeBytes(): Long = runCatching { StatFs(context.filesDir.path).availableBytes }.getOrDefault(0L)

    /** Queues a download. Returns false if it was already downloaded, running or queued. */
    @Synchronized
    fun enqueue(req: DlRequest): Boolean {
        val cur = state(req.id)
        if (cur is DlState.Done || cur is DlState.Running || (cur is DlState.Queued && requests.containsKey(req.id))) {
            // Asking by hand for something a rule queued keeps it even if the rule is switched off later.
            if (!req.auto) requests[req.id]?.let { old -> if (old.auto) requests[req.id] = old.copy(auto = false) }
            return false
        }
        requests[req.id] = req
        pending.addLast(req)
        _titles.update { it + (req.id to req.title) }
        _states.update { it + (req.id to DlState.Queued()) }
        ensureWorker()
        return true
    }

    /** Drops rule-queued downloads that are no longer wanted (e.g. a library switched from Download all to Stream). */
    @Synchronized
    fun cancelAutoExcept(wanted: Set<String>) {
        val drop = pending.filter { it.auto && it.id !in wanted }.map { it.id }.toSet()
        if (drop.isEmpty()) return
        pending.removeAll { it.id in drop }
        drop.forEach { requests.remove(it); File(dir, "$it.part").delete() }
        _states.update { m -> m.filterKeys { it !in drop || m[it] is DlState.Done } }
    }

    @Synchronized
    fun retry(id: String) {
        val req = requests[id] ?: return
        _states.update { it + (id to DlState.Queued()) }
        pending.addLast(req)
        ensureWorker()
    }

    @Synchronized
    fun cancel(id: String) {
        pending.removeAll { it.id == id }
        requests.remove(id)
        if (state(id) !is DlState.Done) _states.update { it - id }
        File(dir, "$id.part").delete()
    }

    fun remove(id: String) {
        cancel(id)
        fileFor(id)?.delete()
        _states.update { it - id }
        persist()
    }

    private fun persist() {
        store.save(_states.value.mapNotNull { (k, v) -> (v as? DlState.Done)?.let { k to it.file } }.toMap())
    }

    /** Runs up to [PARALLEL] downloads at once; small files like songs go much faster than one at a time. */
    @Synchronized
    private fun ensureWorker() {
        workers.removeAll { !it.isActive }
        if (workers.isEmpty()) onQueueActive(true)
        while (workers.size < PARALLEL && workers.size < pending.size) {
            workers += scope.launch(Dispatchers.IO) {
                try {
                    while (isActive) {
                        val next = synchronized(this@Downloads) { pending.removeFirstOrNull() } ?: break
                        runOne(next)
                    }
                } finally {
                    val idle = synchronized(this@Downloads) { workers.remove(coroutineContext[Job]); workers.none { it.isActive } }
                    if (idle) onQueueActive(false)
                }
            }
        }
    }

    companion object {
        const val PARALLEL = 3
    }

    private fun onWifi(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ||
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }

    private suspend fun runOne(req: DlRequest) {
        val s = settings()
        // Wait for Wi-Fi / storage instead of failing.
        while (s.wifiOnly && !onWifi()) {
            _states.update { it + (req.id to DlState.Queued("WI-FI")) }
            delay(15_000)
        }
        val floor = settings().lowStoragePauseGb * (1L shl 30)
        while (freeBytes() - req.size < floor) {
            _states.update { it + (req.id to DlState.Queued("LOW STORAGE")) }
            delay(60_000)
        }
        val quality = if (req.kind == "track" && settings().dlQualityLossless1644) "16-44" else "original"
        var attempts = 0
        while (true) {
            attempts++
            val result = runCatching { fetch(req, quality) }
            if (result.isSuccess) return
            if (attempts >= 3) {
                _states.update { it + (req.id to DlState.Failed(result.exceptionOrNull()?.message ?: "failed", attempts)) }
                return
            }
            delay(2_000L * (1 shl attempts))
        }
    }

    private fun fetch(req: DlRequest, quality: String) {
        val url = urlFor(req.id, quality) ?: error("STUDIO-PC not paired")
        val part = File(dir, "${req.id}.part")
        val have = if (part.exists() && quality == "original") part.length() else 0L
        if (have == 0L) part.delete()
        val builder = Request.Builder().url(url)
        if (have > 0) builder.header("Range", "bytes=$have-")
        http.newCall(builder.build()).execute().use { res ->
            if (!res.isSuccessful) error("HTTP ${res.code}")
            val resumed = res.code == 206
            val body = res.body ?: error("empty body")
            val total = (if (resumed) have else 0L) + body.contentLength().coerceAtLeast(0)
            val served = res.header("X-Signal-Quality") ?: quality
            RandomAccessFile(part, "rw").use { raf ->
                var written = if (resumed) have else 0L
                raf.setLength(written)
                raf.seek(written)
                val buf = ByteArray(64 * 1024)
                var lastEmit = 0L
                body.byteStream().use { input ->
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        raf.write(buf, 0, n)
                        written += n
                        val now = System.currentTimeMillis()
                        if (now - lastEmit > 300) {
                            lastEmit = now
                            val p = if (total > 0) written.toFloat() / total else 0f
                            _states.update { it + (req.id to DlState.Running(p.coerceIn(0f, 0.99f))) }
                        }
                        if (!requests.containsKey(req.id)) error("cancelled")
                    }
                }
            }
            val ext = if (served == "16-44") "flac" else req.ext.lowercase()
            val dest = File(dir, "${req.id}.$ext")
            dest.delete()
            if (!part.renameTo(dest)) error("could not save file")
            val f = DownloadedFile(req.id, dest.path, dest.length(), served, req.kind, System.currentTimeMillis())
            _states.update { it + (req.id to DlState.Done(f)) }
            persist()
        }
    }
}
