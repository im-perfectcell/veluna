package com.example.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.VelunaUserPrefs
import com.example.model.BrowseItem
import com.example.model.BrowseKind
import com.example.model.SongItem
import com.example.ui.components.ArtWashBackdrop
import com.example.ui.components.FamiliarArtistCard
import com.example.ui.components.MediaSquareCard
import com.example.ui.components.SectionHeading
import com.example.ui.components.ShortcutTile
import com.example.ui.components.TrackRow
import com.example.ui.theme.VelunaMotion
import com.example.ui.viewmodel.HomeUiState
import java.time.LocalTime

@Composable
fun HomeScreen(
    homeState: HomeUiState,
    prefs: VelunaUserPrefs,
    shortcuts: List<BrowseItem>,
    jumpBack: List<BrowseItem>,
    familiarArtists: List<BrowseItem>,
    nowPlayingSong: SongItem?,
    artworkAccent: Color?,
    likedIds: Set<String> = emptySet(),
    onSelectChip: (String?) -> Unit,
    onRetry: () -> Unit,
    onOpenBrowseItem: (BrowseItem) -> Unit,
    onTrackMore: (SongItem) -> Unit,
    onToggleLike: ((SongItem) -> Unit)? = null,
    onOpenHistory: () -> Unit,
    onOpenEditHome: () -> Unit,
    modifier: Modifier = Modifier
) {
    val greeting = remember(prefs.userDisplayName) {
        val hour = LocalTime.now().hour
        val prefix = when (hour) {
            in 5..11 -> "Good morning"
            in 12..16 -> "Good afternoon"
            in 17..21 -> "Good evening"
            else -> "Good night"
        }
        val name = prefs.userDisplayName.takeIf { it.isNotBlank() && it != "Listener" }
        if (name != null) "$prefix, $name" else prefix
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (prefs.showHeroArtworkBackdrop) {
            ArtWashBackdrop(
                artworkUrl = nowPlayingSong?.thumbnail,
                accentColor = artworkAccent,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(320.dp)
            )
        }

        LazyColumn(
            contentPadding = PaddingValues(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            modifier = Modifier
                .fillMaxSize()
                .testTag("home_screen_list")
        ) {
            // Top Greeting & Action Bar
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 8.dp, top = 14.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_veluna_mark_art),
                            contentDescription = "Veluna",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = homeState.moodsTitle ?: greeting,
                                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onBackground,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (homeState.moodsTitle != null) "Mood & Genre Mix" else "Veluna Music",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Row {
                        IconButton(
                            onClick = onOpenHistory,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("home_history_btn")
                        ) {
                            Icon(Icons.Rounded.History, contentDescription = "Listening history")
                        }
                        IconButton(
                            onClick = onOpenEditHome,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("home_customize_btn")
                        ) {
                            Icon(Icons.Rounded.Tune, contentDescription = "Customize home")
                        }
                    }
                }
            }

            // Mood Filter Chips
            if (prefs.showMoodChips && homeState.homePage.chips.isNotEmpty()) {
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item {
                            FilterChip(
                                selected = homeState.selectedChipParams == null && homeState.moodsTitle == null,
                                onClick = { onSelectChip(null) },
                                label = { Text("All", maxLines = 1, softWrap = false) },
                                modifier = Modifier.testTag("home_chip_all")
                            )
                        }
                        items(homeState.homePage.chips, key = { "${it.title}_${it.params}" }) { chip ->
                            FilterChip(
                                selected = homeState.selectedChipParams == chip.params && chip.params.isNotBlank(),
                                onClick = {
                                    if (chip.params.isNotBlank()) onSelectChip(chip.params)
                                    else onSelectChip(null)
                                },
                                label = { Text(chip.title, maxLines = 1, softWrap = false) },
                                modifier = Modifier.testTag("home_chip_${chip.title.lowercase().replace(' ', '_')}")
                            )
                        }
                    }
                }
            }

            // Shortcuts Grid
            if (shortcuts.isNotEmpty()) {
                item {
                    Column(modifier = Modifier.animateContentSize(VelunaMotion.expressiveSpatialSpring())) {
                        SectionHeading(title = "Shortcuts")
                        val chunked = shortcuts.take(6).chunked(2)
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(horizontal = 16.dp)
                        ) {
                            chunked.forEach { pair ->
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    pair.forEach { sc ->
                                        ShortcutTile(
                                            item = sc,
                                            onClick = { onOpenBrowseItem(sc) },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                    if (pair.size == 1) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Jump Back In
            if (jumpBack.isNotEmpty() && homeState.moodsTitle == null && homeState.selectedChipParams == null) {
                item {
                    Column {
                        SectionHeading(title = "Jump back in", actionLabel = "History", onActionClick = onOpenHistory)
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(jumpBack, key = { "jump_${it.id}" }) { item ->
                                MediaSquareCard(
                                    item = item,
                                    showPlayBadge = true,
                                    onClick = { onOpenBrowseItem(item) }
                                )
                            }
                        }
                    }
                }
            }

            // Familiar Artists (01, 02, ...)
            if (familiarArtists.isNotEmpty() && homeState.moodsTitle == null && homeState.selectedChipParams == null) {
                item {
                    Column {
                        SectionHeading(title = "Familiar Artists")
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            itemsIndexed(familiarArtists, key = { _, a -> "fam_${a.id}" }) { idx, artist ->
                                FamiliarArtistCard(
                                    index = idx,
                                    item = artist,
                                    onClick = { onOpenBrowseItem(artist) }
                                )
                            }
                        }
                    }
                }
            }

            // Loading / Error state for remote InnerTube home feed
            if (homeState.isLoading) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            } else if (!homeState.error.isNullOrBlank() && homeState.homePage.sections.isEmpty()) {
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "Could not load online feed",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = homeState.error,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Button(
                                onClick = onRetry,
                                modifier = Modifier.testTag("home_retry_btn")
                            ) {
                                Icon(Icons.Rounded.Refresh, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Retry", maxLines = 1, softWrap = false)
                            }
                        }
                    }
                }
            }

            // Remote InnerTube & Personalized Home Shelves
            itemsIndexed(
                homeState.homePage.sections,
                key = { idx, sec -> "${idx}_${sec.kind}_${sec.title}_${sec.items.size}" }
            ) { _, section ->
                val isQuickPicks = section.kind == "PERSONALIZED_SONGS" ||
                    section.title.contains("quick picks", ignoreCase = true) ||
                    (section.items.isNotEmpty() && section.items.all { it.kind == BrowseKind.SONG } && section.items.size <= 16)

                Column(
                    modifier = Modifier.animateItem(
                        fadeInSpec = VelunaMotion.fastFade(),
                        placementSpec = VelunaMotion.expressiveIntOffsetSpring()
                    )
                ) {
                    SectionHeading(
                        title = section.title,
                        actionLabel = if (!section.moreBrowseId.isNullOrBlank()) "Open" else null,
                        onActionClick = section.moreBrowseId?.let { bid ->
                            {
                                onOpenBrowseItem(
                                    BrowseItem(
                                        kind = BrowseKind.PLAYLIST,
                                        id = bid,
                                        title = section.title
                                    )
                                )
                            }
                        }
                    )

                    if (isQuickPicks) {
                        Column(modifier = Modifier.padding(horizontal = 4.dp)) {
                            section.items.take(8).forEach { item ->
                                val song = item.toSongItem()
                                TrackRow(
                                    song = song,
                                    isPlaying = nowPlayingSong?.videoId == song.videoId,
                                    isLiked = song.videoId in likedIds,
                                    onClick = { onOpenBrowseItem(item) },
                                    onMoreClick = { onTrackMore(song) },
                                    onLikeClick = onToggleLike?.let { fn -> { fn(song) } }
                                )
                            }
                        }
                    } else {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(section.items, key = { "${section.title}_${it.id}_${it.title}" }) { item ->
                                MediaSquareCard(
                                    item = item,
                                    showPlayBadge = item.kind == BrowseKind.SONG,
                                    onClick = { onOpenBrowseItem(item) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
