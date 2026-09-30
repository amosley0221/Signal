package com.amosley.signal.core

/** Minimal .lrc parser: `[mm:ss.xx]text`, several stamps per line, `[la:xx]` / `[lang:xx]` metadata. */
object Lrc {
    private val stamp = Regex("\\[(\\d{1,3}):(\\d{1,2})(?:[.:](\\d{1,3}))?]")
    private val meta = Regex("^\\[([a-zA-Z]+):(.*)]\\s*$")

    data class Parsed(val lang: String?, val lines: List<LyricLine>, val synced: Boolean)

    fun parse(text: String): Parsed {
        var lang: String? = null
        val out = mutableListOf<LyricLine>()
        val plain = mutableListOf<String>()
        text.lineSequence().forEach { raw ->
            val line = raw.trim().removePrefix("﻿")
            if (line.isEmpty()) return@forEach
            val stamps = stamp.findAll(line).toList()
            if (stamps.isEmpty()) {
                val m = meta.find(line)
                if (m != null) {
                    val key = m.groupValues[1].lowercase()
                    if (key == "la" || key == "lang" || key == "language") lang = m.groupValues[2].trim().lowercase().ifEmpty { null }
                } else {
                    plain += line
                }
                return@forEach
            }
            // Text after the last leading timestamp.
            val body = line.substring(stamps.last().range.last + 1).trim()
            stamps.forEach { s ->
                val min = s.groupValues[1].toInt()
                val sec = s.groupValues[2].toInt()
                val fracStr = s.groupValues[3]
                val frac = if (fracStr.isEmpty()) 0.0 else fracStr.toInt() / Math.pow(10.0, fracStr.length.toDouble())
                out += LyricLine(min * 60 + sec + frac, body)
            }
        }
        if (out.isEmpty() && plain.isNotEmpty()) {
            return Parsed(lang, plain.mapIndexed { i, s -> LyricLine(i * 4.0, s) }, synced = false)
        }
        return Parsed(lang, out.filter { it.text.isNotEmpty() }.sortedBy { it.t }, synced = true)
    }

    /** Attach translation lines to originals by nearest timestamp (or by index for unsynced). */
    fun merge(original: List<LyricLine>, translation: List<LyricLine>): List<LyricLine> {
        if (translation.isEmpty()) return original
        return original.mapIndexed { i, line ->
            val tr = translation.minByOrNull { kotlin.math.abs(it.t - line.t) }
                ?.takeIf { kotlin.math.abs(it.t - line.t) < 1.5 }
                ?: translation.getOrNull(i)
            line.copy(tr = tr?.text)
        }
    }

    /** Index of the active line at [posSec], or -1 before the first line. */
    fun activeIndex(lines: List<LyricLine>, posSec: Double): Int {
        var idx = -1
        for (i in lines.indices) {
            if (lines[i].t <= posSec + 0.05) idx = i else break
        }
        return idx
    }
}

object Fmt {
    fun dur(ms: Long): String {
        val s = (ms / 1000).coerceAtLeast(0)
        val h = s / 3600
        val m = (s % 3600) / 60
        val r = s % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, r) else "%d:%02d".format(m, r)
    }

    /** "2h 04m" / "48 min" */
    fun runtime(ms: Long): String {
        val totalMin = (ms / 60000).toInt()
        return if (totalMin >= 60) "%dh %02dm".format(totalMin / 60, totalMin % 60) else "$totalMin min"
    }

    fun bytes(b: Long): String = when {
        b >= 1L shl 30 -> "%.1f GB".format(b / (1L shl 30).toDouble())
        b >= 1L shl 20 -> "%.0f MB".format(b / (1L shl 20).toDouble())
        b >= 1L shl 10 -> "%.0f KB".format(b / (1L shl 10).toDouble())
        else -> "$b B"
    }

    fun ago(nowMs: Long, thenMs: Long): String {
        if (thenMs <= 0) return "never"
        val min = ((nowMs - thenMs) / 60000).coerceAtLeast(0)
        return when {
            min < 1 -> "just now"
            min < 60 -> "$min min ago"
            min < 60 * 24 -> "${min / 60} h ago"
            else -> "${min / (60 * 24)} d ago"
        }
    }
}

/** Helpers for lyrics pasted from Suno (or anywhere) into the lyrics editor. */
object PastedLyrics {
    private val sectionLine = Regex("^\\s*[\\[(][^\\])]*[\\])]\\s*$")

    /** Lines to sync: drops section labels like [Verse 1] / [Chorus] / (Instrumental), blank lines and stray spaces. */
    fun clean(text: String): List<String> =
        text.replace("\r", "").lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !sectionLine.matches(it) }

    /** True when the pasted text is already an .lrc with timestamps. */
    fun isLrc(text: String): Boolean = Regex("^\\s*\\[\\d{1,3}:\\d{1,2}([.:]\\d{1,3})?]", RegexOption.MULTILINE).containsMatchIn(text)

    /** Spread lines evenly over the song, as a starting point when the user doesn't tap-sync. */
    fun spread(lines: List<String>, durationSec: Double): List<LyricLine> {
        if (lines.isEmpty()) return emptyList()
        val start = (durationSec * 0.06).coerceAtMost(15.0)
        val step = ((durationSec - start) / lines.size).coerceAtLeast(1.0)
        return lines.mapIndexed { i, s -> LyricLine(start + i * step, s) }
    }

    fun toLrc(lines: List<LyricLine>, synced: Boolean): String = lines.joinToString("\n") { l ->
        if (!synced) l.text else {
            val cs = Math.round(l.t * 100).coerceAtLeast(0)
            String.format(java.util.Locale.ROOT, "[%02d:%02d.%02d]%s", cs / 6000, (cs / 100) % 60, cs % 100, l.text)
        }
    }
}
