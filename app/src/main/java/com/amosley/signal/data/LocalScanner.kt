package com.amosley.signal.data

import android.content.ContentUris
import android.content.Context
import android.media.AudioFormat
import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.Build
import android.provider.MediaStore
import com.amosley.signal.core.Movie
import com.amosley.signal.core.Origin
import com.amosley.signal.core.Track

/** Reads music and videos that are already on the phone from MediaStore. */
class LocalScanner(private val context: Context) {

    fun scanAudio(excludeDir: String?): List<Track> {
        val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val cols = mutableListOf(
            MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM, MediaStore.Audio.Media.ALBUM_ID, MediaStore.Audio.Media.YEAR,
            MediaStore.Audio.Media.TRACK, MediaStore.Audio.Media.DURATION, MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.SIZE, MediaStore.Audio.Media.DATE_ADDED, MediaStore.Audio.Media.DATE_MODIFIED,
            MediaStore.Audio.Media.DATA, MediaStore.Audio.Media.DISPLAY_NAME,
        )
        if (Build.VERSION.SDK_INT >= 30) cols += listOf(MediaStore.Audio.Media.ALBUM_ARTIST, MediaStore.Audio.Media.GENRE, MediaStore.Audio.Media.DISC_NUMBER)
        val out = mutableListOf<Track>()
        val cursor = runCatching {
            context.contentResolver.query(uri, cols.toTypedArray(), "${MediaStore.Audio.Media.IS_MUSIC} != 0", null, null)
        }.getOrNull() ?: return out
        cursor.use { c ->
            fun idx(name: String) = c.getColumnIndex(name)
            fun str(name: String) = idx(name).takeIf { it >= 0 }?.let { c.getString(it) }
            fun int(name: String) = idx(name).takeIf { it >= 0 && !c.isNull(it) }?.let { c.getInt(it) }
            fun long(name: String) = idx(name).takeIf { it >= 0 && !c.isNull(it) }?.let { c.getLong(it) }
            while (c.moveToNext()) {
                val id = c.getLong(idx(MediaStore.Audio.Media._ID))
                val path = str(MediaStore.Audio.Media.DATA)
                if (excludeDir != null && path != null && path.startsWith(excludeDir)) continue
                val name = str(MediaStore.Audio.Media.DISPLAY_NAME).orEmpty()
                val ext = name.substringAfterLast('.', "").uppercase()
                val mime = str(MediaStore.Audio.Media.MIME_TYPE).orEmpty()
                val rawTrack = int(MediaStore.Audio.Media.TRACK) ?: 0
                // MediaStore encodes disc*1000 + track in TRACK on older versions.
                val discFromTrack = if (rawTrack >= 1000) rawTrack / 1000 else null
                val trackNo = (rawTrack % 1000).takeIf { it > 0 }
                val artist = str(MediaStore.Audio.Media.ARTIST)?.takeUnless { it.isBlank() || it == "<unknown>" }
                val album = str(MediaStore.Audio.Media.ALBUM)?.takeUnless { it.isBlank() || it == "<unknown>" }
                val container = containerOf(ext, mime)
                val itemUri = ContentUris.withAppendedId(uri, id)
                val mtime = (long(MediaStore.Audio.Media.DATE_MODIFIED) ?: 0) * 1000
                val (bits, sampleRate) = audioFormat(itemUri, container, "$id:$mtime")
                out += Track(
                    id = "loc:$id",
                    origin = Origin.PHONE,
                    title = str(MediaStore.Audio.Media.TITLE)?.takeUnless { it.isBlank() } ?: name.substringBeforeLast('.'),
                    artist = artist,
                    album = album,
                    albumArtist = if (Build.VERSION.SDK_INT >= 30) str(MediaStore.Audio.Media.ALBUM_ARTIST)?.takeUnless { it.isBlank() } else null,
                    year = int(MediaStore.Audio.Media.YEAR)?.takeIf { it > 0 },
                    disc = (if (Build.VERSION.SDK_INT >= 30) str(MediaStore.Audio.Media.DISC_NUMBER)?.substringBefore('/')?.toIntOrNull() else null) ?: discFromTrack,
                    track = trackNo,
                    durationMs = long(MediaStore.Audio.Media.DURATION) ?: 0,
                    container = container,
                    codec = mime,
                    bitDepth = bits,
                    sampleRate = sampleRate,
                    size = long(MediaStore.Audio.Media.SIZE) ?: 0,
                    mtime = mtime,
                    addedAt = (long(MediaStore.Audio.Media.DATE_ADDED) ?: 0) * 1000,
                    genres = if (Build.VERSION.SDK_INT >= 30) str(MediaStore.Audio.Media.GENRE)?.split(';', ',', '/')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty() else emptyList(),
                    hasArt = true,
                    uri = itemUri.toString(),
                    path = path,
                )
            }
        }
        return out
    }

