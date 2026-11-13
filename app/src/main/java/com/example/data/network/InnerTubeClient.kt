package com.example.data.network

import com.example.model.AlbumPage
import com.example.model.ArtistPage
import com.example.model.AudioQuality
import com.example.model.BrowseItem
import com.example.model.BrowseKind
import com.example.model.CommentsPage
import com.example.model.HomePage
import com.example.model.MoodSection
import com.example.model.PlaylistPage
import com.example.model.ResolvedStream
import com.example.model.SearchResults
import com.example.model.SearchSuggestions
import com.example.model.SongItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import kotlin.math.abs

data class ClientIdentity(
    val key: String,
    val clientName: String,
    val clientVersion: String,
    val clientId: String,
    val userAgent: String,
    val osName: String? = null,
    val osVersion: String? = null,
    val deviceMake: String? = null,
    val deviceModel: String? = null,
    val androidSdkVersion: String? = null
)

class InnerTubeClient(
    val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    @Volatile
    private var cachedVisitorData: String? = null

    private val webRemix = ClientIdentity(
        key = "WEB_REMIX",
        clientName = "WEB_REMIX",
        clientVersion = "1.20260213.01.00",
        clientId = "67",
        userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0"
    )

    private val iosMusic = ClientIdentity(
        key = "IOS_MUSIC",
        clientName = "IOS_MUSIC",
        clientVersion = "7.01.05",
        clientId = "26",
        userAgent = "com.google.ios.youtubemusic/7.01.05 (iPhone16,2; U; CPU iOS 18_2 like Mac OS X;)",
        osName = "iOS",
        osVersion = "18.2.22C152",
        deviceMake = "Apple",
        deviceModel = "iPhone16,2"
    )

    private val androidVr = ClientIdentity(
        key = "ANDROID_VR",
        clientName = "ANDROID_VR",
        clientVersion = "1.65.10",
        clientId = "28",
        userAgent = "com.google.android.apps.youtube.vr.oculus/1.65.10 (Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip",
        osName = "Android",
        osVersion = "12L",
        deviceMake = "Oculus",
        deviceModel = "Quest 3",
        androidSdkVersion = "32"
    )

    private val visionOs = ClientIdentity(
        key = "VISIONOS",
        clientName = "VISIONOS",
        clientVersion = "0.1",
        clientId = "101",
        userAgent = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.0 Safari/605.1.15",
        osName = "visionOS",
        osVersion = "1.3.21O771",
        deviceMake = "Apple",
        deviceModel = "RealityDevice14,1"
    )

    private suspend fun ensureVisitorData(): String? = withContext(Dispatchers.IO) {
        cachedVisitorData?.let { return@withContext it }
        runCatching {
            val req = Request.Builder()
                .url("https://music.youtube.com/sw.js_data")
                .header("User-Agent", webRemix.userAgent)
                .header("Accept-Language", "en-US,en;q=0.9")
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val raw = resp.body?.string()?.removePrefix(")]}'") ?: return@use null
                val match = Regex("""Cgt[A-Za-z0-9_\-]{10,}""").find(raw)?.value
                if (match != null) cachedVisitorData = match
                match
            }
        }.getOrNull()
    }

    private fun buildContext(client: ClientIdentity, visitorData: String?): JsonObject = buildJsonObject {
        putJsonObject("client") {
            put("clientName", client.clientName)
            put("clientVersion", client.clientVersion)
            put("hl", "en")
            put("gl", "US")
            if (visitorData != null) put("visitorData", visitorData)
            client.osName?.let { put("osName", it) }
            client.osVersion?.let { put("osVersion", it) }
            client.deviceMake?.let { put("deviceMake", it) }
            client.deviceModel?.let { put("deviceModel", it) }
            client.androidSdkVersion?.let { put("androidSdkVersion", it) }
        }
    }

    private suspend fun post(
        endpoint: String,
        client: ClientIdentity,
        extraFields: Map<String, JsonElement>
    ): JsonElement = withContext(Dispatchers.IO) {
        val visitor = ensureVisitorData()
        val bodyObj = buildJsonObject {
            put("context", buildContext(client, visitor))
            extraFields.forEach { (k, v) -> put(k, v) }
        }
        val bodyStr = json.encodeToString(JsonObject.serializer(), bodyObj)
        val req = Request.Builder()
            .url("https://music.youtube.com/youtubei/v1/$endpoint?prettyPrint=false")
            .post(bodyStr.toRequestBody(jsonMediaType))
            .header("User-Agent", client.userAgent)
            .header("X-YouTube-Client-Name", client.clientId)
            .header("X-YouTube-Client-Version", client.clientVersion)
            .header("Origin", "https://music.youtube.com")
            .header("Referer", "https://music.youtube.com/")
            .header("Accept-Language", "en-US,en;q=0.9")
            .apply {
                if (visitor != null) header("X-Goog-Visitor-Id", visitor)
            }
            .build()

        httpClient.newCall(req).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw IllegalStateException("InnerTube $endpoint HTTP ${resp.code}")
            }
            json.parseToJsonElement(raw)
        }
    }

    suspend fun getHome(params: String? = null): Result<HomePage> = runCatching {
        val fields = buildMap<String, JsonElement> {
            put("browseId", JsonPrimitive("FEmusic_home"))
            if (!params.isNullOrBlank()) put("params", JsonPrimitive(params))
        }
        val root = post("browse", webRemix, fields)
        InnerTubeParsers.parseHome(root)
    }

    suspend fun getMoodsAndGenres(): Result<List<MoodSection>> = runCatching {
        val root = post("browse", webRemix, mapOf("browseId" to JsonPrimitive("FEmusic_moods_and_genres")))
        InnerTubeParsers.parseMoods(root)
    }

    suspend fun getMoodCategory(params: String): Result<HomePage> = runCatching {
        val root = post(
            "browse",
            webRemix,
            mapOf(
                "browseId" to JsonPrimitive("FEmusic_moods_and_genres_category"),
                "params" to JsonPrimitive(params)
            )
        )
        InnerTubeParsers.parseHome(root)
    }

    suspend fun searchAll(query: String): Result<SearchResults> = runCatching {
        val root = post("search", webRemix, mapOf("query" to JsonPrimitive(query)))
        InnerTubeParsers.parseSearchAll(root)
    }

    suspend fun searchSongs(query: String): Result<List<SongItem>> = runCatching {
        val root = post(
            "search",
            webRemix,
            mapOf(
                "query" to JsonPrimitive(query),
                "params" to JsonPrimitive("EgWKAQIIAWoKEAkQBRAKEAMQBA%3D%3D")
            )
        )
        InnerTubeParsers.parseSearchSongs(root)
    }

    suspend fun searchVideos(query: String): Result<List<SongItem>> = runCatching {
        val root = post(
            "search",
            webRemix,
            mapOf(
                "query" to JsonPrimitive(query),
                "params" to JsonPrimitive("EgWKAQIQAWoKEAkQChAFEAMQBA%3D%3D")
            )
        )
        InnerTubeParsers.parseSearchSongs(root)
    }

    suspend fun searchSuggestions(input: String): Result<SearchSuggestions> = runCatching {
        val root = post(
            "music/get_search_suggestions",
            webRemix,
            mapOf("input" to JsonPrimitive(input))
        )
        InnerTubeParsers.parseSearchSuggestions(root)
    }

    suspend fun getAlbum(browseId: String): Result<AlbumPage> = runCatching {
        val root = post("browse", webRemix, mapOf("browseId" to JsonPrimitive(browseId)))
        InnerTubeParsers.parseAlbum(browseId, root)
    }

    suspend fun getArtist(channelId: String): Result<ArtistPage> = runCatching {
        val root = post("browse", webRemix, mapOf("browseId" to JsonPrimitive(channelId)))
        InnerTubeParsers.parseArtist(channelId, root)
    }

    suspend fun getPlaylist(browseId: String): Result<PlaylistPage> = runCatching {
        val id = if (browseId.startsWith("PL") || browseId.startsWith("RD") || browseId.startsWith("OLAK")) {
            "VL$browseId"
        } else {
            browseId
        }
        val root = post("browse", webRemix, mapOf("browseId" to JsonPrimitive(id)))
        InnerTubeParsers.parsePlaylist(browseId, root)
    }

    suspend fun getNext(videoId: String?, playlistId: String? = null): Result<List<SongItem>> = runCatching {
        val fields = buildMap<String, JsonElement> {
            videoId?.let { put("videoId", JsonPrimitive(it)) }
            playlistId?.let { put("playlistId", JsonPrimitive(it)) }
            put("isAudioOnly", JsonPrimitive(true))
        }
        val root = post("next", webRemix, fields)
        InnerTubeParsers.parseNext(root).first
    }

    suspend fun getYouTubeLyrics(videoId: String): Result<Pair<List<com.example.model.LyricLine>, String?>> = runCatching {
        val nextRoot = post(
            "next",
            webRemix,
            mapOf("videoId" to JsonPrimitive(videoId), "isAudioOnly" to JsonPrimitive(true))
        )
        val (_, lyricsBrowseId) = InnerTubeParsers.parseNext(nextRoot)
        if (lyricsBrowseId == null) return@runCatching emptyList<com.example.model.LyricLine>() to null

        val timedRoot = post("browse", iosMusic, mapOf("browseId" to JsonPrimitive(lyricsBrowseId)))
        val timed = InnerTubeParsers.parseTimedLyrics(timedRoot)
        if (timed.isNotEmpty()) return@runCatching timed to null

        val plainRoot = post("browse", webRemix, mapOf("browseId" to JsonPrimitive(lyricsBrowseId)))
        val plain = InnerTubeParsers.parsePlainLyrics(plainRoot)
        emptyList<com.example.model.LyricLine>() to plain
    }

    suspend fun getComments(videoId: String): Result<CommentsPage> = runCatching {
        val tabsRoot = post(
            "next",
            webRemix,
            mapOf("videoId" to JsonPrimitive(videoId), "isAudioOnly" to JsonPrimitive(true))
        )
        val token = InnerTubeParsers.parseCommentsToken(tabsRoot)
            ?: return@runCatching CommentsPage(disabled = true)
        val pageRoot = post("next", webRemix, mapOf("continuation" to JsonPrimitive(token)))
        InnerTubeParsers.parseCommentsPage(pageRoot)
    }

    suspend fun resolveStreamUrl(videoId: String, quality: AudioQuality): Result<ResolvedStream> = runCatching {
        val clients = listOf(androidVr, iosMusic, visionOs, webRemix)
        var lastError: Throwable? = null
        for (client in clients) {
            try {
                val root = post(
                    "player",
                    client,
                    mapOf(
                        "videoId" to JsonPrimitive(videoId),
                        "contentCheckOk" to JsonPrimitive(true),
                        "racyCheckOk" to JsonPrimitive(true)
                    )
                )
                val obj = root.jsonObject
                val status = obj["playabilityStatus"]?.jsonObject?.get("status")?.jsonPrimitive?.contentOrNull
                if (status != "OK") continue
                val streamingData = obj["streamingData"]?.jsonObject ?: continue
                val adaptive = streamingData["adaptiveFormats"]?.jsonArray.orEmpty()
                val formats = streamingData["formats"]?.jsonArray.orEmpty()

                val audioCandidates = (adaptive + formats).mapNotNull { el ->
                    val f = el.jsonObject
                    val url = f["url"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                    val mime = f["mimeType"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    if (!mime.startsWith("audio/")) return@mapNotNull null
                    val bitrate = f["bitrate"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                        ?: f["bitrate"]?.jsonPrimitive?.intOrNull ?: 128000
                    Triple(url, mime, bitrate)
                }

                val chosen = selectFormatForQuality(audioCandidates, quality) ?: continue
                val loudnessDb = obj["playerConfig"]?.jsonObject
                    ?.get("audioConfig")?.jsonObject
                    ?.get("loudnessDb")?.jsonPrimitive?.doubleOrNull

                val musicVideoType = obj["videoDetails"]?.jsonObject
                    ?.get("musicVideoType")?.jsonPrimitive?.contentOrNull
                val isVideo = musicVideoType != null &&
                    musicVideoType != "MUSIC_VIDEO_TYPE_ATV" &&
                    musicVideoType != "MUSIC_VIDEO_TYPE_PRIVATELY_OWNED_TRACK"

                return@runCatching ResolvedStream(
                    videoId = videoId,
                    url = chosen.first,
                    clientName = client.key,
                    userAgent = client.userAgent,
                    mimeType = chosen.second,
                    bitrateKbps = chosen.third / 1000,
                    loudnessDb = loudnessDb,
                    isVideo = isVideo
                )
            } catch (t: Throwable) {
                lastError = t
            }
        }
        throw (lastError ?: IllegalStateException("No playable stream found for $videoId"))
    }

    private fun selectFormatForQuality(
        candidates: List<Triple<String, String, Int>>,
        quality: AudioQuality
    ): Triple<String, String, Int>? {
        if (candidates.isEmpty()) return null
        val targetBps = quality.targetBitrateKbps * 1000
        return when (quality) {
            AudioQuality.HIGH -> candidates.maxByOrNull { it.third }
            AudioQuality.LOW -> candidates.minByOrNull { it.third }
            AudioQuality.MEDIUM -> candidates.minByOrNull { abs(it.third - targetBps) }
        }
    }

    companion object {
        fun fallbackDiscoveryHome(): HomePage = HomePage(
            chips = listOf(
                com.example.model.HomeChip("Energize", ""),
                com.example.model.HomeChip("Focus", ""),
                com.example.model.HomeChip("Relax", ""),
                com.example.model.HomeChip("Workout", ""),
                com.example.model.HomeChip("Feel good", ""),
                com.example.model.HomeChip("Commute", "")
            ),
            sections = emptyList()
        )
    }
}
