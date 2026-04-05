package com.example.fluxsona.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(entities = [SongEntity::class, TagEntity::class], version = 1)
@TypeConverters(Converters::class)
abstract class MusicDatabase : RoomDatabase() {
    abstract fun songDao(): SongDao
    abstract fun tagDao(): TagDao

    companion object {
        const val DATABASE_NAME = "fluxsona_db"
    }
}
