package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.model.BrowseItem
import com.example.model.BrowseKind
import com.example.model.LOCAL_PLAYLIST_PREFIX
import com.example.model.SongItem
import com.example.ui.components.AddToPlaylistDialog
import com.example.ui.components.CreateEditPlaylistDialog
import com.example.ui.components.EditHomeDialog
import com.example.ui.components.EqualizerBottomSheet
import com.example.ui.components.ImportPlaylistDialog
import com.example.ui.components.MiniPlayerBar
import com.example.ui.components.NowPlayingOverlay
import com.example.ui.components.SleepTimerDialog
import com.example.ui.components.TrackMenuSheet
import com.example.ui.navigation.AlbumRoute
import com.example.ui.navigation.ArtistRoute
import com.example.ui.navigation.HistoryRoute
import com.example.ui.navigation.HomeRoute
import com.example.ui.navigation.LibraryRoute
import com.example.ui.navigation.PlaylistRoute
import com.example.ui.navigation.SearchRoute
import com.example.ui.navigation.SettingsRoute
import com.example.ui.navigation.StatsRoute
import com.example.ui.theme.velunaNavBarItemColors
import com.example.ui.theme.velunaNavRailItemColors
import com.example.ui.navigation.VelunaTopTab
import com.example.ui.screens.AlbumDetailScreen
import com.example.ui.screens.ArtistDetailScreen
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.LibraryScreen
import com.example.ui.screens.PlaylistDetailScreen
import com.example.ui.screens.SearchScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.StatsScreen
import com.example.ui.screens.TasteOnboardingScreen
import com.example.ui.theme.VelunaMotion
import com.example.ui.theme.VelunaTheme
import com.example.ui.viewmodel.DetailViewModel
import com.example.ui.viewmodel.HomeSearchViewModel
import com.example.ui.viewmodel.LibraryStatsViewModel
import com.example.ui.viewmodel.NowPlayingViewModel

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        enableMaxRefreshRate()
        val container = (application as VelunaApplication).container

        setContent {
            val widthSizeClass = calculateWindowSizeClass(this).widthSizeClass
            VelunaApp(
                container = container,
                useNavigationRail = widthSizeClass != WindowWidthSizeClass.Compact
            )
        }
    }

    override fun onResume() {
        super.onResume()
        enableMaxRefreshRate()
    }

    private fun enableMaxRefreshRate() {
        runCatching {
            val activeDisplay = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                display
            } else {
                @Suppress("DEPRECATION")
                windowManager.defaultDisplay
            } ?: return

            val currentMode = activeDisplay.mode
            val sameResModes = activeDisplay.supportedModes.filter {
                it.physicalWidth == currentMode.physicalWidth &&
                    it.physicalHeight == currentMode.physicalHeight
            }
            val bestMode = (sameResModes.ifEmpty { activeDisplay.supportedModes.toList() })
                .maxByOrNull { it.refreshRate } ?: currentMode

            val attrs = window.attributes
            attrs.preferredDisplayModeId = bestMode.modeId
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                attrs.preferredRefreshRate = bestMode.refreshRate
            }
            window.attributes = attrs

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                window.frameRateBoostOnTouchEnabled = true
                window.isFrameRatePowerSavingsBalanced = false
                window.decorView.requestedFrameRate = bestMode.refreshRate
            }
        }
    }
}

