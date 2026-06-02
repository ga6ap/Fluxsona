package com.example.fluxsona.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [SongEntity::class, TagEntity::class], version = 5)
@TypeConverters(Converters::class)
abstract class MusicDatabase : RoomDatabase() {
    abstract fun songDao(): SongDao
    abstract fun tagDao(): TagDao

    companion object {
        const val DATABASE_NAME = "fluxsona_db"

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE songs ADD COLUMN dateAdded INTEGER NOT NULL DEFAULT 0")
                // Initialize existing songs with a sequential timestamp to preserve existing order
                db.execSQL("UPDATE songs SET dateAdded = (SELECT rowid * 1000 FROM songs AS s2 WHERE s2.id = songs.id)")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tags ADD COLUMN category TEXT NOT NULL DEFAULT 'Default'")
            }
        }
    }
}
