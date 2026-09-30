package com.amosley.signal.core

/**
 * Turns release-style file names ("Minions.and.Monsters.2026.2160p.iT.WEB-DL.DDP5.1.Atmos.DV.HDR.H.265-BYNDR")
 * into a title and year, and matches phone videos to the PC/Plex library by normalised title.
 */
object VideoNames {
    private val yearRe = Regex("(?<![0-9])(19[0-9]{2}|20[0-9]{2})(?![0-9])")
    private val junkRe = Regex(
        "(?i)\\b(2160p|1080p|720p|576p|480p|4k|uhd|hdr10\\+?|hdr|dv|dovi|web[- ]?dl|webrip|web|bluray|blu[- ]?ray|brrip|bdrip|remux|" +
            "dvdrip|hdtv|x26[45]|h ?26[45]|hevc|avc|ddp?[0-9. ]*|dd\\+?|aac[0-9. ]*|atmos|truehd|dts(-hd)?|10bit|imax|proper|repack|extended|unrated|" +
            "amzn|nf|dsnp|hmax|atvp|it|ma)\\b",
    )
    private val episodeRe = Regex("(?i)\\bS(\\d{1,2})\\s*E(\\d{1,3})\\b|\\b(\\d{1,2})x(\\d{2})\\b")

    data class Parsed(val title: String, val year: Int?)

    /** Title + year from a file name (extension optional). */
    fun parse(fileName: String): Parsed {
        var s = fileName.substringBeforeLast('.', fileName).let { if (it.length < fileName.length - 5) fileName else it }
        s = s.replace(Regex("[._]+"), " ").replace(Regex("\\s+"), " ").trim()
        // "Title (2026)" / "Title [2026]"
        val bracket = Regex("^(.*?)[\\s]*[(\\[](19\\d{2}|20\\d{2})[)\\]]").find(s)
        if (bracket != null && bracket.groupValues[1].isNotBlank()) return Parsed(tidy(bracket.groupValues[1]), bracket.groupValues[2].toInt())
        // Cut at the last year that isn't the very start of the name ("2012" the movie), else at the first junk token.
        val years = yearRe.findAll(s).filter { it.range.first > 0 }.toList()
        val y = years.lastOrNull()
        if (y != null) return Parsed(tidy(s.substring(0, y.range.first)), y.value.toInt())
        val junk = junkRe.find(s)?.takeIf { it.range.first > 0 }
        return Parsed(tidy(if (junk != null) s.substring(0, junk.range.first) else s), null)
    }

    /** Show name + season/episode for TV files, or null. */
    fun parseEpisode(fileName: String): Triple<String, Int, Int>? {
        val s = fileName.replace(Regex("[._]+"), " ")
        val m = episodeRe.find(s) ?: return null
        val season = (m.groupValues[1].ifEmpty { m.groupValues[3] }).toInt()
        val episode = (m.groupValues[2].ifEmpty { m.groupValues[4] }).toInt()
        val show = parse(s.substring(0, m.range.first)).title
        return Triple(show, season, episode)
    }

    private fun tidy(t: String) = t.trim(' ', '-', '_', '.', '(', '[').replace(Regex("\\s+"), " ")

    /** For matching: lower-case, "&" = "and", no punctuation or leading "the". */
    fun norm(t: String?): String =
        (t ?: "").lowercase().replace("&", " and ").replace(Regex("[^a-z0-9]+"), " ").trim().removePrefix("the ").replace(" ", "")

    /** Best PC movie for a phone video: same normalised title, and same year (±1) when both are known. */
    fun matchMovie(parsed: Parsed, candidates: List<Movie>): Movie? {
        val key = norm(parsed.title)
        if (key.isEmpty()) return null
        return candidates.filter { norm(it.title) == key }
            .firstOrNull { parsed.year == null || it.year == null || kotlin.math.abs(it.year - parsed.year) <= 1 }
    }
}
