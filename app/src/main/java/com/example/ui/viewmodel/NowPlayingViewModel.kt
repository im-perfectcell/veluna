package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.UserPrefsRepository
import com.example.data.local.VelunaUserPrefs
import com.example.data.network.InnerTubeClient
import com.example.data.network.LyricsRepository
import com.example.model.CommentsPage
import com.example.model.EqualizerPresets
import com.example.model.LyricsData
import com.example.model.SongItem
import com.example.playback.PlayerController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LyricsUiState(
    val isLoading: Boolean = false,
    val data: LyricsData? = null,
    val videoId: String? = null
)

data class CommentsUiState(
    val isLoading: Boolean = false,
    val page: CommentsPage? = null,
    val error: String? = null,
    val videoId: String? = null
)

class NowPlayingViewModel(
    private val playerController: PlayerController,
    private val lyricsRepo: LyricsRepository,
    private val innerTube: InnerTubeClient,
    private val prefsRepo: UserPrefsRepository
) : ViewModel() {

    val prefs: StateFlow<VelunaUserPrefs> = prefsRepo.prefsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), VelunaUserPrefs())

    private val _lyricsState = MutableStateFlow(LyricsUiState())
    val lyricsState: StateFlow<LyricsUiState> = _lyricsState.asStateFlow()

    private val _commentsState = MutableStateFlow(CommentsUiState())
    val commentsState: StateFlow<CommentsUiState> = _commentsState.asStateFlow()

    init {
        viewModelScope.launch {
            playerController.state
                .map { it.nowPlaying }
                .distinctUntilChanged { a, b -> a?.videoId == b?.videoId }
                .collect { song ->
                    if (song == null) {
                        _lyricsState.value = LyricsUiState()
                        _commentsState.value = CommentsUiState()
                    } else {
                        loadLyrics(song, forceProviderId = null)
                        _commentsState.value = CommentsUiState(videoId = song.videoId)
                    }
                }
        }
        // Re-validate synced lyrics once ExoPlayer resolves exact track duration
        viewModelScope.launch {
            playerController.positionState
                .map { it.durationMs }
                .distinctUntilChanged()
                .collect { durMs ->
                    if (durMs >= 30_000L) {
                        val cur = _lyricsState.value
                        val data = cur.data
                        val vid = cur.videoId
                        if (data != null && vid != null && data.synced && data.validatedDurationMs != durMs) {
                            val revalidated = lyricsRepo.revalidateAgainstDuration(vid, data, durMs)
                            if (revalidated != data) {
                                _lyricsState.update { st ->
                                    if (st.videoId == vid) st.copy(data = revalidated) else st
                                }
                            }
                        }
                    }
                }
        }
    }

    fun loadLyrics(song: SongItem, forceProviderId: String? = null) {
        viewModelScope.launch {
            _lyricsState.value = LyricsUiState(isLoading = true, videoId = song.videoId)
            val durMs = playerController.positionState.value.durationMs.takeIf { it > 0L }
                ?: (song.durationSeconds * 1000L)
            val result = lyricsRepo.getLyrics(
                song = song,
                providerOrderRaw = prefs.value.lyricsProviderOrder,
                forceProviderId = forceProviderId,
                trackDurationMs = durMs
            )
            _lyricsState.value = LyricsUiState(isLoading = false, data = result, videoId = song.videoId)
        }
    }

    fun nudgeLyricsOffset(deltaMs: Long) {
        val current = _lyricsState.value.data ?: return
        if (!current.synced) return
        val vid = _lyricsState.value.videoId ?: return
        val newOffset = (current.offsetMs + deltaMs).coerceIn(-10_000L, 10_000L)
        viewModelScope.launch {
            val updated = lyricsRepo.updateOffset(vid, newOffset)
            if (updated != null) {
                _lyricsState.update { it.copy(data = updated) }
            }
        }
    }

    fun resetPinnedLyrics() {
        val song = playerController.state.value.nowPlaying ?: return
        viewModelScope.launch {
            _lyricsState.update { it.copy(isLoading = true) }
            val durMs = playerController.positionState.value.durationMs.takeIf { it > 0L }
                ?: (song.durationSeconds * 1000L)
            val result = lyricsRepo.clearPinnedSource(song, prefs.value.lyricsProviderOrder, durMs)
            _lyricsState.value = LyricsUiState(isLoading = false, data = result, videoId = song.videoId)
        }
    }

    fun toggleRomanize() {
        viewModelScope.launch { prefsRepo.setRomanizeLyrics(!prefs.value.romanizeLyrics) }
    }

    fun loadCommentsIfNeeded(song: SongItem) {
        if (song.isLocal) {
            _commentsState.value = CommentsUiState(
                isLoading = false,
                page = CommentsPage(disabled = true),
                videoId = song.videoId
            )
            return
        }
        val cur = _commentsState.value
        if (cur.videoId == song.videoId && cur.page != null) return
        viewModelScope.launch {
            _commentsState.value = CommentsUiState(isLoading = true, videoId = song.videoId)
            innerTube.getComments(song.videoId).onSuccess { page ->
                _commentsState.value = CommentsUiState(isLoading = false, page = page, videoId = song.videoId)
            }.onFailure { err ->
                _commentsState.value = CommentsUiState(
                    isLoading = false,
                    error = err.message ?: "Failed to load comments",
                    videoId = song.videoId
                )
            }
        }
    }

    fun setEqEnabled(enabled: Boolean) {
        viewModelScope.launch {
            prefsRepo.setEqualizer(enabled, prefs.value.eqPresetName, prefs.value.eqBandsDb)
        }
    }

    fun applyEqPreset(name: String) {
        val bands = EqualizerPresets.presets[name] ?: return
        viewModelScope.launch {
            prefsRepo.setEqualizer(true, name, bands)
        }
    }

    fun setEqBand(index: Int, db: Float) {
        val current = prefs.value.eqBandsDb.toMutableList()
        if (index !in current.indices) return
        current[index] = db.coerceIn(-12f, 12f)
        viewModelScope.launch {
            prefsRepo.setEqualizer(true, "Custom", current)
        }
    }

    class Factory(
        private val playerController: PlayerController,
        private val lyricsRepo: LyricsRepository,
        private val innerTube: InnerTubeClient,
        private val prefsRepo: UserPrefsRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            NowPlayingViewModel(playerController, lyricsRepo, innerTube, prefsRepo) as T
    }
}
