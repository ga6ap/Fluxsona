package com.example.fluxsona.data.repository

import com.example.fluxsona.data.local.*
import com.example.fluxsona.data.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class MusicRepository(
    private val db: MusicDatabase,
    private val userPreferences: UserPreferences
) {
    private val songDao = db.songDao()
    private val tagDao = db.tagDao()

    val songs: Flow<List<Song>> = songDao.getAllSongs().map { entities ->
        entities.map { it.toSong() }
    }

    val tags: Flow<List<Tag>> = tagDao.getAllTags().map { entities ->
        entities.map { Tag(it.name) }
    }

    suspend fun insertSongs(songs: List<Song>) {
        songDao.insertSongs(songs.map { it.toEntity() })
    }

    suspend fun updateSong(song: Song) {
        songDao.updateSong(song.toEntity())
    }

    suspend fun updateFavorite(songId: String, isFavorite: Boolean) {
        songDao.updateFavorite(songId, isFavorite)
    }

    suspend fun insertTag(name: String) {
        tagDao.insertTag(TagEntity(name))
    }

    suspend fun deleteTag(name: String) {
        tagDao.deleteTag(TagEntity(name))
    }
}

fun SongEntity.toSong() = Song(
    id = id,
    title = title,
    artist = artist,
    duration = duration,
    thumbnailUri = thumbnailUri,
    tags = tags,
    cacheStatus = CacheStatus(hasAudio, hasVideo, hasThumbnail, hasLyrics),
    isFavorite = isFavorite
)

fun Song.toEntity() = SongEntity(
    id = id,
    title = title,
    artist = artist,
    duration = duration,
    thumbnailUri = thumbnailUri,
    tags = tags,
    hasAudio = cacheStatus.hasAudio,
    hasVideo = cacheStatus.hasVideo,
    hasThumbnail = cacheStatus.hasThumbnail,
    hasLyrics = cacheStatus.hasLyrics,
    isFavorite = isFavorite
)
