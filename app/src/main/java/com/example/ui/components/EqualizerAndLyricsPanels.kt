package com.example.ui.components

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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.UserPrefsRepository
import com.example.model.EqualizerPresets
import com.example.model.SongItem
import com.example.playback.PlaybackPositionState
import com.example.ui.viewmodel.CommentsUiState
import com.example.ui.viewmodel.LyricsUiState
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.roundToInt

@Composable
fun SyncedLyricsPanel(
    lyricsState: LyricsUiState,
    positionFlow: StateFlow<PlaybackPositionState>,
    romanizeEnabled: Boolean,
    onToggleRomanize: () -> Unit,
    onSelectProvider: (String) -> Unit,
    onResetPinned: () -> Unit,
    onNudgeOffset: (Long) -> Unit,
    onSeekToLine: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var providerMenuOpen by remember { mutableStateOf(false) }
    val data = lyricsState.data

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box {
                AssistChip(
                    onClick = { providerMenuOpen = true },
                    label = {
                        Text(
                            text = data?.source ?: "Source",
                            maxLines = 1
                        )
                    },
                    leadingIcon = {
                        Icon(Icons.Rounded.Lyrics, contentDescription = null, modifier = Modifier.size(16.dp))
                    },
                    modifier = Modifier.testTag("lyrics_source_picker")
                )
                DropdownMenu(
                    expanded = providerMenuOpen,
                    onDismissRequest = { providerMenuOpen = false }
                ) {
                    UserPrefsRepository.ALL_LYRICS_PROVIDERS.forEach { (id, name) ->
                        DropdownMenuItem(
                            text = { Text(name) },
                            onClick = {
                                providerMenuOpen = false
                                onSelectProvider(id)
                            }
                        )
                    }
                    if (data?.pinned == true) {
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("Reset to auto") },
                            leadingIcon = { Icon(Icons.Rounded.Refresh, contentDescription = null) },
                            onClick = {
                                providerMenuOpen = false
                                onResetPinned()
                            }
                        )
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (data?.synced == true) {
                    TextButton(
                        onClick = { onNudgeOffset(-250L) },
                        modifier = Modifier.testTag("lyrics_offset_minus")
                    ) { Text("-0.25s") }
                    Text(
                        text = "${data.offsetMs / 1000.0}s",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(
                        onClick = { onNudgeOffset(250L) },
                        modifier = Modifier.testTag("lyrics_offset_plus")
                    ) { Text("+0.25s") }
                } else if (data != null && !data.instrumental && data.lines.isNotEmpty()) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.padding(end = 6.dp)
                    ) {
                        Text(
                            text = "Plain lyrics",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                IconButton(
                    onClick = onToggleRomanize,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("lyrics_toggle_romanize")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Translate,
                        contentDescription = "Toggle romanization",
                        tint = if (romanizeEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        when {
            lyricsState.isLoading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            data == null -> {
                Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(
                        text = "No lyrics found for this track.\nTry switching provider above.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
            data.instrumental -> {
                Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(
                        text = "Instrumental track",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            else -> {
                val positionState by positionFlow.collectAsStateWithLifecycle()
                val effectivePos = positionState.positionMs - data.offsetMs
                val activeIndex = remember(data.lines, effectivePos, data.synced) {
                    if (!data.synced) -1
                    else data.lines.indexOfLast { (it.timeMs ?: Long.MAX_VALUE) <= effectivePos }
                }
                val listState = rememberLazyListState()

                LaunchedEffect(activeIndex, data.synced) {
                    if (data.synced && activeIndex >= 0) {
                        runCatching {
                            listState.animateScrollToItem((activeIndex - 2).coerceAtLeast(0))
                        }
                    }
                }

                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    itemsIndexed(data.lines) { idx, line ->
                        val isActive = data.synced && idx == activeIndex
                        val isPast = data.synced && idx < activeIndex
                        val canSeek = data.synced && line.timeMs != null
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.small)
                                .clickable(enabled = canSeek) {
                                    line.timeMs?.let { onSeekToLine((it + data.offsetMs).coerceAtLeast(0L)) }
                                }
                                .padding(vertical = 4.dp, horizontal = 6.dp)
                                .alpha(
                                    when {
                                        !data.synced -> 0.96f
                                        isActive -> 1f
                                        isPast -> 0.62f
                                        else -> 0.80f
                                    }
                                )
                        ) {
                            if (isActive && line.words.isNotEmpty()) {
                                val primaryColor = MaterialTheme.colorScheme.primary
                                val upcomingColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f)
                                val annotated = buildAnnotatedString {
                                    line.words.forEachIndexed { wIdx, w ->
                                        if (wIdx > 0) append(" ")
                                        val reached = effectivePos >= w.startMs
                                        withStyle(
                                            SpanStyle(
                                                color = if (reached) primaryColor else upcomingColor,
                                                fontWeight = if (reached) FontWeight.Bold else FontWeight.Medium
                                            )
                                        ) {
                                            append(w.text)
                                        }
                                    }
                                }
                                Text(
                                    text = annotated,
                                    style = MaterialTheme.typography.headlineMedium
                                )
                            } else {
                                Text(
                                    text = line.text,
                                    style = if (isActive) {
                                        MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold)
                                    } else {
                                        MaterialTheme.typography.titleLarge
                                    },
                                    color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                            }
                            if (romanizeEnabled && !line.romanized.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = line.romanized,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun QueuePanel(
    queue: List<SongItem>,
    currentIndex: Int,
    sourceName: String?,
    onSelectIndex: (Int) -> Unit,
    onRemoveIndex: (Int) -> Unit,
    onClearQueue: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Up Next", style = MaterialTheme.typography.titleMedium)
                if (!sourceName.isNullOrBlank()) {
                    Text(
                        text = "Playing from $sourceName",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (queue.size > 1) {
                TextButton(
                    onClick = onClearQueue,
                    modifier = Modifier.testTag("queue_clear_btn")
                ) {
                    Icon(Icons.Rounded.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Clear")
                }
            }
        }

        if (queue.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Queue is empty", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(bottom = 24.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                itemsIndexed(queue, key = { idx, s -> "${idx}_${s.videoId}" }) { idx, song ->
                    val isCurrent = idx == currentIndex
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectIndex(idx) }
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .testTag("queue_item_${idx}"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        VelunaArtwork(
                            url = song.thumbnail,
                            contentDescription = song.title,
                            modifier = Modifier.size(44.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = song.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                if (song.autoplay) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Icon(
                                        imageVector = Icons.Rounded.AutoAwesome,
                                        contentDescription = "Autoplay",
                                        tint = MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                            Text(
                                text = song.artists,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (!isCurrent) {
                            IconButton(
                                onClick = { onRemoveIndex(idx) },
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("queue_remove_${idx}")
                            ) {
                                Icon(Icons.Rounded.Close, contentDescription = "Remove from queue")
                            }
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.GraphicEq,
                                contentDescription = "Now playing",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(end = 12.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CommentsPanel(
    state: CommentsUiState,
    modifier: Modifier = Modifier
) {
    when {
        state.isLoading -> {
            Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        state.page == null -> {
            Box(modifier = modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Text(
                    text = state.error ?: "Comments unavailable",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        state.page.disabled -> {
            Box(modifier = modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Text(
                    text = "Comments are disabled or unavailable for this track.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
        else -> {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = modifier.fillMaxSize()
            ) {
                if (!state.page.countText.isNullOrBlank()) {
                    item {
                        Text(
                            text = "${state.page.countText} comments",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                items(state.page.comments, key = { it.id }) { comment ->
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            VelunaArtwork(
                                url = comment.authorAvatar,
                                contentDescription = comment.author,
                                isCircle = true,
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = comment.author,
                                        style = MaterialTheme.typography.labelLarge,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = comment.publishedTime,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (comment.isPinned) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Icon(Icons.Rounded.PushPin, contentDescription = "Pinned", modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                                    }
                                    if (comment.isHearted) {
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Icon(Icons.Rounded.Favorite, contentDescription = "Hearted", modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = comment.text,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                if (!comment.likeCount.isNullOrBlank()) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Rounded.ThumbUp, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = comment.likeCount,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EqualizerBottomSheet(
    enabled: Boolean,
    presetName: String,
    bandsDb: List<Float>,
    loudnessNormEnabled: Boolean,
    onToggleEnabled: (Boolean) -> Unit,
    onSelectPreset: (String) -> Unit,
    onBandChange: (Int, Float) -> Unit,
    onToggleLoudness: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("equalizer_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("10-Band Equalizer", style = MaterialTheme.typography.headlineMedium)
                Switch(
                    checked = enabled,
                    onCheckedChange = onToggleEnabled,
                    modifier = Modifier.testTag("eq_enable_switch")
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Loudness Normalization", style = MaterialTheme.typography.bodyMedium)
                Switch(
                    checked = loudnessNormEnabled,
                    onCheckedChange = onToggleLoudness,
                    modifier = Modifier.testTag("eq_loudness_switch")
                )
            }

            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                EqualizerPresets.presets.keys.forEach { name ->
                    FilterChip(
                        selected = presetName == name,
                        onClick = { onSelectPreset(name) },
                        label = { Text(name) },
                        modifier = Modifier.testTag("eq_preset_${name.lowercase().replace(' ', '_')}")
                    )
                }
            }

            EqualizerPresets.bandLabels.forEachIndexed { idx, hzLabel ->
                val value = bandsDb.getOrElse(idx) { 0f }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "${hzLabel}Hz",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.width(56.dp)
                    )
                    Slider(
                        value = value,
                        onValueChange = { onBandChange(idx, it) },
                        valueRange = -12f..12f,
                        enabled = enabled,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("eq_band_slider_$idx")
                    )
                    Text(
                        text = "${value.roundToInt()} dB",
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.End,
                        modifier = Modifier.width(48.dp)
                    )
                }
            }
        }
    }
}
