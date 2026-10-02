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
import com.amosley.signal.core.WatchProgress
import com.amosley.signal.core.Watching
import com.amosley.signal.core.groupAlbums
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.SetSerializer
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
    /** Track id → main artist (features folded in), see ArtistNames. */
    val artistOf: Map<String, String> = emptyMap(),
    /** Plex's Continue Watching row (item ids), or null without Plex. */
    val plexContinue: Set<String>? = null,
    /** Movies and episodes watched in Signal (they drive Continue Watching and Up Next). */
    val watchedInSignal: Set<String> = emptySet(),
) {
    /** A movie by id, including the extra versions folded under another poster. */
    fun movie(id: String): Movie? = movies.firstOrNull { it.id == id } ?: movies.firstNotNullOfOrNull { m -> m.versions.firstOrNull { it.id == id } }

    fun artistTracks(name: String): List<Track> = tracks.filter { (artistOf[it.id] ?: it.artist) == name || it.artist == name }
}

@Serializable
data class TagConflict(val trackId: String, val title: String, val phoneArtist: String, val pcArtist: String)

@Serializable
data class TagJob(
    val trackId: String, val title: String, val artist: String, val state: String, val message: String? = null, val at: Long = 0,
    /** All fields to write; empty = artist only (older jobs). */
    val fields: Map<String, String> = emptyMap(),
)

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
    private val editsStore = JsonStore(File(files, "track-edits.json"), MapSerializer(String.serializer(), TrackEdit.serializer())) { emptyMap() }
    private val favoritesStore = JsonStore(File(files, "favorites.json"), SetSerializer(String.serializer())) { emptySet() }
    private val progressStore = JsonStore(File(files, "watch-progress.json"), MapSerializer(String.serializer(), WatchProgress.serializer())) { emptyMap() }
    private val movieInfoStore = JsonStore(File(files, "movie-info.json"), MapSerializer(String.serializer(), MovieInfo.serializer())) { emptyMap() }
    private val showInfoStore = JsonStore(File(files, "show-info.json"), MapSerializer(String.serializer(), ShowInfo.serializer())) { emptyMap() }
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
    private val _edits = MutableStateFlow(editsStore.load())
    val edits: StateFlow<Map<String, TrackEdit>> = _edits
    private val _favorites = MutableStateFlow(favoritesStore.load())
    /** Track ids marked as favourites (heart). */
    val favorites: StateFlow<Set<String>> = _favorites
    private val _progress = MutableStateFlow(progressStore.load())
    /** Online details for phone-only videos: phone video id → movie, normalised show name → show. */
    private val _movieInfo = MutableStateFlow(movieInfoStore.load())
    private val _showInfo = MutableStateFlow(showInfoStore.load())
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
        // Only finished downloads matter here (and only offline): don't rebuild the whole library on every progress tick.
        combine(_overrides, _edits, _progress) { o, e, pr -> Triple(o, e, pr) }, _settings,
        combine(_settings, downloads.states) { s, m -> if (s.offline) m.filterValues { it is DlState.Done }.keys else emptySet() }.distinctUntilChanged(),
        combine(_movieInfo, _showInfo) { m, sh -> m to sh },
    ) { (cat, localTracks, localVideos), (overrides, edits, progress), settings, dlKeys, (movieInfo, showInfo) ->
        buildView(cat, localTracks, localVideos, overrides, settings, dlKeys, edits, progress, movieInfo, showInfo)
    }.stateIn(scope, SharingStarted.Eagerly, LibraryView())

    private fun buildView(cat: Catalog, localTracks: List<Track>, localVideos: List<Movie>, overrides: Map<String, String>, s: Settings, dlKeys: Set<String>, edits: Map<String, TrackEdit> = emptyMap(), progress: Map<String, WatchProgress> = emptyMap(),
        movieInfo: Map<String, MovieInfo> = emptyMap(), showInfo: Map<String, ShowInfo> = emptyMap(),
    ): LibraryView {
        val enabledLibs = s.libModes.keys
        fun libOk(id: String?) = id == null || enabledLibs.isEmpty() || id in enabledLibs
        fun avail(id: String, origin: Origin) = !s.offline || origin == Origin.PHONE || (id in dlKeys && downloads.isDownloaded(id))
        val phone = PhoneLibrary.split(localTracks, localVideos, s.phoneFolders)
        val remoteTracks = cat.tracks.filter { libOk(it.libraryId) }.map { it.copy(origin = Origin.PC, uri = it.id) }
        val tracks = (remoteTracks + phone.tracks)
            .map { t -> overrides[t.id]?.let { t.copy(artist = it) } ?: t }
            .map { t -> edits[t.id]?.apply(t) ?: t }
            .filter { avail(it.id, it.origin) }
            .let { com.amosley.signal.core.Duplicates.merge(it, s.duplicates) }
        val videos = cat.videos.filter { libOk(it.libraryId) && avail(it.id, Origin.PC) } + phone.musicVideos
        // Phone videos that are also in the PC/Plex library show Plex's details but play from the phone.
        val pcMovies = cat.movies.filter { libOk(it.libraryId) }
        val movies = PhoneMatch.movies(pcMovies, phone.movies)
            .filter { it.origin == Origin.PHONE || avail(it.id, Origin.PC) }
            // Not in the PC library: use details found online (if any).
            .map { m -> if (m.id.startsWith("locv:")) movieInfo[m.id]?.let { OnlineVideoInfo.apply(m, it) } ?: m else m }
            .map { Watching.applyLocal(it, progress[it.id]) }
            .let { com.amosley.signal.core.MovieVersions.merge(it) }
        val shows = PhoneMatch.shows(cat.shows.filter { libOk(it.libraryId) }, phone.shows)
            .map { sh -> if (sh.id.startsWith("locs:")) showInfo[com.amosley.signal.core.VideoNames.norm(sh.title)]?.let { OnlineVideoInfo.apply(sh, it) } ?: sh else sh }
            .map { sh ->
            sh.copy(seasons = sh.seasons.map { se -> se.copy(episodes = se.episodes.map { Watching.applyLocal(it, progress[it.id]) }) })
        }.mapNotNull { show ->
            if (!s.offline) show else show.copy(seasons = show.seasons.map { se -> se.copy(episodes = se.episodes.filter { it.uri.isNotEmpty() || downloads.isDownloaded(it.id) }) }.filter { it.episodes.isNotEmpty() })
                .takeIf { it.seasons.isNotEmpty() }
        }
        val artistOf = com.amosley.signal.core.ArtistNames.primary(tracks)
        val artists = artistOf.values.groupingBy { it }.eachCount().toList().sortedBy { it.first.lowercase() }
        return LibraryView(tracks, groupAlbums(tracks, videos), videos, movies, shows, artists, artistOf,
            if (s.plexContinueWatching) cat.continueWatching?.toSet() else null, progress.keys)
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
            enrichPhoneVideos()
        }
    }

    // ---- Online details for phone-only movies and shows ----------------------------------------

    private val onlineVideo = OnlineVideoInfo(http)
    private val videoMisses = java.util.Collections.synchronizedSet(HashSet<String>())
    private val enrichLock = Mutex()

    /** Look up movies/shows that are only on the phone (not matched to the PC) once, and remember the result. */
    fun enrichPhoneVideos() {
        scope.launch(Dispatchers.IO) {
            enrichLock.withLock {
                val s = _settings.value
                if (!s.onlineVideoInfo || s.offline) return@withLock
                delay(1500) // let the library settle after a scan
                val view = library.value
                for (m in view.movies.filter { it.id.startsWith("locv:") && it.id !in _movieInfo.value && it.id !in videoMisses }) {
                    val found = runCatching { onlineVideo.findMovie(m.title, m.year) }.getOrNull()
                    if (found == null) videoMisses += m.id else setMovieInfo(m.id, found)
                    delay(300)
                }
                for (sh in view.shows.filter { it.id.startsWith("locs:") && it.matchedBy != "TVMAZE" }) {
                    val key = com.amosley.signal.core.VideoNames.norm(sh.title)
                    if (key in _showInfo.value || key in videoMisses) continue
                    val found = runCatching { onlineVideo.findShow(sh.title) }.getOrNull()
                    if (found == null) videoMisses += key else setShowInfo(key, found)
                    delay(300)
                }
            }
        }
    }

    fun setMovieInfo(phoneId: String, info: MovieInfo?) {
        _movieInfo.update { if (info == null) it - phoneId else it + (phoneId to info) }
        if (info == null) videoMisses += phoneId
        movieInfoStore.save(_movieInfo.value)
    }

    fun setShowInfo(key: String, info: ShowInfo?) {
        _showInfo.update { if (info == null) it - key else it + (key to info) }
        if (info == null) videoMisses += key
        showInfoStore.save(_showInfo.value)
    }

    /** The phone video's own name, before online details were applied (for Fix match). */
    fun phoneVideoName(id: String): Pair<String, Int?>? = _localVideos.value.firstOrNull { it.id == id }?.let { it.title to it.year }

    /** A phone show's name from its files (before online details), by its "locs:" id. */
    fun phoneShowName(id: String): String? =
        PhoneLibrary.split(emptyList(), _localVideos.value, _settings.value.phoneFolders).shows.firstOrNull { it.id == id }?.title

    suspend fun searchMovies(q: String) = onlineVideo.searchMovies(q)
    suspend fun searchShows(q: String) = onlineVideo.searchShows(q)
    suspend fun showDetails(picked: ShowInfo): ShowInfo =
        picked.source.removePrefix("TVMAZE:").toLongOrNull()?.let { runCatching { onlineVideo.showById(it) }.getOrNull() } ?: picked

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
            val info = runCatching { agent.info(url) }.getOrNull()
            if (info != null) {
                // Keep the PC's name and version current (they change when the agent updates or is renamed).
                if (info.version != p.agentVersion || info.name != p.name || info.plex != p.plex) {
                    updateSettings { s -> s.copy(pc = s.pc?.copy(agentVersion = info.version, name = info.name, plex = info.plex)) }
                }
                // The router may have given the PC a new home address: speakers are sent there, so keep it current.
                com.amosley.signal.core.LanUrl.refreshed(p.lanUrl, info.addresses, com.amosley.signal.playback.wifiAddress(context))?.let { fresh ->
                    updateSettings { s -> s.copy(pc = s.pc?.copy(lanUrl = fresh)) }
                }
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
            enrichPhoneVideos()
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
                refreshNow(force = true)
                // While the PC is still scanning, check every minute so new songs/episodes appear quickly.
                val scanning = _remoteActivity.value.any { it.kind == "scan" && it.state.equals("running", true) }
                // Couldn't reach the PC (busy, or Wi-Fi just changed): try again soon instead of in 10 minutes.
                val unreachable = pc != null && !_status.value.reachable
                delay(if (unreachable) 30_000L else if (scanning) 60_000L else 10 * 60_000L)
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
        val now = System.currentTimeMillis()
        updateSettings { it.copy(pc = pc, libModes = libModes, libModeSince = libModes.mapValues { now }, remoteMode = remote, skippedPairing = false, importReviewedAt = now) }
        meta = meta.copy(knownIds = emptySet())
        metaStore.save(meta)
        _status.value = PcStatus(paired = true)
    }

    /** Hides a PC library on this phone (its rule-queued downloads stop; downloaded files stay). */
    fun clearLibMode(libId: String) {
        updateSettings { it.copy(libModes = it.libModes - libId, libModeSince = it.libModeSince - libId) }
        scope.launch { applyLibraryRules(_catalog.value) }
    }

    fun setLibMode(libId: String, mode: LibMode) {
        updateSettings { it.copy(libModes = it.libModes + (libId to mode), libModeSince = it.libModeSince + (libId to System.currentTimeMillis())) }
        scope.launch { applyLibraryRules(_catalog.value) }
    }

    // ---- URLs -----------------------------------------------------------------------------------

    private fun base(): String? = _status.value.baseUrl ?: pc?.lanUrl

    /**
     * URL for a speaker or TV to fetch from the PC: always the PC's home-network address. The phone itself may be
     * reaching the PC over Tailscale (100.x), which Sonos and Chromecast can't use.
     */
    fun speakerUrl(path: String): String? {
        val p = pc ?: return null
        return AgentClient.mediaUrl(p.lanUrl, p.token, path)
    }
    fun speakerStreamUrl(id: String): String? = speakerUrl("/api/stream/$id")
    fun speakerArtUrl(id: String): String? = speakerUrl("/api/art/$id?kind=cover")
    fun speakerTranscodedUrl(id: String): String? = speakerUrl("/api/download/$id?quality=16-44")

    fun remoteUrl(path: String): String? {
        if (path.startsWith("http://") || path.startsWith("https://")) return path
        val p = pc ?: return null
        val b = base() ?: return null
        return AgentClient.mediaUrl(b, p.token, path)
    }

    fun streamUrl(id: String): String? = remoteUrl("/api/stream/$id")
    /** [version] (the song file's mtime) changes the URL when the file changes, so cached covers can be kept for good. */
    fun artUrl(id: String, kind: String = "cover", version: Long = 0): String? =
        remoteUrl("/api/art/$id?kind=$kind" + if (version > 0) "&v=$version" else "")
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

    fun download(track: Track, auto: Boolean = false) {
        if (track.origin != Origin.PC) return
        // Fetch lyrics only when the song is newly queued, not on every sync.
        if (downloads.enqueue(DlRequest(track.id, "track", track.title, track.container ?: "bin", track.size, auto))) scope.launch { lyrics(track) }
    }
    fun download(video: MusicVideo, auto: Boolean = false) { downloads.enqueue(DlRequest(video.id, "video", video.title, video.container ?: "mp4", video.size, auto)) }
    fun download(movie: Movie, auto: Boolean = false) { if (movie.origin == Origin.PC) downloads.enqueue(DlRequest(movie.id, "movie", movie.title, movie.container ?: "mkv", movie.size, auto)) }
    fun download(ep: Episode, show: Show, auto: Boolean = false) { downloads.enqueue(DlRequest(ep.id, "episode", "${show.title} · S${ep.season}E${ep.episode}", ep.container ?: "mkv", ep.size, auto)) }

    private fun applyLibraryRules(cat: Catalog) {
        var s = _settings.value
        val now = System.currentTimeMillis()
        // Libraries set before "since" was recorded: count from now, so an update doesn't start downloading everything.
        val missing = s.libModes.keys.filter { it !in s.libModeSince }
        if (missing.isNotEmpty()) {
            updateSettings { st -> st.copy(libModeSince = st.libModeSince + missing.associateWith { now }) }
            s = _settings.value
        }
        val allIds = HashSet<String>()
        val wanted = HashSet<String>()
        fun mode(lib: String?) = lib?.let { s.libModes[it] } ?: LibMode.STREAM
        /** "Download new" = added to the PC after the mode was chosen (by file date, so restarts and partial scans don't matter). */
        fun want(lib: String?, addedAt: Long): Boolean {
            val m = mode(lib)
            return m == LibMode.DOWNLOAD_ALL || (m == LibMode.DOWNLOAD_NEW && addedAt > (lib?.let { s.libModeSince[it] } ?: now))
        }
        cat.tracks.forEach { t ->
            allIds += t.id
            if (want(t.libraryId, t.addedAt)) { wanted += t.id; download(t.copy(origin = Origin.PC), auto = true) }
        }
        cat.videos.forEach { v ->
            allIds += v.id
            if (want(v.libraryId, v.addedAt)) { wanted += v.id; download(v, auto = true) }
        }
        cat.movies.forEach { mv ->
            allIds += mv.id
            if (!mv.watched && want(mv.libraryId, mv.addedAt)) { wanted += mv.id; download(mv, auto = true) }
        }
        cat.shows.forEach { show ->
            val m = mode(show.libraryId)
            val eps = show.allEpisodes
            eps.forEach { allIds += it.id }
            val pick = when (m) {
                LibMode.DOWNLOAD_ALL -> eps.filter { !it.watched }
                LibMode.DOWNLOAD_NEW -> eps.filter { !it.watched }.take(s.keepEpisodes)
                LibMode.STREAM -> emptyList()
            }
            pick.forEach { wanted += it.id; download(it, show, auto = true) }
            if (s.autoRemoveWatched) {
                // Only episodes you finished in Signal after downloading them (not ones watched long ago in Plex,
                // e.g. when downloading a whole series you've seen), a day after you watched them.
                eps.filter { it.watched && downloads.isDownloaded(it.id) }.forEach { ep ->
                    val at = meta.watchedAt[ep.id] ?: return@forEach
                    val downloadedAt = (downloads.state(ep.id) as? DlState.Done)?.file?.finishedAt ?: return@forEach
                    if (at > downloadedAt && now - at > 24 * 3600_000L) downloads.remove(ep.id)
                }
            }
        }
        downloads.cancelAutoExcept(wanted)
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
        // Lyrics the user pasted / synced on the phone always win.
        runCatching { SignalJson.decodeFromString(Lyrics.serializer(), userLyricsFile(track).readText()) }.getOrNull()?.let { mine ->
            _lyrics.update { it + (track.id to mine) }
            return@withContext mine
        }
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

    private val userLyricsDir = File(files, "lyrics-user").apply { mkdirs() }
    private fun userLyricsFile(track: Track) = File(userLyricsDir, safe(track.id) + ".json")

    /**
     * Save lyrics typed or tap-synced on the phone. Kept on the phone (they take priority everywhere) and,
     * for PC songs, written to `<song>.lrc` on the PC so they travel with the file. Returns a status message.
     */
    suspend fun saveUserLyrics(track: Track, lines: List<com.amosley.signal.core.LyricLine>, synced: Boolean): String = withContext(Dispatchers.IO) {
        val lyrics = Lyrics(lang = null, lines = lines, synced = synced, source = "user")
        userLyricsFile(track).writeText(SignalJson.encodeToString(Lyrics.serializer(), lyrics))
        _lyrics.update { it + (track.id to lyrics) }
        if (track.origin != Origin.PC) return@withContext "Lyrics saved"
        val p = pc
        val b = _status.value.baseUrl
        if (p == null || b == null || !_status.value.reachable) return@withContext "Lyrics saved on this phone · ${pcName} is offline, so the .lrc wasn't written there"
        runCatching { agent.saveLyrics(b, p.token, track.id, com.amosley.signal.core.PastedLyrics.toLrc(lines, synced)) }
            .fold({ "Lyrics saved · .lrc written on ${p.name}" }, { "Lyrics saved on this phone · couldn't write the .lrc on ${p.name}" })
    }

    fun deleteUserLyrics(track: Track) {
        userLyricsFile(track).delete()
        refreshLyrics(track)
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

    /** Save edited tags (title, album, genre, …). Kept on the phone; written into PC files by the agent. */
    fun editTrack(track: Track, requested: TrackEdit) {
        var edit = requested
        // Giving a song the name of an album that already has an album artist (e.g. a soundtrack combined into
        // one album) joins that album, so it gets the same cover instead of starting a new album.
        val prev = _edits.value[track.id]
        val album = edit.album ?: prev?.album ?: track.album
        if (edit.albumArtist == null && (prev?.albumArtist ?: track.albumArtist) == null && album != null) {
            albumArtistFor(album, track.id)?.let { edit = edit.copy(albumArtist = it) }
        }
        edit.artist?.let { a -> _overrides.update { it + (track.id to a) }; overridesStore.save(_overrides.value); onlineMisses -= track.id }
        _edits.update { all -> all + (track.id to (all[track.id]?.merge(edit) ?: edit)) }
        editsStore.save(_edits.value)
        if (track.origin == Origin.PC) {
            val fields = (_edits.value[track.id] ?: edit).toFields() + (edit.artist?.let { mapOf("artist" to it) } ?: emptyMap())
            _tagJobs.update { jobs ->
                jobs.filterNot { it.trackId == track.id } +
                    TagJob(track.id, edit.title ?: track.title, edit.artist ?: track.artist.orEmpty(), "QUEUED", at = System.currentTimeMillis(), fields = fields)
            }
            tagJobsStore.save(_tagJobs.value)
            scope.launch { flushTagJobs() }
        }
    }

    /** The album artist already used by other songs of the album called [album], if there is exactly one. */
    private fun albumArtistFor(album: String, exceptId: String): String? =
        library.value.tracks.filter { it.id != exceptId && it.album?.trim().equals(album.trim(), ignoreCase = true) }
            .mapNotNull { it.albumArtist?.takeIf { a -> a.isNotBlank() } }.distinctBy { it.lowercase() }.singleOrNull()

    /**
     * Albums with the same title listed under different artists (a soundtrack, a compilation): give every song the
     * same album artist so they show as one album. The first custom cover found is kept for the combined album.
     */
    fun combineAlbums(albums: List<com.amosley.signal.core.Album>, albumArtist: String) {
        val name = albumArtist.trim().ifEmpty { return }
        val first = albums.firstOrNull() ?: return
        val newKey = com.amosley.signal.core.albumKeyOf(first.title, name)
        if (albumArtFile(newKey) == null) {
            albums.firstNotNullOfOrNull { albumArtFile(it.key) }?.let { src ->
                val dest = artFile("album", newKey)
                runCatching { src.copyTo(dest, overwrite = true) }.onSuccess { artNames += dest.name; _artVersion.update { it + 1 } }
            }
        }
        albums.flatMap { it.tracks }.filter { it.albumArtist != name }.forEach { editTrack(it, TrackEdit(albumArtist = name)) }
    }

    // ---- Custom art (albums and artists) --------------------------------------------------------

    private val artDir = File(files, "art").apply { mkdirs() }
    private val _artVersion = MutableStateFlow(0)
    /** Bumps whenever custom art changes, so screens reload images. */
    val artVersion: StateFlow<Int> = _artVersion

    private fun artFile(kind: String, key: String): File {
        val hash = java.security.MessageDigest.getInstance("SHA-1").digest(key.lowercase().toByteArray()).joinToString("") { "%02x".format(it) }
        return File(artDir, "$kind-$hash.jpg")
    }

    /** Names of custom art files, kept in memory so screens don't hit the disk while drawing. */
    private val artNames: MutableSet<String> = java.util.concurrent.ConcurrentHashMap.newKeySet<String>().apply {
        artDir.list()?.let { addAll(it) }
    }

    fun albumArtFile(albumKey: String): File? = artFile("album", albumKey).takeIf { it.name in artNames }
    fun artistArtFile(name: String): File? = artFile("artist", name).takeIf { it.name in artNames }

    /** Save album art on the phone and, for PC albums, as cover.jpg in the album folder on the PC. */
    suspend fun saveAlbumArt(album: com.amosley.signal.core.Album, jpeg: ByteArray): String = withContext(Dispatchers.IO) {
        artFile("album", album.key).also { it.writeBytes(jpeg); artNames += it.name }
        _artVersion.update { it + 1 }
        val pcTrack = album.tracks.firstOrNull { it.origin == Origin.PC } ?: return@withContext "Album art saved"
        val p = pc
        val b = _status.value.baseUrl
        if (p == null || b == null || !_status.value.reachable) return@withContext "Album art saved on this phone · ${pcName} is offline"
        runCatching { agent.uploadCover(b, p.token, pcTrack.id, jpeg) }
            .fold({ "Album art saved · also saved as cover.jpg on ${p.name}" }, { "Album art saved on this phone · couldn't save it on ${p.name}" })
    }

    suspend fun saveArtistArt(name: String, jpeg: ByteArray): String = withContext(Dispatchers.IO) {
        artFile("artist", name).also { it.writeBytes(jpeg); artNames += it.name }
        _artVersion.update { it + 1 }
        "Artist picture saved"
    }

    fun removeAlbumArt(albumKey: String) { artFile("album", albumKey).also { it.delete(); artNames -= it.name }; _artVersion.update { it + 1 } }
    fun removeArtistArt(name: String) { artFile("artist", name).also { it.delete(); artNames -= it.name }; _artVersion.update { it + 1 } }

    private suspend fun flushTagJobs(force: Set<String> = emptySet()) {
        val p = pc ?: return
        val b = _status.value.baseUrl ?: return
        val jobs = _tagJobs.value.filter { it.state == "QUEUED" || it.state == "FAILED" }
        for (job in jobs) {
            val track = _catalog.value.tracks.firstOrNull { it.id == job.trackId }
            val res = runCatching {
                if (job.fields.isNotEmpty()) agent.writeTags(b, p.token, job.trackId, job.fields, track?.mtime ?: 0, job.trackId in force)
                else agent.writeArtist(b, p.token, job.trackId, job.artist, track?.mtime ?: 0, job.trackId in force)
            }
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

    fun toggleFavorite(trackId: String): Boolean {
        val now = trackId !in _favorites.value
        _favorites.update { if (now) it + trackId else it - trackId }
        favoritesStore.save(_favorites.value)
        return now
    }

    /** Remember where you are in a movie/episode (on the phone right away; on the PC / Plex when reachable). */
    fun reportProgress(id: String, positionMs: Long, durationMs: Long) {
        val watched = Watching.isWatched(positionMs, durationMs)
        _progress.update { it + (id to WatchProgress(positionMs, durationMs, watched, System.currentTimeMillis())) }
        progressStore.save(_progress.value)
        if (id.startsWith("locv:")) return // phone-only video: nothing to tell the PC
        val p = pc ?: return
        val b = _status.value.baseUrl ?: return
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
