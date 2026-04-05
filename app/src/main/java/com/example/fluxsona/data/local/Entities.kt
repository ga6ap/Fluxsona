package com.example.fluxsona.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import com.example.fluxsona.data.model.CacheStatus
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

@Entity(tableName = "songs")
data class SongEntity(
    @PrimaryKey val id: String,
    val title: String,
    val artist: String,
    val duration: String,
    val thumbnailUri: String?,
    val tags: List<String>,
    val hasAudio: Boolean,
    val hasVideo: Boolean,
    val hasThumbnail: Boolean,
    val hasLyrics: Boolean,
    val isFavorite: Boolean
)

@Entity(tableName = "tags")
data class TagEntity(
    @PrimaryKey val name: String
)

class Converters {
    @TypeConverter
    fun fromStringList(value: List<String>): String {
        return Gson().toJson(value)
    }

    @TypeConverter
    fun toStringList(value: String): List<String> {
        val listType = object : TypeToken<List<String>>() {}.type
        return Gson().fromJson(value, listType)
    }
}
