package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.BlockedArtistEntity
import com.example.data.local.UserPrefsRepository
import com.example.data.local.VelunaUserPrefs
import com.example.model.AudioQuality
import com.example.model.LyricsProviderInfo
import com.example.model.ThemeMode
import com.example.model.VelunaThemePreset
import com.example.ui.theme.parseHexColorOrNull
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    prefs: VelunaUserPrefs,
    blockedArtists: List<BlockedArtistEntity>,
    onSetThemePreset: (VelunaThemePreset) -> Unit,
    onSetThemeMode: (ThemeMode) -> Unit,
    onSetCustomAccent: (String?) -> Unit,
    onSetAdaptToArtwork: (Boolean) -> Unit,
    onSetCornerRadius: (Int) -> Unit,
    onSetDisplayName: (String) -> Unit,
    onSetAudioQuality: (AudioQuality) -> Unit,
    onSetAutoplay: (Boolean) -> Unit,
    onSetLoudnessNormalization: (Boolean) -> Unit,
    onSetHideMusicVideos: (Boolean) -> Unit,
    onOpenEqualizer: () -> Unit,
    onUpdateLyricsProviders: (List<LyricsProviderInfo>) -> Unit,
    onSaveScrobbling: (Boolean, String, String, String, String, String) -> Unit,
    onUnblockArtist: (String) -> Unit,
    onBuildBackupJson: suspend () -> String,
    onRestoreBackupJson: (String) -> Unit,
    onStatusMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var displayName by remember(prefs.userDisplayName) { mutableStateOf(prefs.userDisplayName) }
    var scrobbleEnabled by remember(prefs.scrobblingEnabled) { mutableStateOf(prefs.scrobblingEnabled) }
    var listenBrainzToken by remember(prefs.listenBrainzToken) { mutableStateOf(prefs.listenBrainzToken) }
    var lastFmUser by remember(prefs.lastFmUsername) { mutableStateOf(prefs.lastFmUsername) }
    var lastFmKey by remember(prefs.lastFmApiKey) { mutableStateOf(prefs.lastFmApiKey) }
    var lastFmSecret by remember(prefs.lastFmApiSecret) { mutableStateOf(prefs.lastFmApiSecret) }
    var lastFmSession by remember(prefs.lastFmSessionKey) { mutableStateOf(prefs.lastFmSessionKey) }

    val providers = remember(prefs.lyricsProviderOrder) {
        UserPrefsRepository.parseProviderOrder(prefs.lyricsProviderOrder)
    }

    val exportBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val payload = onBuildBackupJson()
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(payload) }
                    onStatusMessage("Backup exported")
                }.onFailure {
                    onStatusMessage("Export failed: ${it.message}")
                }
            }
        }
    }

    val restoreBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val text = runCatching {
                    context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                }.getOrNull()
                if (!text.isNullOrBlank()) {
                    onRestoreBackupJson(text)
                }
            }
        }
    }

    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier
            .fillMaxSize()
            .padding(bottom = 84.dp)
            .testTag("settings_screen")
    ) {
        item {
            Text("Settings", style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold))
        }

        // Profile
        item {
            SettingsCard(title = "Profile") {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = displayName,
                        onValueChange = { displayName = it },
                        label = { Text("Display Name") },
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("settings_display_name_input")
                    )
                    Button(
                        onClick = {
                            onSetDisplayName(displayName)
                            onStatusMessage("Name updated")
                        },
                        modifier = Modifier.testTag("settings_display_name_save")
                    ) { Text("Save") }
                }
            }
        }

        // Appearance
        item {
            SettingsCard(title = "Appearance") {
                // Live theme preview swatch
                Surface(
                    color = MaterialTheme.colorScheme.background,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(prefs.themePreset.label, style = MaterialTheme.typography.titleSmall)
                            Text("Corner radius ${prefs.cornerRadiusDp}dp", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                        )
                    }
                }

                Text("Theme Mode", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeMode.entries.forEach { mode ->
                        FilterChip(
                            selected = prefs.themeMode == mode,
                            onClick = { onSetThemeMode(mode) },
                            label = { Text(mode.label) },
                            modifier = Modifier.testTag("settings_theme_mode_${mode.name.lowercase()}")
                        )
                    }
                }

                Text("Theme Preset", style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    VelunaThemePreset.entries.forEach { preset ->
                        FilterChip(
                            selected = prefs.themePreset == preset,
                            onClick = { onSetThemePreset(preset) },
                            label = { Text(preset.label) },
                            modifier = Modifier.testTag("settings_preset_${preset.id}")
                        )
                    }
                }

                Text("Accent Swatch", style = MaterialTheme.typography.labelLarge)
                val swatches = listOf<String?>(null, "#EA580C", "#E11D48", "#8B5CF6", "#0EA5E9", "#10B981", "#F59E0B")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    swatches.forEach { hex ->
                        val c = parseHexColorOrNull(hex) ?: MaterialTheme.colorScheme.primary
                        val selected = prefs.customAccentHex == hex
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(if (hex == null) MaterialTheme.colorScheme.surfaceVariant else c)
                                .border(
                                    width = if (selected) 3.dp else 1.dp,
                                    color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
                                    shape = CircleShape
                                )
                                .clickable { onSetCustomAccent(hex) }
                                .testTag("settings_accent_${hex ?: "default"}"),
                            contentAlignment = Alignment.Center
                        ) {
                            if (hex == null) Text("A", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }

                SettingSwitchRow(
                    title = "Adapt accent to Now Playing artwork",
                    checked = prefs.adaptToArtwork,
                    onCheckedChange = onSetAdaptToArtwork,
                    tag = "settings_adapt_art"
                )

                Text("Corner Radius (${prefs.cornerRadiusDp} dp)", style = MaterialTheme.typography.labelLarge)
                Slider(
                    value = prefs.cornerRadiusDp.toFloat(),
                    onValueChange = { onSetCornerRadius(it.roundToInt()) },
                    valueRange = 4f..24f,
                    modifier = Modifier.testTag("settings_corner_radius_slider")
                )
            }
        }

        // Audio & Playback
        item {
            SettingsCard(title = "Audio & Playback") {
                Text("Streaming & Download Quality", style = MaterialTheme.typography.labelLarge)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    AudioQuality.entries.forEach { q ->
                        FilterChip(
                            selected = prefs.audioQuality == q,
                            onClick = { onSetAudioQuality(q) },
                            label = { Text(q.label) },
                            modifier = Modifier.testTag("settings_quality_${q.name.lowercase()}")
                        )
                    }
                }
                SettingSwitchRow("Autoplay similar tracks", prefs.autoplay, onSetAutoplay, "settings_autoplay")
                SettingSwitchRow("Loudness normalization", prefs.loudnessNormalization, onSetLoudnessNormalization, "settings_loudness")
                SettingSwitchRow("Prefer audio-only over music videos", prefs.hideMusicVideos, onSetHideMusicVideos, "settings_hide_videos")

                OutlinedButton(
                    onClick = onOpenEqualizer,
                    modifier = Modifier.fillMaxWidth().testTag("settings_open_eq_btn")
                ) {
                    Icon(Icons.Rounded.Equalizer, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Open 10-Band Equalizer (${if (prefs.eqEnabled) prefs.eqPresetName else "Off"})")
                }
            }
        }

        // Lyrics Providers
        item {
            SettingsCard(title = "Lyrics Providers") {
                Text(
                    "Providers are queried in priority order. Toggle or reorder them below.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                providers.forEachIndexed { idx, info ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Switch(
                            checked = info.enabled,
                            onCheckedChange = { checked ->
                                val next = providers.toMutableList()
                                next[idx] = info.copy(enabled = checked)
                                onUpdateLyricsProviders(next)
                            },
                            modifier = Modifier.testTag("lyrics_provider_toggle_${info.id}")
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(info.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        IconButton(
                            enabled = idx > 0,
                            onClick = {
                                val next = providers.toMutableList()
                                val item = next.removeAt(idx)
                                next.add(idx - 1, item)
                                onUpdateLyricsProviders(next)
                            },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(Icons.Rounded.ArrowUpward, contentDescription = "Move up")
                        }
                        IconButton(
                            enabled = idx < providers.lastIndex,
                            onClick = {
                                val next = providers.toMutableList()
                                val item = next.removeAt(idx)
                                next.add(idx + 1, item)
                                onUpdateLyricsProviders(next)
                            },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(Icons.Rounded.ArrowDownward, contentDescription = "Move down")
                        }
                    }
                }
            }
        }

        // Scrobbling
        item {
            SettingsCard(title = "Scrobbling (Last.fm & ListenBrainz)") {
                SettingSwitchRow(
                    title = "Enable scrobbling",
                    checked = scrobbleEnabled,
                    onCheckedChange = { scrobbleEnabled = it },
                    tag = "settings_scrobble_switch"
                )
                OutlinedTextField(
                    value = listenBrainzToken,
                    onValueChange = { listenBrainzToken = it },
                    label = { Text("ListenBrainz User Token") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = lastFmUser,
                    onValueChange = { lastFmUser = it },
                    label = { Text("Last.fm Username") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = lastFmKey,
                    onValueChange = { lastFmKey = it },
                    label = { Text("Last.fm API Key") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = lastFmSecret,
                    onValueChange = { lastFmSecret = it },
                    label = { Text("Last.fm API Secret") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = lastFmSession,
                    onValueChange = { lastFmSession = it },
                    label = { Text("Last.fm Session Key") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = {
                        onSaveScrobbling(scrobbleEnabled, lastFmUser, lastFmKey, lastFmSecret, lastFmSession, listenBrainzToken)
                    },
                    modifier = Modifier.fillMaxWidth().testTag("settings_scrobble_save_btn")
                ) {
                    Text("Save Scrobbling Settings")
                }
            }
        }

        // Blocked Artists
        if (blockedArtists.isNotEmpty()) {
            item {
                SettingsCard(title = "Hidden Artists") {
                    blockedArtists.forEach { b ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(b.name, style = MaterialTheme.typography.bodyMedium)
                            TextButton(onClick = { onUnblockArtist(b.name) }) {
                                Text("Unhide")
                            }
                        }
                    }
                }
            }
        }

        // Backup & Restore
        item {
            SettingsCard(title = "Backup & Restore") {
                Text(
                    "Export your playlists, liked tracks, hidden artists, and theme settings as JSON.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = { exportBackupLauncher.launch("veluna-backup.json") },
                        modifier = Modifier.weight(1f).testTag("settings_backup_export_btn")
                    ) {
                        Icon(Icons.Rounded.Backup, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Export JSON")
                    }
                    OutlinedButton(
                        onClick = { restoreBackupLauncher.launch(arrayOf("application/json", "*/*")) },
                        modifier = Modifier.weight(1f).testTag("settings_backup_restore_btn")
                    ) {
                        Icon(Icons.Rounded.Restore, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Restore")
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(32.dp)) }
    }
}

@Composable
private fun SettingsCard(
    title: String,
    content: @Composable () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            content()
        }
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    tag: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(tag)
        )
    }
}
