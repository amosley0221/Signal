package com.amosley.signal.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import java.text.Normalizer

/**
 * Playlists brought in from Apple Music: read from a shared playlist page (music.apple.com) or from a playlist
 * exported by the Apple Music / iTunes app (.txt, .xml or .m3u), then matched to songs in the library.
 */
object PlaylistImport {
    data class Entry(val title: String, val artist: String? = null, val album: String? = null, val durationMs: Long? = null)

    /** [declaredCount]: how many songs the source says the playlist has (a shared page may list fewer). */
    data class Parsed(val name: String?, val entries: List<Entry>, val declaredCount: Int? = null)

    data class Match(val entry: Entry, val trackId: String?)

    // ---- Apple Music shared page ----------------------------------------------------------------

    private val json = Json { isLenient = true; ignoreUnknownKeys = true }
    private val SCRIPT = Regex("<script([^>]*)>(.*?)</script>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))

    fun isAppleMusicLink(s: String) = Regex("https?://(\\w+\\.)?music\\.apple\\.com/\\S+", RegexOption.IGNORE_CASE).containsMatchIn(s)
    fun appleMusicLink(s: String): String? = Regex("https?://(\\w+\\.)?music\\.apple\\.com/\\S+", RegexOption.IGNORE_CASE).find(s)?.value?.trimEnd('.', ',', ')')

    fun fromApplePage(html: String): Parsed {
        var name: String? = null
        var declared: Int? = null
        val ld = mutableListOf<Entry>()
        val server = mutableListOf<Entry>()
        for (m in SCRIPT.findAll(html)) {
            val attrs = m.groupValues[1]
            val isLd = attrs.contains("ld+json", true)
            val isData = attrs.contains("serialized-server-data", true) || attrs.contains("application/json", true)
            if (!isLd && !isData) continue
            val root = runCatching { json.parseToJsonElement(m.groupValues[2].trim()) }.getOrNull() ?: continue
            if (isLd) {
                walk(root) { o ->
                    when (o.str("@type")) {
                        "MusicPlaylist" -> { if (name == null) name = o.str("name"); o.num("numTracks")?.let { declared = it.toInt() } }
                        "MusicRecording" -> o.str("name")?.let { t -> ld += Entry(t, artistOf(o["byArtist"]), (o["inAlbum"] as? JsonObject)?.str("name"), isoDuration(o.str("duration"))) }
                    }
                }
            } else {
                walk(root) { o -> songLockup(o)?.let { server += it } }
            }
        }
        if (name == null) name = Regex("<meta[^>]+property=\"og:title\"[^>]+content=\"([^\"]*)\"", RegexOption.IGNORE_CASE).find(html)?.groupValues?.get(1)?.let(::unescape)
        name = name?.replace(Regex("\\s+(-|on)\\s+Apple Music$", RegexOption.IGNORE_CASE), "")?.trim()
        val entries = if (server.size >= ld.size) server else ld
        return Parsed(name, entries, declared)
    }

    /** A song row in Apple's page data: has a title and an artist, and is marked as a song or has a length. */
    private fun songLockup(o: JsonObject): Entry? {
        val title = o.str("title") ?: return null
        val artist = o.str("artistName") ?: ((o["subtitleLinks"] as? JsonArray)?.firstOrNull() as? JsonObject)?.str("title") ?: return null
        val kind = (o["contentDescriptor"] as? JsonObject)?.str("kind")
        val dur = o.num("duration")
        val isSong = kind == "song" || (kind == null && dur != null && dur > 0)
        if (!isSong) return null
        val album = o.str("albumName") ?: ((o["tertiaryLinks"] as? JsonArray)?.firstOrNull() as? JsonObject)?.str("title")
        return Entry(title, artist, album, dur?.takeIf { it > 0 })
    }

    private fun artistOf(e: JsonElement?): String? = when (e) {
        is JsonObject -> e.str("name")
        is JsonArray -> e.mapNotNull { (it as? JsonObject)?.str("name") }.joinToString(", ").ifEmpty { null }
        is JsonPrimitive -> e.contentOrNull
        else -> null
    }

    private fun walk(e: JsonElement, f: (JsonObject) -> Unit) {
        when (e) {
            is JsonObject -> { f(e); e.values.forEach { walk(it, f) } }
            is JsonArray -> e.forEach { walk(it, f) }
            else -> Unit
        }
    }

    private fun JsonObject.str(k: String) = (this[k] as? JsonPrimitive)?.takeIf { it.isString }?.content
    private fun JsonObject.num(k: String) = (this[k] as? JsonPrimitive)?.let { it.longOrNull ?: it.content.toDoubleOrNull()?.toLong() }

    /** "PT3M25S" → 205000. */
    fun isoDuration(s: String?): Long? {
        val m = Regex("^P(?:T)?(?:(\\d+)H)?(?:(\\d+)M)?(?:(\\d+(?:\\.\\d+)?)S)?$").find(s ?: return null) ?: return null
        val (h, mi, se) = m.destructured
        val ms = ((h.toLongOrNull() ?: 0) * 3600 + (mi.toLongOrNull() ?: 0) * 60) * 1000 + ((se.toDoubleOrNull() ?: 0.0) * 1000).toLong()
        return ms.takeIf { it > 0 }
    }

    private fun unescape(s: String) = s.replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'").replace("&#x27;", "'").replace("&lt;", "<").replace("&gt;", ">")

    // ---- Exported files -------------------------------------------------------------------------

    /** Text of an exported file: Apple Music / iTunes write .txt as UTF-16 with a byte-order mark. */
    fun decode(bytes: ByteArray): String = when {
        bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte() -> String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE)
        bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte() -> String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
        bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte() -> String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
        else -> String(bytes, Charsets.UTF_8)
    }

