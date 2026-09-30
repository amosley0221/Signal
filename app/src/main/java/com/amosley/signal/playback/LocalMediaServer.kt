package com.amosley.signal.playback

import android.content.Context
import android.net.ConnectivityManager
import android.net.Uri
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * Tiny HTTP server so Sonos speakers and Chromecasts on the Wi-Fi can fetch songs and videos that
 * live on the phone (they can't read content:// uris). Serves registered items only, with Range
 * support, under an unguessable per-launch path.
 */
class LocalMediaServer(private val context: Context) {
    private data class Item(val uri: Uri, val mime: String)

    private val items = ConcurrentHashMap<String, Item>()
    private val secret = UUID.randomUUID().toString().replace("-", "")
    private val pool = Executors.newCachedThreadPool()
    @Volatile private var server: ServerSocket? = null

    val port: Int get() = server?.localPort ?: 0

    @Synchronized
    private fun ensureStarted() {
        if (server?.isClosed == false) return
        val s = ServerSocket()
        s.reuseAddress = true
        s.bind(InetSocketAddress(0))
        server = s
        pool.execute {
            while (!s.isClosed) {
                val client = runCatching { s.accept() }.getOrNull() ?: break
                pool.execute { runCatching { handle(client) }; runCatching { client.close() } }
            }
        }
    }

    /** The phone's Wi-Fi IPv4 address, or null when not on a network. */
    private fun lanAddress(): String? {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val lp = cm.getLinkProperties(cm.activeNetwork) ?: return null
        return lp.linkAddresses.map { it.address }.firstOrNull { it is Inet4Address && !it.isLoopbackAddress }?.hostAddress
    }

    /** Returns an http:// URL a speaker on the same network can fetch, or null when offline. */
    fun urlFor(id: String, uri: Uri, mime: String): String? {
        val host = lanAddress() ?: return null
        ensureStarted()
        val key = id.replace(Regex("[^A-Za-z0-9_-]"), "_")
        items[key] = Item(uri, mime)
        val ext = when {
            mime.contains("flac") -> "flac"; mime.contains("wav") -> "wav"; mime.contains("mpeg") -> "mp3"
            mime.contains("mp4") && mime.startsWith("audio") -> "m4a"; mime.startsWith("video") -> "mp4"; else -> "bin"
        }
        return "http://$host:$port/$secret/$key.$ext"
    }

    private fun handle(socket: Socket) {
        socket.soTimeout = 30_000
        val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.ISO_8859_1))
        val requestLine = reader.readLine() ?: return
        val headers = HashMap<String, String>()
        while (true) {
            val line = reader.readLine() ?: break
            if (line.isEmpty()) break
            val i = line.indexOf(':')
            if (i > 0) headers[line.substring(0, i).trim().lowercase()] = line.substring(i + 1).trim()
        }
        val parts = requestLine.split(' ')
        val method = parts.getOrNull(0) ?: "GET"
        val path = parts.getOrNull(1)?.substringBefore('?') ?: "/"
        val out = socket.getOutputStream()
        val segs = path.trim('/').split('/')
        val item = if (segs.size == 2 && segs[0] == secret) items[segs[1].substringBeforeLast('.')] else null
        if (item == null) return status(out, 404, "Not Found")

        val resolver = context.contentResolver
        val total = runCatching { resolver.openAssetFileDescriptor(item.uri, "r")?.use { it.length } }.getOrNull()?.takeIf { it > 0 }
        val range = headers["range"]?.let { Regex("bytes=(\\d*)-(\\d*)").find(it) }
        var start = 0L
        var end = (total ?: 0L) - 1
        val partial = range != null && total != null
        if (partial) {
            val (a, b) = range!!.destructured
            if (a.isEmpty()) { start = (total!! - (b.toLongOrNull() ?: 0)).coerceAtLeast(0) } else {
                start = a.toLong()
                if (b.isNotEmpty()) end = minOf(b.toLong(), total!! - 1)
            }
            if (start >= total!!) {
                out.write("HTTP/1.1 416 Range Not Satisfiable\r\nContent-Range: bytes */$total\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
                return
            }
        }
        val length = if (total != null) end - start + 1 else null
        val head = buildString {
            append(if (partial) "HTTP/1.1 206 Partial Content\r\n" else "HTTP/1.1 200 OK\r\n")
            append("Content-Type: ${item.mime}\r\n")
            append("Accept-Ranges: bytes\r\n")
            if (length != null) append("Content-Length: $length\r\n")
            if (partial) append("Content-Range: bytes $start-$end/$total\r\n")
            append("transferMode.dlna.org: Streaming\r\n")
            append("Connection: close\r\n\r\n")
        }
        out.write(head.toByteArray())
        if (method == "HEAD") return out.flush()
        resolver.openInputStream(item.uri)?.use { input -> copy(input, out, start, length) }
        out.flush()
    }

    private fun copy(input: InputStream, out: OutputStream, skip: Long, length: Long?) {
        var toSkip = skip
        while (toSkip > 0) {
            val n = input.skip(toSkip)
            if (n <= 0) break
            toSkip -= n
        }
        val buf = ByteArray(64 * 1024)
        var left = length ?: Long.MAX_VALUE
        while (left > 0) {
            val n = input.read(buf, 0, minOf(buf.size.toLong(), left).toInt())
            if (n < 0) break
            out.write(buf, 0, n)
            left -= n
        }
    }

    private fun status(out: OutputStream, code: Int, text: String) {
        out.write("HTTP/1.1 $code $text\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
        out.flush()
    }
}
