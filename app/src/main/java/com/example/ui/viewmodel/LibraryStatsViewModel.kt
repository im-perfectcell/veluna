package com.example.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.BlockedArtistEntity
import com.example.data.local.UserPrefsRepository
import com.example.data.local.VelunaUserPrefs
import com.example.data.repository.VelunaRepository
import com.example.model.AudioQuality
import com.example.model.BrowseItem
import com.example.model.HistoryGroup
import com.example.model.LyricsProviderInfo
import com.example.model.SongItem
import com.example.model.StatsRange
import com.example.model.StatsSummary
import com.example.model.ThemeMode
import com.example.model.VelunaThemePreset
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class StatsUiState(
    val range: StatsRange = StatsRange.MONTH,
    val isLoading: Boolean = true,
    val summary: StatsSummary = StatsSummary(StatsRange.MONTH)
)

data class ImportDialogState(
    val isImporting: Boolean = false,
    val statusText: String = "",
    val progress: Float = 0f,
    val error: String? = null,
    val completedPlaylistId: Long? = null
)

class LibraryStatsViewModel(
    private val repository: VelunaRepository,
    private val prefsRepo: UserPrefsRepository
) : ViewModel() {

    val prefs: StateFlow<VelunaUserPrefs> = prefsRepo.prefsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), VelunaUserPrefs())

    val playlists: StateFlow<List<BrowseItem>> = repository.playlistsBrowseFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val likedSongs: StateFlow<List<SongItem>> = repository.likedSongsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val savedAlbums: StateFlow<List<BrowseItem>> = repository.savedAlbumsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val followedArtists: StateFlow<List<BrowseItem>> = repository.followedArtistsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val localTracks: StateFlow<List<SongItem>> = repository.localTracksFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val downloadedTracks: StateFlow<List<SongItem>> = repository.downloadedTracksFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val historyGroups: StateFlow<List<HistoryGroup>> = repository.observeHistoryGroups()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val blockedArtists: StateFlow<List<BlockedArtistEntity>> = repository.blockedArtistsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _statsState = MutableStateFlow(StatsUiState())
    val statsState: StateFlow<StatsUiState> = _statsState.asStateFlow()

    private val _importState = MutableStateFlow(ImportDialogState())
    val importState: StateFlow<ImportDialogState> = _importState.asStateFlow()

    private val _statusBanner = MutableStateFlow<String?>(null)
    val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

    init {
        refreshStats(StatsRange.MONTH)
    }

    fun consumeStatusBanner() {
        _statusBanner.value = null
    }

    fun postStatus(msg: String) {
        _statusBanner.value = msg
    }

    fun refreshStats(range: StatsRange = _statsState.value.range) {
        viewModelScope.launch {
            _statsState.update { it.copy(range = range, isLoading = true) }
            val summary = repository.computeStats(range)
            _statsState.update { it.copy(range = range, isLoading = false, summary = summary) }
        }
    }

    // --- Playlists & Track Actions ---
    fun createPlaylist(title: String, description: String = "", initialSong: SongItem? = null) {
        if (title.isBlank()) return
        viewModelScope.launch {
            val id = repository.createPlaylist(title, description)
            if (initialSong != null) {
                repository.addTrackToPlaylist(id, initialSong)
            }
            _statusBanner.value = "Created playlist \"$title\""
        }
    }

    fun addTrackToPlaylist(playlistId: Long, song: SongItem) {
        viewModelScope.launch {
            val added = repository.addTrackToPlaylist(playlistId, song)
            _statusBanner.value = if (added) "Added to playlist" else "Track already in playlist"
        }
    }

    fun toggleLike(song: SongItem) {
        viewModelScope.launch { repository.toggleLike(song) }
    }

    fun blockArtist(name: String, channelId: String? = null) {
        viewModelScope.launch {
            repository.blockArtist(name, channelId)
            _statusBanner.value = "Hidden artist \"$name\""
        }
    }

    fun unblockArtist(name: String) {
        viewModelScope.launch { repository.unblockArtist(name) }
    }

    // --- Local Audio Picking ---
    fun importLocalFiles(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val count = repository.importAudioUris(uris)
            _statusBanner.value = "Imported $count audio file(s)"
        }
    }

    fun importLocalFolder(treeUri: Uri) {
        viewModelScope.launch {
            val count = repository.importFolderTree(treeUri)
            _statusBanner.value = "Scanned folder: $count audio track(s)"
        }
    }

    fun removeLocalTrack(uri: String) {
        viewModelScope.launch { repository.removeLocalTrack(uri) }
    }

    // --- Playlist Imports ---
    fun resetImportState() {
        _importState.value = ImportDialogState()
    }

    fun importYouTubePlaylist(url: String) {
        if (url.isBlank()) return
        viewModelScope.launch {
            _importState.value = ImportDialogState(isImporting = true, statusText = "Resolving playlist…", progress = 0.25f)
            repository.importFromYouTubeUrl(url) { status ->
                _importState.update { it.copy(statusText = status, progress = 0.6f) }
            }.onSuccess { id ->
                _importState.value = ImportDialogState(
                    isImporting = false,
                    statusText = "Imported playlist!",
                    progress = 1f,
                    completedPlaylistId = id
                )
            }.onFailure { err ->
                _importState.value = ImportDialogState(
                    isImporting = false,
                    error = err.message ?: "Failed to import playlist"
                )
            }
        }
    }

    fun importCsvOrM3u(uri: Uri, name: String) {
        viewModelScope.launch {
            _importState.value = ImportDialogState(isImporting = true, statusText = "Parsing file…", progress = 0.05f)
            repository.importFromFileUri(uri, name) { done, total, status ->
                _importState.update {
                    it.copy(
                        statusText = "$done/$total • $status",
                        progress = if (total > 0) done.toFloat() / total.toFloat() else 0f
                    )
                }
            }.onSuccess { id ->
                _importState.value = ImportDialogState(
                    isImporting = false,
                    statusText = "Imported playlist!",
                    progress = 1f,
                    completedPlaylistId = id
                )
            }.onFailure { err ->
                _importState.value = ImportDialogState(
                    isImporting = false,
                    error = err.message ?: "Import failed"
                )
            }
        }
    }

    // --- Backup & Restore ---
    suspend fun buildBackupJson(): String = repository.safeExportBackupJson(prefs.value)

    fun restoreBackupJson(raw: String) {
        viewModelScope.launch {
            repository.importBackupJson(raw).onSuccess { count ->
                _statusBanner.value = "Restored settings & $count playlist(s)"
            }.onFailure { err ->
                _statusBanner.value = "Restore failed: ${err.message}"
            }
        }
    }

    // --- Settings ---
    fun setThemePreset(preset: VelunaThemePreset) = viewModelScope.launch { prefsRepo.setThemePreset(preset) }
    fun setThemeMode(mode: ThemeMode) = viewModelScope.launch { prefsRepo.setThemeMode(mode) }
    fun setCustomAccent(hex: String?) = viewModelScope.launch { prefsRepo.setCustomAccent(hex) }
    fun setAdaptToArtwork(v: Boolean) = viewModelScope.launch { prefsRepo.setAdaptToArtwork(v) }
    fun setCornerRadius(dp: Int) = viewModelScope.launch { prefsRepo.setCornerRadius(dp) }
    fun setAudioQuality(q: AudioQuality) = viewModelScope.launch { prefsRepo.setAudioQuality(q) }
    fun setAutoplay(v: Boolean) = viewModelScope.launch { prefsRepo.setAutoplay(v) }
    fun setLoudnessNormalization(v: Boolean) = viewModelScope.launch { prefsRepo.setLoudnessNormalization(v) }
    fun setHideMusicVideos(v: Boolean) = viewModelScope.launch { prefsRepo.setHideMusicVideos(v) }
    fun setShowMoodChips(v: Boolean) = viewModelScope.launch { prefsRepo.setShowMoodChips(v) }
    fun setShowHeroBackdrop(v: Boolean) = viewModelScope.launch { prefsRepo.setShowHeroArtworkBackdrop(v) }
    fun setDisplayName(name: String) = viewModelScope.launch { prefsRepo.setDisplayName(name) }

    fun updateLyricsProviders(list: List<LyricsProviderInfo>) {
        viewModelScope.launch {
            prefsRepo.setLyricsProvidersOrder(UserPrefsRepository.serializeProviderOrder(list))
        }
    }

    fun setScrobbling(
        enabled: Boolean,
        lastFmUser: String,
        lastFmKey: String,
        lastFmSecret: String,
        lastFmSession: String,
        listenBrainzToken: String
    ) {
        viewModelScope.launch {
            prefsRepo.setScrobblingConfig(
                enabled,
                lastFmUser,
                lastFmKey,
                lastFmSecret,
                lastFmSession,
                listenBrainzToken
            )
            _statusBanner.value = "Scrobbling configuration saved"
        }
    }

    class Factory(
        private val repository: VelunaRepository,
        private val prefsRepo: UserPrefsRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            LibraryStatsViewModel(repository, prefsRepo) as T
    }
}
