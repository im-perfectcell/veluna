package com.example.playback

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
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
import com.example.data.repository.TasteEngine
import com.example.data.repository.VelunaRepository
import com.example.model.DownloadProgress
import com.example.model.DownloadStatus
import com.example.model.Rating
import com.example.model.RepeatMode
import com.example.model.ResolvedStream
import com.example.model.SongItem
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

@Immutable
data class PlaybackUiState(
    val queue: List<SongItem> = emptyList(),
    val currentIndex: Int = -1,
    val nowPlaying: SongItem? = null,
    val sourceName: String? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val shuffle: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val speed: Float = 1.0f,
    val streamClient: String? = null,
    val artworkAccentColor: Color? = null,
    val sleepTimerRemainingMs: Long? = null,
    val errorMessage: String? = null,
    val nowPlayingOpen: Boolean = false
)

@Immutable
data class PlaybackPositionState(
    val positionMs: Long = 0L,
    val durationMs: Long = 0L
)

class PlayerController(
    private val context: Context,
    private val dao: VelunaDao,
    private val repository: VelunaRepository,
    private val prefsRepo: UserPrefsRepository,
    private val innerTube: InnerTubeClient,
    private val scrobbleClient: ScrobbleClient,
    private val tasteEngine: TasteEngine? = null
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _state = MutableStateFlow(PlaybackUiState())
    val state: StateFlow<PlaybackUiState> = _state.asStateFlow()

    private val _positionState = MutableStateFlow(PlaybackPositionState())
    val positionState: StateFlow<PlaybackPositionState> = _positionState.asStateFlow()

    private val _downloads = MutableStateFlow<Map<String, DownloadProgress>>(emptyMap())
    val downloads: StateFlow<Map<String, DownloadProgress>> = _downloads.asStateFlow()

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val events: SharedFlow<String> = _events.asSharedFlow()

    private var currentPrefs = VelunaUserPrefs()
    private var mediaController: MediaController? = null
    private val controllerReady = CompletableDeferred<MediaController>()
    private var currentListenId: Long? = null
    private var currentTrackStartedAtSecs: Long = 0L
    private var scrobbledCurrentTrack = false
    private var heardSecondsThisTrack = 0L
    private var pendingFlushSecs = 0L

    private var activeCandidates: List<ResolvedStream> = emptyList()
    private var activeCandidateIndex: Int = -1
    private var triedMirrorsForCurrent: Boolean = false
    private var currentCandidateSong: SongItem? = null

    private var tickerJob: Job? = null
    private var sleepJob: Job? = null
    private var resolveJob: Job? = null
    private var prefetchJob: Job? = null
    private var autoplayJob: Job? = null
    private var radioJob: Job? = null
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
        scope.launch {
            repository.likedVideoIdsFlow.collect { likedIds ->
                _state.update { st ->
                    val updatedNow = st.nowPlaying?.let { np ->
                        val desired = if (np.videoId in likedIds) Rating.LIKE else Rating.INDIFFERENT
                        if (np.rating != desired) np.copy(rating = desired) else np
                    }
                    val updatedQueue = st.queue.map { item ->
                        val desired = if (item.videoId in likedIds) Rating.LIKE else Rating.INDIFFERENT
                        if (item.rating != desired) item.copy(rating = desired) else item
                    }
                    st.copy(nowPlaying = updatedNow, queue = updatedQueue)
                }
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
                if (!controllerReady.isCompleted) {
                    controllerReady.complete(controller)
                }
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
            if (!isPlaying) {
                flushListenSeconds()
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            val buffering = playbackState == Player.STATE_BUFFERING
            _state.update { it.copy(isBuffering = buffering) }
            if (playbackState == Player.STATE_READY) {
                val ctrl = mediaController
                if (ctrl != null) {
                    val dur = ctrl.duration.takeIf { it > 0 } ?: 0L
                    if (dur > 0L && _positionState.value.durationMs != dur) {
                        _positionState.update { it.copy(durationMs = dur) }
                    }
                }
            } else if (playbackState == Player.STATE_ENDED) {
                flushListenSeconds()
                onTrackEnded()
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            val song = currentCandidateSong
            if (song != null && !song.isLocal) {
                innerTube.invalidateStreamCache(song.videoId)
                val nextIdx = activeCandidateIndex + 1
                if (nextIdx in activeCandidates.indices) {
                    scope.launch {
                        tryCandidateStream(song, nextIdx)
                    }
                    return
                }
                if (!triedMirrorsForCurrent) {
                    triedMirrorsForCurrent = true
                    scope.launch {
                        val mirrors = innerTube.resolveMirrorStreamCandidates(song.videoId, currentPrefs.audioQuality)
                        if (mirrors.isNotEmpty()) {
                            val start = activeCandidates.size
                            activeCandidates = activeCandidates + mirrors
                            tryCandidateStream(song, start)
                            return@launch
                        }
                        surfacePlaybackError(error)
                    }
                    return
                }
            }
            surfacePlaybackError(error)
        }
    }

    private fun surfacePlaybackError(error: PlaybackException) {
        _state.update {
            it.copy(
                isBuffering = false,
                isPlaying = false,
                errorMessage = "Playback failed (${error.errorCodeName}): ${error.localizedMessage ?: "Source unavailable"}"
            )
        }
    }

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = scope.launch {
            var subTick = 0
            while (true) {
                val ctrl = mediaController
                if (ctrl != null && (ctrl.isPlaying || _state.value.nowPlayingOpen || _state.value.isBuffering)) {
                    val pos = ctrl.currentPosition.coerceAtLeast(0L)
                    val dur = ctrl.duration.takeIf { it > 0 } ?: _positionState.value.durationMs
                    val curPosState = _positionState.value
                    if (curPosState.positionMs != pos || curPosState.durationMs != dur) {
                        _positionState.value = PlaybackPositionState(positionMs = pos, durationMs = dur)
                    }

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
        pendingFlushSecs++
        if (pendingFlushSecs >= 15L) {
            flushListenSeconds()
        }
        if (!scrobbledCurrentTrack && heardSecondsThisTrack >= 30L) {
            scrobbledCurrentTrack = true
            scope.launch(Dispatchers.IO) {
                scrobbleClient.submitScrobble(song, currentTrackStartedAtSecs, currentPrefs)
            }
        }
    }

    private fun flushListenSeconds() {
        val lid = currentListenId ?: return
        val delta = pendingFlushSecs
        if (delta <= 0L) return
        pendingFlushSecs = 0L
        scope.launch(Dispatchers.IO) {
            dao.addListenSeconds(lid, delta)
        }
    }

    private fun finalizeCurrentTrackOutcome() {
        flushListenSeconds()
        val prevSong = _state.value.nowPlaying ?: return
        val heard = heardSecondsThisTrack
        if (heard <= 0L) return
        val durSecs = (_positionState.value.durationMs / 1000L).takeIf { it > 0L } ?: prevSong.durationSeconds
        scope.launch {
            tasteEngine?.recordPlaybackOutcome(prevSong, heard, durSecs)
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
        _events.tryEmit("Playing next: ${song.title}")
    }

    fun addToQueue(song: SongItem) {
        val cur = _state.value
        if (cur.queue.isEmpty()) {
            playSong(song)
            return
        }
        val updated = cur.queue + song.copy(queuedEnd = true)
        _state.update { it.copy(queue = updated) }
        _events.tryEmit("Added to queue: ${song.title}")
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
            finalizeCurrentTrackOutcome()
            mediaController?.stop()
            _state.update { it.copy(nowPlaying = null, isPlaying = false) }
            _positionState.value = PlaybackPositionState()
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
            _positionState.value = PlaybackPositionState()
        }
    }

    fun togglePlayPause() {
        val ctrl = mediaController ?: return
        if (ctrl.isPlaying) {
            flushListenSeconds()
            ctrl.pause()
        } else {
            ctrl.play()
        }
    }

    fun seekTo(positionMs: Long) {
        val clamped = positionMs.coerceAtLeast(0L)
        mediaController?.seekTo(clamped)
        _positionState.update { it.copy(positionMs = clamped) }
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
        } else if (currentPrefs.autoplay && cur.nowPlaying != null && !cur.nowPlaying.isLocal) {
            fetchAutoplayContinuation(cur.nowPlaying, playImmediately = true)
        }
    }

    fun skipPrevious() {
        if (_positionState.value.positionMs > 4000L) {
            seekTo(0L)
            return
        }
        val cur = _state.value
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
        toggleLike(song)
    }

    fun toggleLike(song: SongItem) {
        scope.launch {
            val liked = repository.toggleLike(song)
            val newRating = if (liked) Rating.LIKE else Rating.INDIFFERENT
            _state.update { st ->
                val updatedNow = if (st.nowPlaying?.videoId == song.videoId) {
                    st.nowPlaying.copy(rating = newRating)
                } else {
                    st.nowPlaying
                }
                st.copy(
                    nowPlaying = updatedNow,
                    queue = st.queue.map { if (it.videoId == song.videoId) it.copy(rating = newRating) else it }
                )
            }
            tasteEngine?.recordLikeChange(song, liked)
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
        if (song.isLocal) return
        radioJob?.cancel()
        val cur = _state.value
        val alreadyPlayingThisSong = cur.nowPlaying?.videoId == song.videoId && cur.currentIndex in cur.queue.indices

        if (!alreadyPlayingThisSong) {
            // Start playing the seed track immediately without blocking on the radio mix network call
            playQueue(listOf(song), 0, "${song.title} Radio")
        } else {
            _state.update { it.copy(sourceName = "${song.title} Radio") }
        }

        radioJob = scope.launch {
            val radioTracks = if (tasteEngine != null) {
                tasteEngine.generateSmartAutoplayTracks(
                    seed = song,
                    existingQueueIds = setOf(song.videoId),
                    limit = 25
                )
            } else {
                innerTube.getNext(song.videoId, playlistId = "RDAMVM${song.videoId}").getOrNull().orEmpty()
                    .filter { it.videoId != song.videoId }
                    .distinctBy { it.videoId }
                    .take(25)
                    .map { it.copy(autoplay = true) }
            }

            val finalTracks = if (radioTracks.isNotEmpty()) {
                radioTracks
            } else {
                val artistLead = song.artists.substringBefore(",").trim()
                innerTube.searchSongs("$artistLead ${song.title}".trim()).getOrNull().orEmpty()
                    .filter { it.videoId != song.videoId }
                    .distinctBy { it.videoId }
                    .take(20)
                    .map { it.copy(autoplay = true) }
            }

            if (finalTracks.isNotEmpty()) {
                _state.update { st ->
                    val currentIdx = st.currentIndex
                    val keepPrefix = if (currentIdx in st.queue.indices) {
                        st.queue.subList(0, currentIdx + 1).toList()
                    } else {
                        listOf(song)
                    }
                    val prefixIds = keepPrefix.map { it.videoId }.toHashSet()
                    val filteredRadio = finalTracks.filter { it.videoId !in prefixIds }
                    st.copy(
                        queue = keepPrefix + filteredRadio,
                        currentIndex = if (currentIdx >= 0) currentIdx else 0,
                        sourceName = "${song.title} Radio"
                    )
                }
                _events.tryEmit("Queued ${finalTracks.size} radio songs")
                prefetchNextInQueue()
            } else {
                _events.tryEmit("Could not find radio tracks right now")
            }
        }
    }

    fun playIndex(index: Int) {
        val cur = _state.value
        val song = cur.queue.getOrNull(index) ?: return
        if (cur.nowPlaying != null) {
            finalizeCurrentTrackOutcome()
        }
        resolveJob?.cancel()
        prefetchJob?.cancel()

        resolveJob = scope.launch {
            val isLiked = withContext(Dispatchers.IO) { repository.isLiked(song.videoId) }
            val hydratedSong = song.copy(rating = if (isLiked) Rating.LIKE else Rating.INDIFFERENT)

            activeCandidates = emptyList()
            activeCandidateIndex = -1
            triedMirrorsForCurrent = false
            currentCandidateSong = hydratedSong

            _state.update {
                it.copy(
                    currentIndex = index,
                    nowPlaying = hydratedSong,
                    isBuffering = true,
                    errorMessage = null
                )
            }
            _positionState.value = PlaybackPositionState(
                positionMs = 0L,
                durationMs = hydratedSong.durationSeconds * 1000L
            )

            extractArtworkAccent(hydratedSong.thumbnail)
            recordListenStart(hydratedSong)

            // 1) Local audio file
            if (hydratedSong.isLocal && hydratedSong.localUri != null) {
                playUri(Uri.parse(hydratedSong.localUri), hydratedSong, "Local File", null, null)
                prefetchNextInQueue()
                return@launch
            }

            // 2) Offline downloaded track
            val downloaded = withContext(Dispatchers.IO) { dao.getDownload(hydratedSong.videoId) }
            if (downloaded != null && File(downloaded.filePath).exists()) {
                playUri(Uri.fromFile(File(downloaded.filePath)), hydratedSong, "Offline", null, null)
                prefetchNextInQueue()
                return@launch
            }

            // 3) Resolve prioritized stream candidates via Hedged InnerTube + Parallel Mirror Race
            val resolved = innerTube.resolveStreamCandidates(hydratedSong.videoId, currentPrefs.audioQuality)
            resolved.onSuccess { list ->
                activeCandidates = list
                tryCandidateStream(hydratedSong, 0)
                prefetchNextInQueue()
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

    private fun prefetchNextInQueue() {
        val nextSong = _state.value.queue.getOrNull(_state.value.currentIndex + 1) ?: return
        if (nextSong.isLocal) return
        prefetchJob?.cancel()
        prefetchJob = scope.launch(Dispatchers.IO) {
            delay(900L)
            runCatching {
                innerTube.resolveStreamCandidates(nextSong.videoId, currentPrefs.audioQuality)
            }
        }
    }

    private suspend fun tryCandidateStream(song: SongItem, idx: Int) {
        val candidate = activeCandidates.getOrNull(idx) ?: return
        activeCandidateIndex = idx
        VelunaPlaybackService.registerStreamHeaders(candidate.url, candidate.userAgent)
        val mimeOverride = if (candidate.isHls) MimeTypes.APPLICATION_M3U8 else null
        playUri(
            uri = Uri.parse(candidate.url),
            song = song,
            clientName = candidate.clientName,
            loudnessDb = candidate.loudnessDb,
            mimeTypeOverride = mimeOverride
        )
    }

    private suspend fun playUri(
        uri: Uri,
        song: SongItem,
        clientName: String,
        loudnessDb: Double?,
        mimeTypeOverride: String?
    ) {
        val ctrl = mediaController ?: withTimeoutOrNull(5000L) { controllerReady.await() } ?: return
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
            .apply {
                if (mimeTypeOverride != null) setMimeType(mimeTypeOverride)
            }
            .setMediaMetadata(metadata)
            .build()

        VelunaPlaybackService.instance?.applyLoudnessNormalization(loudnessDb)
        ctrl.setMediaItem(mediaItem)
        ctrl.prepare()
        ctrl.play()

        _state.update { it.copy(streamClient = clientName, isBuffering = false, errorMessage = null) }
    }

    private fun recordListenStart(song: SongItem) {
        heardSecondsThisTrack = 0L
        pendingFlushSecs = 0L
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
        if (autoplayJob?.isActive == true && !playImmediately) return
        autoplayJob?.cancel()
        autoplayJob = scope.launch {
            val existingIds = _state.value.queue.map { it.videoId }.toHashSet()
            val fresh = if (tasteEngine != null) {
                tasteEngine.generateSmartAutoplayTracks(seed, existingIds, limit = 16)
            } else {
                innerTube.getNext(seed.videoId, playlistId = "RDAMVM${seed.videoId}").getOrNull().orEmpty()
                    .filter { it.videoId !in existingIds }
                    .map { it.copy(autoplay = true) }
            }
            if (fresh.isNotEmpty()) {
                val startNextIdx = _state.value.queue.size
                _state.update { it.copy(queue = it.queue + fresh) }
                if (playImmediately) {
                    playIndex(startNextIdx)
                } else {
                    prefetchNextInQueue()
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
                .size(120)
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
