package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.runtime.remember
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.R
import com.example.data.network.InnerTubeParsers
import com.example.model.LIKED_MUSIC_ID
import com.example.model.ON_REPEAT_ID

@Composable
fun ArtWashBackdrop(
    artworkUrl: String?,
    accentColor: Color?,
    modifier: Modifier = Modifier,
    useStageFallback: Boolean = true
) {
    val context = LocalContext.current
    val bg = MaterialTheme.colorScheme.background
    val primary = MaterialTheme.colorScheme.primary
    val animatedAccent by animateColorAsState(
        targetValue = accentColor ?: primary,
        label = "art_wash_accent"
    )

    Box(modifier = modifier.background(bg)) {
        if (!artworkUrl.isNullOrBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(artworkUrl)
                    .size(128)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(0.28f)
                    .blur(40.dpCompat())
            )
        } else if (useStageFallback) {
            Image(
                painter = painterResource(id = R.drawable.img_veluna_stage),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(0.32f)
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.0f to animatedAccent.copy(alpha = 0.22f),
                        0.45f to bg.copy(alpha = 0.72f),
                        1.0f to bg
                    )
                )
        )
    }
}

private fun Int.dpCompat() = androidx.compose.ui.unit.Dp(this.toFloat())

@Composable
fun VelunaArtwork(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.medium,
    specialId: String? = null,
    isCircle: Boolean = false
) {
    val context = LocalContext.current
    val effectiveShape = if (isCircle) CircleShape else shape
    val resolvedUrl = remember(url) { InnerTubeParsers.upgradeThumbnailUrl(url, 720) }
    Box(
        modifier = modifier
            .clip(effectiveShape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        when {
            specialId == LIKED_MUSIC_ID -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    Color(0xFFEA580C),
                                    Color(0xFFBE123C)
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Favorite,
                        contentDescription = contentDescription,
                        tint = Color.White,
                        modifier = Modifier.fillMaxSize(0.42f)
                    )
                }
            }
            specialId == ON_REPEAT_ID -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    Color(0xFFD97706),
                                    Color(0xFFEA580C),
                                    Color(0xFF7C2D12)
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Repeat,
                        contentDescription = contentDescription,
                        tint = Color.White,
                        modifier = Modifier.fillMaxSize(0.45f)
                    )
                }
            }
            !resolvedUrl.isNullOrBlank() -> {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(resolvedUrl)
                        .crossfade(true)
                        .allowHardware(true)
                        .build(),
                    contentDescription = contentDescription,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            else -> {
                Icon(
                    imageVector = if (isCircle) Icons.Rounded.MusicNote else Icons.Rounded.LibraryMusic,
                    contentDescription = contentDescription,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.fillMaxSize(0.4f)
                )
            }
        }
    }
}
