package com.amosley.signal.cast

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import com.amosley.signal.playback.OutputKind
import com.amosley.signal.playback.RemoteItem
import com.amosley.signal.playback.RemoteOutput
import com.amosley.signal.playback.RemoteState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.DatagramPacket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.MulticastSocket
import java.net.SocketTimeoutException
import java.net.URL
import java.util.concurrent.TimeUnit

data class SonosRoom(val uuid: String, val name: String, val model: String, val ip: String) {
    val base: String get() = "http://$ip:1400"
}

/**
 * Local Sonos control over UPnP (port 1400) — no Sonos account needed. Discovery via SSDP,
 * playback via AVTransport, grouping via x-rincon URIs and group volume via GroupRenderingControl.
 */
class SonosController(private val context: Context, private val http: OkHttpClient, private val scope: CoroutineScope) {
    private val _rooms = MutableStateFlow<List<SonosRoom>>(emptyList())
    val rooms: StateFlow<List<SonosRoom>> = _rooms
    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning

    private val prefs = context.getSharedPreferences("sonos", Context.MODE_PRIVATE)

    /** The phone's Wi-Fi network. Sockets bound to it bypass a VPN, which otherwise swallows SSDP and LAN traffic. */
    private fun wifiNetwork(): Network? {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        @Suppress("DEPRECATION")
        return cm.allNetworks.firstOrNull { cm.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true }
    }

    @Volatile private var lanClient: Pair<Network?, OkHttpClient>? = null

    /** HTTP client for talking to speakers: short timeouts, routed over Wi-Fi even when a VPN is on. */
    private fun lan(): OkHttpClient {
        val net = wifiNetwork()
        lanClient?.let { (n, c) -> if (n == net) return c }
        val c = http.newBuilder()
            .connectTimeout(2, TimeUnit.SECONDS).readTimeout(8, TimeUnit.SECONDS)
            .apply { if (net != null) socketFactory(net.socketFactory) }
            .build()
        lanClient = net to c
        return c
    }

    /**
     * Finds Sonos rooms, showing each as soon as it answers:
     * 1. rooms found last time (checked directly, so they appear almost instantly);
     * 2. SSDP multicast on the Wi-Fi interface;
     * 3. the zone topology of any room found, which lists every other room;
     * 4. if still nothing, a quick sweep of the Wi-Fi subnet for Sonos' port 1400.
     */
    fun discover() {
        if (_scanning.value) return
        _scanning.value = true
        scope.launch(Dispatchers.IO) {
            val found = LinkedHashMap<String, SonosRoom>()
            val tried = java.util.Collections.synchronizedSet(HashSet<String>())
            fun add(r: SonosRoom) = synchronized(found) {
                found[r.uuid] = r
                _rooms.value = found.values.sortedBy { it.name }
            }
            suspend fun check(location: String) {
                if (!tried.add(location)) return
                runCatching { describe(location) }.getOrNull()?.let(::add)
            }
            runCatching {
                coroutineScope {
                    cached().forEach { r -> launch { check("http://${r.ip}:1400/xml/device_description.xml") } }
                    launch { ssdp { loc -> launch { check(loc) } } }
                }
                // Every room the found ones know about (catches rooms that missed the multicast).
                synchronized(found) { found.values.toList() }.firstOrNull()?.let { r ->
                    val locs = runCatching { topologyLocations(r) }.getOrDefault(emptyList())
                    coroutineScope { locs.forEach { launch { check(it) } } }
                }
                if (synchronized(found) { found.isEmpty() }) {
                    coroutineScope { subnetHosts().forEach { ip -> launch { if (port1400Open(ip)) check("http://$ip:1400/xml/device_description.xml") } } }
                }
            }
            val all = synchronized(found) { found.values.sortedBy { it.name } }
            _rooms.value = all
            if (all.isNotEmpty()) prefs.edit().putString("rooms", all.joinToString("\n") { "${it.uuid}\t${it.name}\t${it.model}\t${it.ip}" }).apply()
            _scanning.value = false
        }
    }

