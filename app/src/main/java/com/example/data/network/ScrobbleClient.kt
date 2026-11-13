package com.example.data.network

import com.example.data.local.VelunaUserPrefs
import com.example.model.SongItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.security.MessageDigest

class ScrobbleClient(private val httpClient: OkHttpClient) {

    suspend fun submitNowPlaying(song: SongItem, prefs: VelunaUserPrefs) {
        if (!prefs.scrobblingEnabled || song.isLocal) return
        if (prefs.listenBrainzToken.isNotBlank()) {
            submitListenBrainz(song, prefs.listenBrainzToken, "playing_now")
        }
        if (prefs.lastFmSessionKey.isNotBlank() && prefs.lastFmApiKey.isNotBlank() && prefs.lastFmApiSecret.isNotBlank()) {
            submitLastFm(
                method = "track.updateNowPlaying",
                song = song,
                apiKey = prefs.lastFmApiKey,
                secret = prefs.lastFmApiSecret,
                sessionKey = prefs.lastFmSessionKey,
                timestampSecs = null
            )
        }
    }

    suspend fun submitScrobble(song: SongItem, startedAtSecs: Long, prefs: VelunaUserPrefs) {
        if (!prefs.scrobblingEnabled || song.isLocal) return
        if (prefs.listenBrainzToken.isNotBlank()) {
            submitListenBrainz(song, prefs.listenBrainzToken, "single", startedAtSecs)
        }
        if (prefs.lastFmSessionKey.isNotBlank() && prefs.lastFmApiKey.isNotBlank() && prefs.lastFmApiSecret.isNotBlank()) {
            submitLastFm(
                method = "track.scrobble",
                song = song,
                apiKey = prefs.lastFmApiKey,
                secret = prefs.lastFmApiSecret,
                sessionKey = prefs.lastFmSessionKey,
                timestampSecs = startedAtSecs
            )
        }
    }

    private suspend fun submitListenBrainz(
        song: SongItem,
        token: String,
        listenType: String,
        listenedAt: Long? = null
    ) = withContext(Dispatchers.IO) {
        runCatching {
            val payload = buildJsonObject {
                put("listen_type", listenType)
                put("payload", buildJsonArray {
                    add(buildJsonObject {
                        listenedAt?.let { put("listened_at", it) }
                        putJsonObject("track_metadata") {
                            put("artist_name", song.artists)
                            put("track_name", song.title)
                            song.album?.let { put("release_name", it) }
                        }
                    })
                })
            }
            val req = Request.Builder()
                .url("https://api.listenbrainz.org/1/submit-listens")
                .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                .header("Authorization", "Token $token")
                .build()
            httpClient.newCall(req).execute().close()
        }
    }

    private suspend fun submitLastFm(
        method: String,
        song: SongItem,
        apiKey: String,
        secret: String,
        sessionKey: String,
        timestampSecs: Long?
    ) = withContext(Dispatchers.IO) {
        runCatching {
            val params = sortedMapOf(
                "method" to method,
                "api_key" to apiKey,
                "sk" to sessionKey,
                "artist" to song.artists,
                "track" to song.title
            )
            song.album?.let { params["album"] = it }
            timestampSecs?.let { params["timestamp"] = it.toString() }

            val sigBase = params.entries.joinToString("") { "${it.key}${it.value}" } + secret
            val sig = md5Hex(sigBase)

            val form = FormBody.Builder().apply {
                params.forEach { (k, v) -> add(k, v) }
                add("api_sig", sig)
                add("format", "json")
            }.build()

            val req = Request.Builder()
                .url("https://ws.audioscrobbler.com/2.0/")
                .post(form)
                .build()
            httpClient.newCall(req).execute().close()
        }
    }

    private fun md5Hex(input: String): String {
        val bytes = MessageDigest.getInstance("MD5").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
