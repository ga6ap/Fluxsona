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
        entities.map { Tag(it.name, category = it.category) }
    }

    val darkMode: Flow<String> = userPreferences.darkMode
    val sortMode: Flow<String> = userPreferences.sortMode
    val cookiesFilePath: Flow<String?> = userPreferences.cookiesFilePath
    val skipForwardSeconds: Flow<Int> = userPreferences.skipForwardSeconds
    val skipBackwardSeconds: Flow<Int> = userPreferences.skipBackwardSeconds
    val shuffleEnabled: Flow<Boolean> = userPreferences.shuffleEnabled
    val volume: Flow<Float> = userPreferences.volume
    val soundOption: Flow<String> = userPreferences.soundOption
    val lyricsTextSize: Flow<Float> = userPreferences.lyricsTextSize

    suspend fun saveLyricsTextSize(size: Float) {
        userPreferences.saveLyricsTextSize(size)
    }

    suspend fun saveVolume(value: Float) {
        userPreferences.saveVolume(value)
    }

    suspend fun saveSoundOption(option: String) {
        userPreferences.saveSoundOption(option)
    }

    suspend fun saveShuffleEnabled(enabled: Boolean) {
        userPreferences.saveShuffleEnabled(enabled)
    }

    suspend fun saveSkipForwardSeconds(seconds: Int) {
        userPreferences.saveSkipForwardSeconds(seconds)
    }

    suspend fun saveSkipBackwardSeconds(seconds: Int) {
        userPreferences.saveSkipBackwardSeconds(seconds)
    }

    suspend fun saveSortMode(mode: String) {
        userPreferences.saveSortMode(mode)
    }

    suspend fun saveCookiesFilePath(path: String?) {
        userPreferences.saveCookiesFilePath(path)
    }

    suspend fun insertSongs(songs: List<Song>) {
        songDao.insertSongs(songs.map { it.toEntity() })
    }

    suspend fun updateSong(song: Song) {
        songDao.updateSong(song.toEntity())
    }

    suspend fun updateSongs(songs: List<Song>) {
        songDao.updateSongs(songs.map { it.toEntity() })
    }

    suspend fun updateFavorite(songId: String, isFavorite: Boolean) {
        songDao.updateFavorite(songId, isFavorite)
    }

    suspend fun insertTag(name: String, category: String = "Default") {
        tagDao.insertTag(TagEntity(name, category))
    }

    suspend fun updateTagCategory(name: String, newCategory: String) {
        tagDao.updateTag(TagEntity(name, newCategory))
    }

    suspend fun deleteTag(name: String) {
        tagDao.deleteTag(TagEntity(name))
    }

    suspend fun deleteSong(song: Song) {
        songDao.deleteSong(song.toEntity())
    }

    suspend fun saveDarkMode(mode: String) {
        userPreferences.saveDarkMode(mode)
    }
}

fun SongEntity.toSong() = Song(
    id = id,
    title = title,
    artist = artist,
    duration = duration,
    thumbnailUri = thumbnailUri,
    tags = tags,
    cacheStatus = CacheStatus(hasAudio, hasVideo, hasThumbnail, hasLyrics, failedAudio, failedThumbnail, failedLyrics),
    isFavorite = isFavorite,
    localAudioPath = localAudioPath,
    localThumbnailPath = localThumbnailPath,
    localLyricsPath = localLyricsPath,
    originalUrl = originalUrl,
    dateAdded = dateAdded
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
    failedAudio = cacheStatus.failedAudio,
    failedThumbnail = cacheStatus.failedThumbnail,
    failedLyrics = cacheStatus.failedLyrics,
    isFavorite = isFavorite,
    localAudioPath = localAudioPath,
    localThumbnailPath = localThumbnailPath,
    localLyricsPath = localLyricsPath,
    originalUrl = originalUrl,
    dateAdded = dateAdded
)