    private fun cached(): List<SonosRoom> = prefs.getString("rooms", null).orEmpty().lines().mapNotNull { l ->
        val p = l.split('\t')
        if (p.size == 4) SonosRoom(p[0], p[1], p[2], p[3]) else null
    }

    /** SSDP M-SEARCH for Sonos players; calls [onLocation] for each reply's description URL. */
    private suspend fun ssdp(onLocation: (String) -> Unit) = withContext(Dispatchers.IO) {
        val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val lock = wifi.createMulticastLock("signal-sonos").apply { setReferenceCounted(false) }
        try {
            lock.acquire()
            MulticastSocket(null).use { sock ->
                sock.reuseAddress = true
                sock.bind(null)
                val net = wifiNetwork()
                if (net != null) {
                    runCatching { net.bindSocket(sock) }
                    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                    cm.getLinkProperties(net)?.interfaceName?.let { name ->
                        runCatching { NetworkInterface.getByName(name)?.let { sock.networkInterface = it } }
                    }
                }
                sock.timeToLive = 4
                sock.soTimeout = 500
                val group = InetAddress.getByName("239.255.255.250")
                fun search(st: String) {
                    val msg = ("M-SEARCH * HTTP/1.1\r\nHOST: 239.255.255.250:1900\r\nMAN: \"ssdp:discover\"\r\nMX: 1\r\nST: $st\r\n\r\n").toByteArray()
                    runCatching { sock.send(DatagramPacket(msg, msg.size, group, 1900)) }
                }
                val buf = ByteArray(2048)
                val start = System.currentTimeMillis()
                var sent = 0
                while (System.currentTimeMillis() - start < 4000) {
                    // Resend a few times: Wi-Fi drops multicast packets easily.
                    if (sent < 3 && System.currentTimeMillis() - start >= sent * 1000L) {
                        search("urn:schemas-upnp-org:device:ZonePlayer:1")
                        if (sent == 0) search("urn:smartspeaker-audio:service:SpeakerGroup:1")
                        sent++
                    }
                    try {
                        val p = DatagramPacket(buf, buf.size)
                        sock.receive(p)
                        val text = String(p.data, 0, p.length)
                        if (!text.contains("Sonos", ignoreCase = true) && !text.contains("ZonePlayer")) continue
                        Regex("(?im)^LOCATION:\\s*(\\S+)").find(text)?.groupValues?.get(1)?.let(onLocation)
                    } catch (_: SocketTimeoutException) {
                    }
                }
            }
        } finally {
            if (lock.isHeld) lock.release()
        }
    }

    /** Description URLs of every player in [room]'s household, from ZoneGroupTopology. */
    private suspend fun topologyLocations(room: SonosRoom): List<String> {
        val xml = soap(room, "/ZoneGroupTopology/Control", "ZoneGroupTopology", "GetZoneGroupState", emptyList())
        return Regex("Location=(?:&quot;|\")(http://[^&\"]+)").findAll(xml).map { it.groupValues[1] }.distinct().toList()
    }

    /** Other addresses on the phone's Wi-Fi subnet (at most a /24 around the phone). */
    private fun subnetHosts(): List<String> {
        val net = wifiNetwork() ?: return emptyList()
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val own = cm.getLinkProperties(net)?.linkAddresses?.map { it.address }?.filterIsInstance<Inet4Address>()?.firstOrNull() ?: return emptyList()
        val b = own.address.map { it.toInt() and 0xff }
        return (1..254).filter { it != b[3] }.map { "${b[0]}.${b[1]}.${b[2]}.$it" }
    }

    private fun port1400Open(ip: String): Boolean = runCatching {
        val sf = wifiNetwork()?.socketFactory ?: javax.net.SocketFactory.getDefault()
        sf.createSocket().use { it.connect(InetSocketAddress(ip, 1400), 400) }
        true
    }.getOrDefault(false)