@Composable
fun VelunaApp(
    container: VelunaAppContainer,
    useNavigationRail: Boolean
) {
    val context = LocalContext.current

    val homeSearchVm: HomeSearchViewModel = viewModel(
        factory = HomeSearchViewModel.Factory(
            container.innerTubeClient,
            container.repository,
            container.prefsRepository,
            container.tasteEngine
        )
    )
    val detailVm: DetailViewModel = viewModel(
        factory = DetailViewModel.Factory(
            container.innerTubeClient,
            container.repository,
            container.tasteEngine
        )
    )
    val nowPlayingVm: NowPlayingViewModel = viewModel(
        factory = NowPlayingViewModel.Factory(
            container.playerController,
            container.lyricsRepository,
            container.innerTubeClient,
            container.prefsRepository
        )
    )
    val libraryStatsVm: LibraryStatsViewModel = viewModel(
        factory = LibraryStatsViewModel.Factory(
            container.repository,
            container.prefsRepository,
            container.tasteEngine
        )
    )

    val prefs by libraryStatsVm.prefs.collectAsStateWithLifecycle()
    val playbackState by container.playerController.state.collectAsStateWithLifecycle()
    val activeDownloads by container.playerController.downloads.collectAsStateWithLifecycle()
    val downloadedTracks by libraryStatsVm.downloadedTracks.collectAsStateWithLifecycle()
    val playlists by libraryStatsVm.playlists.collectAsStateWithLifecycle()
    val statusBanner by libraryStatsVm.statusBanner.collectAsStateWithLifecycle()
    val likedIds by libraryStatsVm.likedVideoIds.collectAsStateWithLifecycle()
    val tasteProfile by homeSearchVm.tasteProfile.collectAsStateWithLifecycle()
    val onboardingSearch by homeSearchVm.onboardingSearch.collectAsStateWithLifecycle()
    val cloudAuthState by container.cloudSyncCoordinator.authState.collectAsStateWithLifecycle()

    val downloadedIds = remember(downloadedTracks) {
        downloadedTracks.map { it.videoId }.toHashSet()
    }

    LaunchedEffect(Unit) {
        container.cloudSyncCoordinator.attemptAutoSignInOnLaunch(context)
    }

    var requestedNotificationPerm by remember { mutableStateOf(false) }
    val notificationPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ -> }

    fun ensureNotificationPermission() {
        if (requestedNotificationPerm) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                requestedNotificationPerm = true
                notificationPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    VelunaTheme(
        prefs = prefs,
        artworkAccent = playbackState.artworkAccentColor
    ) {
        val navController = rememberNavController()
        val backStackEntry by navController.currentBackStackEntryAsState()
        val snackbarHostState = remember { SnackbarHostState() }

        LaunchedEffect(statusBanner) {
            val msg = statusBanner
            if (!msg.isNullOrBlank()) {
                snackbarHostState.showSnackbar(msg)
                libraryStatsVm.consumeStatusBanner()
            }
        }

        LaunchedEffect(cloudAuthState.errorMessage) {
            val err = cloudAuthState.errorMessage
            if (!err.isNullOrBlank()) {
                snackbarHostState.showSnackbar(err)
                container.cloudSyncCoordinator.clearError()
            }
        }

        LaunchedEffect(container.playerController) {
            container.playerController.events.collect { msg ->
                snackbarHostState.showSnackbar(msg)
            }
        }

        // Modal dialog states
        var menuTrack by remember { mutableStateOf<SongItem?>(null) }
        var addToPlaylistTrack by remember { mutableStateOf<SongItem?>(null) }
        var showCreatePlaylist by remember { mutableStateOf(false) }
        var showImportPlaylist by remember { mutableStateOf(false) }
        var showSleepTimer by remember { mutableStateOf(false) }
        var showEqualizer by remember { mutableStateOf(false) }
        var showEditHome by remember { mutableStateOf(false) }
        var showTasteEditor by remember { mutableStateOf(false) }

        fun playTracks(tracks: List<SongItem>, index: Int, source: String) {
            ensureNotificationPermission()
            container.playerController.playQueue(tracks, index, source)
        }

        fun openBrowseItem(item: BrowseItem) {
            when (item.kind) {
                BrowseKind.SONG -> {
                    ensureNotificationPermission()
                    container.playerController.playSong(item.toSongItem(), item.title)
                }
                BrowseKind.ALBUM -> {
                    container.playerController.openNowPlaying(false)
                    navController.navigate(AlbumRoute(item.id))
                }
                BrowseKind.ARTIST -> {
                    container.playerController.openNowPlaying(false)
                    navController.navigate(ArtistRoute(item.id))
                }
                BrowseKind.PLAYLIST -> {
                    container.playerController.openNowPlaying(false)
                    navController.navigate(PlaylistRoute(item.id))
                }
            }
        }

        fun navigateTopTab(tab: VelunaTopTab) {
            val route: Any = when (tab) {
                VelunaTopTab.HOME -> HomeRoute
                VelunaTopTab.SEARCH -> SearchRoute
                VelunaTopTab.LIBRARY -> LibraryRoute
                VelunaTopTab.STATS -> StatsRoute
                VelunaTopTab.SETTINGS -> SettingsRoute
            }
            if (tab == VelunaTopTab.STATS) {
                libraryStatsVm.refreshStats()
            }
            navController.navigate(route) {
                popUpTo(navController.graph.findStartDestination().id) {
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
        }

        val currentDestination = backStackEntry?.destination
        val selectedTopTab = when {
            currentDestination?.hasRoute<SearchRoute>() == true -> VelunaTopTab.SEARCH
            currentDestination?.hasRoute<LibraryRoute>() == true -> VelunaTopTab.LIBRARY
            currentDestination?.hasRoute<StatsRoute>() == true -> VelunaTopTab.STATS
            currentDestination?.hasRoute<SettingsRoute>() == true -> VelunaTopTab.SETTINGS
            else -> VelunaTopTab.HOME
        }

        Surface(
            color = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.fillMaxSize()
        ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                contentColor = MaterialTheme.colorScheme.onBackground,
                contentWindowInsets = WindowInsets.safeDrawing.only(
                    WindowInsetsSides.Horizontal + WindowInsetsSides.Top
                ),
                snackbarHost = { SnackbarHost(snackbarHostState) },
                bottomBar = {
                    if (!useNavigationRail) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            AnimatedVisibility(
                                visible = playbackState.nowPlaying != null,
                                enter = slideInVertically(
                                    animationSpec = VelunaMotion.expressiveIntOffsetSpring(),
                                    initialOffsetY = { it / 2 }
                                ) + fadeIn(VelunaMotion.fastFade()),
                                exit = slideOutVertically(
                                    animationSpec = VelunaMotion.snappyIntOffsetSpring(),
                                    targetOffsetY = { it / 2 }
                                ) + fadeOut(VelunaMotion.fastFade())
                            ) {
                                MiniPlayerBar(
                                    state = playbackState,
                                    positionFlow = container.playerController.positionState,
                                    isLiked = playbackState.nowPlaying?.videoId in likedIds,
                                    onOpenNowPlaying = { container.playerController.openNowPlaying(true) },
                                    onTogglePlayPause = { container.playerController.togglePlayPause() },
                                    onSkipNext = { container.playerController.skipNext() },
                                    onToggleLike = { container.playerController.toggleLikeCurrent() }
                                )
                            }
                            VelunaBottomNavBar(
                                selectedTab = selectedTopTab,
                                onSelectTab = ::navigateTopTab
                            )
                        }
                    } else {
                        AnimatedVisibility(
                            visible = playbackState.nowPlaying != null,
                            enter = slideInVertically(
                                animationSpec = VelunaMotion.expressiveIntOffsetSpring(),
                                initialOffsetY = { it }
                            ) + fadeIn(VelunaMotion.fastFade()),
                            exit = slideOutVertically(
                                animationSpec = VelunaMotion.snappyIntOffsetSpring(),
                                targetOffsetY = { it }
                            ) + fadeOut(VelunaMotion.fastFade())
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom))
                                    .padding(horizontal = 16.dp, vertical = 6.dp)
                            ) {
                                MiniPlayerBar(
                                    state = playbackState,
                                    positionFlow = container.playerController.positionState,
                                    isLiked = playbackState.nowPlaying?.videoId in likedIds,
                                    onOpenNowPlaying = { container.playerController.openNowPlaying(true) },
                                    onTogglePlayPause = { container.playerController.togglePlayPause() },
                                    onSkipNext = { container.playerController.skipNext() },
                                    onToggleLike = { container.playerController.toggleLikeCurrent() }
                                )
                            }
                        }
                    }
                }
            ) { innerPadding ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    if (useNavigationRail) {
                        VelunaNavRail(
                            selectedTab = selectedTopTab,
                            onSelectTab = ::navigateTopTab,
                            modifier = Modifier
                                .fillMaxHeight()
                                .statusBarsPadding()
                        )
                    }

                    NavHost(
                        navController = navController,
                        startDestination = HomeRoute,
                        enterTransition = {
                            slideInHorizontally(
                                animationSpec = VelunaMotion.expressiveIntOffsetSpring(),
                                initialOffsetX = { (it * 0.08f).toInt() }
                            ) + scaleIn(
                                animationSpec = VelunaMotion.snappySpring(),
                                initialScale = 0.985f
                            ) + fadeIn(VelunaMotion.fastFade())
                        },
                        exitTransition = {
                            slideOutHorizontally(
                                animationSpec = VelunaMotion.snappyIntOffsetSpring(),
                                targetOffsetX = { -(it * 0.05f).toInt() }
                            ) + fadeOut(VelunaMotion.fastFade())
                        },
                        popEnterTransition = {
                            slideInHorizontally(
                                animationSpec = VelunaMotion.expressiveIntOffsetSpring(),
                                initialOffsetX = { -(it * 0.08f).toInt() }
                            ) + fadeIn(VelunaMotion.fastFade())
                        },
                        popExitTransition = {
                            slideOutHorizontally(
                                animationSpec = VelunaMotion.snappyIntOffsetSpring(),
                                targetOffsetX = { (it * 0.08f).toInt() }
                            ) + scaleOut(
                                animationSpec = VelunaMotion.snappySpring(),
                                targetScale = 0.98f
                            ) + fadeOut(VelunaMotion.fastFade())
                        },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    ) {
                        composable<HomeRoute> {
                            val homeState by homeSearchVm.homeState.collectAsStateWithLifecycle()
                            val shortcuts by homeSearchVm.shortcuts.collectAsStateWithLifecycle()
                            val jumpBack by homeSearchVm.recentJumpBack.collectAsStateWithLifecycle()
                            val familiarArtists by homeSearchVm.familiarArtists.collectAsStateWithLifecycle()

                            HomeScreen(
                                homeState = homeState,
                                prefs = prefs,
                                shortcuts = shortcuts,
                                jumpBack = jumpBack,
                                familiarArtists = familiarArtists,
                                nowPlayingSong = playbackState.nowPlaying,
                                artworkAccent = playbackState.artworkAccentColor,
                                likedIds = likedIds,
                                cloudAuthState = cloudAuthState,
                                onSignInWithGoogle = { container.cloudSyncCoordinator.signIn(context) },
                                onOpenAccountSettings = { navigateTopTab(VelunaTopTab.SETTINGS) },
                                onSelectChip = homeSearchVm::loadHome,
                                onRetry = { homeSearchVm.loadHome(homeState.selectedChipParams) },
                                onOpenBrowseItem = ::openBrowseItem,
                                onTrackMore = { menuTrack = it },
                                onToggleLike = { container.playerController.toggleLike(it) },
                                onOpenHistory = { navController.navigate(HistoryRoute) },
                                onOpenEditHome = { showEditHome = true }
                            )
                        }

                        composable<SearchRoute> {
                            val searchState by homeSearchVm.searchState.collectAsStateWithLifecycle()
                            val history by homeSearchVm.searchHistory.collectAsStateWithLifecycle()

                            SearchScreen(
                                state = searchState,
                                searchHistory = history,
                                nowPlayingId = playbackState.nowPlaying?.videoId,
                                onQueryChange = homeSearchVm::onQueryChange,
                                onSubmitSearch = homeSearchVm::submitSearch,
                                onClearSearch = homeSearchVm::clearSearchResults,
                                onSelectCategory = homeSearchVm::selectSearchCategory,
                                onRemoveHistory = homeSearchVm::removeSearchHistoryItem,
                                onClearHistory = homeSearchVm::clearSearchHistory,
                                onOpenBrowseItem = ::openBrowseItem,
                                onTrackMore = { menuTrack = it },
                                onOpenMoodTile = { tile ->
                                    homeSearchVm.openMoodTile(tile)
                                    navigateTopTab(VelunaTopTab.HOME)
                                }
                            )
                        }

                        composable<LibraryRoute> {
                            val likedSongs by libraryStatsVm.likedSongs.collectAsStateWithLifecycle()
                            val savedAlbums by libraryStatsVm.savedAlbums.collectAsStateWithLifecycle()
                            val followedArtists by libraryStatsVm.followedArtists.collectAsStateWithLifecycle()
                            val localTracks by libraryStatsVm.localTracks.collectAsStateWithLifecycle()

                            LibraryScreen(
                                playlists = playlists,
                                likedSongs = likedSongs,
                                savedAlbums = savedAlbums,
                                followedArtists = followedArtists,
                                downloads = downloadedTracks,
                                activeDownloads = activeDownloads,
                                localTracks = localTracks,
                                nowPlayingId = playbackState.nowPlaying?.videoId,
                                onOpenBrowseItem = ::openBrowseItem,
                                onPlaySongs = ::playTracks,
                                onTrackMore = { menuTrack = it },
                                onToggleLike = { container.playerController.toggleLike(it) },
                                onOpenCreatePlaylist = { showCreatePlaylist = true },
                                onOpenImportPlaylist = {
                                    libraryStatsVm.resetImportState()
                                    showImportPlaylist = true
                                },
                                onImportLocalFiles = libraryStatsVm::importLocalFiles,
                                onImportLocalFolder = libraryStatsVm::importLocalFolder,
                                onRemoveLocalTrack = libraryStatsVm::removeLocalTrack,
                                onDeleteDownload = container.playerController::deleteDownload
                            )
                        }

                        composable<StatsRoute> {
                            val statsState by libraryStatsVm.statsState.collectAsStateWithLifecycle()
                            StatsScreen(
                                state = statsState,
                                nowPlayingId = playbackState.nowPlaying?.videoId,
                                onSelectRange = libraryStatsVm::refreshStats,
                                onOpenHistory = { navController.navigate(HistoryRoute) },
                                onPlaySong = { song ->
                                    ensureNotificationPermission()
                                    container.playerController.playSong(song, "Top Songs")
                                },
                                onTrackMore = { menuTrack = it },
                                onOpenArtist = { id -> navController.navigate(ArtistRoute(id)) },
                                onOpenAlbum = { id -> navController.navigate(AlbumRoute(id)) }
                            )
                        }

                        composable<SettingsRoute> {
                            val blockedArtists by libraryStatsVm.blockedArtists.collectAsStateWithLifecycle()
                            SettingsScreen(
                                prefs = prefs,
                                blockedArtists = blockedArtists,
                                tasteProfile = tasteProfile,
                                cloudAuthState = cloudAuthState,
                                onSignInWithGoogle = { container.cloudSyncCoordinator.signIn(context) },
                                onSignOut = { container.cloudSyncCoordinator.signOut() },
                                onSyncNow = { container.cloudSyncCoordinator.syncNow() },
                                onOpenTasteOnboarding = { showTasteEditor = true },
                                onSetThemePreset = libraryStatsVm::setThemePreset,
                                onSetThemeMode = libraryStatsVm::setThemeMode,
                                onSetCustomAccent = libraryStatsVm::setCustomAccent,
                                onSetAdaptToArtwork = libraryStatsVm::setAdaptToArtwork,
                                onSetCornerRadius = libraryStatsVm::setCornerRadius,
                                onSetDisplayName = libraryStatsVm::setDisplayName,
                                onSetAudioQuality = libraryStatsVm::setAudioQuality,
                                onSetAutoplay = libraryStatsVm::setAutoplay,
                                onSetLoudnessNormalization = libraryStatsVm::setLoudnessNormalization,
                                onSetHideMusicVideos = libraryStatsVm::setHideMusicVideos,
                                onOpenEqualizer = { showEqualizer = true },
                                onUpdateLyricsProviders = libraryStatsVm::updateLyricsProviders,
                                onSaveScrobbling = libraryStatsVm::setScrobbling,
                                onUnblockArtist = libraryStatsVm::unblockArtist,
                                onBuildBackupJson = libraryStatsVm::buildBackupJson,
                                onRestoreBackupJson = libraryStatsVm::restoreBackupJson,
                                onStatusMessage = libraryStatsVm::postStatus
                            )
                        }

                        composable<AlbumRoute> { entry ->
                            val route = entry.toRoute<AlbumRoute>()
                            LaunchedEffect(route.albumId) { detailVm.loadAlbum(route.albumId) }
                            val albumState by detailVm.albumState.collectAsStateWithLifecycle()
                            val isSaved by detailVm.observeIsAlbumSaved(route.albumId).collectAsStateWithLifecycle()

                            AlbumDetailScreen(
                                state = albumState,
                                isSaved = isSaved,
                                nowPlayingId = playbackState.nowPlaying?.videoId,
                                onBack = { navController.popBackStack() },
                                onToggleSave = { detailVm.toggleSaveAlbum(isSaved) },
                                onPinShortcut = detailVm::pinAsShortcut,
                                onPlayTracks = ::playTracks,
                                onTrackMore = { menuTrack = it },
                                onOpenBrowseItem = ::openBrowseItem,
                                onOpenArtist = { id -> navController.navigate(ArtistRoute(id)) }
                            )
                        }

                        composable<ArtistRoute> { entry ->
                            val route = entry.toRoute<ArtistRoute>()
                            LaunchedEffect(route.channelId) { detailVm.loadArtist(route.channelId) }
                            val artistState by detailVm.artistState.collectAsStateWithLifecycle()
                            val isFollowed by detailVm.observeIsArtistFollowed(route.channelId).collectAsStateWithLifecycle()

                            ArtistDetailScreen(
                                state = artistState,
                                isFollowed = isFollowed,
                                nowPlayingId = playbackState.nowPlaying?.videoId,
                                onBack = { navController.popBackStack() },
                                onToggleFollow = { detailVm.toggleFollowArtist(isFollowed) },
                                onPinShortcut = detailVm::pinAsShortcut,
                                onPlayTracks = ::playTracks,
                                onTrackMore = { menuTrack = it },
                                onOpenBrowseItem = ::openBrowseItem
                            )
                        }

                        composable<PlaylistRoute> { entry ->
                            val route = entry.toRoute<PlaylistRoute>()
                            LaunchedEffect(route.playlistId) { detailVm.loadPlaylist(route.playlistId, force = true) }
                            val playlistState by detailVm.playlistState.collectAsStateWithLifecycle()

                            PlaylistDetailScreen(
                                state = playlistState,
                                nowPlayingId = playbackState.nowPlaying?.videoId,
                                onBack = { navController.popBackStack() },
                                onFilterChange = detailVm::setPlaylistFilter,
                                onSortChange = detailVm::setPlaylistSort,
                                onPlayTracks = ::playTracks,
                                onTrackMore = { menuTrack = it },
                                onEditLocalPlaylist = detailVm::updateLocalPlaylistDetails,
                                onRemoveDuplicates = { id ->
                                    detailVm.removeDuplicatesFromLocalPlaylist(id) { removed ->
                                        libraryStatsVm.postStatus("Removed $removed duplicate track(s)")
                                    }
                                },
                                onDeletePlaylist = { id ->
                                    detailVm.deleteLocalPlaylist(id) { navController.popBackStack() }
                                },
                                onExportM3u = detailVm::exportM3uText,
                                onPinShortcut = detailVm::pinAsShortcut,
                                onStatusMessage = libraryStatsVm::postStatus
                            )
                        }

                        composable<HistoryRoute> {
                            val groups by libraryStatsVm.historyGroups.collectAsStateWithLifecycle()
                            HistoryScreen(
                                groups = groups,
                                onBack = { navController.popBackStack() },
                                onPlaySong = { song ->
                                    ensureNotificationPermission()
                                    container.playerController.playSong(song, "Listening History")
                                },
                                onTrackMore = { menuTrack = it }
                            )
                        }
                    }
                }
            }

            // Full-Screen Immersive Now Playing Overlay with expressive spring sheet reveal
            AnimatedVisibility(
                visible = playbackState.nowPlayingOpen && playbackState.nowPlaying != null,
                enter = slideInVertically(
                    animationSpec = VelunaMotion.expressiveIntOffsetSpring(),
                    initialOffsetY = { it }
                ) + fadeIn(VelunaMotion.fastFade()),
                exit = slideOutVertically(
                    animationSpec = VelunaMotion.snappyIntOffsetSpring(),
                    targetOffsetY = { it }
                ) + fadeOut(VelunaMotion.fastFade())
            ) {
                val lyricsState by nowPlayingVm.lyricsState.collectAsStateWithLifecycle()
                val commentsState by nowPlayingVm.commentsState.collectAsStateWithLifecycle()
                val curSong = playbackState.nowPlaying
                val isCurDownloaded = curSong != null && curSong.videoId in downloadedIds

                NowPlayingOverlay(
                    playbackState = playbackState,
                    positionFlow = container.playerController.positionState,
                    isLiked = curSong?.videoId in likedIds,
                    prefs = prefs,
                    lyricsState = lyricsState,
                    commentsState = commentsState,
                    isDownloaded = isCurDownloaded,
                    onClose = { container.playerController.openNowPlaying(false) },
                    onTogglePlayPause = { container.playerController.togglePlayPause() },
                    onSeekTo = { container.playerController.seekTo(it) },
                    onSkipNext = { container.playerController.skipNext() },
                    onSkipPrevious = { container.playerController.skipPrevious() },
                    onToggleShuffle = { container.playerController.toggleShuffle() },
                    onCycleRepeat = { container.playerController.cycleRepeat() },
                    onToggleLike = { container.playerController.toggleLikeCurrent() },
                    onSetSpeed = { container.playerController.setPlaybackSpeed(it) },
                    onOpenSleepTimer = { showSleepTimer = true },
                    onOpenEqualizer = { showEqualizer = true },
                    onToggleDownload = {
                        curSong?.let { song ->
                            if (isCurDownloaded) container.playerController.deleteDownload(song.videoId)
                            else container.playerController.downloadTrack(song)
                        }
                    },
                    onOpenMoreMenu = { menuTrack = it },
                    onSelectQueueIndex = { container.playerController.playIndex(it) },
                    onRemoveQueueIndex = { container.playerController.removeFromQueue(it) },
                    onClearQueue = { container.playerController.clearQueue() },
                    onToggleRomanize = nowPlayingVm::toggleRomanize,
                    onSelectLyricsProvider = { pid ->
                        curSong?.let { nowPlayingVm.loadLyrics(it, forceProviderId = pid) }
                    },
                    onResetPinnedLyrics = nowPlayingVm::resetPinnedLyrics,
                    onNudgeLyricsOffset = nowPlayingVm::nudgeLyricsOffset,
                    onLoadComments = nowPlayingVm::loadCommentsIfNeeded,
                    onArtistClick = { artistId ->
                        container.playerController.openNowPlaying(false)
                        navController.navigate(ArtistRoute(artistId))
                    },
                    onAlbumClick = { albumId ->
                        container.playerController.openNowPlaying(false)
                        navController.navigate(AlbumRoute(albumId))
                    }
                )
            }

            // Full-Screen Taste Onboarding / Re-tuning Overlay
            val showOnboardingOverlay = (tasteProfile != null && tasteProfile?.onboardingCompleted == false) || showTasteEditor
            AnimatedVisibility(
                visible = showOnboardingOverlay,
                enter = slideInVertically(
                    animationSpec = VelunaMotion.expressiveIntOffsetSpring(),
                    initialOffsetY = { it / 2 }
                ) + fadeIn(VelunaMotion.fastFade()),
                exit = slideOutVertically(
                    animationSpec = VelunaMotion.snappyIntOffsetSpring(),
                    targetOffsetY = { it / 2 }
                ) + fadeOut(VelunaMotion.fastFade())
            ) {
                TasteOnboardingScreen(
                    initialProfile = tasteProfile,
                    searchState = onboardingSearch,
                    canNavigateBack = tasteProfile?.onboardingCompleted == true,
                    onQueryChange = homeSearchVm::onOnboardingQueryChange,
                    onSave = { genres, artists, songs ->
                        homeSearchVm.saveOnboardingTaste(genres, artists, songs) {
                            showTasteEditor = false
                        }
                    },
                    onSkip = {
                        if (tasteProfile?.onboardingCompleted == true) {
                            showTasteEditor = false
                        } else {
                            homeSearchVm.skipOnboarding {
                                showTasteEditor = false
                            }
                        }
                    }
                )
            }

            // --- Shared Dialogs & Sheets ---
            menuTrack?.let { song ->
                val isDl = song.videoId in downloadedIds
                TrackMenuSheet(
                    song = song,
                    isDownloaded = isDl,
                    isLiked = song.videoId in likedIds,
                    onDismiss = { menuTrack = null },
                    onPlayNext = { container.playerController.playNext(song) },
                    onAddToQueue = { container.playerController.addToQueue(song) },
                    onStartRadio = {
                        ensureNotificationPermission()
                        container.playerController.startRadio(song)
                    },
                    onToggleLike = { container.playerController.toggleLike(song) },
                    onAddToPlaylist = { addToPlaylistTrack = song },
                    onToggleDownload = {
                        if (isDl) container.playerController.deleteDownload(song.videoId)
                        else container.playerController.downloadTrack(song)
                    },
                    onGoToArtist = { artistId ->
                        container.playerController.openNowPlaying(false)
                        navController.navigate(ArtistRoute(artistId))
                    },
                    onGoToAlbum = { albumId ->
                        container.playerController.openNowPlaying(false)
                        navController.navigate(AlbumRoute(albumId))
                    },
                    onPinShortcut = {
                        homeSearchVm.addShortcut(
                            BrowseItem(BrowseKind.SONG, song.videoId, song.title, song.artists, song.thumbnail)
                        )
                        libraryStatsVm.postStatus("Pinned to Shortcuts")
                    },
                    onBlockArtist = {
                        libraryStatsVm.blockArtist(song.artists.substringBefore(',').trim(), song.artistId)
                    }
                )
            }

            addToPlaylistTrack?.let { song ->
                AddToPlaylistDialog(
                    song = song,
                    playlists = playlists,
                    onDismiss = { addToPlaylistTrack = null },
                    onPickPlaylist = { plId -> libraryStatsVm.addTrackToPlaylist(plId, song) },
                    onCreateNew = { title -> libraryStatsVm.createPlaylist(title, initialSong = song) }
                )
            }

            if (showCreatePlaylist) {
                CreateEditPlaylistDialog(
                    onDismiss = { showCreatePlaylist = false },
                    onConfirm = { title, desc, _ -> libraryStatsVm.createPlaylist(title, desc) }
                )
            }

            if (showImportPlaylist) {
                val importState by libraryStatsVm.importState.collectAsStateWithLifecycle()
                ImportPlaylistDialog(
                    state = importState,
                    onDismiss = { showImportPlaylist = false },
                    onImportYouTube = libraryStatsVm::importYouTubePlaylist,
                    onImportFile = libraryStatsVm::importCsvOrM3u,
                    onOpenPlaylist = { id ->
                        navController.navigate(PlaylistRoute("$LOCAL_PLAYLIST_PREFIX$id"))
                    }
                )
            }

            if (showSleepTimer) {
                SleepTimerDialog(
                    activeRemainingMs = playbackState.sleepTimerRemainingMs,
                    onDismiss = { showSleepTimer = false },
                    onSelectMinutes = { container.playerController.setSleepTimer(it) }
                )
            }

            if (showEqualizer) {
                EqualizerBottomSheet(
                    enabled = prefs.eqEnabled,
                    presetName = prefs.eqPresetName,
                    bandsDb = prefs.eqBandsDb,
                    loudnessNormEnabled = prefs.loudnessNormalization,
                    onToggleEnabled = nowPlayingVm::setEqEnabled,
                    onSelectPreset = nowPlayingVm::applyEqPreset,
                    onBandChange = nowPlayingVm::setEqBand,
                    onToggleLoudness = libraryStatsVm::setLoudnessNormalization,
                    onDismiss = { showEqualizer = false }
                )
            }

            if (showEditHome) {
                val shortcuts by homeSearchVm.shortcuts.collectAsStateWithLifecycle()
                EditHomeDialog(
                    showMoodChips = prefs.showMoodChips,
                    showHeroBackdrop = prefs.showHeroArtworkBackdrop,
                    shortcuts = shortcuts,
                    onToggleMoodChips = libraryStatsVm::setShowMoodChips,
                    onToggleHeroBackdrop = libraryStatsVm::setShowHeroBackdrop,
                    onRemoveShortcut = homeSearchVm::removeShortcut,
                    onDismiss = { showEditHome = false }
                )
            }
        }
        }
    }
}

