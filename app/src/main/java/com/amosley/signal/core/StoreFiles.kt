package com.amosley.signal.core

import java.net.URLDecoder

/** Naming rules for music bought in the in-app store (Qobuz) and saved to the phone. */
object StoreFiles {
    /** Where purchases go on the phone (inside the shared Music folder). */
    const val ROOT = "Music/Signal"

    fun qobuzSearch(q: String) = "https://www.qobuz.com/us-en/search?q=" + java.net.URLEncoder.encode(q.trim(), "UTF-8")
    const val QOBUZ_HOME = "https://www.qobuz.com/us-en/shop"

    private val AUDIO = mapOf(
        "flac" to "audio/flac", "mp3" to "audio/mpeg", "m4a" to "audio/mp4", "alac" to "audio/mp4", "aac" to "audio/aac",
        "wav" to "audio/x-wav", "aif" to "audio/x-aiff", "aiff" to "audio/x-aiff", "ogg" to "audio/ogg", "opus" to "audio/ogg", "wma" to "audio/x-ms-wma",
    )

    fun audioMime(name: String): String? = AUDIO[name.substringAfterLast('.', "").lowercase()]
    fun isZip(name: String, mime: String?) = name.endsWith(".zip", true) || mime?.contains("zip", true) == true

    /** The file name from a Content-Disposition header (filename* first), else the last part of the URL. */
    fun fileName(url: String, contentDisposition: String?, fallback: String = "download"): String {
        val cd = contentDisposition.orEmpty()
        Regex("filename\\*\\s*=\\s*([^']*)'[^']*'([^;]+)", RegexOption.IGNORE_CASE).find(cd)?.let { m ->
            val cs = m.groupValues[1].ifBlank { "UTF-8" }
            return clean(runCatching { URLDecoder.decode(m.groupValues[2].trim().trim('"'), cs) }.getOrDefault(m.groupValues[2]))
        }
        Regex("filename\\s*=\\s*\"([^\"]+)\"|filename\\s*=\\s*([^;]+)", RegexOption.IGNORE_CASE).find(cd)?.let { m ->
            return clean(m.groupValues[1].ifEmpty { m.groupValues[2] }.trim())
        }
        val last = url.substringBefore('?').substringAfterLast('/')
        return clean(runCatching { URLDecoder.decode(last, "UTF-8") }.getOrDefault(last)).ifEmpty { fallback }
    }

    /** One safe path segment: no slashes, reserved characters or leading dots. */
    fun clean(s: String): String = s.replace(Regex("[\\\\/:*?\"<>|\\u0000-\\u001f]"), "_").trim().trimStart('.').trim().take(120)

    /** A zip entry ("Future - Monster/01 - Radical.flac") as (folder under [ROOT], file name); null for folders and non-audio. */
    fun zipTarget(entry: String, zipName: String): Pair<String, String>? {
        val parts = entry.replace('\\', '/').split('/').map(::clean).filter { it.isNotEmpty() && it != ".." }
        val name = parts.lastOrNull() ?: return null
        if (entry.endsWith("/") || audioMime(name) == null) return null
        val dirs = parts.dropLast(1).ifEmpty { listOf(clean(zipName.substringBeforeLast('.'))).filter { it.isNotEmpty() } }
        return "$ROOT/" + (dirs + "").joinToString("/") to name
    }
}
