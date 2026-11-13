package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.BrowseItem
import com.example.model.DownloadProgress
import com.example.model.DownloadStatus
import com.example.model.SongItem
import com.example.ui.components.TrackRow
import com.example.ui.components.VelunaArtwork

@Composable
fun LibraryScreen(
    playlists: List<BrowseItem>,
    likedSongs: List<SongItem>,
    savedAlbums: List<BrowseItem>,
    followedArtists: List<BrowseItem>,
    downloads: List<SongItem>,
    activeDownloads: Map<String, DownloadProgress>,
    localTracks: List<SongItem>,
    nowPlayingId: String?,
    onOpenBrowseItem: (BrowseItem) -> Unit,
    onPlaySongs: (List<SongItem>, Int, String) -> Unit,
    onTrackMore: (SongItem) -> Unit,
    onToggleLike: (SongItem) -> Unit,
    onOpenCreatePlaylist: () -> Unit,
    onOpenImportPlaylist: () -> Unit,
    onImportLocalFiles: (List<Uri>) -> Unit,
    onImportLocalFolder: (Uri) -> Unit,
    onRemoveLocalTrack: (String) -> Unit,
    onDeleteDownload: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Playlists", "Liked Songs", "Albums", "Artists", "Downloads", "Local Music")

    val pickFilesLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) onImportLocalFiles(uris)
    }

    val pickFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { treeUri ->
        if (treeUri != null) onImportLocalFolder(treeUri)
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Your Library",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onOpenImportPlaylist,
                    modifier = Modifier.testTag("library_import_btn")
                ) {
                    Icon(Icons.Rounded.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Import")
                }
                Button(
                    onClick = onOpenCreatePlaylist,
                    modifier = Modifier.testTag("library_new_playlist_btn")
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("New")
                }
            }
        }

        // Category Filter Chips
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            tabs.forEachIndexed { idx, label ->
                FilterChip(
                    selected = selectedTab == idx,
                    onClick = { selectedTab = idx },
                    label = { Text(label) },
                    modifier = Modifier.testTag("library_tab_${label.lowercase().replace(' ', '_')}")
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        when (selectedTab) {
            0 -> {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 110.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(playlists, key = { it.id }) { pl ->
                        ListItem(
                            headlineContent = { Text(pl.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            supportingContent = { Text(pl.subtitle.orEmpty(), maxLines = 1) },
                            leadingContent = {
                                VelunaArtwork(
                                    url = pl.thumbnail,
                                    contentDescription = pl.title,
                                    specialId = pl.id,
                                    modifier = Modifier.size(54.dp)
                                )
                            },
                            modifier = Modifier
                                .clickable { onOpenBrowseItem(pl) }
                                .testTag("library_playlist_${pl.id}")
                        )
                    }
                }
            }

            1 -> {
                if (likedSongs.isEmpty()) {
                    EmptyLibraryNotice("No liked songs yet", "Tap the heart icon on any track to save it here.")
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(bottom = 110.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        item {
                            PlayShuffleHeader(
                                countLabel = "${likedSongs.size} liked tracks",
                                onPlayAll = { onPlaySongs(likedSongs, 0, "Liked Music") },
                                onShuffleAll = {
                                    val shuffled = likedSongs.shuffled()
                                    onPlaySongs(shuffled, 0, "Liked Music")
                                }
                            )
                        }
                        items(likedSongs, key = { "liked_${it.videoId}" }) { song ->
                            TrackRow(
                                song = song,
                                isPlaying = nowPlayingId == song.videoId,
                                onClick = {
                                    val idx = likedSongs.indexOfFirst { it.videoId == song.videoId }.coerceAtLeast(0)
                                    onPlaySongs(likedSongs, idx, "Liked Music")
                                },
                                onMoreClick = { onTrackMore(song) },
                                onLikeClick = { onToggleLike(song) }
                            )
                        }
                    }
                }
            }

            2 -> {
                if (savedAlbums.isEmpty()) {
                    EmptyLibraryNotice("No saved albums", "Open any album and tap Save to build your collection.")
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(bottom = 110.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(savedAlbums, key = { it.id }) { al ->
                            ListItem(
                                headlineContent = { Text(al.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                supportingContent = { Text(al.subtitle.orEmpty(), maxLines = 1) },
                                leadingContent = {
                                    VelunaArtwork(
                                        url = al.thumbnail,
                                        contentDescription = al.title,
                                        modifier = Modifier.size(54.dp)
                                    )
                                },
                                modifier = Modifier
                                    .clickable { onOpenBrowseItem(al) }
                                    .testTag("library_album_${al.id}")
                            )
                        }
                    }
                }
            }

            3 -> {
                if (followedArtists.isEmpty()) {
                    EmptyLibraryNotice("No followed artists", "Follow artists from their page to keep them close.")
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(bottom = 110.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(followedArtists, key = { it.id }) { ar ->
                            ListItem(
                                headlineContent = { Text(ar.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                supportingContent = { Text(ar.subtitle.orEmpty(), maxLines = 1) },
                                leadingContent = {
                                    VelunaArtwork(
                                        url = ar.thumbnail,
                                        contentDescription = ar.title,
                                        isCircle = true,
                                        modifier = Modifier.size(52.dp)
                                    )
                                },
                                modifier = Modifier
                                    .clickable { onOpenBrowseItem(ar) }
                                    .testTag("library_artist_${ar.id}")
                            )
                        }
                    }
                }
            }

            4 -> {
                val activeList = activeDownloads.values.filter { it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.QUEUED || it.status == DownloadStatus.FAILED }
                if (activeList.isEmpty() && downloads.isEmpty()) {
                    EmptyLibraryNotice("No offline downloads", "Use the download action in a track menu or player to save tracks offline.")
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(bottom = 110.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(activeList, key = { "dl_active_${it.videoId}" }) { prog ->
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                shape = MaterialTheme.shapes.medium,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 6.dp)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Rounded.Download, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(prog.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text(prog.error ?: prog.status.name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    LinearProgressIndicator(progress = { prog.progress }, modifier = Modifier.fillMaxWidth())
                                }
                            }
                        }

                        if (downloads.isNotEmpty()) {
                            item {
                                PlayShuffleHeader(
                                    countLabel = "${downloads.size} offline track(s)",
                                    onPlayAll = { onPlaySongs(downloads, 0, "Downloads") },
                                    onShuffleAll = { onPlaySongs(downloads.shuffled(), 0, "Downloads") }
                                )
                            }
                        }

                        items(downloads, key = { "dl_done_${it.videoId}" }) { song ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.weight(1f)) {
                                    TrackRow(
                                        song = song,
                                        isPlaying = nowPlayingId == song.videoId,
                                        isDownloaded = true,
                                        onClick = {
                                            val idx = downloads.indexOfFirst { it.videoId == song.videoId }.coerceAtLeast(0)
                                            onPlaySongs(downloads, idx, "Downloads")
                                        },
                                        onMoreClick = { onTrackMore(song) }
                                    )
                                }
                                IconButton(
                                    onClick = { onDeleteDownload(song.videoId) },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(Icons.Rounded.Delete, contentDescription = "Delete download")
                                }
                            }
                        }
                    }
                }
            }

            else -> {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 110.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = { pickFilesLauncher.launch(arrayOf("audio/*")) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("local_pick_files_btn")
                            ) {
                                Icon(Icons.Rounded.AudioFile, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Add Files")
                            }
                            Button(
                                onClick = { pickFolderLauncher.launch(null) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("local_pick_folder_btn")
                            ) {
                                Icon(Icons.Rounded.CreateNewFolder, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Scan Folder")
                            }
                        }
                    }

                    if (localTracks.isEmpty()) {
                        item {
                            EmptyLibraryNotice(
                                "No local audio files added",
                                "Pick MP3, FLAC, M4A, or Opus files or scan a folder using Android's secure file picker."
                            )
                        }
                    } else {
                        item {
                            PlayShuffleHeader(
                                countLabel = "${localTracks.size} local track(s)",
                                onPlayAll = { onPlaySongs(localTracks, 0, "Local Music") },
                                onShuffleAll = { onPlaySongs(localTracks.shuffled(), 0, "Local Music") }
                            )
                        }
                        items(localTracks, key = { it.videoId }) { song ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.weight(1f)) {
                                    TrackRow(
                                        song = song,
                                        isPlaying = nowPlayingId == song.videoId,
                                        onClick = {
                                            val idx = localTracks.indexOfFirst { it.videoId == song.videoId }.coerceAtLeast(0)
                                            onPlaySongs(localTracks, idx, "Local Music")
                                        },
                                        onMoreClick = { onTrackMore(song) }
                                    )
                                }
                                IconButton(
                                    onClick = { song.localUri?.let(onRemoveLocalTrack) },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(Icons.Rounded.Delete, contentDescription = "Remove local track")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayShuffleHeader(
    countLabel: String,
    onPlayAll: () -> Unit,
    onShuffleAll: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(countLabel, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onShuffleAll) {
                Icon(Icons.Rounded.Shuffle, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Shuffle")
            }
            Button(onClick = onPlayAll) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Play")
            }
        }
    }
}

@Composable
private fun EmptyLibraryNotice(title: String, body: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
