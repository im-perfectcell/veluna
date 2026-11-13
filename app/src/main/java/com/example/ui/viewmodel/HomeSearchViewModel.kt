package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.UserPrefsRepository
import com.example.data.network.InnerTubeClient
import com.example.data.repository.VelunaRepository
import com.example.model.BrowseItem
import com.example.model.BrowseKind
import com.example.model.HomePage
import com.example.model.MoodSection
import com.example.model.MoodTile
import com.example.model.SearchResults
import com.example.model.SearchSuggestions
import com.example.model.SongItem
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val selectedChipParams: String? = null,
    val homePage: HomePage = InnerTubeClient.fallbackDiscoveryHome(),
    val moodsTitle: String? = null
)

data class SearchUiState(
    val query: String = "",
    val isSearching: Boolean = false,
    val error: String? = null,
    val results: SearchResults? = null,
    val suggestions: SearchSuggestions = SearchSuggestions(),
    val moodsSections: List<MoodSection> = emptyList(),
    val isMoodsLoading: Boolean = false,
    val selectedCategoryTab: Int = 0
)

class HomeSearchViewModel(
    private val innerTube: InnerTubeClient,
    private val repository: VelunaRepository,
    private val prefsRepo: UserPrefsRepository
) : ViewModel() {

    private val _homeState = MutableStateFlow(HomeUiState())
    val homeState: StateFlow<HomeUiState> = _homeState.asStateFlow()

    private val _searchState = MutableStateFlow(SearchUiState())
    val searchState: StateFlow<SearchUiState> = _searchState.asStateFlow()

    val shortcuts: StateFlow<List<BrowseItem>> = repository.shortcutsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val searchHistory: StateFlow<List<String>> = repository.searchHistoryFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentJumpBack: StateFlow<List<BrowseItem>> = combine(
        repository.observeHistoryGroups(),
        repository.blockedArtistsFlow
    ) { groups, blocked ->
        val blockedSet = blocked.map { it.name.lowercase() }.toHashSet()
        groups.flatMap { it.items }
            .map { it.second }
            .filter { song -> song.artists.lowercase() !in blockedSet }
            .distinctBy { it.videoId }
            .take(12)
            .map { song ->
                BrowseItem(
                    kind = BrowseKind.SONG,
                    id = song.videoId,
                    title = song.title,
                    subtitle = song.artists,
                    thumbnail = song.thumbnail,
                    duration = song.duration,
                    albumId = song.albumId,
                    artistRuns = song.artistRuns,
                    explicit = song.explicit,
                    isVideo = song.isVideo
                )
            }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val familiarArtists: StateFlow<List<BrowseItem>> = combine(
        repository.observeHistoryGroups(),
        repository.followedArtistsFlow,
        repository.blockedArtistsFlow
    ) { groups, followed, blocked ->
        val blockedSet = blocked.map { it.name.lowercase() }.toHashSet()
        val fromHistory = groups.flatMap { it.items }
            .map { it.second }
            .mapNotNull { s ->
                val artistId = s.artistId ?: s.artistRuns.firstOrNull { it.id != null }?.id ?: return@mapNotNull null
                val name = s.artists.split(",").firstOrNull()?.trim().orEmpty()
                if (name.isBlank() || name.lowercase() in blockedSet) return@mapNotNull null
                BrowseItem(
                    kind = BrowseKind.ARTIST,
                    id = artistId,
                    title = name,
                    subtitle = "Recent favorite",
                    thumbnail = s.thumbnail
                )
            }
        (followed + fromHistory)
            .distinctBy { it.id }
            .take(10)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private var suggestJob: Job? = null

    init {
        loadHome(null)
        loadMoods()
    }

    fun loadHome(params: String?) {
        viewModelScope.launch {
            _homeState.update {
                it.copy(isLoading = true, error = null, selectedChipParams = params, moodsTitle = null)
            }
            innerTube.getHome(params).onSuccess { page ->
                _homeState.update {
                    it.copy(
                        isLoading = false,
                        homePage = page.copy(
                            chips = page.chips.ifEmpty { InnerTubeClient.fallbackDiscoveryHome().chips }
                        ),
                        error = null
                    )
                }
            }.onFailure { err ->
                _homeState.update {
                    it.copy(
                        isLoading = false,
                        error = err.message ?: "Unable to reach YouTube Music"
                    )
                }
            }
        }
    }

    fun openMoodTile(tile: MoodTile) {
        viewModelScope.launch {
            _homeState.update { it.copy(isLoading = true, error = null, moodsTitle = tile.title) }
            innerTube.getMoodCategory(tile.params).onSuccess { page ->
                _homeState.update { it.copy(isLoading = false, homePage = page) }
            }.onFailure { err ->
                _homeState.update { it.copy(isLoading = false, error = err.message) }
            }
        }
    }

    fun loadMoods() {
        viewModelScope.launch {
            _searchState.update { it.copy(isMoodsLoading = true) }
            innerTube.getMoodsAndGenres().onSuccess { sections ->
                _searchState.update { it.copy(moodsSections = sections, isMoodsLoading = false) }
            }.onFailure {
                _searchState.update { it.copy(isMoodsLoading = false) }
            }
        }
    }

    fun onQueryChange(q: String) {
        _searchState.update { it.copy(query = q) }
        suggestJob?.cancel()
        if (q.isBlank()) {
            _searchState.update { it.copy(suggestions = SearchSuggestions(), results = null, error = null) }
            return
        }
        suggestJob = viewModelScope.launch {
            delay(260L)
            innerTube.searchSuggestions(q).onSuccess { sug ->
                _searchState.update { it.copy(suggestions = sug) }
            }
        }
    }

    fun submitSearch(queryOverride: String? = null) {
        val q = (queryOverride ?: _searchState.value.query).trim()
        if (q.isBlank()) return
        suggestJob?.cancel()
        _searchState.update { it.copy(query = q, isSearching = true, error = null) }
        viewModelScope.launch {
            repository.recordSearchQuery(q)
            innerTube.searchAll(q).onSuccess { res ->
                _searchState.update {
                    it.copy(isSearching = false, results = res, error = null)
                }
            }.onFailure { err ->
                _searchState.update {
                    it.copy(isSearching = false, error = err.message ?: "Search failed")
                }
            }
        }
    }

    fun selectSearchCategory(tabIndex: Int) {
        _searchState.update { it.copy(selectedCategoryTab = tabIndex) }
    }

    fun clearSearchResults() {
        _searchState.update { it.copy(query = "", results = null, suggestions = SearchSuggestions(), error = null) }
    }

    fun removeSearchHistoryItem(query: String) {
        viewModelScope.launch { repository.removeSearchQuery(query) }
    }

    fun clearSearchHistory() {
        viewModelScope.launch { repository.clearSearchHistory() }
    }

    fun addShortcut(item: BrowseItem) {
        viewModelScope.launch { repository.toggleShortcut(item) }
    }

    fun removeShortcut(id: String) {
        viewModelScope.launch { repository.removeShortcut(id) }
    }

    fun addSongToQuickPlaylistOrLike(song: SongItem) {
        viewModelScope.launch { repository.toggleLike(song) }
    }

    class Factory(
        private val innerTube: InnerTubeClient,
        private val repository: VelunaRepository,
        private val prefsRepo: UserPrefsRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            HomeSearchViewModel(innerTube, repository, prefsRepo) as T
    }
}
