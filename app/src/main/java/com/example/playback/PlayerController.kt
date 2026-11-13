package com.example.playback

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.palette.graphics.Palette
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.example.data.local.DownloadedTrackEntity
import com.example.data.local.ListenEntity
import com.example.data.local.ListenSongEntity
import com.example.data.local.UserPrefsRepository
import com.example.data.local.VelunaDao
import com.example.data.local.VelunaUserPrefs
import com.example.data.network.InnerTubeClient
import com.example.data.network.ScrobbleClient
import com.example.data.repository.VelunaRepository
import com.example.model.DownloadProgress
import com.example.model.DownloadStatus
import com.example.model.Rating
import com.example.model.RepeatMode
import com.example.model.SongItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

data class PlaybackUiState(
    val queue: List<SongItem> = emptyList(),
    val currentIndex: Int = -1,
    val nowPlaying: SongItem? = null,
    val sourceName: String? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val shuffle: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val speed: Float = 1.0f,
    val streamClient: String? = null,
    val artworkAccentColor: Color? = null,
    val sleepTimerRemainingMs: Long? = null,
    val errorMessage: String? = null,
    val nowPlayingOpen: Boolean = false
)

class PlayerController(
    private val context: Context,
    private val dao: VelunaDao,
    private val repository: VelunaRepository,
    private val prefsRepo: UserPrefsRepository,
    private val innerTube: InnerTubeClient,
    private val scrobbleClient: ScrobbleClient
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _state = MutableStateFlow(PlaybackUiState())
    val state: StateFlow<PlaybackUiState> = _state.asStateFlow()

    private val _downloads = MutableStateFlow<Map<String, DownloadProgress>>(emptyMap())
    val downloads: StateFlow<Map<String, DownloadProgress>> = _downloads.asStateFlow()

    private var currentPrefs = VelunaUserPrefs()
    private var mediaController: MediaController? = null
    private var currentListenId: Long? = null
    private var currentTrackStartedAtSecs: Long = 0L
    private var scrobbledCurrentTrack = false
    private var heardSecondsThisTrack = 0L

    private var tickerJob: Job? = null
    private var sleepJob: Job? = null
    private var resolveJob: Job? = null
    private val imageLoader = ImageLoader(context)

    init {
        scope.launch {
            prefsRepo.prefsFlow.collect { prefs ->
                currentPrefs = prefs
                VelunaPlaybackService.loudnessNormEnabled = prefs.loudnessNormalization
                VelunaPlaybackService.instance?.applyEqualizerState(prefs.eqEnabled, prefs.eqBandsDb)
                mediaController?.playbackParameters = PlaybackParameters(prefs.playbackSpeed)
                _state.update { it.copy(speed = prefs.playbackSpeed) }
            }
        }
        connectService()
    }

    private fun connectService() {
        val token = SessionToken(context, ComponentName(context, VelunaPlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener(
            {
                val controller = runCatching { future.get() }.getOrNull() ?: return@addListener
                mediaController = controller
                controller.playbackParameters = PlaybackParameters(currentPrefs.playbackSpeed)
                controller.addListener(playerListener)
                startTicker()
            },
            ContextCompat.getMainExecutor(context)
        )
    }

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _state.update { it.copy(isPlaying = isPlaying) }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            val buffering = playbackState == Player.STATE_BUFFERING
            _state.update { it.copy(isBuffering = buffering) }
            if (playbackState == Player.STATE_ENDED) {
                onTrackEnded()
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            _state.update {
                it.copy(
                    isBuffering = false,
                    isPlaying = false,
                    errorMessage = error.localizedMessage ?: "Playback failed"
                )
            }
        }
    }

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = scope.launch {
            var subTick = 0
            while (true) {
                val ctrl = mediaController
                if (ctrl != null) {
                    val pos = ctrl.currentPosition.coerceAtLeast(0L)
                    val dur = ctrl.duration.takeIf { it > 0 } ?: 0L
                    _state.update { it.copy(positionMs = pos, durationMs = dur) }

                    if (ctrl.isPlaying) {
                        subTick++
                        if (subTick >= 4) {
                            subTick = 0
                            recordOneSecondHeard()
                        }
                    }
                }
                delay(250L)
            }
        }
    }

    private fun recordOneSecondHeard() {
        val song = _state.value.nowPlaying ?: return
        heardSecondsThisTrack++
        val lid = currentListenId
        scope.launch(Dispatchers.IO) {
            if (lid != null) {
                dao.addListenSeconds(lid, 1L)
            }
            if (!scrobbledCurrentTrack && heardSecondsThisTrack >= 30L) {
                scrobbledCurrentTrack = true
                scrobbleClient.submitScrobble(song, currentTrackStartedAtSecs, currentPrefs)
            }
        }
    }

    fun openNowPlaying(open: Boolean) {
        _state.update { it.copy(nowPlayingOpen = open) }
    }

    fun playSong(song: SongItem, sourceName: String? = null, openPlayer: Boolean = true) {
        playQueue(listOf(song), 0, sourceName ?: song.title, openPlayer)
    }

    fun playQueue(
        items: List<SongItem>,
        startIndex: Int = 0,
        sourceName: String? = null,
        openPlayer: Boolean = true
    ) {
        if (items.isEmpty()) return
        val safeIdx = startIndex.coerceIn(0, items.lastIndex)
        _state.update {
            it.copy(
                queue = items,
                currentIndex = safeIdx,
                sourceName = sourceName,
                nowPlayingOpen = if (openPlayer) true else it.nowPlayingOpen,
                errorMessage = null
            )
        }
        playIndex(safeIdx)
        if (currentPrefs.autoplay && items.size == 1 && !items[0].isLocal) {
            fetchAutoplayContinuation(items[0])
        }
    }

    fun playNext(song: SongItem) {
        val cur = _state.value
        if (cur.queue.isEmpty()) {
            playSong(song)
            return
        }
        val insertAt = (cur.currentIndex + 1).coerceAtMost(cur.queue.size)
        val updated = cur.queue.toMutableList().apply {
            add(insertAt, song.copy(queued = true))
        }
        _state.update { it.copy(queue = updated) }
    }

    fun addToQueue(song: SongItem) {
        val cur = _state.value
        if (cur.queue.isEmpty()) {
            playSong(song)
            return
        }
        val updated = cur.queue + song.copy(queuedEnd = true)
        _state.update { it.copy(queue = updated) }
    }

    fun removeFromQueue(index: Int) {
        val cur = _state.value
        if (index !in cur.queue.indices) return
        val updated = cur.queue.toMutableList().apply { removeAt(index) }
        val newIdx = when {
            updated.isEmpty() -> -1
            index < cur.currentIndex -> cur.currentIndex - 1
            index == cur.currentIndex -> cur.currentIndex.coerceAtMost(updated.lastIndex)
            else -> cur.currentIndex
        }
        _state.update { it.copy(queue = updated, currentIndex = newIdx) }
        if (index == cur.currentIndex && newIdx >= 0) {
            playIndex(newIdx)
        } else if (updated.isEmpty()) {
            mediaController?.stop()
            _state.update { it.copy(nowPlaying = null, isPlaying = false) }
        }
    }

    fun clearQueue() {
        val cur = _state.value
        val active = cur.nowPlaying
        if (active != null) {
            _state.update { it.copy(queue = listOf(active), currentIndex = 0) }
        } else {
            mediaController?.stop()
            _state.update { it.copy(queue = emptyList(), currentIndex = -1, nowPlaying = null) }
        }
    }

    fun togglePlayPause() {
        val ctrl = mediaController ?: return
        if (ctrl.isPlaying) ctrl.pause() else ctrl.play()
    }

    fun seekTo(positionMs: Long) {
        mediaController?.seekTo(positionMs.coerceAtLeast(0L))
        _state.update { it.copy(positionMs = positionMs.coerceAtLeast(0L)) }
    }

    fun skipNext() {
        val cur = _state.value
        if (cur.queue.isEmpty()) return
        val nextIdx = if (cur.shuffle && cur.queue.size > 1) {
            val candidates = cur.queue.indices.filter { it != cur.currentIndex }
            candidates.random()
        } else {
            cur.currentIndex + 1
        }

        if (nextIdx in cur.queue.indices) {
            playIndex(nextIdx)
        } else if (cur.repeatMode == RepeatMode.ALL && cur.queue.isNotEmpty()) {
            playIndex(0)
        }
    }

    fun skipPrevious() {
        val cur = _state.value
        if (cur.positionMs > 4000L) {
            seekTo(0L)
            return
        }
        val prevIdx = cur.currentIndex - 1
        if (prevIdx in cur.queue.indices) {
            playIndex(prevIdx)
        } else {
            seekTo(0L)
        }
    }

    private fun onTrackEnded() {
        val cur = _state.value
        when {
            cur.repeatMode == RepeatMode.ONE -> {
                seekTo(0L)
                mediaController?.play()
            }
            cur.currentIndex + 1 in cur.queue.indices -> {
                skipNext()
            }
            cur.repeatMode == RepeatMode.ALL && cur.queue.isNotEmpty() -> {
                playIndex(0)
            }
            currentPrefs.autoplay && cur.nowPlaying != null && !cur.nowPlaying.isLocal -> {
                fetchAutoplayContinuation(cur.nowPlaying, playImmediately = true)
            }
        }
    }

    fun toggleShuffle() {
        _state.update { it.copy(shuffle = !it.shuffle) }
    }

    fun cycleRepeat() {
        _state.update {
            val next = when (it.repeatMode) {
                RepeatMode.OFF -> RepeatMode.ALL
                RepeatMode.ALL -> RepeatMode.ONE
                RepeatMode.ONE -> RepeatMode.OFF
            }
            it.copy(repeatMode = next)
        }
    }

    fun setPlaybackSpeed(speed: Float) {
        scope.launch { prefsRepo.setPlaybackSpeed(speed) }
        mediaController?.playbackParameters = PlaybackParameters(speed)
    }

    fun toggleLikeCurrent() {
        val song = _state.value.nowPlaying ?: return
        scope.launch {
            val liked = repository.toggleLike(song)
            val newRating = if (liked) Rating.LIKE else Rating.INDIFFERENT
            _state.update { st ->
                st.copy(
                    nowPlaying = st.nowPlaying?.copy(rating = newRating),
                    queue = st.queue.map { if (it.videoId == song.videoId) it.copy(rating = newRating) else it }
                )
            }
        }
    }

    fun setSleepTimer(minutes: Int?) {
        sleepJob?.cancel()
        if (minutes == null || minutes <= 0) {
            _state.update { it.copy(sleepTimerRemainingMs = null) }
            return
        }
        var remaining = minutes * 60_000L
        _state.update { it.copy(sleepTimerRemainingMs = remaining) }
        sleepJob = scope.launch {
            while (remaining > 0L) {
                delay(1000L)
                remaining -= 1000L
                _state.update { it.copy(sleepTimerRemainingMs = remaining.coerceAtLeast(0L)) }
            }
            mediaController?.pause()
            _state.update { it.copy(sleepTimerRemainingMs = null) }
        }
    }

    fun startRadio(song: SongItem) {
        scope.launch {
            val radio = innerTube.getNext(song.videoId).getOrNull().orEmpty()
            val list = (listOf(song) + radio.filter { it.videoId != song.videoId })
            playQueue(list, 0, "${song.title} Radio")
        }
    }

    fun playIndex(index: Int) {
        val cur = _state.value
        val song = cur.queue.getOrNull(index) ?: return
        resolveJob?.cancel()

        resolveJob = scope.launch {
            val isLiked = repository.isLiked(song.videoId)
            val hydratedSong = song.copy(rating = if (isLiked) Rating.LIKE else song.rating)

            _state.update {
                it.copy(
                    currentIndex = index,
                    nowPlaying = hydratedSong,
                    isBuffering = true,
                    positionMs = 0L,
                    errorMessage = null
                )
            }

            extractArtworkAccent(hydratedSong.thumbnail)
            recordListenStart(hydratedSong)

            // 1) Local audio file
            if (hydratedSong.isLocal && hydratedSong.localUri != null) {
                playUri(Uri.parse(hydratedSong.localUri), hydratedSong, "Local File", null)
                return@launch
            }

            // 2) Offline downloaded track
            val downloaded = withContext(Dispatchers.IO) { dao.getDownload(hydratedSong.videoId) }
            if (downloaded != null && File(downloaded.filePath).exists()) {
                playUri(Uri.fromFile(File(downloaded.filePath)), hydratedSong, "Offline", null)
                return@launch
            }

            // 3) Resolve stream via InnerTube orchestrator
            val resolved = innerTube.resolveStreamUrl(hydratedSong.videoId, currentPrefs.audioQuality)
            resolved.onSuccess { stream ->
                playUri(Uri.parse(stream.url), hydratedSong, stream.clientName, stream.loudnessDb)
            }.onFailure { err ->
                _state.update {
                    it.copy(
                        isBuffering = false,
                        isPlaying = false,
                        errorMessage = err.message ?: "Unable to resolve audio stream"
                    )
                }
            }

            if (currentPrefs.autoplay && index >= _state.value.queue.lastIndex - 1 && !hydratedSong.isLocal) {
                fetchAutoplayContinuation(hydratedSong)
            }
        }
    }

    private fun playUri(uri: Uri, song: SongItem, clientName: String, loudnessDb: Double?) {
        val ctrl = mediaController ?: return
        val metadata = MediaMetadata.Builder()
            .setTitle(song.title)
            .setArtist(song.artists)
            .setAlbumTitle(song.album)
            .apply {
                song.thumbnail?.let { setArtworkUri(Uri.parse(it)) }
            }
            .build()

        val mediaItem = MediaItem.Builder()
            .setMediaId(song.videoId)
            .setUri(uri)
            .setMediaMetadata(metadata)
            .build()

        VelunaPlaybackService.instance?.applyLoudnessNormalization(loudnessDb)
        ctrl.setMediaItem(mediaItem)
        ctrl.prepare()
        ctrl.play()

        _state.update { it.copy(streamClient = clientName, isBuffering = false) }
    }

    private fun recordListenStart(song: SongItem) {
        heardSecondsThisTrack = 0L
        scrobbledCurrentTrack = false
        val nowMs = System.currentTimeMillis()
        currentTrackStartedAtSecs = nowMs / 1000L
        scope.launch(Dispatchers.IO) {
            dao.upsertListenSong(ListenSongEntity(song.videoId, repository.encodeSong(song)))
            currentListenId = dao.insertListen(
                ListenEntity(videoId = song.videoId, playedAt = nowMs, secs = 0L)
            )
            scrobbleClient.submitNowPlaying(song, currentPrefs)
        }
    }

    private fun fetchAutoplayContinuation(seed: SongItem, playImmediately: Boolean = false) {
        scope.launch {
            val nextSongs = innerTube.getNext(seed.videoId).getOrNull().orEmpty()
            val existingIds = _state.value.queue.map { it.videoId }.toHashSet()
            val fresh = nextSongs.filter { it.videoId !in existingIds }
                .map { it.copy(autoplay = true) }
            if (fresh.isNotEmpty()) {
                val startNextIdx = _state.value.queue.size
                _state.update { it.copy(queue = it.queue + fresh) }
                if (playImmediately) {
                    playIndex(startNextIdx)
                }
            }
        }
    }

    private fun extractArtworkAccent(url: String?) {
        if (url.isNullOrBlank()) {
            _state.update { it.copy(artworkAccentColor = null) }
            return
        }
        scope.launch(Dispatchers.IO) {
            val bmp = loadBitmap(url) ?: return@launch
            val palette = Palette.from(bmp).generate()
            val swatch = palette.vibrantSwatch ?: palette.lightVibrantSwatch ?: palette.dominantSwatch
            if (swatch != null) {
                _state.update { it.copy(artworkAccentColor = Color(swatch.rgb)) }
            }
        }
    }

    private suspend fun loadBitmap(url: String): Bitmap? = withContext(Dispatchers.IO) {
        runCatching {
            val req = ImageRequest.Builder(context)
                .data(url)
                .allowHardware(false)
                .size(160)
                .build()
            val res = imageLoader.execute(req)
            ((res as? SuccessResult)?.drawable as? BitmapDrawable)?.bitmap
        }.getOrNull()
    }

    fun downloadTrack(song: SongItem) {
        if (song.isLocal) return
        if (_downloads.value[song.videoId]?.status == DownloadStatus.DOWNLOADING) return

        scope.launch(Dispatchers.IO) {
            _downloads.update {
                it + (song.videoId to DownloadProgress(
                    videoId = song.videoId,
                    title = song.title,
                    artists = song.artists,
                    thumbnail = song.thumbnail,
                    progress = 0f,
                    bytesDownloaded = 0L,
                    totalBytes = 0L,
                    status = DownloadStatus.DOWNLOADING
                ))
            }

            try {
                val stream = innerTube.resolveStreamUrl(song.videoId, currentPrefs.audioQuality).getOrThrow()
                val ext = when {
                    "webm" in stream.mimeType || "opus" in stream.mimeType -> "opus"
                    else -> "m4a"
                }
                val dir = File(context.filesDir, "downloads").apply { mkdirs() }
                val outFile = File(dir, "${song.videoId}.$ext")

                val req = Request.Builder()
                    .url(stream.url)
                    .header("User-Agent", stream.userAgent)
                    .build()

                innerTube.httpClient.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) throw IllegalStateException("HTTP ${resp.code}")
                    val body = resp.body ?: throw IllegalStateException("Empty body")
                    val total = body.contentLength()
                    var downloaded = 0L
                    val buf = ByteArray(16 * 1024)
                    body.byteStream().use { input ->
                        FileOutputStream(outFile).use { output ->
                            while (true) {
                                val read = input.read(buf)
                                if (read <= 0) break
                                output.write(buf, 0, read)
                                downloaded += read
                                val pct = if (total > 0) (downloaded.toFloat() / total.toFloat()).coerceIn(0f, 1f) else 0.5f
                                _downloads.update { map ->
                                    map + (song.videoId to map.getValue(song.videoId).copy(
                                        progress = pct,
                                        bytesDownloaded = downloaded,
                                        totalBytes = total
                                    ))
                                }
                            }
                        }
                    }

                    dao.upsertDownload(
                        DownloadedTrackEntity(
                            videoId = song.videoId,
                            filePath = outFile.absolutePath,
                            format = ext.uppercase(),
                            bitrateKbps = stream.bitrateKbps,
                            sizeBytes = outFile.length(),
                            songJson = repository.encodeSong(song)
                        )
                    )

                    _downloads.update { map ->
                        map + (song.videoId to map.getValue(song.videoId).copy(
                            progress = 1f,
                            status = DownloadStatus.COMPLETED
                        ))
                    }
                }
            } catch (t: Throwable) {
                _downloads.update { map ->
                    map + (song.videoId to map.getValue(song.videoId).copy(
                        status = DownloadStatus.FAILED,
                        error = t.message
                    ))
                }
            }
        }
    }

    fun deleteDownload(videoId: String) {
        scope.launch(Dispatchers.IO) {
            dao.getDownload(videoId)?.let { File(it.filePath).delete() }
            dao.deleteDownload(videoId)
            _downloads.update { it - videoId }
        }
    }
}
