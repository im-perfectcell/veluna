package com.example.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "listens",
    indices = [Index(value = ["playedAt"])]
)
data class ListenEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val videoId: String,
    val playedAt: Long,
    val secs: Long
)

@Entity(tableName = "listen_songs")
data class ListenSongEntity(
    @PrimaryKey val videoId: String,
    val songJson: String
)

@Entity(tableName = "local_playlists")
data class LocalPlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val description: String = "",
    val coverUri: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "local_playlist_tracks",
    indices = [
        Index(value = ["playlistId"]),
        Index(value = ["videoId"])
    ]
)
data class LocalPlaylistTrackEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val playlistId: Long,
    val videoId: String,
    val songJson: String,
    val position: Int,
    val addedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "liked_songs")
data class LikedSongEntity(
    @PrimaryKey val videoId: String,
    val songJson: String,
    val likedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "saved_albums")
data class SavedAlbumEntity(
    @PrimaryKey val albumId: String,
    val title: String,
    val subtitle: String?,
    val thumbnail: String?,
    val savedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "followed_artists")
data class FollowedArtistEntity(
    @PrimaryKey val channelId: String,
    val name: String,
    val thumbnail: String?,
    val subscribers: String?,
    val followedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "shortcuts")
data class ShortcutEntity(
    @PrimaryKey val id: String,
    val kind: String,
    val title: String,
    val subtitle: String?,
    val thumbnail: String?,
    val position: Int
)

@Entity(
    tableName = "local_tracks",
    indices = [Index(value = ["albumKey"])]
)
data class LocalTrackEntity(
    @PrimaryKey val uri: String,
    val title: String,
    val artist: String,
    val album: String,
    val albumKey: String,
    val trackNo: Int = 0,
    val durationSecs: Long = 0L,
    val coverUri: String? = null,
    val addedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "downloaded_tracks")
data class DownloadedTrackEntity(
    @PrimaryKey val videoId: String,
    val filePath: String,
    val format: String,
    val bitrateKbps: Int,
    val sizeBytes: Long,
    val songJson: String,
    val downloadedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "search_history")
data class SearchHistoryEntity(
    @PrimaryKey val query: String,
    val searchedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "blocked_artists")
data class BlockedArtistEntity(
    @PrimaryKey val name: String,
    val channelId: String? = null,
    val blockedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "lyrics_cache")
data class LyricsCacheEntity(
    @PrimaryKey val videoId: String,
    val lyricsJson: String,
    val pinned: Boolean = false,
    val offsetMs: Long = 0L,
    val fetchedAt: Long = System.currentTimeMillis()
)
