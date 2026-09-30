package com.amosley.signal.data

import android.content.Context
import android.net.Uri
import android.os.Build
import com.amosley.signal.core.Album
import com.amosley.signal.core.Catalog
import com.amosley.signal.core.Episode
import com.amosley.signal.core.Library
import com.amosley.signal.core.Lrc
import com.amosley.signal.core.Lyrics
import com.amosley.signal.core.Movie
import com.amosley.signal.core.MusicVideo
import com.amosley.signal.core.Origin
import com.amosley.signal.core.Show
import com.amosley.signal.core.Track
import com.amosley.signal.core.UserPlaylist
import com.amosley.signal.core.groupAlbums
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okio.BufferedSink
import okio.source
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit

data class PcStatus(
    val paired: Boolean = false,
    val reachable: Boolean = false,
    val checking: Boolean = false,
    val baseUrl: String? = null,
    val viaRemote: Boolean = false,
    val lastSync: Long = 0,
    val error: String? = null,
)

/** Everything the UI browses, already merged (phone + PC), overrides applied, and offline-filtered. */
data class LibraryView(
    val tracks: List<Track> = emptyList(),
    val albums: List<Album> = emptyList(),
    val videos: List<MusicVideo> = emptyList(),
    val movies: List<Movie> = emptyList(),
    val shows: List<Show> = emptyList(),
    val artists: List<Pair<String, Int>> = emptyList(),
)

@Serializable
data class TagConflict(val trackId: String, val title: String, val phoneArtist: String, val pcArtist: String)

@Serializable
data class TagJob(val trackId: String, val title: String, val artist: String, val state: String, val message: String? = null, val at: Long = 0)

@Serializable
private data class Meta(
    val lastSync: Long = 0,
    val watchedAt: Map<String, Long> = emptyMap(),
    val uploaded: Set<String> = emptySet(),
    val knownIds: Set<String> = emptySet(),
)

class Repository(val context: Context, val scope: CoroutineScope) {
    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
    val agent = AgentClient(http)
    val discovery = Discovery(context)
    private val scanner = LocalScanner(context)
    private val files = context.filesDir

    private val settingsStore = JsonStore(File(files, "settings.json"), Settings.serializer()) { Settings() }
    private val catalogStore = JsonStore(File(files, "catalog.json"), Catalog.serializer()) { Catalog() }
    private val librariesStore = JsonStore(File(files, "libraries.json"), ListSerializer(Library.serializer())) { emptyList() }
    private val overridesStore = JsonStore(File(files, "artist-overrides.json"), MapSerializer(String.serializer(), String.serializer())) { emptyMap() }
    private val playlistsStore = JsonStore(File(files, "playlists.json"), ListSerializer(UserPlaylist.serializer())) { emptyList() }
    private val conflictsStore = JsonStore(File(files, "conflicts.json"), ListSerializer(TagConflict.serializer())) { emptyList() }
    private val tagJobsStore = JsonStore(File(files, "tag-jobs.json"), ListSerializer(TagJob.serializer())) { emptyList() }
    private val metaStore = JsonStore(File(files, "meta.json"), Meta.serializer()) { Meta() }

    private val _settings = MutableStateFlow(settingsStore.load())
    val settings: StateFlow<Settings> = _settings
    private val _catalog = MutableStateFlow(catalogStore.load())
    val catalog: StateFlow<Catalog> = _catalog
    private val _libraries = MutableStateFlow(librariesStore.load())
    val libraries: StateFlow<List<Library>> = _libraries
    private val _overrides = MutableStateFlow(overridesStore.load())
    val overrides: StateFlow<Map<String, String>> = _overrides
    private val _playlists = MutableStateFlow(playlistsStore.load())
    val playlists: StateFlow<List<UserPlaylist>> = _playlists
    private val _conflicts = MutableStateFlow(conflictsStore.load())
    val conflicts: StateFlow<List<TagConflict>> = _conflicts
    private val _tagJobs = MutableStateFlow(tagJobsStore.load())
    val tagJobs: StateFlow<List<TagJob>> = _tagJobs
    private var meta = metaStore.load()
    private val _localTracks = MutableStateFlow<List<Track>>(emptyList())
    private val _localVideos = MutableStateFlow<List<Movie>>(emptyList())
    private val _status = MutableStateFlow(PcStatus(paired = _settings.value.pc != null, lastSync = meta.lastSync))
    val status: StateFlow<PcStatus> = _status
    private val _remoteActivity = MutableStateFlow<List<ActivityItem>>(emptyList())
    val remoteActivity: StateFlow<List<ActivityItem>> = _remoteActivity
    private val _uploads = MutableStateFlow<Map<String, String>>(emptyMap())
    val uploads: StateFlow<Map<String, String>> = _uploads
    private val _lyrics = MutableStateFlow<Map<String, Lyrics>>(emptyMap())
    /** Lyrics loaded so far (used for search "In lyrics"). */
    val lyricsLoaded: StateFlow<Map<String, Lyrics>> = _lyrics
    private val lyricsDir = File(files, "lyrics").apply { mkdirs() }
    private val refreshLock = Mutex()

