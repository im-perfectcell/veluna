package com.example.data.cloud

import com.example.model.SongItem
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class CloudUserProfile(
    @DocumentId val docId: String = "",
    val userId: String = "",
    val displayName: String = "Listener",
    val email: String? = null,
    val photoUrl: String? = null,
    val themePreset: String = "default",
    val themeMode: String = "DARK",
    val customAccentHex: String? = null,
    val adaptToArtwork: Boolean = true,
    val cornerRadiusDp: Long = 12L,
    val audioQuality: String = "HIGH",
    val autoplay: Boolean = true,
    val loudnessNormalization: Boolean = true,
    val hideMusicVideos: Boolean = false,
    val selectedGenres: List<String> = emptyList(),
    val onboardingCompleted: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class CloudTasteSeed(
    @DocumentId val seedId: String = "",
    val kind: String = "ARTIST",
    val name: String = "",
    val subtitle: String? = null,
    val thumbnail: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class CloudLikedSong(
    @DocumentId val videoId: String = "",
    val title: String = "",
    val artists: String = "",
    val album: String? = null,
    val albumId: String? = null,
    val artistId: String? = null,
    val duration: String? = null,
    val thumbnail: String? = null,
    val explicit: Boolean = false,
    val likedAt: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toSongItem(): SongItem = SongItem(
        videoId = videoId,
        title = title,
        artists = artists,
        album = album,
        albumId = albumId,
        artistId = artistId,
        duration = duration,
        thumbnail = thumbnail,
        explicit = explicit,
        rating = com.example.model.Rating.LIKE
    )
}

@IgnoreExtraProperties
data class CloudPlaylist(
    @DocumentId val playlistKey: String = "",
    val title: String = "",
    val description: String = "",
    val coverUri: String? = null,
    val tracks: List<Map<String, Any?>> = emptyList(),
    val clientUpdatedAt: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class CloudHiddenArtist(
    @DocumentId val artistKey: String = "",
    val name: String = "",
    val channelId: String? = null,
    val blockedAt: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

data class CloudAccountSnapshot(
    val profile: CloudUserProfile? = null,
    val tasteSeeds: List<CloudTasteSeed> = emptyList(),
    val likedSongs: List<CloudLikedSong> = emptyList(),
    val playlists: List<CloudPlaylist> = emptyList(),
    val hiddenArtists: List<CloudHiddenArtist> = emptyList()
)
