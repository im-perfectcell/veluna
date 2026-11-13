package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

data class PlaylistTrackCountRow(
    val playlistId: Long,
    val count: Int,
    val firstSongJson: String?
)

@Dao
interface VelunaDao {
    // --- Listens & Stats ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertListen(listen: ListenEntity): Long

    @Query("UPDATE listens SET secs = secs + :deltaSecs WHERE id = :listenId")
    suspend fun addListenSeconds(listenId: Long, deltaSecs: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertListenSong(song: ListenSongEntity)

    @Query("SELECT * FROM listens WHERE playedAt >= :sinceMs ORDER BY playedAt DESC")
    fun observeListensSince(sinceMs: Long): Flow<List<ListenEntity>>

    @Query("SELECT * FROM listens ORDER BY playedAt DESC LIMIT 500")
    fun observeAllListens(): Flow<List<ListenEntity>>

    @Query("SELECT * FROM listens WHERE playedAt >= :sinceMs ORDER BY playedAt DESC")
    suspend fun getListensSince(sinceMs: Long): List<ListenEntity>

    @Query("SELECT * FROM listens ORDER BY playedAt ASC")
    suspend fun getAllListensAsc(): List<ListenEntity>

    @Query("SELECT * FROM listen_songs")
    fun observeListenSongs(): Flow<List<ListenSongEntity>>

    @Query("SELECT * FROM listen_songs")
    suspend fun getAllListenSongs(): List<ListenSongEntity>

    // --- Local Playlists ---
    @Query("SELECT * FROM local_playlists ORDER BY updatedAt DESC")
    fun observePlaylists(): Flow<List<LocalPlaylistEntity>>

    @Query("SELECT * FROM local_playlists ORDER BY updatedAt DESC")
    suspend fun getPlaylistsOnce(): List<LocalPlaylistEntity>

    @Query("SELECT * FROM local_playlists WHERE id = :id")
    suspend fun getPlaylistById(id: Long): LocalPlaylistEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: LocalPlaylistEntity): Long

