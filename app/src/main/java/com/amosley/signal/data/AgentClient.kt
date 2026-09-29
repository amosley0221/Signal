package com.amosley.signal.data

import com.amosley.signal.core.ArtistSuggestion
import com.amosley.signal.core.Catalog
import com.amosley.signal.core.Library
import com.amosley.signal.core.LyricLine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit

val SignalJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    coerceInputValues = true
    encodeDefaults = true
}

@Serializable data class AgentInfo(val name: String, val version: String? = null, val id: String, val plex: Boolean = false, val addresses: List<String> = emptyList())
@Serializable data class PairStart(val requestId: String, val code: String)
@Serializable data class PairStatus(val status: String, val token: String? = null)
@Serializable data class ApiLyrics(val lang: String? = null, val lines: List<LyricLine> = emptyList(), val translation: List<LyricLine>? = null, val synced: Boolean = true)
@Serializable data class ActivityItem(val id: String, val kind: String, val title: String, val detail: String? = null, val state: String, val progress: Double? = null)
@Serializable data class TagResult(val ok: Boolean = false, val mtime: Long? = null, val error: String? = null, val pcArtist: String? = null)
@Serializable private data class ApiSuggestion(val name: String, val why: String, val kind: String)
@Serializable private data class PairStartBody(val deviceName: String)
@Serializable private data class TagBody(val artist: String, val baseMtime: Long, val force: Boolean = false)
@Serializable private data class ProgressBody(val id: String, val positionMs: Long, val watched: Boolean)
@Serializable private data class SuggestBody(val trackId: String, val prompt: String)

class AgentException(val code: Int, message: String) : IOException(message)

/** HTTP client for the Signal Agent running on the PC. See docs/API.md. */
class AgentClient(private val http: OkHttpClient) {
    private val jsonType = "application/json".toMediaType()

    private suspend fun call(base: String, path: String, token: String?, body: RequestBody? = null, timeoutSec: Long? = null): String =
        withContext(Dispatchers.IO) {
            val req = Request.Builder().url(base.trimEnd('/') + path).apply {
                if (token != null) header("Authorization", "Bearer $token")
                if (body != null) post(body)
            }.build()
            val client = if (timeoutSec != null) http.newBuilder().callTimeout(timeoutSec, TimeUnit.SECONDS).build() else http
            client.newCall(req).execute().use { res -> res.bodyOrThrow() }
        }

    private fun Response.bodyOrThrow(): String {
        val text = body?.string().orEmpty()
        if (!isSuccessful && code != 409) throw AgentException(code, "HTTP $code ${text.take(200)}")
        return text
    }

    suspend fun info(base: String): AgentInfo = SignalJson.decodeFromString(call(base, "/api/info", null, timeoutSec = 4))

    suspend fun pairStart(base: String, deviceName: String): PairStart =
        SignalJson.decodeFromString(call(base, "/api/pair/start", null, SignalJson.encodeToString(PairStartBody.serializer(), PairStartBody(deviceName)).toRequestBody(jsonType)))

    suspend fun pairStatus(base: String, requestId: String): PairStatus =
        SignalJson.decodeFromString(call(base, "/api/pair/status/$requestId", null))

    suspend fun libraries(base: String, token: String): List<Library> = SignalJson.decodeFromString(call(base, "/api/libraries", token))

    suspend fun catalog(base: String, token: String): Catalog = SignalJson.decodeFromString(call(base, "/api/catalog", token, timeoutSec = 120))

    suspend fun lyrics(base: String, token: String, id: String): ApiLyrics = SignalJson.decodeFromString(call(base, "/api/lyrics/$id", token))

    suspend fun activity(base: String, token: String): List<ActivityItem> = SignalJson.decodeFromString(call(base, "/api/activity", token))

    suspend fun rescan(base: String, token: String) { call(base, "/api/rescan", token, "{}".toRequestBody(jsonType)) }

    suspend fun writeArtist(base: String, token: String, id: String, artist: String, baseMtime: Long, force: Boolean): TagResult =
        SignalJson.decodeFromString(call(base, "/api/tags/$id", token, SignalJson.encodeToString(TagBody.serializer(), TagBody(artist, baseMtime, force)).toRequestBody(jsonType)))

    suspend fun progress(base: String, token: String, id: String, positionMs: Long, watched: Boolean) {
        call(base, "/api/progress", token, SignalJson.encodeToString(ProgressBody.serializer(), ProgressBody(id, positionMs, watched)).toRequestBody(jsonType))
    }

    suspend fun suggest(base: String, token: String, trackId: String, prompt: String): List<ArtistSuggestion> =
        SignalJson.decodeFromString<List<ApiSuggestion>>(call(base, "/api/suggest-artists", token, SignalJson.encodeToString(SuggestBody.serializer(), SuggestBody(trackId, prompt)).toRequestBody(jsonType)))
            .map { ArtistSuggestion(it.name, it.why, it.kind) }

    suspend fun upload(base: String, token: String, libraryId: String, name: String, body: RequestBody) {
        val url = (base.trimEnd('/') + "/api/upload").toHttpUrl().newBuilder()
            .addQueryParameter("library", libraryId).addQueryParameter("name", name).build()
        withContext(Dispatchers.IO) {
            http.newBuilder().callTimeout(30, TimeUnit.MINUTES).build()
                .newCall(Request.Builder().url(url).header("Authorization", "Bearer $token").post(body).build())
                .execute().use { it.bodyOrThrow() }
        }
    }

    companion object {
        /** Media URLs carry the token as a query parameter so Cast / Sonos receivers can fetch them too. */
        fun mediaUrl(base: String, token: String, path: String): String {
            val full = if (path.startsWith("http")) path else base.trimEnd('/') + path
            return full.toHttpUrl().newBuilder().addQueryParameter("token", token).build().toString()
        }
    }
}