    val downloads = Downloads(context, http, scope, ::downloadUrl) { _settings.value }

    init {
        // Warm the lyric index from disk for offline search.
        scope.launch(Dispatchers.IO) {
            val loaded = lyricsDir.listFiles().orEmpty().mapNotNull { f ->
                runCatching { f.nameWithoutExtension to SignalJson.decodeFromString(Lyrics.serializer(), f.readText()) }.getOrNull()
            }.toMap()
            _lyrics.update { loaded + it }
        }
    }

    val library: StateFlow<LibraryView> = combine(
        combine(_catalog, _localTracks, _localVideos) { c, lt, lv -> Triple(c, lt, lv) },
        _overrides, _settings, downloads.states,
    ) { (cat, localTracks, localVideos), overrides, settings, dl ->
        buildView(cat, localTracks, localVideos, overrides, settings, dl.keys)
    }.stateIn(scope, SharingStarted.Eagerly, LibraryView())

    private fun buildView(cat: Catalog, localTracks: List<Track>, localVideos: List<Movie>, overrides: Map<String, String>, s: Settings, dlKeys: Set<String>): LibraryView {
        val enabledLibs = s.libModes.keys
        fun libOk(id: String?) = id == null || enabledLibs.isEmpty() || id in enabledLibs
        fun avail(id: String, origin: Origin) = !s.offline || origin == Origin.PHONE || (id in dlKeys && downloads.isDownloaded(id))
        val phone = PhoneLibrary.split(localTracks, localVideos, s.phoneFolders)
        val remoteTracks = cat.tracks.filter { libOk(it.libraryId) }.map { it.copy(origin = Origin.PC, uri = it.id) }
        val tracks = (remoteTracks + phone.tracks)
            .map { t -> overrides[t.id]?.let { t.copy(artist = it) } ?: t }
            .filter { avail(it.id, it.origin) }
        val videos = cat.videos.filter { libOk(it.libraryId) && avail(it.id, Origin.PC) } + phone.musicVideos
        val movies = cat.movies.filter { libOk(it.libraryId) && avail(it.id, Origin.PC) } + phone.movies
        val shows = cat.shows.filter { libOk(it.libraryId) }.mapNotNull { show ->
            if (!s.offline) show else show.copy(seasons = show.seasons.map { se -> se.copy(episodes = se.episodes.filter { downloads.isDownloaded(it.id) }) }.filter { it.episodes.isNotEmpty() })
                .takeIf { it.seasons.isNotEmpty() }
        } + phone.shows
        val artists = tracks.mapNotNull { it.artist }.groupingBy { it }.eachCount().toList().sortedBy { it.first.lowercase() }
        return LibraryView(tracks, groupAlbums(tracks, videos), videos, movies, shows, artists)
    }

    fun updateSettings(f: (Settings) -> Settings) {
        _settings.update(f)
        settingsStore.save(_settings.value)
    }

    // ---- Phone library --------------------------------------------------------------------------

    /** Every phone folder that holds music or video, for the folder picker. */
    val phoneFolders: StateFlow<List<PhoneFolder>> = combine(_localTracks, _localVideos) { t, v -> PhoneLibrary.folders(t, v) }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    fun setPhoneFolder(path: String, type: PhoneFolderType?) {
        updateSettings { s ->
            s.copy(phoneFolders = if (type == null) s.phoneFolders - path else s.phoneFolders + (path to type), phoneFoldersChosen = true)
        }
    }

    fun rescanPhone() {
        scope.launch(Dispatchers.IO) {
            _localTracks.value = scanner.scanAudio(null)
            _localVideos.value = scanner.scanVideos()
        }
    }

    // ---- PC connection --------------------------------------------------------------------------

    val pc: PairedPc? get() = _settings.value.pc
    val pcName: String get() = pc?.name ?: "PC"

