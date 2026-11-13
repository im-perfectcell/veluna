package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.BlockedArtistEntity
import com.example.data.local.UserPrefsRepository
import com.example.data.local.VelunaUserPrefs
import com.example.model.AudioQuality
import com.example.model.LyricsProviderInfo
import com.example.model.ThemeMode
import com.example.model.VelunaThemePreset
import com.example.ui.theme.VelunaMotion
import com.example.ui.theme.parseHexColorOrNull
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
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

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .widthIn(max = 720.dp)
                .fillMaxSize()
                .testTag("settings_screen")
        ) {
            item {
                Text(
                    text = "Settings",
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(bottom = 2.dp)
                )
            }

            // Profile
            item {
                SettingsCard(title = "Profile") {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = displayName,
                            onValueChange = { displayName = it },
                            label = { Text("Display Name") },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("settings_display_name_input")
                        )
                        Button(
                            onClick = {
                                onSetDisplayName(displayName)
                                onStatusMessage("Name updated")
                            },
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("settings_display_name_save")
                        ) {
                            Text("Save", maxLines = 1, softWrap = false)
                        }
                    }
                }
            }

            // Appearance
            item {
                SettingsCard(title = "Appearance") {
                    val previewBg by animateColorAsState(
                        targetValue = MaterialTheme.colorScheme.background,
                        animationSpec = VelunaMotion.snappySpring(),
                        label = "preview_bg"
                    )
                    val previewPrimary by animateColorAsState(
                        targetValue = MaterialTheme.colorScheme.primary,
                        animationSpec = VelunaMotion.snappySpring(),
                        label = "preview_primary"
                    )

                    Surface(
                        color = previewBg,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = prefs.themePreset.label,
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "Corner radius ${prefs.cornerRadiusDp}dp • ${prefs.themeMode.label}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(previewPrimary)
                            )
                        }
                    }

                    Text("Theme Mode", style = MaterialTheme.typography.labelLarge)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        ThemeMode.entries.forEach { mode ->
                            FilterChip(
                                selected = prefs.themeMode == mode,
                                onClick = { onSetThemeMode(mode) },
                                label = { Text(mode.label, maxLines = 1, softWrap = false) },
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("settings_theme_mode_${mode.name.lowercase()}")
                            )
                        }
                    }

                    Text("Theme Preset", style = MaterialTheme.typography.labelLarge)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        VelunaThemePreset.entries.forEach { preset ->
                            FilterChip(
                                selected = prefs.themePreset == preset,
                                onClick = { onSetThemePreset(preset) },
                                label = { Text(preset.label, maxLines = 1, softWrap = false) },
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("settings_preset_${preset.id}")
                            )
                        }
                    }

                    Text("Accent Swatch", style = MaterialTheme.typography.labelLarge)
                    val swatches = listOf<String?>(null, "#EA580C", "#E11D48", "#8B5CF6", "#0EA5E9", "#10B981", "#F59E0B")
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        swatches.forEach { hex ->
                            val c = parseHexColorOrNull(hex) ?: MaterialTheme.colorScheme.primary
                            val selected = prefs.customAccentHex == hex
                            val swatchScale by animateFloatAsState(
                                targetValue = if (selected) 1.08f else 1f,
                                animationSpec = VelunaMotion.bouncyScaleSpring(),
                                label = "swatch_scale"
                            )
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .clickable { onSetCustomAccent(hex) }
                                    .testTag("settings_accent_${hex ?: "default"}")
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .scale(swatchScale)
                                        .clip(CircleShape)
                                        .background(if (hex == null) MaterialTheme.colorScheme.surfaceVariant else c)
                                        .border(
                                            width = if (selected) 3.dp else 1.dp,
                                            color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
                                            shape = CircleShape
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (hex == null) {
                                        Text("A", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("settings_corner_radius_slider")
                    )
                }
            }

            // Audio & Playback
            item {
                SettingsCard(title = "Audio & Playback") {
                    Text("Streaming & Download Quality", style = MaterialTheme.typography.labelLarge)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        AudioQuality.entries.forEach { q ->
                            val selected = prefs.audioQuality == q
                            val rowBg by animateColorAsState(
                                targetValue = if (selected) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerHigh
                                },
                                animationSpec = VelunaMotion.snappySpring(),
                                label = "quality_row_bg"
                            )
                            Surface(
                                color = rowBg,
                                shape = MaterialTheme.shapes.medium,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .clip(MaterialTheme.shapes.medium)
                                    .clickable { onSetAudioQuality(q) }
                                    .testTag("settings_quality_${q.name.lowercase()}")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 10.dp)
                                ) {
                                    Text(
                                        text = q.label,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                                        ),
                                        color = if (selected) {
                                            MaterialTheme.colorScheme.onPrimaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        },
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Icon(
                                        imageVector = if (selected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                                        contentDescription = null,
                                        tint = if (selected) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }

                    SettingSwitchRow("Autoplay similar tracks", prefs.autoplay, onSetAutoplay, "settings_autoplay")
                    SettingSwitchRow("Loudness normalization", prefs.loudnessNormalization, onSetLoudnessNormalization, "settings_loudness")
                    SettingSwitchRow("Prefer audio-only over music videos", prefs.hideMusicVideos, onSetHideMusicVideos, "settings_hide_videos")

                    OutlinedButton(
                        onClick = onOpenEqualizer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("settings_open_eq_btn")
                    ) {
                        Icon(Icons.Rounded.Equalizer, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Equalizer (${if (prefs.eqEnabled) prefs.eqPresetName else "Off"})",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
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
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp),
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
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = info.name,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("settings_scrobble_save_btn")
                    ) {
                        Text("Save Scrobbling Settings", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }

            // Blocked Artists
            if (blockedArtists.isNotEmpty()) {
                item {
                    SettingsCard(title = "Hidden Artists") {
                        blockedArtists.forEach { b ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = b.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(
                                    onClick = { onUnblockArtist(b.name) },
                                    modifier = Modifier.heightIn(min = 48.dp)
                                ) {
                                    Text("Unhide", maxLines = 1, softWrap = false)
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
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(
                            onClick = { exportBackupLauncher.launch("veluna-backup.json") },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("settings_backup_export_btn")
                        ) {
                            Icon(Icons.Rounded.Backup, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Export", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                        }
                        OutlinedButton(
                            onClick = { restoreBackupLauncher.launch(arrayOf("application/json", "*/*")) },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("settings_backup_restore_btn")
                        ) {
                            Icon(Icons.Rounded.Restore, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Restore", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
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
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = VelunaMotion.expressiveSpatialSpring())
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
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
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(MaterialTheme.shapes.small)
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp)
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(tag)
        )
    }
}
