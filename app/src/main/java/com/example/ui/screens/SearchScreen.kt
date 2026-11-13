package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.NorthWest
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.BrowseItem
import com.example.model.BrowseKind
import com.example.model.MoodTile
import com.example.model.SongItem
import com.example.ui.components.MoodGridTile
import com.example.ui.components.SectionHeading
import com.example.ui.components.TrackRow
import com.example.ui.components.VelunaArtwork
import com.example.ui.theme.VelunaMotion
import com.example.ui.viewmodel.SearchUiState

@Composable
fun SearchScreen(
    state: SearchUiState,
    searchHistory: List<String>,
    nowPlayingId: String?,
    onQueryChange: (String) -> Unit,
    onSubmitSearch: (String?) -> Unit,
    onClearSearch: () -> Unit,
    onSelectCategory: (Int) -> Unit,
    onRemoveHistory: (String) -> Unit,
    onClearHistory: () -> Unit,
    onOpenBrowseItem: (BrowseItem) -> Unit,
    onTrackMore: (SongItem) -> Unit,
    onOpenMoodTile: (MoodTile) -> Unit,
    modifier: Modifier = Modifier
) {
    val keyboard = LocalSoftwareKeyboardController.current

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 840.dp)
                .fillMaxSize()
        ) {
            OutlinedTextField(
                value = state.query,
                onValueChange = onQueryChange,
                placeholder = { Text("Search songs, albums, artists…", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                trailingIcon = {
                    if (state.query.isNotEmpty()) {
                        IconButton(
                            onClick = onClearSearch,
                            modifier = Modifier.size(48.dp).testTag("search_clear_btn")
                        ) {
                            Icon(Icons.Rounded.Close, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true,
                shape = MaterialTheme.shapes.large,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        keyboard?.hide()
                        onSubmitSearch(null)
                    }
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .testTag("search_input")
            )

            when {
                state.isSearching -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }

                state.results != null -> {
                    val tabs = listOf("Top", "Songs", "Videos", "Albums", "Artists", "Playlists")
                    ScrollableTabRow(
                        selectedTabIndex = state.selectedCategoryTab,
                        edgePadding = 12.dp
                    ) {
                        tabs.forEachIndexed { idx, label ->
                            Tab(
                                selected = state.selectedCategoryTab == idx,
                                onClick = { onSelectCategory(idx) },
                                text = { Text(label, maxLines = 1, softWrap = false) },
                                modifier = Modifier.testTag("search_tab_${label.lowercase()}")
                            )
                        }
                    }

                    val list = when (state.selectedCategoryTab) {
                        0 -> state.results.top + state.results.songs.take(6) + state.results.albums.take(4) + state.results.artists.take(4)
                        1 -> state.results.songs
                        2 -> state.results.videos
                        3 -> state.results.albums
                        4 -> state.results.artists
                        else -> state.results.playlists
                    }.distinctBy { "${it.kind}_${it.id}" }

                    if (list.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No results in ${tabs[state.selectedCategoryTab]}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(top = 8.dp, bottom = 28.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(list, key = { "${it.kind}_${it.id}_${it.title}" }) { item ->
                                if (item.kind == BrowseKind.SONG) {
                                    val song = item.toSongItem()
                                    TrackRow(
                                        song = song,
                                        isPlaying = nowPlayingId == song.videoId,
                                        onClick = { onOpenBrowseItem(item) },
                                        onMoreClick = { onTrackMore(song) },
                                        modifier = Modifier.animateItem(
                                            fadeInSpec = VelunaMotion.fastFade(),
                                            placementSpec = VelunaMotion.expressiveIntOffsetSpring()
                                        )
                                    )
                                } else {
                                    ListItem(
                                        headlineContent = { Text(item.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                        supportingContent = {
                                            Text(
                                                listOfNotNull(item.kind.name.lowercase().replaceFirstChar { it.uppercase() }, item.subtitle).joinToString(" • "),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        },
                                        leadingContent = {
                                            VelunaArtwork(
                                                url = item.thumbnail,
                                                contentDescription = item.title,
                                                isCircle = item.kind == BrowseKind.ARTIST,
                                                modifier = Modifier.size(48.dp)
                                            )
                                        },
                                        modifier = Modifier
                                            .clickable { onOpenBrowseItem(item) }
                                            .animateItem(
                                                fadeInSpec = VelunaMotion.fastFade(),
                                                placementSpec = VelunaMotion.expressiveIntOffsetSpring()
                                            )
                                            .testTag("search_result_${item.id}")
                                    )
                                }
                            }
                        }
                    }
                }

                state.query.isNotBlank() -> {
                    LazyColumn(
                        contentPadding = PaddingValues(bottom = 28.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(state.suggestions.queries, key = { "sug_$it" }) { suggestion ->
                            ListItem(
                                headlineContent = { Text(suggestion, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                leadingContent = { Icon(Icons.Rounded.Search, contentDescription = null) },
                                trailingContent = {
                                    IconButton(
                                        onClick = { onQueryChange(suggestion) },
                                        modifier = Modifier.size(48.dp)
                                    ) {
                                        Icon(Icons.Rounded.NorthWest, contentDescription = "Fill query")
                                    }
                                },
                                modifier = Modifier
                                    .clickable {
                                        keyboard?.hide()
                                        onSubmitSearch(suggestion)
                                    }
                                    .testTag("search_suggestion_$suggestion")
                            )
                        }
                        items(state.suggestions.items, key = { "sug_item_${it.id}" }) { item ->
                            if (item.kind == BrowseKind.SONG) {
                                val song = item.toSongItem()
                                TrackRow(
                                    song = song,
                                    isPlaying = nowPlayingId == song.videoId,
                                    onClick = { onOpenBrowseItem(item) },
                                    onMoreClick = { onTrackMore(song) }
                                )
                            } else {
                                ListItem(
                                    headlineContent = { Text(item.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                    supportingContent = { Text(item.subtitle.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                    leadingContent = {
                                        VelunaArtwork(
                                            url = item.thumbnail,
                                            contentDescription = item.title,
                                            isCircle = item.kind == BrowseKind.ARTIST,
                                            modifier = Modifier.size(44.dp)
                                        )
                                    },
                                    modifier = Modifier.clickable { onOpenBrowseItem(item) }
                                )
                            }
                        }
                    }
                }

                else -> {
                    LazyColumn(
                        contentPadding = PaddingValues(bottom = 28.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        if (searchHistory.isNotEmpty()) {
                            item {
                                Column {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Recent searches", style = MaterialTheme.typography.titleMedium)
                                        TextButton(onClick = onClearHistory) { Text("Clear all", maxLines = 1, softWrap = false) }
                                    }
                                    searchHistory.take(6).forEach { hist ->
                                        ListItem(
                                            headlineContent = { Text(hist, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                            leadingContent = { Icon(Icons.Rounded.History, contentDescription = null) },
                                            trailingContent = {
                                                IconButton(
                                                    onClick = { onRemoveHistory(hist) },
                                                    modifier = Modifier.size(48.dp)
                                                ) {
                                                    Icon(Icons.Rounded.Close, contentDescription = "Remove")
                                                }
                                            },
                                            modifier = Modifier
                                                .clickable { onSubmitSearch(hist) }
                                                .testTag("search_history_$hist")
                                        )
                                    }
                                }
                            }
                        }

                        if (state.isMoodsLoading && state.moodsSections.isEmpty()) {
                            item {
                                Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator()
                                }
                            }
                        }

                        items(state.moodsSections, key = { it.title }) { section ->
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                SectionHeading(title = section.title)
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.padding(horizontal = 16.dp)
                                ) {
                                    section.items.chunked(2).forEach { pair ->
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            pair.forEach { tile ->
                                                MoodGridTile(
                                                    tile = tile,
                                                    sectionTitle = section.title,
                                                    onClick = { onOpenMoodTile(tile) },
                                                    modifier = Modifier.weight(1f)
                                                )
                                            }
                                            if (pair.size == 1) Spacer(modifier = Modifier.weight(1f))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
