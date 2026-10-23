package com.lumisound.android.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        CloudTrackEntity::class,
        FavoriteEntity::class,
        PlaylistEntity::class,
        PlaylistTrackEntity::class,
        PlayHistoryEntity::class,
        LocalTrackEntity::class,
        DownloadEntity::class,
    ],
    version = 4,
    exportSchema = true,
)
abstract class LumiDatabase : RoomDatabase() {
    abstract fun cloudTracks(): CloudTrackDao
    abstract fun favorites(): FavoriteDao
    abstract fun playlists(): PlaylistDao
    abstract fun history(): PlayHistoryDao
    abstract fun localTracks(): LocalTrackDao
    abstract fun downloads(): DownloadDao

    companion object {
        fun build(context: Context): LumiDatabase =
            Room.databaseBuilder(context, LumiDatabase::class.java, "lumimusic.db")
                // Every table here is a mirror of server state that a re-import
                // rebuilds, so a destructive migration costs a refresh, not data.
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
    }
}
