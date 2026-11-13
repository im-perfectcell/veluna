package com.example.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Comment
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.VelunaUserPrefs
import com.example.model.Rating
import com.example.model.RepeatMode
import com.example.model.SongItem
import com.example.playback.PlaybackPositionState
import com.example.playback.PlaybackUiState
import com.example.ui.theme.VelunaMotion
import com.example.ui.viewmodel.CommentsUiState
import com.example.ui.viewmodel.LyricsUiState
import kotlinx.coroutines.flow.StateFlow

private val VelunaHeartRed = Color(0xFFEF4444)

@Composable
fun NowPlayingOverlay(
    playbackState: PlaybackUiState,
    positionFlow: StateFlow<PlaybackPositionState>,
    isLiked: Boolean = playbackState.nowPlaying?.rating == Rating.LIKE,
    prefs: VelunaUserPrefs,
    lyricsState: LyricsUiState,
    commentsState: CommentsUiState,
    isDownloaded: Boolean,
    onClose: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onToggleLike: () -> Unit,
    onSetSpeed: (Float) -> Unit,
    onOpenSleepTimer: () -> Unit,
    onOpenEqualizer: () -> Unit,
    onToggleDownload: () -> Unit,
    onOpenMoreMenu: (SongItem) -> Unit,
    onSelectQueueIndex: (Int) -> Unit,
    onRemoveQueueIndex: (Int) -> Unit,
    onClearQueue: () -> Unit,
    onToggleRomanize: () -> Unit,
    onSelectLyricsProvider: (String) -> Unit,
    onResetPinnedLyrics: () -> Unit,
    onNudgeLyricsOffset: (Long) -> Unit,
    onLoadComments: (SongItem) -> Unit,
    onArtistClick: (String) -> Unit,
    onAlbumClick: (String) -> Unit
) {
    val song = playbackState.nowPlaying ?: return
    BackHandler { onClose() }

    var compactTab by remember { mutableIntStateOf(0) } // 0=Player, 1=Queue, 2=Lyrics, 3=Comments
    var widePanelTab by remember { mutableIntStateOf(0) } // 0=Queue, 1=Lyrics, 2=Comments

    LaunchedEffect(compactTab, widePanelTab, song.videoId) {
        if (compactTab == 3 || widePanelTab == 2) {
            onLoadComments(song)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("now_playing_screen")
    ) {
        ArtWashBackdrop(
            artworkUrl = song.thumbnail,
            accentColor = playbackState.artworkAccentColor,
            modifier = Modifier.fillMaxSize()
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // Top bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = onClose,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("now_playing_collapse_btn")
                ) {
                    Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Minimize player")
                }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = playbackState.sourceName ?: "Now Playing",
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!playbackState.streamClient.isNullOrBlank()) {
                        Text(
                            text = playbackState.streamClient,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                IconButton(
                    onClick = { onOpenMoreMenu(song) },
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("now_playing_more_btn")
                ) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = "More options")
                }
            }

            BoxWithConstraints(modifier = Modifier.weight(1f)) {
                val isWide = maxWidth >= 720.dp

                if (isWide) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 24.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        PlayerDeck(
                            song = song,
                            playbackState = playbackState,
                            positionFlow = positionFlow,
                            isLiked = isLiked,
                            isDownloaded = isDownloaded,
                            onTogglePlayPause = onTogglePlayPause,
                            onSeekTo = onSeekTo,
                            onSkipNext = onSkipNext,
                            onSkipPrevious = onSkipPrevious,
                            onToggleShuffle = onToggleShuffle,
                            onCycleRepeat = onCycleRepeat,
                            onToggleLike = onToggleLike,
                            onSetSpeed = onSetSpeed,
                            onOpenSleepTimer = onOpenSleepTimer,
                            onOpenEqualizer = onOpenEqualizer,
                            onToggleDownload = onToggleDownload,
                            onArtistClick = onArtistClick,
                            onAlbumClick = onAlbumClick,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        )
                        Surface(
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
                            shape = MaterialTheme.shapes.large,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        ) {
                            Column {
                                TabRow(selectedTabIndex = widePanelTab) {
                                    Tab(selected = widePanelTab == 0, onClick = { widePanelTab = 0 }, text = { Text("Queue", maxLines = 1, softWrap = false) })
                                    Tab(selected = widePanelTab == 1, onClick = { widePanelTab = 1 }, text = { Text("Lyrics", maxLines = 1, softWrap = false) })
                                    Tab(selected = widePanelTab == 2, onClick = { widePanelTab = 2 }, text = { Text("Comments", maxLines = 1, softWrap = false) })
                                }
                                AnimatedContent(
                                    targetState = widePanelTab,
                                    transitionSpec = { fadeIn(VelunaMotion.fastFade()) togetherWith fadeOut(VelunaMotion.fastFade()) },
                                    label = "wide_now_playing_tab"
                                ) { tab ->
                                    when (tab) {
                                        0 -> QueuePanel(
                                            queue = playbackState.queue,
                                            currentIndex = playbackState.currentIndex,
                                            sourceName = playbackState.sourceName,
                                            onSelectIndex = onSelectQueueIndex,
                                            onRemoveIndex = onRemoveQueueIndex,
                                            onClearQueue = onClearQueue
                                        )
                                        1 -> SyncedLyricsPanel(
                                            lyricsState = lyricsState,
                                            positionFlow = positionFlow,
                                            romanizeEnabled = prefs.romanizeLyrics,
                                            onToggleRomanize = onToggleRomanize,
                                            onSelectProvider = onSelectLyricsProvider,
                                            onResetPinned = onResetPinnedLyrics,
                                            onNudgeOffset = onNudgeLyricsOffset,
                                            onSeekToLine = onSeekTo
                                        )
                                        else -> CommentsPanel(state = commentsState)
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Box(modifier = Modifier.weight(1f)) {
                            AnimatedContent(
                                targetState = compactTab,
                                transitionSpec = {
                                    fadeIn(VelunaMotion.fastFade()) togetherWith fadeOut(VelunaMotion.fastFade())
                                },
                                label = "compact_now_playing_tab"
                            ) { tab ->
                                when (tab) {
                                    0 -> PlayerDeck(
                                        song = song,
                                        playbackState = playbackState,
                                        positionFlow = positionFlow,
                                        isLiked = isLiked,
                                        isDownloaded = isDownloaded,
                                        onTogglePlayPause = onTogglePlayPause,
                                        onSeekTo = onSeekTo,
                                        onSkipNext = onSkipNext,
                                        onSkipPrevious = onSkipPrevious,
                                        onToggleShuffle = onToggleShuffle,
                                        onCycleRepeat = onCycleRepeat,
                                        onToggleLike = onToggleLike,
                                        onSetSpeed = onSetSpeed,
                                        onOpenSleepTimer = onOpenSleepTimer,
                                        onOpenEqualizer = onOpenEqualizer,
                                        onToggleDownload = onToggleDownload,
                                        onArtistClick = onArtistClick,
                                        onAlbumClick = onAlbumClick,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(horizontal = 24.dp, vertical = 8.dp)
                                    )
                                    1 -> QueuePanel(
                                        queue = playbackState.queue,
                                        currentIndex = playbackState.currentIndex,
                                        sourceName = playbackState.sourceName,
                                        onSelectIndex = onSelectQueueIndex,
                                        onRemoveIndex = onRemoveQueueIndex,
                                        onClearQueue = onClearQueue
                                    )
                                    2 -> SyncedLyricsPanel(
                                        lyricsState = lyricsState,
                                        positionFlow = positionFlow,
                                        romanizeEnabled = prefs.romanizeLyrics,
                                        onToggleRomanize = onToggleRomanize,
                                        onSelectProvider = onSelectLyricsProvider,
                                        onResetPinned = onResetPinnedLyrics,
                                        onNudgeOffset = onNudgeLyricsOffset,
                                        onSeekToLine = onSeekTo
                                    )
                                    else -> CommentsPanel(state = commentsState)
                                }
                            }
                        }

                        // Compact tab bar (prevents label wrap/cut-off on narrow widths)
                        TabRow(
                            selectedTabIndex = compactTab,
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.65f)
                        ) {
                            Tab(
                                selected = compactTab == 0,
                                onClick = { compactTab = 0 },
                                text = {
                                    Text("Player", style = MaterialTheme.typography.labelSmall, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                                },
                                icon = { Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp)) }
                            )
                            Tab(
                                selected = compactTab == 1,
                                onClick = { compactTab = 1 },
                                text = {
                                    Text("Queue", style = MaterialTheme.typography.labelSmall, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                                },
                                icon = { Icon(Icons.AutoMirrored.Rounded.QueueMusic, contentDescription = null, modifier = Modifier.size(18.dp)) }
                            )
                            Tab(
                                selected = compactTab == 2,
                                onClick = { compactTab = 2 },
                                text = {
                                    Text("Lyrics", style = MaterialTheme.typography.labelSmall, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                                },
                                icon = { Icon(Icons.Rounded.Lyrics, contentDescription = null, modifier = Modifier.size(18.dp)) }
                            )
                            Tab(
                                selected = compactTab == 3,
                                onClick = { compactTab = 3 },
                                text = {
                                    Text("Comments", style = MaterialTheme.typography.labelSmall, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                                },
                                icon = { Icon(Icons.AutoMirrored.Rounded.Comment, contentDescription = null, modifier = Modifier.size(18.dp)) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerDeck(
    song: SongItem,
    playbackState: PlaybackUiState,
    positionFlow: StateFlow<PlaybackPositionState>,
    isLiked: Boolean,
    isDownloaded: Boolean,
    onTogglePlayPause: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onToggleLike: () -> Unit,
    onSetSpeed: (Float) -> Unit,
    onOpenSleepTimer: () -> Unit,
    onOpenEqualizer: () -> Unit,
    onToggleDownload: () -> Unit,
    onArtistClick: (String) -> Unit,
    onAlbumClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var speedMenuOpen by remember { mutableStateOf(false) }

    val likeScale by animateFloatAsState(
        targetValue = if (isLiked) 1.18f else 1f,
        animationSpec = VelunaMotion.bouncyScaleSpring(),
        label = "deck_like_scale"
    )
    val likeTint by animateColorAsState(
        targetValue = if (isLiked) VelunaHeartRed else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = VelunaMotion.snappySpring(),
        label = "deck_like_tint"
    )

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically)
    ) {
        VelunaArtwork(
            url = song.thumbnail,
            contentDescription = song.title,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier
                .weight(1f, fill = false)
                .widthIn(max = 320.dp)
                .aspectRatio(1f)
                .shadow(20.dp, MaterialTheme.shapes.large)
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 460.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = song.title,
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        modifier = Modifier.basicMarquee()
                    )
                    val artistId = song.artistId ?: song.artistRuns.firstOrNull { it.id != null }?.id
                    Text(
                        text = song.artists.ifBlank { "Unknown Artist" },
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clickable(enabled = artistId != null) {
                            artistId?.let(onArtistClick)
                        }
                    )
                    if (!song.album.isNullOrBlank()) {
                        Text(
                            text = song.album,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.clickable(enabled = !song.albumId.isNullOrBlank()) {
                                song.albumId?.let(onAlbumClick)
                            }
                        )
                    }
                }
                IconButton(
                    onClick = onToggleLike,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("now_playing_like_btn")
                ) {
                    Icon(
                        imageVector = if (isLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        contentDescription = "Like track",
                        tint = likeTint,
                        modifier = Modifier.scale(likeScale)
                    )
                }
            }

            AnimatedVisibility(visible = !playbackState.errorMessage.isNullOrBlank()) {
                Column {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = playbackState.errorMessage.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            TrackSeekBar(
                positionFlow = positionFlow,
                onSeekTo = onSeekTo
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Transport controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onToggleShuffle,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("now_playing_shuffle_btn")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Shuffle,
                        contentDescription = "Shuffle",
                        tint = if (playbackState.shuffle) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = onSkipPrevious,
                    modifier = Modifier
                        .size(54.dp)
                        .testTag("now_playing_prev_btn")
                ) {
                    Icon(Icons.Rounded.SkipPrevious, contentDescription = "Previous", modifier = Modifier.size(34.dp))
                }
                FilledIconButton(
                    onClick = onTogglePlayPause,
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier
                        .size(66.dp)
                        .testTag("now_playing_play_pause_btn")
                ) {
                    if (playbackState.isBuffering) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(28.dp)
                        )
                    } else {
                        AnimatedContent(
                            targetState = playbackState.isPlaying,
                            transitionSpec = {
                                (scaleIn(VelunaMotion.bouncyScaleSpring(), initialScale = 0.7f) + fadeIn(VelunaMotion.fastFade()))
                                    .togetherWith(scaleOut(VelunaMotion.snappySpring(), targetScale = 0.7f) + fadeOut(VelunaMotion.fastFade()))
                            },
                            label = "deck_play_pause"
                        ) { playing ->
                            Icon(
                                imageVector = if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = if (playing) "Pause" else "Play",
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }
                }
                IconButton(
                    onClick = onSkipNext,
                    modifier = Modifier
                        .size(54.dp)
                        .testTag("now_playing_next_btn")
                ) {
                    Icon(Icons.Rounded.SkipNext, contentDescription = "Next", modifier = Modifier.size(34.dp))
                }
                IconButton(
                    onClick = onCycleRepeat,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("now_playing_repeat_btn")
                ) {
                    Icon(
                        imageVector = if (playbackState.repeatMode == RepeatMode.ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                        contentDescription = "Repeat",
                        tint = if (playbackState.repeatMode != RepeatMode.OFF) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Utility strip
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box {
                    IconButton(
                        onClick = { speedMenuOpen = true },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("now_playing_speed_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Speed,
                            contentDescription = "Playback speed ${playbackState.speed}x",
                            tint = if (playbackState.speed != 1.0f) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    DropdownMenu(
                        expanded = speedMenuOpen,
                        onDismissRequest = { speedMenuOpen = false }
                    ) {
                        listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { sp ->
                            DropdownMenuItem(
                                text = { Text("${sp}x") },
                                onClick = {
                                    speedMenuOpen = false
                                    onSetSpeed(sp)
                                }
                            )
                        }
                    }
                }

                IconButton(
                    onClick = onOpenSleepTimer,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("now_playing_sleep_btn")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Timer,
                        contentDescription = "Sleep timer",
                        tint = if (playbackState.sleepTimerRemainingMs != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(
                    onClick = onOpenEqualizer,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("now_playing_eq_btn")
                ) {
                    Icon(Icons.Rounded.Equalizer, contentDescription = "Equalizer", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                if (!song.isLocal) {
                    IconButton(
                        onClick = onToggleDownload,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("now_playing_download_btn")
                    ) {
                        Icon(
                            imageVector = if (isDownloaded) Icons.Rounded.DownloadDone else Icons.Rounded.Download,
                            contentDescription = if (isDownloaded) "Downloaded" else "Download offline",
                            tint = if (isDownloaded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TrackSeekBar(
    positionFlow: StateFlow<PlaybackPositionState>,
    onSeekTo: (Long) -> Unit
) {
    val posState by positionFlow.collectAsStateWithLifecycle()
    var draggingSlider by remember { mutableStateOf(false) }
    var dragPosMs by remember { mutableFloatStateOf(0f) }

    val duration = posState.durationMs.coerceAtLeast(1L)
    val sliderPos = if (draggingSlider) dragPosMs else posState.positionMs.toFloat().coerceIn(0f, duration.toFloat())

    Column(modifier = Modifier.fillMaxWidth()) {
        Slider(
            value = sliderPos,
            onValueChange = {
                draggingSlider = true
                dragPosMs = it
            },
            onValueChangeFinished = {
                draggingSlider = false
                onSeekTo(dragPosMs.toLong())
            },
            valueRange = 0f..duration.toFloat(),
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("now_playing_seek_slider")
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(formatMs(sliderPos.toLong()), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(formatMs(posState.durationMs), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun formatMs(ms: Long): String {
    if (ms <= 0L) return "0:00"
    val totalSecs = ms / 1000L
    val h = totalSecs / 3600L
    val m = (totalSecs % 3600L) / 60L
    val s = totalSecs % 60L
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
