package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.Explicit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.BrowseItem
import com.example.model.BrowseKind
import com.example.model.Rating
import com.example.model.SongItem

@Composable
fun SectionHeading(
    title: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onActionClick: (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (actionLabel != null && onActionClick != null) {
            TextButton(
                onClick = onActionClick,
                modifier = Modifier.testTag("section_action_${title.lowercase().replace(' ', '_')}")
            ) {
                Text(actionLabel)
            }
        }
    }
}

@Composable
fun MediaSquareCard(
    item: BrowseItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showPlayBadge: Boolean = false
) {
    val isArtist = item.kind == BrowseKind.ARTIST
    Column(
        modifier = modifier
            .width(152.dp)
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(6.dp)
            .testTag("media_card_${item.id}")
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
        ) {
            VelunaArtwork(
                url = item.thumbnail,
                contentDescription = item.title,
                specialId = item.id,
                isCircle = isArtist,
                modifier = Modifier.matchParentSize()
            )
            if (showPlayBadge && item.kind == BrowseKind.SONG) {
                Surface(
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(50),
                    shadowElevation = 4.dp,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .size(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = "Play",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(6.dp)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = item.title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = if (isArtist) TextAlign.Center else TextAlign.Start,
            modifier = Modifier.fillMaxWidth()
        )
        if (!item.subtitle.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = item.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = if (isArtist) TextAlign.Center else TextAlign.Start,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun FamiliarArtistCard(
    index: Int,
    item: BrowseItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .width(128.dp)
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(6.dp)
            .testTag("familiar_artist_${item.id}"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(112.dp)
        ) {
            VelunaArtwork(
                url = item.thumbnail,
                contentDescription = item.title,
                isCircle = true,
                modifier = Modifier.matchParentSize()
            )
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(2.dp)
            ) {
                Text(
                    text = "%02d".format(index + 1),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = item.title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun ShortcutTile(
    item: BrowseItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
        shape = MaterialTheme.shapes.medium,
        modifier = modifier
            .height(60.dp)
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .testTag("shortcut_tile_${item.id}")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(end = 10.dp)
        ) {
            VelunaArtwork(
                url = item.thumbnail,
                contentDescription = item.title,
                specialId = item.id,
                shape = RoundedCornerShape(topStart = 10.dp, bottomStart = 10.dp),
                modifier = Modifier.size(60.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun TrackRow(
    song: SongItem,
    isPlaying: Boolean = false,
    isDownloaded: Boolean = false,
    trackNumber: Int? = null,
    onClick: () -> Unit,
    onMoreClick: () -> Unit,
    onLikeClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val rowBg = if (isPlaying) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
    } else {
        MaterialTheme.colorScheme.background.copy(alpha = 0f)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(rowBg)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .testTag("track_row_${song.videoId}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (trackNumber != null) {
            Text(
                text = trackNumber.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(28.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
        } else {
            VelunaArtwork(
                url = song.thumbnail,
                contentDescription = song.title,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
        }

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (song.explicit) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Rounded.Explicit,
                        contentDescription = "Explicit",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(15.dp)
                    )
                }
                if (isDownloaded) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Rounded.DownloadDone,
                        contentDescription = "Downloaded",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = listOfNotNull(
                    song.artists.takeIf { it.isNotBlank() },
                    song.album?.takeIf { it.isNotBlank() }
                ).joinToString(" • ").ifBlank { "Track" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (!song.duration.isNullOrBlank()) {
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = song.duration,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (onLikeClick != null) {
            IconButton(
                onClick = onLikeClick,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("track_like_${song.videoId}")
            ) {
                Icon(
                    imageVector = if (song.rating == Rating.LIKE) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    contentDescription = if (song.rating == Rating.LIKE) "Unlike" else "Like",
                    tint = if (song.rating == Rating.LIKE) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        IconButton(
            onClick = onMoreClick,
            modifier = Modifier
                .size(48.dp)
                .testTag("track_more_${song.videoId}")
        ) {
            Icon(
                imageVector = Icons.Rounded.MoreVert,
                contentDescription = "Track options",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