@Composable
private fun VelunaBottomNavBar(
    selectedTab: VelunaTopTab,
    onSelectTab: (VelunaTopTab) -> Unit
) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.testTag("bottom_nav_bar")
    ) {
        val navColors = velunaNavBarItemColors()
        VelunaTopTab.entries.forEach { tab ->
            val selected = selectedTab == tab
            val icon = tabIcon(tab, selected)
            NavigationBarItem(
                selected = selected,
                onClick = { onSelectTab(tab) },
                colors = navColors,
                icon = { Icon(icon, contentDescription = tab.label) },
                label = { Text(tab.label, maxLines = 1, softWrap = false) },
                modifier = Modifier.testTag("nav_item_${tab.name.lowercase()}")
            )
        }
    }
}

@Composable
private fun VelunaNavRail(
    selectedTab: VelunaTopTab,
    onSelectTab: (VelunaTopTab) -> Unit,
    modifier: Modifier = Modifier
) {
    NavigationRail(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = modifier.testTag("side_nav_rail")
    ) {
        val railColors = velunaNavRailItemColors()
        VelunaTopTab.entries.forEach { tab ->
            val selected = selectedTab == tab
            val icon = tabIcon(tab, selected)
            NavigationRailItem(
                selected = selected,
                onClick = { onSelectTab(tab) },
                colors = railColors,
                icon = { Icon(icon, contentDescription = tab.label) },
                label = { Text(tab.label, maxLines = 1, softWrap = false) },
                modifier = Modifier.testTag("rail_item_${tab.name.lowercase()}")
            )
        }
    }
}

private fun tabIcon(tab: VelunaTopTab, selected: Boolean) = when (tab) {
    VelunaTopTab.HOME -> if (selected) Icons.Rounded.Home else Icons.Outlined.Home
    VelunaTopTab.SEARCH -> if (selected) Icons.Rounded.Search else Icons.Outlined.Search
    VelunaTopTab.LIBRARY -> if (selected) Icons.Rounded.LibraryMusic else Icons.Outlined.LibraryMusic
    VelunaTopTab.STATS -> if (selected) Icons.Rounded.BarChart else Icons.Outlined.BarChart
    VelunaTopTab.SETTINGS -> if (selected) Icons.Rounded.Settings else Icons.Outlined.Settings
}