    fun scanVideos(): List<Movie> {
        val uri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val cols = arrayOf(
            MediaStore.Video.Media._ID, MediaStore.Video.Media.TITLE, MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.WIDTH, MediaStore.Video.Media.HEIGHT, MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DATE_ADDED, MediaStore.Video.Media.DISPLAY_NAME,
        )
        val out = mutableListOf<Movie>()
        val cursor = runCatching { context.contentResolver.query(uri, cols, null, null, null) }.getOrNull() ?: return out
        cursor.use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                val name = c.getString(7).orEmpty()
                out += Movie(
                    id = "locv:$id",
                    origin = Origin.PHONE,
                    title = c.getString(1)?.takeUnless { it.isBlank() } ?: name.substringBeforeLast('.'),
                    durationMs = c.getLong(2),
                    width = c.getInt(3),
                    height = c.getInt(4),
                    size = c.getLong(5),
                    addedAt = c.getLong(6) * 1000,
                    container = name.substringAfterLast('.', "").uppercase().ifEmpty { null },
                    matchedBy = "ON THIS PHONE",
                    uri = ContentUris.withAppendedId(uri, id).toString(),
                )
            }
        }
        return out
    }

    private val formatCache = HashMap<String, Pair<Int?, Int?>>()

    /** Bit depth / sample rate for lossless files (MediaStore doesn't expose them). */
    private fun audioFormat(uri: android.net.Uri, container: String, key: String): Pair<Int?, Int?> {
        if (container !in setOf("FLAC", "WAV", "AIFF", "M4A")) return null to null
        formatCache[key]?.let { return it }
        val result = runCatching {
            val ex = MediaExtractor()
            try {
                ex.setDataSource(context, uri, null)
                val f = (0 until ex.trackCount).map { ex.getTrackFormat(it) }
                    .firstOrNull { it.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true } ?: return@runCatching null to null
                val sr = if (f.containsKey(MediaFormat.KEY_SAMPLE_RATE)) f.getInteger(MediaFormat.KEY_SAMPLE_RATE) else null
                val bits = when {
                    f.containsKey("bits-per-sample") -> f.getInteger("bits-per-sample")
                    f.containsKey(MediaFormat.KEY_PCM_ENCODING) -> when (f.getInteger(MediaFormat.KEY_PCM_ENCODING)) {
                        AudioFormat.ENCODING_PCM_8BIT -> 8
                        AudioFormat.ENCODING_PCM_16BIT -> 16
                        AudioFormat.ENCODING_PCM_24BIT_PACKED -> 24
                        AudioFormat.ENCODING_PCM_32BIT, AudioFormat.ENCODING_PCM_FLOAT -> 32
                        else -> null
                    }
                    else -> null
                }
                bits to sr
            } finally {
                ex.release()
            }
        }.getOrDefault(null to null)
        formatCache[key] = result
        return result
    }

    private fun containerOf(ext: String, mime: String): String = when {
        ext.isNotEmpty() -> if (ext == "AIF") "AIFF" else ext
        mime.contains("flac") -> "FLAC"
        mime.contains("wav") -> "WAV"
        mime.contains("mpeg") -> "MP3"
        mime.contains("mp4") -> "M4A"
        else -> mime.substringAfter('/').uppercase()
    }
}
