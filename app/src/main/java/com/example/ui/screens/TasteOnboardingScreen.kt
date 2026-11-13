package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.repository.TasteEngine
import com.example.model.TasteProfile
import com.example.model.TasteSeed
import com.example.ui.components.ArtWashBackdrop
import com.example.ui.components.VelunaArtwork
import com.example.ui.theme.VelunaMotion
import com.example.ui.viewmodel.OnboardingSearchUiState

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TasteOnboardingScreen(
    initialProfile: TasteProfile?,
    searchState: OnboardingSearchUiState,
    canNavigateBack: Boolean,
    onQueryChange: (String) -> Unit,
    onSave: (genres: List<String>, artists: List<TasteSeed>, songs: List<TasteSeed>) -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (canNavigateBack) {
        BackHandler { onSkip() }
    }

    val selectedGenres = remember(initialProfile) {
        mutableStateListOf<String>().apply {
            initialProfile?.selectedGenres?.let { addAll(it) }
        }
    }
    val selectedArtists = remember(initialProfile) {
        mutableStateListOf<TasteSeed>().apply {
            initialProfile?.seedArtists?.let { addAll(it) }
        }
    }
    val selectedSongs = remember(initialProfile) {
        mutableStateListOf<TasteSeed>().apply {
            initialProfile?.seedSongs?.let { addAll(it) }
        }
    }

    val totalSelected = selectedGenres.size + selectedArtists.size + selectedSongs.size

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("taste_onboarding_screen")
    ) {
        ArtWashBackdrop(
            artworkUrl = selectedSongs.lastOrNull()?.thumbnail ?: selectedArtists.lastOrNull()?.thumbnail,
            accentColor = MaterialTheme.colorScheme.primary,
            modifier = Modifier.fillMaxSize()
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header bar with Skip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 720.dp)
                    .padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 6.dp),
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
                    Column {
                        Text(
                            text = if (canNavigateBack) "Tune Music Taste" else "Welcome to Veluna",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Personalize your Home & Smart Autoplay",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                TextButton(
                    onClick = onSkip,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("onboarding_skip_btn")
                ) {
                    Text(if (canNavigateBack) "Cancel" else "Skip", maxLines = 1, softWrap = false)
                }
            }

            // Main scrollable body
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .widthIn(max = 720.dp)
            ) {
                // Live Search Input for Artists & Songs
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Add Favorite Artists & Songs",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                        OutlinedTextField(
                            value = searchState.query,
                            onValueChange = onQueryChange,
                            placeholder = { Text("Search artists or tracks (e.g. Weeknd, Lo-fi)…") },
                            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                            trailingIcon = {
                                when {
                                    searchState.isSearching -> {
                                        CircularProgressIndicator(
                                            strokeWidth = 2.dp,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    searchState.query.isNotEmpty() -> {
                                        IconButton(
                                            onClick = { onQueryChange("") },
                                            modifier = Modifier.size(48.dp)
                                        ) {
                                            Icon(Icons.Rounded.Close, contentDescription = "Clear search")
                                        }
                                    }
                                }
                            },
                            singleLine = true,
                            shape = MaterialTheme.shapes.large,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("onboarding_search_input")
                        )
                    }
                }

                // Selected Artists & Songs summary chips
                if (selectedArtists.isNotEmpty() || selectedSongs.isNotEmpty()) {
                    item {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.animateContentSize(VelunaMotion.expressiveSpatialSpring())
                        ) {
                            Text(
                                text = "Your Picks (${selectedArtists.size + selectedSongs.size})",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                selectedArtists.forEach { artist ->
                                    InputChip(
                                        selected = true,
                                        onClick = { selectedArtists.removeAll { it.id == artist.id } },
                                        label = { Text(artist.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                        trailingIcon = {
                                            Icon(Icons.Rounded.Close, contentDescription = "Remove", modifier = Modifier.size(16.dp))
                                        }
                                    )
                                }
                                selectedSongs.forEach { song ->
                                    InputChip(
                                        selected = true,
                                        onClick = { selectedSongs.removeAll { it.id == song.id } },
                                        label = { Text(song.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                        trailingIcon = {
                                            Icon(Icons.Rounded.Close, contentDescription = "Remove", modifier = Modifier.size(16.dp))
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // Live Search Results (Artists row + Songs list)
                if (searchState.artists.isNotEmpty()) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                text = "Matching Artists",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                items(searchState.artists, key = { "onb_art_${it.id}" }) { artist ->
                                    val picked = selectedArtists.any { it.id == artist.id }
                                    val borderCol by animateColorAsState(
                                        targetValue = if (picked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                        animationSpec = VelunaMotion.snappySpring(),
                                        label = "artist_pick_border"
                                    )
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier
                                            .width(96.dp)
                                            .clip(MaterialTheme.shapes.medium)
                                            .clickable {
                                                if (picked) {
                                                    selectedArtists.removeAll { it.id == artist.id }
                                                } else {
                                                    selectedArtists.add(
                                                        TasteSeed(
                                                            id = artist.id,
                                                            name = artist.title,
                                                            subtitle = artist.subtitle,
                                                            thumbnail = artist.thumbnail
                                                        )
                                                    )
                                                }
                                            }
                                            .padding(4.dp)
                                            .testTag("onboarding_artist_${artist.id}")
                                    ) {
                                        Box(modifier = Modifier.size(76.dp)) {
                                            VelunaArtwork(
                                                url = artist.thumbnail,
                                                contentDescription = artist.title,
                                                isCircle = true,
                                                modifier = Modifier
                                                    .matchParentSize()
                                                    .border(if (picked) 3.dp else 1.dp, borderCol, CircleShape)
                                            )
                                            if (picked) {
                                                Icon(
                                                    imageVector = Icons.Rounded.CheckCircle,
                                                    contentDescription = "Selected",
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier
                                                        .align(Alignment.BottomEnd)
                                                        .size(22.dp)
                                                        .background(MaterialTheme.colorScheme.surface, CircleShape)
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = artist.title,
                                            style = MaterialTheme.typography.labelMedium,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (searchState.songs.isNotEmpty()) {
                    item {
                        Text(
                            text = "Matching Songs",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    items(searchState.songs, key = { "onb_song_${it.id}" }) { song ->
                        val picked = selectedSongs.any { it.id == song.id }
                        val containerColor by animateColorAsState(
                            targetValue = if (picked) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f) else MaterialTheme.colorScheme.surfaceContainer,
                            animationSpec = VelunaMotion.snappySpring(),
                            label = "song_pick_bg"
                        )
                        Surface(
                            color = containerColor,
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 56.dp)
                                .clip(MaterialTheme.shapes.medium)
                                .clickable {
                                    if (picked) {
                                        selectedSongs.removeAll { it.id == song.id }
                                    } else {
                                        selectedSongs.add(
                                            TasteSeed(
                                                id = song.id,
                                                name = song.title,
                                                subtitle = song.subtitle,
                                                thumbnail = song.thumbnail
                                            )
                                        )
                                    }
                                }
                                .testTag("onboarding_song_${song.id}")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                VelunaArtwork(
                                    url = song.thumbnail,
                                    contentDescription = song.title,
                                    modifier = Modifier.size(44.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = song.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (!song.subtitle.isNullOrBlank()) {
                                        Text(
                                            text = song.subtitle,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                Icon(
                                    imageVector = if (picked) Icons.Rounded.CheckCircle else Icons.Rounded.Add,
                                    contentDescription = if (picked) "Selected" else "Select song",
                                    tint = if (picked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // Favorite Genres Grid
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "Pick Your Favorite Genres",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                        Text(
                            text = "Select 2 or more vibes to bootstrap your recommendations.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            TasteEngine.AVAILABLE_GENRES.forEach { genre ->
                                val selected = genre.id in selectedGenres
                                FilterChip(
                                    selected = selected,
                                    onClick = {
                                        if (selected) selectedGenres.remove(genre.id)
                                        else selectedGenres.add(genre.id)
                                    },
                                    label = { Text(genre.label, maxLines = 1, softWrap = false) },
                                    modifier = Modifier
                                        .heightIn(min = 48.dp)
                                        .testTag("onboarding_genre_${genre.id}")
                                )
                            }
                        }
                    }
                }
            }

            // Sticky Bottom CTA Bar
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.96f),
                tonalElevation = 6.dp,
                shadowElevation = 10.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 720.dp)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (totalSelected == 0) "Select genres, artists, or songs" else "$totalSelected taste seed(s) picked",
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Adapts live as you play, like & skip",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Button(
                        enabled = totalSelected > 0,
                        onClick = {
                            onSave(
                                selectedGenres.toList(),
                                selectedArtists.toList(),
                                selectedSongs.toList()
                            )
                        },
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("onboarding_done_btn")
                    ) {
                        Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Start Listening", maxLines = 1, softWrap = false)
                    }
                }
            }
        }
    }
}
