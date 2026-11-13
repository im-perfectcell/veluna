package com.example.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.BrowseItem
import com.example.model.BrowseKind
import com.example.model.LOCAL_PLAYLIST_PREFIX
import com.example.model.Rating
import com.example.model.SongItem
import com.example.ui.viewmodel.ImportDialogState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackMenuSheet(
    song: SongItem,
    isDownloaded: Boolean,
    onDismiss: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onStartRadio: () -> Unit,
    onToggleLike: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onToggleDownload: () -> Unit,
    onGoToArtist: ((String) -> Unit)?,
    onGoToAlbum: ((String) -> Unit)?,
    onPinShortcut: () -> Unit,
    onBlockArtist: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("track_menu_sheet")
    ) {
        Column(modifier = Modifier.padding(bottom = 24.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                VelunaArtwork(
                    url = song.thumbnail,
                    contentDescription = song.title,
                    modifier = Modifier.size(52.dp)
                )
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(song.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(song.artists, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            MenuActionRow(Icons.AutoMirrored.Rounded.PlaylistPlay, "Play next", "menu_play_next") {
                onPlayNext(); onDismiss()
            }
            MenuActionRow(Icons.Rounded.QueueMusic, "Add to queue", "menu_add_queue") {
                onAddToQueue(); onDismiss()
            }
            if (!song.isLocal) {
                MenuActionRow(Icons.Rounded.Radio, "Start radio", "menu_start_radio") {
                    onStartRadio(); onDismiss()
                }
            }
            MenuActionRow(
                icon = if (song.rating == Rating.LIKE) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                label = if (song.rating == Rating.LIKE) "Remove from Liked" else "Add to Liked Music",
                tag = "menu_toggle_like"
            ) {
                onToggleLike(); onDismiss()
            }
            MenuActionRow(Icons.AutoMirrored.Rounded.PlaylistAdd, "Add to playlist…", "menu_add_playlist") {
                onDismiss(); onAddToPlaylist()
            }
            if (!song.isLocal) {
                MenuActionRow(
                    icon = if (isDownloaded) Icons.Rounded.Delete else Icons.Rounded.Download,
                    label = if (isDownloaded) "Remove offline download" else "Download for offline",
                    tag = "menu_toggle_download"
                ) {
                    onToggleDownload(); onDismiss()
                }
            }
            val artistId = song.artistId ?: song.artistRuns.firstOrNull { it.id != null }?.id
            if (artistId != null && onGoToArtist != null) {
                MenuActionRow(Icons.Rounded.Person, "Go to artist", "menu_go_artist") {
                    onDismiss(); onGoToArtist(artistId)
                }
            }
            if (!song.albumId.isNullOrBlank() && onGoToAlbum != null) {
                MenuActionRow(Icons.Rounded.Album, "Go to album", "menu_go_album") {
                    onDismiss(); onGoToAlbum(song.albumId)
                }
            }
            MenuActionRow(Icons.Rounded.PushPin, "Pin to Home Shortcuts", "menu_pin_shortcut") {
                onPinShortcut(); onDismiss()
            }
            if (song.artists.isNotBlank()) {
                MenuActionRow(Icons.Rounded.Block, "Hide tracks by ${song.artists.substringBefore(',')}", "menu_block_artist") {
                    onBlockArtist(); onDismiss()
                }
            }
        }
    }
}

@Composable
private fun MenuActionRow(
    icon: ImageVector,
    label: String,
    tag: String,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = { Text(label) },
        leadingContent = { Icon(icon, contentDescription = null) },
        modifier = Modifier
            .clickable(onClick = onClick)
            .testTag(tag)
    )
}

@Composable
fun AddToPlaylistDialog(
    song: SongItem,
    playlists: List<BrowseItem>,
    onDismiss: () -> Unit,
    onPickPlaylist: (Long) -> Unit,
    onCreateNew: (String) -> Unit
) {
    var newTitle by remember { mutableStateOf("") }
    val localPlaylists = playlists.filter { it.id.startsWith(LOCAL_PLAYLIST_PREFIX) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add to playlist") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = newTitle,
                        onValueChange = { newTitle = it },
                        label = { Text("New playlist name") },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("quick_new_playlist_input")
                    )
                    Button(
                        enabled = newTitle.isNotBlank(),
                        onClick = {
                            onCreateNew(newTitle.trim())
                            onDismiss()
                        },
                        modifier = Modifier.testTag("quick_new_playlist_create")
                    ) {
                        Text("Create")
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                LazyColumn(modifier = Modifier.height(200.dp)) {
                    items(localPlaylists, key = { it.id }) { pl ->
                        val numericId = pl.id.removePrefix(LOCAL_PLAYLIST_PREFIX).toLongOrNull()
                        if (numericId != null) {
                            ListItem(
                                headlineContent = { Text(pl.title) },
                                supportingContent = { Text(pl.subtitle.orEmpty()) },
                                leadingContent = {
                                    VelunaArtwork(
                                        url = pl.thumbnail,
                                        contentDescription = null,
                                        modifier = Modifier.size(40.dp)
                                    )
                                },
                                modifier = Modifier
                                    .clickable {
                                        onPickPlaylist(numericId)
                                        onDismiss()
                                    }
                                    .testTag("pick_playlist_${pl.id}")
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
fun CreateEditPlaylistDialog(
    initialTitle: String = "",
    initialDescription: String = "",
    initialCoverUri: String? = null,
    isEditing: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: (title: String, description: String, coverUri: String?) -> Unit
) {
    var title by remember { mutableStateOf(initialTitle) }
    var description by remember { mutableStateOf(initialDescription) }
    var coverUri by remember { mutableStateOf(initialCoverUri) }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            coverUri = uri.toString()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isEditing) "Edit playlist" else "New playlist") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (isEditing) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        VelunaArtwork(
                            url = coverUri,
                            contentDescription = "Playlist cover",
                            modifier = Modifier.size(64.dp)
                        )
                        OutlinedButton(
                            onClick = {
                                photoPicker.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            modifier = Modifier.testTag("edit_playlist_cover_btn")
                        ) {
                            Icon(Icons.Rounded.Image, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Choose cover")
                        }
                    }
                }
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("playlist_title_input")
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description (optional)") },
                    maxLines = 3,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("playlist_desc_input")
                )
            }
        },
        confirmButton = {
            Button(
                enabled = title.isNotBlank(),
                onClick = {
                    onConfirm(title.trim(), description.trim(), coverUri)
                    onDismiss()
                },
                modifier = Modifier.testTag("playlist_dialog_confirm")
            ) {
                Text(if (isEditing) "Save" else "Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun ImportPlaylistDialog(
    state: ImportDialogState,
    onDismiss: () -> Unit,
    onImportYouTube: (String) -> Unit,
    onImportFile: (Uri, String) -> Unit,
    onOpenPlaylist: (Long) -> Unit
) {
    var tab by remember { mutableIntStateOf(0) }
    var urlInput by remember { mutableStateOf("") }
    var filePlaylistName by remember { mutableStateOf("Imported Playlist") }

    val fileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            onImportFile(uri, filePlaylistName.ifBlank { "Imported Playlist" })
        }
    }

    AlertDialog(
        onDismissRequest = { if (!state.isImporting) onDismiss() },
        title = { Text("Import Playlist") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TabRow(selectedTabIndex = tab) {
                    Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("YouTube URL") })
                    Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("CSV / M3U") })
                }

                if (tab == 0) {
                    OutlinedTextField(
                        value = urlInput,
                        onValueChange = { urlInput = it },
                        label = { Text("YouTube / YT Music playlist link or ID") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("import_yt_url_input")
                    )
                    Button(
                        enabled = !state.isImporting && urlInput.isNotBlank(),
                        onClick = { onImportYouTube(urlInput) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("import_yt_start_btn")
                    ) {
                        Text("Import from YouTube Music")
                    }
                } else {
                    Text(
                        text = "Supports Exportify Spotify CSV or standard .m3u / .m3u8 files.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = filePlaylistName,
                        onValueChange = { filePlaylistName = it },
                        label = { Text("Destination playlist title") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedButton(
                        enabled = !state.isImporting,
                        onClick = { fileLauncher.launch(arrayOf("*/*")) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("import_file_pick_btn")
                    ) {
                        Icon(Icons.Rounded.UploadFile, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Choose CSV or M3U file")
                    }
                }

                if (state.isImporting || state.statusText.isNotBlank()) {
                    LinearProgressIndicator(
                        progress = { state.progress.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(state.statusText, style = MaterialTheme.typography.bodySmall)
                }
                if (!state.error.isNullOrBlank()) {
                    Text(state.error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            if (state.completedPlaylistId != null) {
                Button(
                    onClick = {
                        onOpenPlaylist(state.completedPlaylistId)
                        onDismiss()
                    },
                    modifier = Modifier.testTag("import_open_result_btn")
                ) {
                    Text("Open Playlist")
                }
            }
        },
        dismissButton = {
            TextButton(
                enabled = !state.isImporting,
                onClick = onDismiss
            ) {
                Text("Close")
            }
        }
    )
}

@Composable
fun SleepTimerDialog(
    activeRemainingMs: Long?,
    onDismiss: () -> Unit,
    onSelectMinutes: (Int?) -> Unit
) {
    val presets = listOf(10, 15, 30, 45, 60, 90)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sleep Timer") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (activeRemainingMs != null) {
                    val mins = (activeRemainingMs / 60_000L) + 1
                    Text(
                        text = "Active: pausing in ~$mins min",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
                presets.forEach { m ->
                    ListItem(
                        headlineContent = { Text("$m minutes") },
                        modifier = Modifier
                            .clickable {
                                onSelectMinutes(m)
                                onDismiss()
                            }
                            .testTag("sleep_preset_$m")
                    )
                }
            }
        },
        confirmButton = {
            if (activeRemainingMs != null) {
                TextButton(onClick = {
                    onSelectMinutes(null)
                    onDismiss()
                }) {
                    Text("Turn Off")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
fun EditHomeDialog(
    showMoodChips: Boolean,
    showHeroBackdrop: Boolean,
    shortcuts: List<BrowseItem>,
    onToggleMoodChips: (Boolean) -> Unit,
    onToggleHeroBackdrop: (Boolean) -> Unit,
    onRemoveShortcut: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Customize Home") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Show mood filter chips", style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = showMoodChips,
                        onCheckedChange = onToggleMoodChips,
                        modifier = Modifier.testTag("home_toggle_mood_chips")
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Artwork atmospheric backdrop", style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = showHeroBackdrop,
                        onCheckedChange = onToggleHeroBackdrop,
                        modifier = Modifier.testTag("home_toggle_hero_backdrop")
                    )
                }
                if (shortcuts.isNotEmpty()) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Text("Pinned Shortcuts", style = MaterialTheme.typography.labelLarge)
                    LazyColumn(modifier = Modifier.height(160.dp)) {
                        items(shortcuts, key = { it.id }) { sc ->
                            ListItem(
                                headlineContent = { Text(sc.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                trailingContent = {
                                    TextButton(onClick = { onRemoveShortcut(sc.id) }) {
                                        Text("Remove")
                                    }
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        }
    )
}
