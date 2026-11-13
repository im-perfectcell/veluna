package com.example.ui.screens

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.PersonRemove
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Sort
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.BrowseItem
import com.example.model.BrowseKind
import com.example.model.LOCAL_PLAYLIST_PREFIX
import com.example.model.SongItem
import com.example.ui.components.ArtWashBackdrop
import com.example.ui.components.CreateEditPlaylistDialog
import com.example.ui.components.MediaSquareCard
import com.example.ui.components.SectionHeading
import com.example.ui.components.TrackRow
import com.example.ui.components.VelunaArtwork
import com.example.ui.viewmodel.AlbumDetailState
import com.example.ui.viewmodel.ArtistDetailState
import com.example.ui.viewmodel.PlaylistDetailState
import com.example.ui.viewmodel.PlaylistSortMode

@Composable
fun AlbumDetailScreen(
    state: AlbumDetailState,
    isSaved: Boolean,
    nowPlayingId: String?,
    onBack: () -> Unit,
    onToggleSave: () -> Unit,
    onPinShortcut: (BrowseItem) -> Unit,
    onPlayTracks: (List<SongItem>, Int, String) -> Unit,
    onTrackMore: (SongItem) -> Unit,
    onOpenBrowseItem: (BrowseItem) -> Unit,
    onOpenArtist: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    when {
        state.isLoading -> DetailLoading(onBack)
        state.page == null -> DetailError(state.error ?: "Album not found", onBack)
        else -> {
            val album = state.page
            Box(modifier = modifier.fillMaxSize()) {
                ArtWashBackdrop(
                    artworkUrl = album.thumbnail,
                    accentColor = null,
                    modifier = Modifier.fillMaxWidth().height(340.dp)
                )
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 110.dp),
                    modifier = Modifier.fillMaxSize().testTag("album_detail_screen")
                ) {
                    item {
                        DetailTopBar(onBack = onBack)
                    }
                    item {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            VelunaArtwork(
                                url = album.thumbnail,
                                contentDescription = album.title,
                                shape = MaterialTheme.shapes.large,
                                modifier = Modifier.size(210.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = album.title,
                                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (!album.artist.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = album.artist,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.clickable(enabled = !album.artistId.isNullOrBlank()) {
                                        album.artistId?.let(onOpenArtist)
                                    }
                                )
                            }
                            if (!album.subtitle.isNullOrBlank() || !album.secondSubtitle.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = listOfNotNull(album.subtitle, album.secondSubtitle).joinToString(" • "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Button(
                                    enabled = album.items.isNotEmpty(),
                                    onClick = { onPlayTracks(album.items, 0, album.title) },
                                    modifier = Modifier.testTag("album_play_btn")
                                ) {
                                    Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Play")
                                }
                                OutlinedButton(
                                    enabled = album.items.isNotEmpty(),
                                    onClick = { onPlayTracks(album.items.shuffled(), 0, album.title) },
                                    modifier = Modifier.testTag("album_shuffle_btn")
                                ) {
                                    Icon(Icons.Rounded.Shuffle, contentDescription = null)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Shuffle")
                                }
                                IconButton(
                                    onClick = onToggleSave,
                                    modifier = Modifier.size(48.dp).testTag("album_save_btn")
                                ) {
                                    Icon(
                                        imageVector = if (isSaved) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                                        contentDescription = if (isSaved) "Remove album" else "Save album",
                                        tint = if (isSaved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        onPinShortcut(
                                            BrowseItem(BrowseKind.ALBUM, album.id, album.title, album.artist, album.thumbnail)
                                        )
                                    },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(Icons.Rounded.PushPin, contentDescription = "Pin shortcut")
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    itemsIndexed(album.items, key = { idx, s -> "al_${idx}_${s.videoId}" }) { idx, song ->
                        TrackRow(
                            song = song,
                            isPlaying = nowPlayingId == song.videoId,
                            trackNumber = idx + 1,
                            onClick = { onPlayTracks(album.items, idx, album.title) },
                            onMoreClick = { onTrackMore(song) }
                        )
                    }

                    items(album.sections, key = { "al_sec_${it.title}" }) { sec ->
                        Spacer(modifier = Modifier.height(14.dp))
                        SectionHeading(title = sec.title)
                        LazyRow(contentPadding = PaddingValues(horizontal = 10.dp)) {
                            items(sec.items, key = { "${sec.title}_${it.id}" }) { item ->
                                MediaSquareCard(item = item, onClick = { onOpenBrowseItem(item) })
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ArtistDetailScreen(
    state: ArtistDetailState,
    isFollowed: Boolean,
    nowPlayingId: String?,
    onBack: () -> Unit,
    onToggleFollow: () -> Unit,
    onPinShortcut: (BrowseItem) -> Unit,
    onPlayTracks: (List<SongItem>, Int, String) -> Unit,
    onTrackMore: (SongItem) -> Unit,
    onOpenBrowseItem: (BrowseItem) -> Unit,
    modifier: Modifier = Modifier
) {
    when {
        state.isLoading -> DetailLoading(onBack)
        state.page == null -> DetailError(state.error ?: "Artist not found", onBack)
        else -> {
            val artist = state.page
            Box(modifier = modifier.fillMaxSize()) {
                ArtWashBackdrop(
                    artworkUrl = artist.thumbnail ?: artist.avatar,
                    accentColor = null,
                    modifier = Modifier.fillMaxWidth().height(320.dp)
                )
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 110.dp),
                    modifier = Modifier.fillMaxSize().testTag("artist_detail_screen")
                ) {
                    item { DetailTopBar(onBack = onBack) }
                    item {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            VelunaArtwork(
                                url = artist.avatar ?: artist.thumbnail,
                                contentDescription = artist.name,
                                isCircle = true,
                                modifier = Modifier.size(164.dp)
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = artist.name,
                                style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold)
                            )
                            val statsLine = listOfNotNull(artist.subscribers, artist.monthlyListeners).joinToString(" • ")
                            if (statsLine.isNotBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(statsLine, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Spacer(modifier = Modifier.height(14.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                if (artist.topSongs.isNotEmpty()) {
                                    Button(
                                        onClick = { onPlayTracks(artist.topSongs, 0, artist.name) },
                                        modifier = Modifier.testTag("artist_play_btn")
                                    ) {
                                        Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Play Top")
                                    }
                                }
                                if (!artist.radioPlaylistId.isNullOrBlank()) {
                                    OutlinedButton(onClick = {
                                        onOpenBrowseItem(BrowseItem(BrowseKind.PLAYLIST, artist.radioPlaylistId, "${artist.name} Radio"))
                                    }) {
                                        Icon(Icons.Rounded.Radio, contentDescription = null)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Radio")
                                    }
                                }
                                OutlinedButton(
                                    onClick = onToggleFollow,
                                    modifier = Modifier.testTag("artist_follow_btn")
                                ) {
                                    Icon(
                                        imageVector = if (isFollowed) Icons.Rounded.PersonRemove else Icons.Rounded.PersonAdd,
                                        contentDescription = null
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(if (isFollowed) "Following" else "Follow")
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                    }

                    if (artist.topSongs.isNotEmpty()) {
                        item { SectionHeading(title = "Top Songs") }
                        itemsIndexed(artist.topSongs, key = { idx, s -> "ar_${idx}_${s.videoId}" }) { idx, song ->
                            TrackRow(
                                song = song,
                                isPlaying = nowPlayingId == song.videoId,
                                onClick = { onPlayTracks(artist.topSongs, idx, artist.name) },
                                onMoreClick = { onTrackMore(song) }
                            )
                        }
                    }

                    items(artist.sections, key = { "ar_sec_${it.title}" }) { sec ->
                        Spacer(modifier = Modifier.height(14.dp))
                        SectionHeading(title = sec.title)
                        LazyRow(contentPadding = PaddingValues(horizontal = 10.dp)) {
                            items(sec.items, key = { "${sec.title}_${it.id}" }) { item ->
                                MediaSquareCard(item = item, onClick = { onOpenBrowseItem(item) })
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PlaylistDetailScreen(
    state: PlaylistDetailState,
    nowPlayingId: String?,
    onBack: () -> Unit,
    onFilterChange: (String) -> Unit,
    onSortChange: (PlaylistSortMode) -> Unit,
    onPlayTracks: (List<SongItem>, Int, String) -> Unit,
    onTrackMore: (SongItem) -> Unit,
    onEditLocalPlaylist: (Long, String, String, String?) -> Unit,
    onRemoveDuplicates: (Long) -> Unit,
    onDeletePlaylist: (Long) -> Unit,
    onExportM3u: () -> Pair<String, String>?,
    onPinShortcut: (BrowseItem) -> Unit,
    onStatusMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showEditDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }
    var sortExpanded by remember { mutableStateOf(false) }
    var pendingM3uContent by remember { mutableStateOf<String?>(null) }

    val exportM3uLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("audio/x-mpegurl")
    ) { uri: Uri? ->
        val content = pendingM3uContent
        if (uri != null && content != null) {
            writeTextToUri(context, uri, content)
            onStatusMessage("Exported M3U8 playlist")
        }
        pendingM3uContent = null
    }

    when {
        state.isLoading -> DetailLoading(onBack)
        state.page == null -> DetailError(state.error ?: "Playlist not found", onBack)
        else -> {
            val page = state.page
            val numericLocalId = if (page.id.startsWith(LOCAL_PLAYLIST_PREFIX)) {
                page.id.removePrefix(LOCAL_PLAYLIST_PREFIX).toLongOrNull()
            } else null

            val displayedTracks = remember(page.items, state.filterQuery, state.sortMode) {
                val filtered = if (state.filterQuery.isBlank()) page.items else {
                    val q = state.filterQuery.trim()
                    page.items.filter {
                        it.title.contains(q, ignoreCase = true) || it.artists.contains(q, ignoreCase = true)
                    }
                }
                when (state.sortMode) {
                    PlaylistSortMode.CUSTOM -> filtered
                    PlaylistSortMode.TITLE -> filtered.sortedBy { it.title.lowercase() }
                    PlaylistSortMode.ARTIST -> filtered.sortedBy { it.artists.lowercase() }
                    PlaylistSortMode.RECENT -> filtered.sortedByDescending { it.addedAt ?: 0L }
                }
            }

            Box(modifier = modifier.fillMaxSize()) {
                ArtWashBackdrop(
                    artworkUrl = page.thumbnail,
                    accentColor = null,
                    modifier = Modifier.fillMaxWidth().height(320.dp)
                )
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 110.dp),
                    modifier = Modifier.fillMaxSize().testTag("playlist_detail_screen")
                ) {
                    item {
                        DetailTopBar(
                            onBack = onBack,
                            actions = {
                                Box {
                                    IconButton(
                                        onClick = { menuExpanded = true },
                                        modifier = Modifier.size(48.dp).testTag("playlist_more_btn")
                                    ) {
                                        Icon(Icons.Rounded.MoreVert, contentDescription = "Playlist menu")
                                    }
                                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                                        DropdownMenuItem(
                                            text = { Text("Pin to Shortcuts") },
                                            leadingIcon = { Icon(Icons.Rounded.PushPin, contentDescription = null) },
                                            onClick = {
                                                menuExpanded = false
                                                onPinShortcut(BrowseItem(BrowseKind.PLAYLIST, page.id, page.title, page.subtitle, page.thumbnail))
                                                onStatusMessage("Pinned to Home Shortcuts")
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Export M3U8") },
                                            leadingIcon = { Icon(Icons.Rounded.Download, contentDescription = null) },
                                            onClick = {
                                                menuExpanded = false
                                                val pair = onExportM3u()
                                                if (pair != null) {
                                                    pendingM3uContent = pair.second
                                                    exportM3uLauncher.launch(pair.first)
                                                }
                                            }
                                        )
                                        if (numericLocalId != null) {
                                            DropdownMenuItem(
                                                text = { Text("Edit details & cover") },
                                                leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                                                onClick = {
                                                    menuExpanded = false
                                                    showEditDialog = true
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Remove duplicates") },
                                                leadingIcon = { Icon(Icons.Rounded.CleaningServices, contentDescription = null) },
                                                onClick = {
                                                    menuExpanded = false
                                                    onRemoveDuplicates(numericLocalId)
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Delete playlist") },
                                                leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null) },
                                                onClick = {
                                                    menuExpanded = false
                                                    showDeleteConfirm = true
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        )
                    }

                    item {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            VelunaArtwork(
                                url = page.thumbnail,
                                contentDescription = page.title,
                                specialId = page.id,
                                shape = MaterialTheme.shapes.large,
                                modifier = Modifier.size(196.dp)
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = page.title,
                                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (!page.subtitle.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(page.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (!page.description.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(page.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3)
                            }
                            Spacer(modifier = Modifier.height(14.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Button(
                                    enabled = displayedTracks.isNotEmpty(),
                                    onClick = { onPlayTracks(displayedTracks, 0, page.title) },
                                    modifier = Modifier.testTag("playlist_play_btn")
                                ) {
                                    Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Play")
                                }
                                OutlinedButton(
                                    enabled = displayedTracks.isNotEmpty(),
                                    onClick = { onPlayTracks(displayedTracks.shuffled(), 0, page.title) },
                                    modifier = Modifier.testTag("playlist_shuffle_btn")
                                ) {
                                    Icon(Icons.Rounded.Shuffle, contentDescription = null)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Shuffle")
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(14.dp))

                        // Filter + Sort bar
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = state.filterQuery,
                                onValueChange = onFilterChange,
                                placeholder = { Text("Filter tracks…") },
                                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                                singleLine = true,
                                modifier = Modifier.weight(1f).testTag("playlist_filter_input")
                            )
                            Box {
                                OutlinedButton(onClick = { sortExpanded = true }) {
                                    Icon(Icons.Rounded.Sort, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(state.sortMode.label)
                                }
                                DropdownMenu(expanded = sortExpanded, onDismissRequest = { sortExpanded = false }) {
                                    PlaylistSortMode.entries.forEach { mode ->
                                        DropdownMenuItem(
                                            text = { Text(mode.label) },
                                            onClick = {
                                                sortExpanded = false
                                                onSortChange(mode)
                                            }
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    if (displayedTracks.isEmpty()) {
                        item {
                            Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                Text("No tracks in this playlist yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    } else {
                        itemsIndexed(displayedTracks, key = { idx, s -> "pl_${idx}_${s.videoId}" }) { idx, song ->
                            TrackRow(
                                song = song,
                                isPlaying = nowPlayingId == song.videoId,
                                onClick = { onPlayTracks(displayedTracks, idx, page.title) },
                                onMoreClick = { onTrackMore(song) }
                            )
                        }
                    }
                }
            }

            if (showEditDialog && numericLocalId != null) {
                CreateEditPlaylistDialog(
                    initialTitle = page.title,
                    initialDescription = page.description.orEmpty(),
                    initialCoverUri = page.coverUri,
                    isEditing = true,
                    onDismiss = { showEditDialog = false },
                    onConfirm = { t, d, cover ->
                        onEditLocalPlaylist(numericLocalId, t, d, cover)
                    }
                )
            }

            if (showDeleteConfirm && numericLocalId != null) {
                AlertDialog(
                    onDismissRequest = { showDeleteConfirm = false },
                    title = { Text("Delete \"${page.title}\"?") },
                    text = { Text("This removes the local playlist and cannot be undone.") },
                    confirmButton = {
                        Button(onClick = {
                            showDeleteConfirm = false
                            onDeletePlaylist(numericLocalId)
                        }) { Text("Delete") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
                    }
                )
            }
        }
    }
}

@Composable
private fun DetailTopBar(
    onBack: () -> Unit,
    actions: @Composable () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier.size(48.dp).testTag("detail_back_btn")
        ) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
        }
        actions()
    }
}

@Composable
private fun DetailLoading(onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        DetailTopBar(onBack = onBack)
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    }
}

@Composable
private fun DetailError(msg: String, onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        DetailTopBar(onBack = onBack)
        Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text(msg, color = MaterialTheme.colorScheme.error)
        }
    }
}

private fun writeTextToUri(context: Context, uri: Uri, content: String) {
    runCatching {
        context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(content) }
    }
}
