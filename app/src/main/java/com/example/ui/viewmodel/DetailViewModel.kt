package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.network.InnerTubeClient
import com.example.data.repository.TasteEngine
import com.example.data.repository.VelunaRepository
import com.example.model.AlbumPage
import com.example.model.ArtistPage
import com.example.model.BrowseItem
import com.example.model.BrowseKind
import com.example.model.PlaylistPage
import com.example.model.SongItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AlbumDetailState(
    val isLoading: Boolean = true,
    val page: AlbumPage? = null,
    val error: String? = null
)

data class ArtistDetailState(
    val isLoading: Boolean = true,
    val page: ArtistPage? = null,
    val error: String? = null
)

data class PlaylistDetailState(
    val isLoading: Boolean = true,
    val page: PlaylistPage? = null,
    val error: String? = null,
    val filterQuery: String = "",
    val sortMode: PlaylistSortMode = PlaylistSortMode.CUSTOM
)

enum class PlaylistSortMode(val label: String) {
    CUSTOM("Custom"),
    TITLE("Title"),
    ARTIST("Artist"),
    RECENT("Recently added")
}

class DetailViewModel(
    private val innerTube: InnerTubeClient,
    private val repository: VelunaRepository,
    private val tasteEngine: TasteEngine? = null
) : ViewModel() {

    private val _albumState = MutableStateFlow(AlbumDetailState())
    val albumState: StateFlow<AlbumDetailState> = _albumState.asStateFlow()

    private val _artistState = MutableStateFlow(ArtistDetailState())
    val artistState: StateFlow<ArtistDetailState> = _artistState.asStateFlow()

    private val _playlistState = MutableStateFlow(PlaylistDetailState())
    val playlistState: StateFlow<PlaylistDetailState> = _playlistState.asStateFlow()

    private var currentAlbumId: String? = null
    private var currentArtistId: String? = null
    private var currentPlaylistId: String? = null

    fun observeIsAlbumSaved(albumId: String): StateFlow<Boolean> =
        repository.observeIsAlbumSaved(albumId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun observeIsArtistFollowed(channelId: String): StateFlow<Boolean> =
        repository.observeIsArtistFollowed(channelId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun loadAlbum(albumId: String, force: Boolean = false) {
        if (!force && currentAlbumId == albumId && _albumState.value.page != null) return
        currentAlbumId = albumId
        viewModelScope.launch {
            _albumState.value = AlbumDetailState(isLoading = true)
            innerTube.getAlbum(albumId).onSuccess { page ->
                _albumState.value = AlbumDetailState(isLoading = false, page = page)
            }.onFailure { err ->
                _albumState.value = AlbumDetailState(isLoading = false, error = err.message ?: "Failed to load album")
            }
        }
    }

    fun toggleSaveAlbum(currentlySaved: Boolean) {
        val page = _albumState.value.page ?: return
        viewModelScope.launch {
            repository.setAlbumSaved(page, !currentlySaved)
        }
    }

    fun loadArtist(channelId: String, force: Boolean = false) {
        if (!force && currentArtistId == channelId && _artistState.value.page != null) return
        currentArtistId = channelId
        viewModelScope.launch {
            _artistState.value = ArtistDetailState(isLoading = true)
            innerTube.getArtist(channelId).onSuccess { page ->
                _artistState.value = ArtistDetailState(isLoading = false, page = page)
            }.onFailure { err ->
                _artistState.value = ArtistDetailState(isLoading = false, error = err.message ?: "Failed to load artist")
            }
        }
    }

    fun toggleFollowArtist(currentlyFollowed: Boolean) {
        val page = _artistState.value.page ?: return
        viewModelScope.launch {
            val nextFollow = !currentlyFollowed
            repository.setArtistFollowed(page, nextFollow)
            tasteEngine?.recordArtistFollow(page.name, page.channelId, nextFollow)
        }
    }

    fun loadPlaylist(playlistId: String, force: Boolean = false) {
        if (!force && currentPlaylistId == playlistId && _playlistState.value.page != null) return
        currentPlaylistId = playlistId
        viewModelScope.launch {
            _playlistState.update { it.copy(isLoading = true, error = null) }
            repository.loadPlaylistPage(playlistId).onSuccess { page ->
                _playlistState.update { it.copy(isLoading = false, page = page, error = null) }
            }.onFailure { err ->
                _playlistState.update { it.copy(isLoading = false, error = err.message ?: "Failed to load playlist") }
            }
        }
    }

    fun setPlaylistFilter(q: String) {
        _playlistState.update { it.copy(filterQuery = q) }
    }

    fun setPlaylistSort(mode: PlaylistSortMode) {
        _playlistState.update { it.copy(sortMode = mode) }
    }

    fun updateLocalPlaylistDetails(playlistNumericId: Long, title: String, description: String, coverUri: String?) {
        viewModelScope.launch {
            repository.updatePlaylist(playlistNumericId, title, description, coverUri)
            currentPlaylistId?.let { loadPlaylist(it, force = true) }
        }
    }

    fun removeTrackFromLocalPlaylist(playlistNumericId: Long, videoId: String) {
        viewModelScope.launch {
            repository.removeTrackFromPlaylist(playlistNumericId, videoId)
            currentPlaylistId?.let { loadPlaylist(it, force = true) }
        }
    }

    fun removeDuplicatesFromLocalPlaylist(playlistNumericId: Long, onResult: (Int) -> Unit) {
        viewModelScope.launch {
            val count = repository.removeDuplicateTracksFromPlaylist(playlistNumericId)
            currentPlaylistId?.let { loadPlaylist(it, force = true) }
            onResult(count)
        }
    }

    fun deleteLocalPlaylist(playlistNumericId: Long, onDeleted: () -> Unit) {
        viewModelScope.launch {
            repository.deletePlaylist(playlistNumericId)
            onDeleted()
        }
    }

    fun pinAsShortcut(item: BrowseItem) {
        viewModelScope.launch { repository.toggleShortcut(item) }
    }

    fun exportM3uText(): Pair<String, String>? {
        val page = _playlistState.value.page ?: return null
        val content = repository.exportPlaylistAsM3u(page.title, page.items)
        val safeName = page.title.replace(Regex("[^A-Za-z0-9_\\- ]"), "_").ifBlank { "veluna_playlist" }
        return "$safeName.m3u8" to content
    }

    class Factory(
        private val innerTube: InnerTubeClient,
        private val repository: VelunaRepository,
        private val tasteEngine: TasteEngine? = null
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            DetailViewModel(innerTube, repository, tasteEngine) as T
    }
}
