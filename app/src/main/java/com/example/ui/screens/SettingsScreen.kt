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
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import com.example.data.cloud.CloudAuthUiState
import com.example.data.local.BlockedArtistEntity
import com.example.data.local.UserPrefsRepository
import com.example.data.local.VelunaUserPrefs
import com.example.model.AudioQuality
import com.example.model.LyricsProviderInfo
import com.example.model.TasteProfile
import com.example.model.ThemeMode
import com.example.model.VelunaThemePreset
import com.example.ui.components.VelunaArtwork
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.ButtonDefaults
import com.example.ui.theme.VelunaMotion
import com.example.ui.theme.parseHexColorOrNull
import com.example.ui.theme.velunaFilterChipBorder
import com.example.ui.theme.velunaFilterChipColors
import com.example.ui.theme.velunaOutlinedButtonBorder
import com.example.ui.theme.velunaOutlinedButtonColors
import com.example.ui.theme.velunaOutlinedFieldColors
import com.example.ui.theme.velunaPrimaryButtonColors
import com.example.ui.theme.velunaSliderColors
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    prefs: VelunaUserPrefs,
    blockedArtists: List<BlockedArtistEntity>,
    tasteProfile: TasteProfile? = null,
    cloudAuthState: CloudAuthUiState = CloudAuthUiState(),
    onSignInWithGoogle: () -> Unit = {},
    onSignOut: () -> Unit = {},
    onSyncNow: () -> Unit = {},
    onOpenTasteOnboarding: () -> Unit = {},
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
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(bottom = 2.dp)
                )
            }

            // Profile & Google Cloud Sync
            item {
                SettingsCard(title = "Profile & Cloud Sync") {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.outlineVariant,
                                MaterialTheme.shapes.medium
                            )
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            if (cloudAuthState.isSignedIn) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    if (!cloudAuthState.photoUrl.isNullOrBlank()) {
                                        VelunaArtwork(
                                            url = cloudAuthState.photoUrl,
                                            contentDescription = cloudAuthState.displayName ?: "Profile picture",
                                            isCircle = true,
                                            modifier = Modifier.size(48.dp)
                                        )
                                    } else {
                                        Surface(
                                            color = MaterialTheme.colorScheme.primaryContainer,
                                            shape = CircleShape,
                                            modifier = Modifier.size(48.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    imageVector = Icons.Rounded.AccountCircle,
                                                    contentDescription = "Google account",
                                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                                    modifier = Modifier.size(28.dp)
                                                )
                                            }
                                        }
                                    }

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = cloudAuthState.displayName?.takeIf { it.isNotBlank() }
                                                ?: prefs.userDisplayName,
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (!cloudAuthState.email.isNullOrBlank()) {
                                            Text(
                                                text = cloudAuthState.email,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        val syncSubtitle = remember(cloudAuthState.isSyncing, cloudAuthState.lastSyncedAtMs, cloudAuthState.syncStatus) {
                                            when {
                                                cloudAuthState.isSyncing -> "Syncing profile, playlists & likes…"
                                                cloudAuthState.lastSyncedAtMs != null -> {
                                                    val fmt = SimpleDateFormat("HH:mm", Locale.getDefault())
                                                    "Cloud backup active • Last synced ${fmt.format(Date(cloudAuthState.lastSyncedAtMs))}"
                                                }
                                                else -> cloudAuthState.syncStatus ?: "Connected with Google"
                                            }
                                        }
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (cloudAuthState.isSyncing) Icons.Rounded.CloudSync else Icons.Rounded.CloudDone,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Text(
                                                text = syncSubtitle,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    OutlinedButton(
                                        onClick = onSyncNow,
                                        enabled = !cloudAuthState.isSyncing && !cloudAuthState.isAuthBusy,
                                        colors = velunaOutlinedButtonColors(),
                                        border = velunaOutlinedButtonBorder(!cloudAuthState.isSyncing && !cloudAuthState.isAuthBusy),
                                        modifier = Modifier
                                            .weight(1f)
                                            .heightIn(min = 48.dp)
                                            .testTag("settings_cloud_sync_btn")
                                    ) {
                                        if (cloudAuthState.isSyncing) {
                                            CircularProgressIndicator(
                                                strokeWidth = 2.dp,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        } else {
                                            Icon(Icons.Rounded.CloudSync, contentDescription = null, modifier = Modifier.size(18.dp))
                                        }
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = if (cloudAuthState.isSyncing) "Syncing…" else "Sync now",
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }

                                    TextButton(
                                        onClick = onSignOut,
                                        enabled = !cloudAuthState.isAuthBusy,
                                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                                        modifier = Modifier
                                            .heightIn(min = 48.dp)
                                            .testTag("settings_google_signout_btn")
                                    ) {
                                        Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Sign out", maxLines = 1, softWrap = false)
                                    }
                                }
                            } else {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        shape = CircleShape,
                                        modifier = Modifier.size(44.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Rounded.AccountCircle,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                                modifier = Modifier.size(26.dp)
                                            )
                                        }
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Google Cloud Sync",
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "Sign in with Google to sync your profile, playlists, liked songs, and taste across devices.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Button(
                                    onClick = onSignInWithGoogle,
                                    enabled = !cloudAuthState.isAuthBusy,
                                    colors = velunaPrimaryButtonColors(),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 48.dp)
                                        .testTag("settings_google_signin_btn")
                                ) {
                                    if (cloudAuthState.isAuthBusy) {
                                        CircularProgressIndicator(
                                            color = MaterialTheme.colorScheme.onPrimary,
                                            strokeWidth = 2.dp,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    } else {
                                        Icon(Icons.AutoMirrored.Rounded.Login, contentDescription = null, modifier = Modifier.size(18.dp))
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = if (cloudAuthState.isAuthBusy) "Signing in…" else "Sign in with Google",
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = displayName,
                            onValueChange = { displayName = it },
                            label = { Text("Display Name") },
                            colors = velunaOutlinedFieldColors(),
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
                            colors = velunaPrimaryButtonColors(),
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("settings_display_name_save")
                        ) {
                            Text("Save", maxLines = 1, softWrap = false)
                        }
                    }

                    val tasteSummary = remember(tasteProfile) {
                        val g = tasteProfile?.selectedGenres?.size ?: 0
                        val a = tasteProfile?.seedArtists?.size ?: 0
                        val s = tasteProfile?.seedSongs?.size ?: 0
                        if (g + a + s == 0) "Pick favorite genres, artists & songs"
                        else "$g genres • $a artists • $s songs"
                    }
                    OutlinedButton(
                        onClick = onOpenTasteOnboarding,
                        colors = velunaOutlinedButtonColors(),
                        border = velunaOutlinedButtonBorder(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("settings_tune_taste_btn")
                    ) {
                        Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Tune Music Taste ($tasteSummary)",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
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
                        contentColor = MaterialTheme.colorScheme.onBackground,
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
                                    color = MaterialTheme.colorScheme.onBackground,
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

                    val chipColors = velunaFilterChipColors()
                    Text("Theme Mode", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        ThemeMode.entries.forEach { mode ->
                            val selected = prefs.themeMode == mode
                            FilterChip(
                                selected = selected,
                                onClick = { onSetThemeMode(mode) },
                                colors = chipColors,
                                border = velunaFilterChipBorder(selected),
                                label = { Text(mode.label, maxLines = 1, softWrap = false) },
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("settings_theme_mode_${mode.name.lowercase()}")
                            )
                        }
                    }

                    Text("Theme Preset", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        VelunaThemePreset.entries.forEach { preset ->
                            val selected = prefs.themePreset == preset
                            FilterChip(
                                selected = selected,
                                onClick = { onSetThemePreset(preset) },
                                colors = chipColors,
                                border = velunaFilterChipBorder(selected),
                                label = { Text(preset.label, maxLines = 1, softWrap = false) },
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("settings_preset_${preset.id}")
                            )
                        }
                    }

                    Text("Accent Swatch", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
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
                                        .background(if (hex == null) MaterialTheme.colorScheme.surfaceContainerHighest else c)
                                        .border(
                                            width = if (selected) 3.dp else 1.dp,
                                            color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
                                            shape = CircleShape
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (hex == null) {
                                        Text(
                                            text = "A",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
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

                    Text("Corner Radius (${prefs.cornerRadiusDp} dp)", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
                    Slider(
                        value = prefs.cornerRadiusDp.toFloat(),
                        onValueChange = { onSetCornerRadius(it.roundToInt()) },
                        valueRange = 4f..24f,
                        colors = velunaSliderColors(),
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
                        colors = velunaOutlinedButtonColors(),
                        border = velunaOutlinedButtonBorder(),
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
                        colors = velunaOutlinedFieldColors(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = lastFmUser,
                        onValueChange = { lastFmUser = it },
                        label = { Text("Last.fm Username") },
                        colors = velunaOutlinedFieldColors(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = lastFmKey,
                        onValueChange = { lastFmKey = it },
                        label = { Text("Last.fm API Key") },
                        colors = velunaOutlinedFieldColors(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = lastFmSecret,
                        onValueChange = { lastFmSecret = it },
                        label = { Text("Last.fm API Secret") },
                        colors = velunaOutlinedFieldColors(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = lastFmSession,
                        onValueChange = { lastFmSession = it },
                        label = { Text("Last.fm Session Key") },
                        colors = velunaOutlinedFieldColors(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = {
                            onSaveScrobbling(scrobbleEnabled, lastFmUser, lastFmKey, lastFmSecret, lastFmSession, listenBrainzToken)
                        },
                        colors = velunaPrimaryButtonColors(),
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
                            colors = velunaPrimaryButtonColors(),
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
                            colors = velunaOutlinedButtonColors(),
                            border = velunaOutlinedButtonBorder(),
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
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)),
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
                color = MaterialTheme.colorScheme.onSurface,
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
            color = MaterialTheme.colorScheme.onSurface,
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
