package com.amosley.signal.core

/** What to show when the same song is both on the phone and on the PC. */
enum class DuplicateMode(val label: String) { PHONE("Phone copy"), PC("PC copy"), BOTH("Show both") }

object Duplicates {
    private fun key(t: Track): String? {
        val title = VideoNames.norm(t.title).ifEmpty { return null }
        return title + "|" + VideoNames.norm(t.albumArtist ?: t.artist) + "|" + VideoNames.norm(t.album)
    }

    private fun sameLength(a: Track, b: Track) =
        a.durationMs <= 0 || b.durationMs <= 0 || kotlin.math.abs(a.durationMs - b.durationMs) <= 3_000

    /**
     * Songs on both the phone and the PC (same title, artist and album, length within 3 s) are shown once:
     * the phone's copy or the PC's, per [mode]. Songs only in one place are untouched.
     */
    fun merge(tracks: List<Track>, mode: DuplicateMode): List<Track> {
        if (mode == DuplicateMode.BOTH) return tracks
        val phone = tracks.filter { it.origin == Origin.PHONE }.groupBy { key(it) }
        val drop = HashSet<String>()
        tracks.filter { it.origin == Origin.PC }.groupBy { key(it) }.forEach { (k, pcs) ->
            if (k == null) return@forEach
            val candidates = phone[k].orEmpty().toMutableList()
            for (pc in pcs) {
                val match = candidates.firstOrNull { sameLength(it, pc) } ?: continue
                candidates.remove(match)
                drop += if (mode == DuplicateMode.PHONE) pc.id else match.id
            }
        }
        return if (drop.isEmpty()) tracks else tracks.filter { it.id !in drop }
    }
}