    /** Pick the endpoint that answers: LAN first, then the away-from-home address. */
    suspend fun connect(): String? {
        val p = pc ?: return null
        _status.update { it.copy(paired = true, checking = true) }
        val candidates = buildList {
            add(p.lanUrl to false)
            if (p.remoteUrl != null && _settings.value.remoteMode != RemoteMode.HOME_ONLY) add(p.remoteUrl to true)
        }
        for ((url, remote) in candidates) {
            val ok = runCatching { agent.info(url) }.isSuccess
            if (ok) {
                _status.update { it.copy(reachable = true, checking = false, baseUrl = url, viaRemote = remote, error = null) }
                return url
            }
        }
        _status.update { it.copy(reachable = false, checking = false, error = "Can't reach ${p.name}") }
        return null
    }

    fun refresh(force: Boolean = false) {
        scope.launch { refreshNow(force) }
    }

    suspend fun refreshNow(force: Boolean = false) = refreshLock.withLock {
        val p = pc ?: return@withLock
        if (!force && System.currentTimeMillis() - meta.lastSync < 30_000 && _status.value.reachable) return@withLock
        val base = connect() ?: return@withLock
        runCatching {
            val libs = agent.libraries(base, p.token)
            _libraries.value = libs
            librariesStore.save(libs)
            val cat = withContext(Dispatchers.IO) { agent.catalog(base, p.token) }
            _catalog.value = cat
            withContext(Dispatchers.IO) { catalogStore.save(cat) }
            meta = meta.copy(lastSync = System.currentTimeMillis())
            metaStore.save(meta)
            _status.update { it.copy(lastSync = meta.lastSync, error = null) }
            applyLibraryRules(cat)
            flushTagJobs()
            uploadPhoneOnly(cat)
            _remoteActivity.value = runCatching { agent.activity(base, p.token) }.getOrDefault(emptyList())
        }.onFailure { e ->
            _status.update { it.copy(error = e.message) }
        }
    }

    private var autoRefresh: kotlinx.coroutines.Job? = null

    fun startAutoRefresh() {
        if (autoRefresh?.isActive == true) return
        autoRefresh = scope.launch {
            while (true) {
                refreshNow()
                delay(10 * 60_000L)
            }
        }
    }

    suspend fun refreshActivity() {
        val p = pc ?: return
        val base = _status.value.baseUrl ?: return
        _remoteActivity.value = runCatching { agent.activity(base, p.token) }.getOrDefault(_remoteActivity.value)
    }

    fun unpair() {
        updateSettings { it.copy(pc = null, libModes = emptyMap()) }
        _catalog.value = Catalog()
        catalogStore.save(Catalog())
        _status.value = PcStatus()
    }

    fun savePairing(pc: PairedPc, libModes: Map<String, LibMode>, remote: RemoteMode) {
        updateSettings { it.copy(pc = pc, libModes = libModes, remoteMode = remote, skippedPairing = false, importReviewedAt = System.currentTimeMillis()) }
        meta = meta.copy(knownIds = emptySet())
        metaStore.save(meta)
        _status.value = PcStatus(paired = true)
    }

    fun setLibMode(libId: String, mode: LibMode) {
        updateSettings { it.copy(libModes = it.libModes + (libId to mode)) }
        scope.launch { applyLibraryRules(_catalog.value) }
    }

    // ---- URLs -----------------------------------------------------------------------------------

    private fun base(): String? = _status.value.baseUrl ?: pc?.lanUrl

    fun remoteUrl(path: String): String? {
        val p = pc ?: return null
        val b = base() ?: return null
        return AgentClient.mediaUrl(b, p.token, path)
    }

    fun streamUrl(id: String): String? = remoteUrl("/api/stream/$id")
    fun artUrl(id: String, kind: String = "cover"): String? = remoteUrl("/api/art/$id?kind=$kind")
    private fun downloadUrl(id: String, quality: String): String? = remoteUrl("/api/download/$id?quality=$quality")
    fun transcodedUrl(id: String): String? = downloadUrl(id, "16-44")

    /** Best uri to play: downloaded file → phone content uri → PC stream. */
    fun playUri(id: String, origin: Origin, phoneUri: String): Uri? {
        downloads.fileFor(id)?.let { return Uri.fromFile(it) }
        if (origin == Origin.PHONE) return Uri.parse(phoneUri)
        if (_settings.value.offline) return null
        return streamUrl(id)?.let(Uri::parse)
    }

