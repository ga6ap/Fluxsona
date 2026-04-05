package com.example.fluxsona.data.model

data class Song(
    val id: String,
    val title: String,
    val artist: String,
    val duration: String,
    val thumbnailUri: String?,
    val tags: List<String> = emptyList(),
    val cacheStatus: CacheStatus = CacheStatus(),
    val isFavorite: Boolean = false
)

data class CacheStatus(
    val hasAudio: Boolean = false,
    val hasVideo: Boolean = false,
    val hasThumbnail: Boolean = false,
    val hasLyrics: Boolean = false
)

enum class TagState {
    NONE,
    INCLUDED,
    EXCLUDED
}

data class Tag(
    val name: String,
    val state: TagState = TagState.NONE
)