    /** A playlist exported from Apple Music / iTunes (.txt, .xml) or any .m3u/.m3u8. [fileName] gives the name when the file has none. */
    fun fromFile(text: String, fileName: String? = null): Parsed {
        val base = fileName?.substringAfterLast('/')?.substringBeforeLast('.')
        val t = text.trimStart('\uFEFF')
        val p = when {
            t.contains("<plist") -> fromPlist(t)
            t.startsWith("#EXTM3U") || t.lineSequence().any { it.startsWith("#EXTINF") } -> fromM3u(t)
            else -> fromTxt(t)
        }
        return if (p.name.isNullOrBlank()) p.copy(name = base) else p
    }

    private fun fromTxt(t: String): Parsed {
        val lines = t.split(Regex("\r\n|\r|\n")).filter { it.isNotBlank() }
        if (lines.isEmpty()) return Parsed(null, emptyList())
        val head = lines.first().split('\t').map { it.trim().lowercase() }
        fun col(vararg names: String) = head.indexOfFirst { it in names }
        val ti = col("name", "title", "nom", "nombre", "titel").takeIf { it >= 0 } ?: return Parsed(null, emptyList())
        val ai = col("artist", "artiste", "artista", "interpret")
        val al = col("album")
        val tm = col("time", "durée", "duración", "dauer")
        val entries = lines.drop(1).mapNotNull { line ->
            val c = line.split('\t')
            val title = c.getOrNull(ti)?.trim().orEmpty()
            if (title.isEmpty()) null
            else Entry(title, c.getOrNull(ai)?.trim()?.ifEmpty { null }, c.getOrNull(al)?.trim()?.ifEmpty { null }, c.getOrNull(tm)?.let(::clockMs))
        }
        return Parsed(null, entries)
    }

    /** "225" (seconds) or "3:45" → ms. */
    private fun clockMs(s: String): Long? {
        val p = s.trim().split(':').map { it.toLongOrNull() ?: return null }
        return p.fold(0L) { acc, v -> acc * 60 + v }.takeIf { it > 0 }?.times(1000)
    }