    @Query("UPDATE local_playlists SET title = :title, description = :description, coverUri = :coverUri, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updatePlaylist(id: Long, title: String, description: String, coverUri: String?, updatedAt: Long)

    @Query("DELETE FROM local_playlists WHERE id = :id")
    suspend fun deletePlaylist(id: Long)

    @Query("DELETE FROM local_playlist_tracks WHERE playlistId = :playlistId")
    suspend fun clearPlaylistTracks(playlistId: Long)

    @Query("SELECT * FROM local_playlist_tracks WHERE playlistId = :playlistId ORDER BY position ASC, addedAt ASC")
    fun observePlaylistTracks(playlistId: Long): Flow<List<LocalPlaylistTrackEntity>>

    @Query("SELECT * FROM local_playlist_tracks WHERE playlistId = :playlistId ORDER BY position ASC, addedAt ASC")
    suspend fun getPlaylistTracks(playlistId: Long): List<LocalPlaylistTrackEntity>

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM local_playlist_tracks WHERE playlistId = :playlistId")
    suspend fun nextPlaylistPosition(playlistId: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylistTrack(track: LocalPlaylistTrackEntity)

    @Query("DELETE FROM local_playlist_tracks WHERE id = :trackRowId")
    suspend fun deletePlaylistTrackByRowId(trackRowId: Long)

    @Query("DELETE FROM local_playlist_tracks WHERE playlistId = :playlistId AND videoId = :videoId")
    suspend fun deletePlaylistTrack(playlistId: Long, videoId: String)

    @Query("UPDATE local_playlist_tracks SET position = :position WHERE id = :rowId")
    suspend fun updatePlaylistTrackPosition(rowId: Long, position: Int)

    @Query(
        """
        SELECT playlistId, COUNT(*) AS count,
        (SELECT songJson FROM local_playlist_tracks t2 WHERE t2.playlistId = t1.playlistId ORDER BY position ASC, addedAt ASC LIMIT 1) AS firstSongJson
        FROM local_playlist_tracks t1
        GROUP BY playlistId
        """
    )
    fun observePlaylistTrackCounts(): Flow<List<PlaylistTrackCountRow>>

    // --- Liked Songs ---
    @Query("SELECT * FROM liked_songs ORDER BY likedAt DESC")
    fun observeLikedSongs(): Flow<List<LikedSongEntity>>

    @Query("SELECT * FROM liked_songs ORDER BY likedAt DESC")
    suspend fun getLikedSongsOnce(): List<LikedSongEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM liked_songs WHERE videoId = :videoId)")
    suspend fun isSongLiked(videoId: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLikedSong(entity: LikedSongEntity)

    @Query("DELETE FROM liked_songs WHERE videoId = :videoId")
    suspend fun deleteLikedSong(videoId: String)

    // --- Saved Albums & Followed Artists ---
    @Query("SELECT * FROM saved_albums ORDER BY savedAt DESC")
    fun observeSavedAlbums(): Flow<List<SavedAlbumEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM saved_albums WHERE albumId = :albumId)")
    fun observeIsAlbumSaved(albumId: String): Flow<Boolean>

    @Query("SELECT EXISTS(SELECT 1 FROM saved_albums WHERE albumId = :albumId)")
    suspend fun isAlbumSaved(albumId: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSavedAlbum(album: SavedAlbumEntity)

    @Query("DELETE FROM saved_albums WHERE albumId = :albumId")
    suspend fun deleteSavedAlbum(albumId: String)

    @Query("SELECT * FROM followed_artists ORDER BY followedAt DESC")
    fun observeFollowedArtists(): Flow<List<FollowedArtistEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM followed_artists WHERE channelId = :channelId)")
    fun observeIsArtistFollowed(channelId: String): Flow<Boolean>

    @Query("SELECT EXISTS(SELECT 1 FROM followed_artists WHERE channelId = :channelId)")
    suspend fun isArtistFollowed(channelId: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFollowedArtist(artist: FollowedArtistEntity)

    @Query("DELETE FROM followed_artists WHERE channelId = :channelId")
    suspend fun deleteFollowedArtist(channelId: String)

    // --- Shortcuts ---
    @Query("SELECT * FROM shortcuts ORDER BY position ASC")
    fun observeShortcuts(): Flow<List<ShortcutEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertShortcut(shortcut: ShortcutEntity)

    @Query("DELETE FROM shortcuts WHERE id = :id")
    suspend fun deleteShortcut(id: String)

    // --- Local Audio Files ---
    @Query("SELECT * FROM local_tracks ORDER BY title ASC")
    fun observeLocalTracks(): Flow<List<LocalTrackEntity>>

    @Query("SELECT * FROM local_tracks WHERE albumKey = :albumKey ORDER BY trackNo ASC, title ASC")
    suspend fun getLocalTracksByAlbum(albumKey: String): List<LocalTrackEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLocalTracks(tracks: List<LocalTrackEntity>)

    @Query("DELETE FROM local_tracks WHERE uri = :uri")
    suspend fun deleteLocalTrack(uri: String)

    // --- Downloads ---
    @Query("SELECT * FROM downloaded_tracks ORDER BY downloadedAt DESC")
    fun observeDownloads(): Flow<List<DownloadedTrackEntity>>

    @Query("SELECT * FROM downloaded_tracks WHERE videoId = :videoId")
    suspend fun getDownload(videoId: String): DownloadedTrackEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDownload(download: DownloadedTrackEntity)

    @Query("DELETE FROM downloaded_tracks WHERE videoId = :videoId")
    suspend fun deleteDownload(videoId: String)

    // --- Search History ---
    @Query("SELECT * FROM search_history ORDER BY searchedAt DESC LIMIT 20")
    fun observeSearchHistory(): Flow<List<SearchHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSearchHistory(entry: SearchHistoryEntity)

    @Query("DELETE FROM search_history WHERE query = :query")
    suspend fun deleteSearchHistory(query: String)

    @Query("DELETE FROM search_history")
    suspend fun clearSearchHistory()

    // --- Blocked Artists ---
    @Query("SELECT * FROM blocked_artists ORDER BY blockedAt DESC")
    fun observeBlockedArtists(): Flow<List<BlockedArtistEntity>>

    @Query("SELECT * FROM blocked_artists ORDER BY blockedAt DESC")
    suspend fun getBlockedArtistsOnce(): List<BlockedArtistEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBlockedArtist(artist: BlockedArtistEntity)

    @Query("DELETE FROM blocked_artists WHERE name = :name")
    suspend fun deleteBlockedArtist(name: String)

    // --- Lyrics Cache ---
    @Query("SELECT * FROM lyrics_cache WHERE videoId = :videoId")
    suspend fun getLyricsCache(videoId: String): LyricsCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLyricsCache(entity: LyricsCacheEntity)

    @Query("DELETE FROM lyrics_cache WHERE pinned = 0")
    suspend fun clearUnpinnedLyricsCache()
}