    private fun describe(location: String): SonosRoom? {
        val xml = lan().newCall(Request.Builder().url(location).build()).execute().use { it.body?.string() } ?: return null
        fun tag(name: String) = Regex("<$name>([^<]*)</$name>").find(xml)?.groupValues?.get(1)
        val uuid = tag("UDN")?.removePrefix("uuid:") ?: return null
        val name = tag("roomName") ?: return null
        val model = tag("modelName") ?: "Sonos"
        // Skip invisible satellites / subs that can't be coordinators.
        if (xml.contains("<invisible>1</invisible>")) return null
        return SonosRoom(uuid, name, model, URL(location).host)
    }

    // ---- SOAP -----------------------------------------------------------------------------------

    private suspend fun soap(room: SonosRoom, path: String, service: String, action: String, args: List<Pair<String, String>>): String =
        withContext(Dispatchers.IO) {
            val body = buildString {
                append("<?xml version=\"1.0\" encoding=\"utf-8\"?>")
                append("<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\"><s:Body>")
                append("<u:$action xmlns:u=\"urn:schemas-upnp-org:service:$service:1\">")
                args.forEach { (k, v) -> append("<$k>${escape(v)}</$k>") }
                append("</u:$action></s:Body></s:Envelope>")
            }
            val req = Request.Builder()
                .url(room.base + path)
                .header("SOAPACTION", "\"urn:schemas-upnp-org:service:$service:1#$action\"")
                .post(body.toRequestBody("text/xml; charset=\"utf-8\"".toMediaType()))
                .build()
            lan().newCall(req).execute().use { res ->
                val text = res.body?.string().orEmpty()
                if (!res.isSuccessful) error("Sonos $action failed (${res.code})")
                text
            }
        }