    fun isAvailable(id: String, origin: Origin): Boolean =
        origin == Origin.PHONE || downloads.isDownloaded(id) || (!_settings.value.offline && _status.value.reachable)

    // ---- Downloads & library rules ------------------------------------------------------------

    fun download(track: Track) {
        if (track.origin != Origin.PC) return
        downloads.enqueue(DlRequest(track.id, "track", track.title, track.container ?: "bin", track.size))
        scope.launch { lyrics(track) }
    }
    fun download(video: MusicVideo) = downloads.enqueue(DlRequest(video.id, "video", video.title, video.container ?: "mp4", video.size))
    fun download(movie: Movie) { if (movie.origin == Origin.PC) downloads.enqueue(DlRequest(movie.id, "movie", movie.title, movie.container ?: "mkv", movie.size)) }
    fun download(ep: Episode, show: Show) = downloads.enqueue(DlRequest(ep.id, "episode", "${show.title} · S${ep.season}E${ep.episode}", ep.container ?: "mkv", ep.size))

    private fun applyLibraryRules(cat: Catalog) {
        val s = _settings.value
        val firstSync = meta.knownIds.isEmpty()
        val allIds = HashSet<String>()
        fun mode(lib: String?) = lib?.let { s.libModes[it] } ?: LibMode.STREAM
        fun isNew(id: String) = !firstSync && id !in meta.knownIds
        cat.tracks.forEach { t ->
            allIds += t.id
            val m = mode(t.libraryId)
            if (m == LibMode.DOWNLOAD_ALL || (m == LibMode.DOWNLOAD_NEW && isNew(t.id))) download(t.copy(origin = Origin.PC))
        }
        cat.videos.forEach { v ->
            allIds += v.id
            val m = mode(v.libraryId)
            if (m == LibMode.DOWNLOAD_ALL || (m == LibMode.DOWNLOAD_NEW && isNew(v.id))) download(v)
        }
        cat.movies.forEach { mv ->
            allIds += mv.id
            val m = mode(mv.libraryId)
            if (!mv.watched && (m == LibMode.DOWNLOAD_ALL || (m == LibMode.DOWNLOAD_NEW && isNew(mv.id)))) download(mv)
        }
        val now = System.currentTimeMillis()
        cat.shows.forEach { show ->
            val m = mode(show.libraryId)
            val eps = show.allEpisodes
            eps.forEach { allIds += it.id }
            when (m) {
                LibMode.DOWNLOAD_ALL -> eps.filter { !it.watched }.forEach { download(it, show) }
                LibMode.DOWNLOAD_NEW -> eps.filter { !it.watched }.take(s.keepEpisodes).forEach { download(it, show) }
                LibMode.STREAM -> Unit
            }
            if (s.autoRemoveWatched) {
                eps.filter { it.watched && downloads.isDownloaded(it.id) }.forEach { ep ->
                    val at = meta.watchedAt[ep.id] ?: now.also { meta = meta.copy(watchedAt = meta.watchedAt + (ep.id to it)) }
                    if (now - at > 24 * 3600_000L) downloads.remove(ep.id)
                }
            }
        }
        meta = meta.copy(knownIds = allIds)
        metaStore.save(meta)
    }

    // ---- Lyrics ---------------------------------------------------------------------------------

    fun cachedLyrics(id: String): Lyrics? = _lyrics.value[id]

    private val onlineLyrics = OnlineLyrics(http)
    /** Songs LRCLIB had nothing for, so we don't ask again every time they play (per app run). */
    private val onlineMisses = java.util.Collections.synchronizedSet(HashSet<String>())

    /**
     * Lyrics, in order: .lrc next to a phone song / the PC agent (.lrc or embedded tag) → cached copy →
     * LRCLIB online lookup by artist + title + duration. Results are cached on the phone for offline use.
     */
    suspend fun lyrics(track: Track): Lyrics? = withContext(Dispatchers.IO) {
        _lyrics.value[track.id]?.let { return@withContext it }
        val file = File(lyricsDir, safe(track.id) + ".json")
        val primary: Lyrics? = if (track.origin == Origin.PHONE) {
            localLrc(track)
        } else {
            val p = pc
            val b = _status.value.baseUrl
            if (p != null && b != null && !_settings.value.offline) runCatching {
                val api = agent.lyrics(b, p.token, track.id)
                Lyrics(api.lang, Lrc.merge(api.lines, api.translation.orEmpty()), api.synced, "pc")
            }.getOrNull() else null
        }?.takeIf { it.lines.isNotEmpty() }
        val result = primary
            ?: runCatching { SignalJson.decodeFromString(Lyrics.serializer(), file.readText()) }.getOrNull()
            ?: fetchOnline(track)
        if (result != null) {
            _lyrics.update { it + (track.id to result) }
            if (result !== primary || track.origin == Origin.PC) runCatching { file.writeText(SignalJson.encodeToString(Lyrics.serializer(), result)) }
        }
        result
    }