    private fun fromM3u(t: String): Parsed {
        val entries = mutableListOf<Entry>()
        var pending: Entry? = null
        for (raw in t.split(Regex("\r\n|\r|\n"))) {
            val line = raw.trim()
            if (line.startsWith("#EXTINF", true)) {
                val info = line.substringAfter(':')
                val secs = info.substringBefore(',').trim().toLongOrNull()
                val label = info.substringAfter(',', "").trim()
                val dash = label.indexOf(" - ")
                pending = if (dash > 0) Entry(label.substring(dash + 3).trim(), label.substring(0, dash).trim(), durationMs = secs?.takeIf { it > 0 }?.times(1000))
                else Entry(label, durationMs = secs?.takeIf { it > 0 }?.times(1000))
            } else if (line.isNotEmpty() && !line.startsWith("#")) {
                entries += pending?.takeIf { it.title.isNotEmpty() } ?: Entry(line.substringAfterLast('/').substringAfterLast('\\').substringBeforeLast('.').replace(Regex("^\\d+[ .-]+"), ""))
                pending = null
            }
        }
        return Parsed(null, entries)
    }

    // A minimal plist reader: dict / array / string / integer / real / date / true / false / data.
    private val TOKEN = Regex("<(/?)(dict|array|key|string|integer|real|date|data|true|false)(\\s*/)?>", RegexOption.IGNORE_CASE)

    private fun fromPlist(t: String): Parsed {
        val root = PlistReader(t).read() as? Map<*, *> ?: return Parsed(null, emptyList())
        val tracks = root["Tracks"] as? Map<*, *> ?: emptyMap<String, Any?>()
        val lists = (root["Playlists"] as? List<*>).orEmpty().filterIsInstance<Map<*, *>>()
        val pl = lists.firstOrNull { it["Master"] != true && it["Distinguished Kind"] == null && (it["Playlist Items"] as? List<*>).orEmpty().isNotEmpty() }
            ?: lists.firstOrNull()
        val ids = (pl?.get("Playlist Items") as? List<*>).orEmpty().mapNotNull { (it as? Map<*, *>)?.get("Track ID")?.toString() }
        val entries = ids.mapNotNull { id ->
            val tr = tracks[id] as? Map<*, *> ?: return@mapNotNull null
            val title = tr["Name"] as? String ?: return@mapNotNull null
            Entry(title, tr["Artist"] as? String, tr["Album"] as? String, (tr["Total Time"] as? Long)?.takeIf { it > 0 })
        }
        return Parsed(pl?.get("Name") as? String, entries)
    }

    private class PlistReader(private val s: String) {
        private val tokens = TOKEN.findAll(s).toList()
        private var i = 0

        fun read(): Any? {
            while (i < tokens.size) {
                val v = value()
                if (v != null) return v
            }
            return null
        }

        private fun text(open: MatchResult): String {
            val close = tokens.getOrNull(i) ?: return ""
            i++
            return unescape(s.substring(open.range.last + 1, close.range.first))
        }

        private fun value(): Any? {
            val tok = tokens.getOrNull(i++) ?: return null
            val closing = tok.groupValues[1] == "/"
            val tag = tok.groupValues[2].lowercase()
            val selfClosing = tok.groupValues[3].isNotEmpty()
            if (closing) return null
            return when (tag) {
                "true" -> true
                "false" -> false
                "dict" -> {
                    if (selfClosing) return emptyMap<String, Any?>()
                    val m = LinkedHashMap<String, Any?>()
                    while (i < tokens.size) {
                        val k = tokens[i]
                        if (k.groupValues[1] == "/" && k.groupValues[2].equals("dict", true)) { i++; break }
                        if (!k.groupValues[2].equals("key", true)) { i++; continue }
                        i++
                        val key = text(k)
                        m[key] = value()
                    }
                    m
                }
                "array" -> {
                    if (selfClosing) return emptyList<Any?>()
                    val l = mutableListOf<Any?>()
                    while (i < tokens.size) {
                        val k = tokens[i]
                        if (k.groupValues[1] == "/" && k.groupValues[2].equals("array", true)) { i++; break }
                        l += value()
                    }
                    l
                }
                "integer" -> if (selfClosing) 0L else text(tok).trim().toLongOrNull()
                "real" -> if (selfClosing) 0.0 else text(tok).trim().toDoubleOrNull()
                else -> if (selfClosing) "" else text(tok)
            }
        }
    }

