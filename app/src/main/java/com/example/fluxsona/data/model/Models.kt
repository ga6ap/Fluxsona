package com.example.fluxsona.data.model

data class Song(
    val id: String,
    val title: String,
    val artist: String,
    val duration: String,
    val thumbnailUri: String?,
    val tags: List<String> = emptyList(),
    val cacheStatus: CacheStatus = CacheStatus(),
    val isFavorite: Boolean = false,
    val localAudioPath: String? = null,
    val localThumbnailPath: String? = null,
    val localLyricsPath: String? = null,
    val originalUrl: String? = null,
    val dateAdded: Long = System.currentTimeMillis()
)

data class CacheStatus(
    val hasAudio: Boolean = false,
    val hasVideo: Boolean = false,
    val hasThumbnail: Boolean = false,
    val hasLyrics: Boolean = false,
    val failedAudio: Boolean = false,
    val failedThumbnail: Boolean = false,
    val failedLyrics: Boolean = false
)

enum class TagState {
    NONE,
    INCLUDED,
    EXCLUDED,
    DISJUNCTION
}

data class Tag(
    val name: String,
    val state: TagState = TagState.NONE,
    val category: String = "Default"
)

data class DownloadTask(
    val url: String,
    val title: String = "Queued...",
    val progress: Float = 0f,
    val isDownloading: Boolean = false,
    val error: String? = null,
    val id: String = java.util.UUID.randomUUID().toString(),
    val downloadAudio: Boolean = true,
    val downloadThumbnail: Boolean = true,
    val downloadLyrics: Boolean = true,
    val currentOperation: String = "Queued",
    val existingSongId: String? = null
)
