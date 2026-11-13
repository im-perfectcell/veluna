package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        ListenEntity::class,
        ListenSongEntity::class,
        LocalPlaylistEntity::class,
        LocalPlaylistTrackEntity::class,
        LikedSongEntity::class,
        SavedAlbumEntity::class,
        FollowedArtistEntity::class,
        ShortcutEntity::class,
        LocalTrackEntity::class,
        DownloadedTrackEntity::class,
        SearchHistoryEntity::class,
        BlockedArtistEntity::class,
        LyricsCacheEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class VelunaDatabase : RoomDatabase() {
    abstract fun velunaDao(): VelunaDao

    companion object {
        @Volatile
        private var INSTANCE: VelunaDatabase? = null

        fun getInstance(context: Context): VelunaDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    VelunaDatabase::class.java,
                    "veluna.db"
                ).build().also { INSTANCE = it }
            }
    }
}