    // ---- Matching -------------------------------------------------------------------------------

    private val EXTRA = Regex("\\s*[(\\[][^)\\]]*\\b(feat|ft|featuring|with|explicit|clean|remaster(ed)?|album version|single version|deluxe|bonus)\\b[^)\\]]*[)\\]]", RegexOption.IGNORE_CASE)
    private val DASH_EXTRA = Regex("\\s+-\\s+[^-]*\\b(remaster(ed)?|version|edit|mono|stereo)\\b.*$", RegexOption.IGNORE_CASE)
    private val FEAT_TAIL = Regex("\\s+(feat\\.?|ft\\.?|featuring)\\s+.*$", RegexOption.IGNORE_CASE)

    private fun fold(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase()
            .replace("&", " and ").replace(Regex("[’'`]"), "").replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()

    /** Title for comparing: no "(feat. …)", "(Remastered)", "- 2011 Remaster" and the like, no punctuation or accents. */
    fun normTitle(s: String): String = fold(s.replace(EXTRA, "").replace(DASH_EXTRA, "").replace(FEAT_TAIL, ""))

    /** Title with every bracketed part removed, as a second try. */
    private fun coreTitle(s: String): String = fold(s.replace(Regex("\\s*[(\\[][^)\\]]*[)\\]]"), "").replace(DASH_EXTRA, "").replace(FEAT_TAIL, ""))

    /** Each credited artist on its own: "Drake, 21 Savage & Future feat. X" → drake, 21 savage, future, x. */
    fun artistNames(s: String?): Set<String> =
        s.orEmpty().split(Regex("\\s*(,|&|;|/|\\bx\\b|\\bfeat\\.?|\\bft\\.?|\\bfeaturing\\b|\\bwith\\b|\\band\\b)\\s*", RegexOption.IGNORE_CASE))
            .map { fold(it) }.filter { it.isNotEmpty() }.toSet()

    private fun artistsMatch(a: Set<String>, b: Set<String>) = a.any { x -> b.any { y -> x == y || (x.length >= 4 && y.length >= 4 && (x.contains(y) || y.contains(x))) } }

    fun match(entries: List<Entry>, tracks: List<Track>): List<Match> {
        val byTitle = tracks.groupBy { normTitle(it.title) }
        val byCore = tracks.groupBy { coreTitle(it.title) }
        return entries.map { e ->
            val cands = byTitle[normTitle(e.title)].orEmpty().ifEmpty { byCore[coreTitle(e.title)].orEmpty() }
            val ea = artistNames(e.artist) + artistNames(Regex("\\((?:feat|ft|featuring|with)\\.?\\s+([^)]*)\\)", RegexOption.IGNORE_CASE).find(e.title)?.groupValues?.get(1))
            val best = cands.mapNotNull { t ->
                val ta = artistNames(t.artist) + artistNames(t.albumArtist) + artistNames(Regex("\\((?:feat|ft|featuring|with)\\.?\\s+([^)]*)\\)", RegexOption.IGNORE_CASE).find(t.title)?.groupValues?.get(1))
                val artistOk = ea.isEmpty() || ta.isEmpty() || artistsMatch(ea, ta)
                if (!artistOk) return@mapNotNull null
                var score = 0
                if (ea.isNotEmpty() && ta.isNotEmpty()) score += 4
                if (e.album != null && t.album != null && normTitle(e.album) == normTitle(t.album)) score += 2
                val d = e.durationMs
                if (d != null && t.durationMs > 0) score += if (kotlin.math.abs(d - t.durationMs) <= 3000) 2 else if (kotlin.math.abs(d - t.durationMs) > 30000) -2 else 0
                if (normTitle(e.title) == normTitle(t.title)) score += 1
                t to score
            }.maxByOrNull { it.second }?.first
            Match(e, best?.id)
        }
    }
}