    private suspend fun fetchOnline(track: Track): Lyrics? {
        val s = _settings.value
        if (!s.onlineLyrics || s.offline || track.artist.isNullOrBlank() || track.id in onlineMisses) return null
        val found = runCatching { onlineLyrics.find(track) }.getOrNull()
        if (found == null) onlineMisses += track.id
        return found
    }

    /** Forget cached lyrics for a song and look again (e.g. after fixing its artist tag). */
    fun refreshLyrics(track: Track) {
        _lyrics.update { it - track.id }
        onlineMisses -= track.id
        File(lyricsDir, safe(track.id) + ".json").delete()
    }

    private fun localLrc(track: Track): Lyrics? {
        val path = track.path ?: return null
        val base = path.substringBeforeLast('.')
        val lrc = File("$base.lrc").takeIf { it.canRead() } ?: return null
        val parsed = Lrc.parse(lrc.readText())
        val tr = File("$base.en.lrc").takeIf { it.canRead() }?.let { Lrc.parse(it.readText()).lines }.orEmpty()
        return Lyrics(parsed.lang, Lrc.merge(parsed.lines, tr), parsed.synced, "phone")
    }

    private fun safe(id: String) = id.replace(Regex("[^A-Za-z0-9_.-]"), "_")

    // ---- Artist tags ----------------------------------------------------------------------------

    fun setArtist(track: Track, artist: String) {
        onlineMisses -= track.id
        _overrides.update { it + (track.id to artist) }
        overridesStore.save(_overrides.value)
        if (track.origin == Origin.PC) {
            _tagJobs.update { jobs -> jobs.filterNot { it.trackId == track.id } + TagJob(track.id, track.title, artist, "QUEUED", at = System.currentTimeMillis()) }
            tagJobsStore.save(_tagJobs.value)
            scope.launch { flushTagJobs() }
        }
    }

    private suspend fun flushTagJobs(force: Set<String> = emptySet()) {
        val p = pc ?: return
        val b = _status.value.baseUrl ?: return
        val jobs = _tagJobs.value.filter { it.state == "QUEUED" || it.state == "FAILED" }
        for (job in jobs) {
            val track = _catalog.value.tracks.firstOrNull { it.id == job.trackId }
            val res = runCatching { agent.writeArtist(b, p.token, job.trackId, job.artist, track?.mtime ?: 0, job.trackId in force) }
            val newState = when {
                res.isFailure -> job.copy(state = "FAILED", message = res.exceptionOrNull()?.message)
                res.getOrNull()?.ok == true -> job.copy(state = "DONE", message = null)
                res.getOrNull()?.error == "conflict" -> {
                    val pcArtist = res.getOrNull()?.pcArtist ?: ""
                    _conflicts.update { cs -> cs.filterNot { it.trackId == job.trackId } + TagConflict(job.trackId, job.title, job.artist, pcArtist) }
                    conflictsStore.save(_conflicts.value)
                    job.copy(state = "DECIDE")
                }
                else -> job.copy(state = "FAILED", message = res.getOrNull()?.error)
            }
            _tagJobs.update { all -> all.map { if (it.trackId == job.trackId) newState.copy(at = System.currentTimeMillis()) else it } }
        }
        _tagJobs.update { all -> all.filter { it.state != "DONE" || System.currentTimeMillis() - it.at < 24 * 3600_000L } }
        tagJobsStore.save(_tagJobs.value)
    }

    fun resolveConflict(trackId: String, keepPhone: Boolean) {
        val c = _conflicts.value.firstOrNull { it.trackId == trackId } ?: return
        _conflicts.update { cs -> cs.filterNot { it.trackId == trackId } }
        conflictsStore.save(_conflicts.value)
        if (keepPhone) {
            _tagJobs.update { all -> all.map { if (it.trackId == trackId) it.copy(state = "QUEUED") else it } }
            scope.launch { flushTagJobs(force = setOf(trackId)) }
        } else {
            _overrides.update { it + (trackId to c.pcArtist) }
            overridesStore.save(_overrides.value)
            _tagJobs.update { all -> all.map { if (it.trackId == trackId) it.copy(state = "DONE", message = "Kept PC version") else it } }
            tagJobsStore.save(_tagJobs.value)
        }
    }