    private fun escape(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    private suspend fun av(room: SonosRoom, action: String, vararg args: Pair<String, String>) =
        soap(room, "/MediaRenderer/AVTransport/Control", "AVTransport", action, listOf("InstanceID" to "0") + args)

    suspend fun setUri(room: SonosRoom, item: RemoteItem) {
        val didl = "<DIDL-Lite xmlns:dc=\"http://purl.org/dc/elements/1.1/\" xmlns:upnp=\"urn:schemas-upnp-org:metadata-1-0/upnp/\" " +
            "xmlns:r=\"urn:schemas-rinconnetworks-com:metadata-1-0/\" xmlns=\"urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/\">" +
            "<item id=\"signal\" parentID=\"0\" restricted=\"true\"><dc:title>${escape(item.title)}</dc:title>" +
            (item.artist?.let { "<dc:creator>${escape(it)}</dc:creator>" } ?: "") +
            (item.album?.let { "<upnp:album>${escape(it)}</upnp:album>" } ?: "") +
            (item.artUrl?.let { "<upnp:albumArtURI>${escape(it)}</upnp:albumArtURI>" } ?: "") +
            "<upnp:class>object.item.audioItem.musicTrack</upnp:class>" +
            "<res protocolInfo=\"http-get:*:${item.mime}:*\">${escape(item.url)}</res></item></DIDL-Lite>"
        av(room, "SetAVTransportURI", "CurrentURI" to item.url, "CurrentURIMetaData" to didl)
    }

    suspend fun play(room: SonosRoom) = av(room, "Play", "Speed" to "1")
    suspend fun pause(room: SonosRoom) = av(room, "Pause")
    suspend fun seek(room: SonosRoom, ms: Long) {
        val s = ms / 1000
        av(room, "Seek", "Unit" to "REL_TIME", "Target" to "%d:%02d:%02d".format(s / 3600, (s % 3600) / 60, s % 60))
    }

    suspend fun position(room: SonosRoom): Pair<Long, Long> {
        val xml = av(room, "GetPositionInfo")
        fun t(tag: String) = Regex("<$tag>([^<]*)</$tag>").find(xml)?.groupValues?.get(1)?.let(::hms) ?: 0L
        return t("RelTime") to t("TrackDuration")
    }

    suspend fun transportState(room: SonosRoom): String =
        Regex("<CurrentTransportState>([^<]*)</CurrentTransportState>").find(av(room, "GetTransportInfo"))?.groupValues?.get(1) ?: "UNKNOWN"

    private fun hms(s: String): Long {
        val parts = s.split(":").mapNotNull { it.substringBefore('.').toLongOrNull() }
        if (parts.size != 3) return 0
        return (parts[0] * 3600 + parts[1] * 60 + parts[2]) * 1000
    }

    /** Joins [member] to [coordinator]'s group. */
    suspend fun join(member: SonosRoom, coordinator: SonosRoom) =
        av(member, "SetAVTransportURI", "CurrentURI" to "x-rincon:${coordinator.uuid}", "CurrentURIMetaData" to "")

    suspend fun leave(member: SonosRoom) = av(member, "BecomeCoordinatorOfStandaloneGroup")

    suspend fun setGroupVolume(coordinator: SonosRoom, volume: Int) {
        soap(coordinator, "/MediaRenderer/GroupRenderingControl/Control", "GroupRenderingControl", "SetGroupVolume",
            listOf("InstanceID" to "0", "DesiredVolume" to volume.coerceIn(0, 100).toString()))
    }

    suspend fun groupVolume(coordinator: SonosRoom): Int? {
        val xml = soap(coordinator, "/MediaRenderer/GroupRenderingControl/Control", "GroupRenderingControl", "GetGroupVolume", listOf("InstanceID" to "0"))
        return Regex("<CurrentVolume>(\\d+)</CurrentVolume>").find(xml)?.groupValues?.get(1)?.toIntOrNull()
    }
}

/** Plays on a Sonos room (the group coordinator); polls position once a second. */
class SonosOutput(
    private val sonos: SonosController,
    val room: SonosRoom,
    private val scope: CoroutineScope,
    private val onError: (String) -> Unit,
) : RemoteOutput {
    override val name: String get() = room.name
    override val kind = OutputKind.SONOS
    private val _state = MutableStateFlow(RemoteState())
    override val state: StateFlow<RemoteState> = _state
    private var poll: Job? = null
    private var sawPlaying = false
    private var expectedDuration = 0L

    private fun run(block: suspend () -> Unit) = scope.launch {
        runCatching { block() }.onFailure { onError("${room.name} couldn't play this song (${it.message ?: "error"})") }
    }

    /** True once a song has been handed to the speaker; Play/Pause before that make Sonos return 500. */
    @Volatile private var loaded = false

    override fun load(item: RemoteItem, startMs: Long, play: Boolean) {
        expectedDuration = item.durationMs
        sawPlaying = false
        _state.value = RemoteState(positionMs = startMs, durationMs = item.durationMs, playing = play)
        run {
            sonos.setUri(room, item)
            loaded = true
            if (startMs > 1000) runCatching { sonos.seek(room, startMs) }
            if (play) sonos.play(room)
            startPolling()
        }
    }

    private fun startPolling() {
        if (poll?.isActive == true) return
        poll = scope.launch {
            while (isActive) {
                delay(1000)
                val st = runCatching { sonos.transportState(room) }.getOrNull() ?: continue
                val (pos, dur) = runCatching { sonos.position(room) }.getOrDefault(0L to 0L)
                val playing = st == "PLAYING" || st == "TRANSITIONING"
                if (playing) sawPlaying = true
                val d = if (dur > 0) dur else expectedDuration
                val ended = sawPlaying && st == "STOPPED"
                if (ended) sawPlaying = false
                _state.value = RemoteState(positionMs = if (ended) d else pos, durationMs = d, playing = playing, ended = ended)
            }
        }
    }

    override fun play() {
        if (!loaded) return
        _state.value = _state.value.copy(playing = true)
        run { sonos.play(room) }
    }

    override fun pause() {
        if (!loaded) return
        _state.value = _state.value.copy(playing = false)
        scope.launch { runCatching { sonos.pause(room) } }
    }

    override fun seek(ms: Long) { if (loaded) run { sonos.seek(room, ms) } }

    override fun release() {
        poll?.cancel()
        if (loaded) scope.launch { runCatching { sonos.pause(room) } }
    }
}
