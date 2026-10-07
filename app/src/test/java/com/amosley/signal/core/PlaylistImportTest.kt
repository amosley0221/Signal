package com.amosley.signal.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaylistImportTest {
    private val lib = listOf(
        Track(id = "a", title = "Get Up On It (feat. Kut Klose)", artist = "Keith Sweat", album = "Get Up On It", durationMs = 303_000),
        Track(id = "b", title = "redrum", artist = "21 Savage", album = "american dream", durationMs = 270_000),
        Track(id = "c", title = "Intro", artist = "Rick Ross", durationMs = 120_000),
        Track(id = "d", title = "Intro", artist = "Jhené Aiko", durationMs = 90_000),
        Track(id = "e", title = "Bohemian Rhapsody", artist = "Queen", album = "A Night at the Opera", durationMs = 354_000),
    )

    @Test fun matchesAcrossFeaturesRemastersAndAccents() {
        val r = PlaylistImport.match(listOf(
            PlaylistImport.Entry("Get Up On It", "Keith Sweat & Kut Klose"),
            PlaylistImport.Entry("Redrum", "21 Savage"),
            PlaylistImport.Entry("Intro", "Jhene Aiko"),
            PlaylistImport.Entry("Bohemian Rhapsody - Remastered 2011", "Queen"),
            PlaylistImport.Entry("Intro", "Drake"),
            PlaylistImport.Entry("Not In Library", "Nobody"),
        ), lib)
        assertEquals(listOf("a", "b", "d", "e", null, null), r.map { it.trackId })
    }

    @Test fun readsSharedPageData() {
        val html = """
            <html><head><meta property="og:title" content="Late Night R&amp;B on Apple Music">
            <script type="application/ld+json">{"@context":"http://schema.org","@type":"MusicPlaylist","name":"Late Night R&B","numTracks":120,
              "track":[{"@type":"MusicRecording","name":"Redrum","duration":"PT4M30S","byArtist":[{"@type":"MusicGroup","name":"21 Savage"}]}]}</script>
            <script type="application/json" id="serialized-server-data">[{"data":{"sections":[
              {"items":[{"title":"Late Night R&B","artistName":"Apple Music R&B"}]},
              {"itemKind":"trackLockup","items":[
                {"title":"Redrum","artistName":"21 Savage","duration":270000,"contentDescriptor":{"kind":"song"},"tertiaryLinks":[{"title":"american dream"}]},
                {"title":"Get Up On It","artistName":"Keith Sweat","duration":303000,"contentDescriptor":{"kind":"song"}}]}]}}]</script>
            </head></html>
        """.trimIndent()
        val p = PlaylistImport.fromApplePage(html)
        assertEquals("Late Night R&B", p.name)
        assertEquals(120, p.declaredCount)
        assertEquals(listOf("Redrum", "Get Up On It"), p.entries.map { it.title })
        assertEquals("american dream", p.entries[0].album)
        assertEquals(270_000L, PlaylistImport.isoDuration("PT4M30S"))
    }

    @Test fun readsExportedText() {
        val txt = "Name\tArtist\tComposer\tAlbum\tGrouping\tTime\r\nRedrum\t21 Savage\t\tamerican dream\t\t270\r\nIntro\tRick Ross\t\t\t\t2:00\r\n"
        val bytes = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + txt.toByteArray(Charsets.UTF_16LE)
        val p = PlaylistImport.fromFile(PlaylistImport.decode(bytes), "Gym.txt")
        assertEquals("Gym", p.name)
        assertEquals(listOf(PlaylistImport.Entry("Redrum", "21 Savage", "american dream", 270_000), PlaylistImport.Entry("Intro", "Rick Ross", null, 120_000)), p.entries)
    }

    @Test fun readsExportedXml() {
        val xml = """<?xml version="1.0" encoding="UTF-8"?>
            <plist version="1.0"><dict>
              <key>Tracks</key><dict>
                <key>101</key><dict><key>Track ID</key><integer>101</integer><key>Name</key><string>Redrum</string><key>Artist</key><string>21 Savage</string><key>Total Time</key><integer>270000</integer><key>Explicit</key><true/></dict>
                <key>102</key><dict><key>Track ID</key><integer>102</integer><key>Name</key><string>Simon &amp; Garfunkel Song</string><key>Artist</key><string>Simon &amp; Garfunkel</string></dict>
              </dict>
              <key>Playlists</key><array>
                <dict><key>Name</key><string>Road Trip</string><key>Playlist Items</key><array>
                  <dict><key>Track ID</key><integer>102</integer></dict><dict><key>Track ID</key><integer>101</integer></dict>
                </array></dict>
              </array>
            </dict></plist>"""
        val p = PlaylistImport.fromFile(xml, "x.xml")
        assertEquals("Road Trip", p.name)
        assertEquals(listOf("Simon & Garfunkel Song", "Redrum"), p.entries.map { it.title })
        assertEquals(270_000L, p.entries[1].durationMs)
    }

    @Test fun readsM3u() {
        val p = PlaylistImport.fromFile("#EXTM3U\n#EXTINF:270,21 Savage - Redrum\nC:\\Music\\redrum.flac\nD:/Music/01 - Intro.mp3\n", "mix.m3u8")
        assertEquals(listOf(PlaylistImport.Entry("Redrum", "21 Savage", durationMs = 270_000), PlaylistImport.Entry("Intro")), p.entries)
        assertEquals("mix", p.name)
        assertNull(PlaylistImport.appleMusicLink("no link here"))
        assertEquals("https://music.apple.com/us/playlist/gym/pl.u-abc", PlaylistImport.appleMusicLink("Check this out https://music.apple.com/us/playlist/gym/pl.u-abc"))
    }
}