    fun retryTagJob(trackId: String) {
        _tagJobs.update { all -> all.map { if (it.trackId == trackId) it.copy(state = "QUEUED") else it } }
        scope.launch { flushTagJobs() }
    }

    fun skipTagJob(trackId: String) {
        _tagJobs.update { all -> all.filterNot { it.trackId == trackId } }
        tagJobsStore.save(_tagJobs.value)
    }

    // ---- Uploads (phone-only songs → PC music folder) -------------------------------------------

    private suspend fun uploadPhoneOnly(cat: Catalog) {
        if (!_settings.value.uploadPhoneOnly) return
        val p = pc ?: return
        val b = _status.value.baseUrl ?: return
        val musicLib = _libraries.value.firstOrNull { it.type == "music" } ?: return
        val onPc = cat.tracks.map { "${it.title.lowercase()}|${it.artist?.lowercase()}" }.toSet()
        val todo = _localTracks.value.filter { PhoneLibrary.typeOf(it.folder, _settings.value.phoneFolders) == PhoneFolderType.MUSIC && it.id !in meta.uploaded && "${it.title.lowercase()}|${it.artist?.lowercase()}" !in onPc }
        for (t in todo) {
            _uploads.update { it + (t.id to "RUNNING") }
            val name = (t.path?.substringAfterLast('/') ?: "${t.title}.${t.container?.lowercase() ?: "bin"}")
            val body = object : RequestBody() {
                override fun contentType() = "application/octet-stream".toMediaType()
                override fun contentLength() = t.size
                override fun writeTo(sink: BufferedSink) {
                    context.contentResolver.openInputStream(Uri.parse(t.uri))?.source()?.use { sink.writeAll(it) }
                }
            }
            val ok = runCatching { agent.upload(b, p.token, musicLib.id, name, body) }.isSuccess
            _uploads.update { it + (t.id to if (ok) "DONE" else "FAILED") }
            if (ok) {
                meta = meta.copy(uploaded = meta.uploaded + t.id)
                metaStore.save(meta)
            }
        }
    }

    // ---- Progress / watched ---------------------------------------------------------------------

    fun reportProgress(id: String, positionMs: Long, durationMs: Long) {
        val p = pc ?: return
        val b = _status.value.baseUrl ?: return
        val watched = durationMs > 0 && positionMs > durationMs * 0.92
        if (watched) {
            meta = meta.copy(watchedAt = meta.watchedAt + (id to System.currentTimeMillis()))
            metaStore.save(meta)
        }
        if (!_settings.value.watchedToPlex) return
        scope.launch { runCatching { agent.progress(b, p.token, id, positionMs, watched) } }
    }

    // ---- Playlists ------------------------------------------------------------------------------

    fun createPlaylist(name: String, firstTrack: String? = null): UserPlaylist {
        val pl = UserPlaylist(UUID.randomUUID().toString(), name.trim().ifEmpty { "New playlist" }, listOfNotNull(firstTrack), System.currentTimeMillis())
        _playlists.update { listOf(pl) + it }
        playlistsStore.save(_playlists.value)
        return pl
    }

    fun addToPlaylist(playlistId: String, trackId: String) {
        _playlists.update { all -> all.map { if (it.id == playlistId && trackId !in it.trackIds) it.copy(trackIds = it.trackIds + trackId) else it } }
        playlistsStore.save(_playlists.value)
    }

    fun removeFromPlaylist(playlistId: String, trackId: String) {
        _playlists.update { all -> all.map { if (it.id == playlistId) it.copy(trackIds = it.trackIds - trackId) else it } }
        playlistsStore.save(_playlists.value)
    }

    fun deletePlaylist(playlistId: String) {
        _playlists.update { all -> all.filterNot { it.id == playlistId } }
        playlistsStore.save(_playlists.value)
    }

    fun addRecentSearch(q: String) {
        val t = q.trim()
        if (t.length < 2) return
        updateSettings { s -> s.copy(recentSearches = (listOf(t) + s.recentSearches.filterNot { it.equals(t, true) }).take(8)) }
    }

    companion object {
        val deviceName: String get() = "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}"
    }
}
