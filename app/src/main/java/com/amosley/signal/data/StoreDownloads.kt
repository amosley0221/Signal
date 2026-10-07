package com.amosley.signal.data

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.MediaStore
import com.amosley.signal.core.StoreFiles
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.FilterInputStream
import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * Saves music bought in the in-app store (Qobuz) into Music/Signal on the phone: single tracks as they are,
 * album ZIPs unpacked. The files then show up in the library like any other music on the phone.
 */
class StoreDownloads(
    private val context: Context,
    private val http: OkHttpClient,
    private val scope: CoroutineScope,
    private val onSaved: () -> Unit,
    private val toast: (String) -> Unit,
) {
    data class Job(val id: Long, val name: String, val bytes: Long = 0, val total: Long = -1, val files: Int = 0, val done: Boolean = false, val error: String? = null)

    private val _jobs = MutableStateFlow<List<Job>>(emptyList())
    val jobs: StateFlow<List<Job>> = _jobs

    /** A download the store page started; [cookies] keep it signed in to the user's account. */
    fun fromWeb(url: String, userAgent: String?, contentDisposition: String?, mime: String?, cookies: String?, referer: String?) {
        if (Build.VERSION.SDK_INT < 29) { toast("Saving purchases needs Android 10 or newer"); return }
        if (!url.startsWith("http")) { toast("This download can't be saved from here. Try the download button next to each song, or open the page in Chrome."); return }
        val name = StoreFiles.fileName(url, contentDisposition)
        val job = Job(System.nanoTime(), name)
        _jobs.update { it + job }
        toast("Downloading $name")
        scope.launch(Dispatchers.IO) {
            withWakeLock {
                runCatching {
                    val req = Request.Builder().url(url).apply {
                        userAgent?.let { header("User-Agent", it) }
                        cookies?.let { header("Cookie", it) }
                        referer?.let { header("Referer", it) }
                    }.build()
                    http.newBuilder().readTimeout(5, java.util.concurrent.TimeUnit.MINUTES).build().newCall(req).execute().use { r ->
                        if (!r.isSuccessful) throw java.io.IOException("the store answered ${r.code}")
                        val body = r.body ?: throw java.io.IOException("empty download")
                        val realName = StoreFiles.fileName(url, r.header("Content-Disposition") ?: contentDisposition, name)
                        update(job.id) { it.copy(name = realName, total = body.contentLength()) }
                        save(job.id, realName, body.contentType()?.toString() ?: mime, counting(job.id, body.byteStream()))
                    }
                }.onSuccess { n -> finish(job.id, n) }.onFailure { e -> fail(job.id, e) }
            }
        }
    }

    /** A file already on the phone (for example downloaded in Chrome): a ZIP or an audio file. */
    fun fromFile(uri: Uri, name: String) {
        if (Build.VERSION.SDK_INT < 29) { toast("Saving purchases needs Android 10 or newer"); return }
        val job = Job(System.nanoTime(), name)
        _jobs.update { it + job }
        scope.launch(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { save(job.id, name, context.contentResolver.getType(uri), counting(job.id, it)) }
                    ?: throw java.io.IOException("couldn't open the file")
            }.onSuccess { n -> finish(job.id, n) }.onFailure { e -> fail(job.id, e) }
        }
    }

    fun dismiss(id: Long) = _jobs.update { all -> all.filterNot { it.id == id } }

    /** Returns how many audio files were saved. */
    private fun save(id: Long, name: String, mime: String?, input: InputStream): Int {
        if (StoreFiles.isZip(name, mime)) {
            var n = 0
            ZipInputStream(input).use { zip ->
                while (true) {
                    val e = zip.nextEntry ?: break
                    val (dir, file) = StoreFiles.zipTarget(e.name, name) ?: continue
                    insert(dir, file, StoreFiles.audioMime(file)!!, zip)
                    n++
                    update(id) { it.copy(files = n) }
                }
            }
            return n
        }
        val audio = StoreFiles.audioMime(name) ?: throw java.io.IOException("$name isn't a music file")
        input.use { insert("${StoreFiles.ROOT}/Qobuz/", StoreFiles.clean(name), audio, it) }
        update(id) { it.copy(files = 1) }
        return 1
    }

    private fun insert(relativeDir: String, name: String, mime: String, data: InputStream) {
        val cr = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativeDir)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = cr.insert(MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values)
            ?: throw java.io.IOException("couldn't create $name")
        try {
            cr.openOutputStream(uri)?.use { out -> data.copyTo(out, 1 shl 16) } ?: throw java.io.IOException("couldn't write $name")
            cr.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
        } catch (e: Throwable) {
            runCatching { cr.delete(uri, null, null) }
            throw e
        }
        // Make sure the tags (title, artist, album) are read right away.
        cr.query(uri, arrayOf(MediaStore.MediaColumns.DATA), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0)?.let { MediaScannerConnection.scanFile(context, arrayOf(it), arrayOf(mime), null) }
        }
    }

    private suspend fun finish(id: Long, files: Int) {
        update(id) { it.copy(done = true) }
        val name = _jobs.value.firstOrNull { it.id == id }?.name.orEmpty()
        toast(if (files == 0) "$name had no music files" else "Added $files song${if (files != 1) "s" else ""} to your library")
        delay(1500) // let the media scanner read the new files' tags
        onSaved()
    }

    private fun fail(id: Long, e: Throwable) {
        update(id) { it.copy(done = true, error = e.message ?: "download failed") }
        toast("Download failed: ${e.message ?: "error"}")
    }

    private fun update(id: Long, f: (Job) -> Job) = _jobs.update { all -> all.map { if (it.id == id) f(it) else it } }

    private fun counting(id: Long, s: InputStream): InputStream = object : FilterInputStream(s) {
        var count = 0L
        var last = 0L
        private fun add(n: Long) {
            if (n <= 0) return
            count += n
            if (count - last > 512 * 1024) { last = count; update(id) { it.copy(bytes = count) } }
        }
        override fun read(): Int = super.read().also { if (it >= 0) add(1) }
        override fun read(b: ByteArray, off: Int, len: Int): Int = super.read(b, off, len).also { add(it.toLong()) }
    }

    private inline fun <T> withWakeLock(block: () -> T): T {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val lock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Signal:store").apply { setReferenceCounted(false); acquire(2 * 60 * 60 * 1000L) }
        try { return block() } finally { runCatching { lock.release() } }
    }
}
