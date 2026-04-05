package com.example.fluxsona.ui

import android.content.ComponentName
import android.content.Context
import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.fluxsona.PlaybackService
import com.example.fluxsona.data.model.CacheStatus
import com.example.fluxsona.data.model.Song
import com.example.fluxsona.data.model.Tag
import com.example.fluxsona.data.model.TagState
import com.example.fluxsona.data.repository.MusicRepository
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class MusicViewModel(private val repository: MusicRepository) : ViewModel() {
    val songs: StateFlow<List<Song>> = repository.songs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _tagFilterStates = mutableStateListOf<Tag>()
    val tagFilterStates: List<Tag> get() = _tagFilterStates

    val tags: StateFlow<List<Tag>> = repository.tags
        .combine(snapshotFlow { _tagFilterStates.toList() }) { dbTags, filterStates ->
            dbTags.map { dbTag ->
                val state = filterStates.find { it.name == dbTag.name }?.state ?: TagState.NONE
                dbTag.copy(state = state)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private var _currentSongId by mutableStateOf<String?>(null)
    var currentSongId: String?
        get() = _currentSongId
        set(value) { _currentSongId = value }
    
    val currentSong: StateFlow<Song?> = combine(songs, snapshotFlow { currentSongId }) { songList, id ->
        songList.find { it.id == id }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    var isPlaying by mutableStateOf(false)

    var currentPosition by mutableStateOf(0L)
    var duration by mutableStateOf(0L)
    var repeatMode by mutableStateOf(Player.REPEAT_MODE_OFF)
    var isShuffleEnabled by mutableStateOf(false)
    var playbackSpeed by mutableStateOf(1.0f)

    var isPlayerExpanded by mutableStateOf(false)

    var currentQueue = mutableStateListOf<Song>()
        private set

    var errorMessage by mutableStateOf<String?>(null)

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private val controller: MediaController?
        get() = try {
            if (controllerFuture?.isDone == true) controllerFuture?.get() else null
        } catch (e: Exception) {
            errorMessage = "Controller error: ${e.message}"
            null
        }

    init {
        viewModelScope.launch {
            repository.tags.collect { dbTags ->
                if (dbTags.isEmpty()) {
                    val initialTags = listOf(
                        "Favorites", "Rock", "Lofi", "Electronic", "Relax"
                    )
                    initialTags.forEach { repository.insertTag(it) }
                }
            }
        }
        viewModelScope.launch {
            repository.songs.collect { dbSongs ->
                if (dbSongs.isEmpty()) {
                    val initialSongs = listOf(
                        Song("1", "Night Owl", "Broke For Free", "111000", "https://picsum.photos/seed/1/200/200", listOf("Lofi", "Relax")),
                        Song("2", "Springish", "Gillicuddy", "154000", "https://picsum.photos/seed/2/200/200", listOf("Rock")),
                        Song("3", "Golden Hour", "Fujii Kaze", "232000", "https://picsum.photos/seed/3/200/200", listOf("Favorites", "Relax")),
                        Song("4", "Cyberpunk", "Synthwave Artist", "210000", "https://picsum.photos/seed/4/200/200", listOf("Electronic")),
                        Song("5", "Midnight City", "M83", "243000", "https://picsum.photos/seed/5/200/200", listOf("Electronic", "Favorites"))
                    )
                    repository.insertSongs(initialSongs)
                }
            }
        }
    }

    fun initController(context: Context) {
        if (controllerFuture != null) return
        
        val sessionToken = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture?.addListener({
            try {
                val player = controller ?: return@addListener
                player.addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        this@MusicViewModel.isPlaying = isPlaying
                        if (isPlaying) {
                            startProgressUpdate()
                        }
                    }

                    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                        val id = mediaItem?.mediaId
                        _currentSongId = id
                    }

                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_READY) {
                            duration = player.duration
                        }
                    }

                    override fun onRepeatModeChanged(newRepeatMode: Int) {
                        repeatMode = newRepeatMode
                    }

                    override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                        isShuffleEnabled = shuffleModeEnabled
                    }

                    override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
                        playbackSpeed = playbackParameters.speed
                    }

                    override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                        errorMessage = "Playback error: ${error.message}"
                    }
                })
                
                // Sync initial state
                isPlaying = player.isPlaying
                repeatMode = player.repeatMode
                isShuffleEnabled = player.shuffleModeEnabled
                playbackSpeed = player.playbackParameters.speed
                duration = player.duration
                currentPosition = player.currentPosition
            } catch (e: Exception) {
                errorMessage = "Failed to init controller: ${e.message}"
            }
        }, MoreExecutors.directExecutor())
    }

    fun cycleTagState(tagName: String) {
        val index = _tagFilterStates.indexOfFirst { it.name == tagName }
        if (index != -1) {
            val tag = _tagFilterStates[index]
            val nextState = when (tag.state) {
                TagState.NONE -> TagState.INCLUDED
                TagState.INCLUDED -> TagState.EXCLUDED
                TagState.EXCLUDED -> TagState.NONE
            }
            if (nextState == TagState.NONE) {
                _tagFilterStates.removeAt(index)
            } else {
                _tagFilterStates[index] = tag.copy(state = nextState)
            }
        } else {
            _tagFilterStates.add(Tag(tagName, TagState.INCLUDED))
        }
    }

    fun addTag(name: String) {
        viewModelScope.launch {
            repository.insertTag(name)
        }
    }

    fun renameTag(oldName: String, newName: String) {
        if (oldName == "Favorites" || newName == "Favorites") return // Cannot rename Favorites
        if (newName.isBlank()) return

        viewModelScope.launch {
            repository.deleteTag(oldName)
            repository.insertTag(newName)
            
            // Update all songs containing this tag
            songs.value.forEach { song ->
                if (song.tags.contains(oldName)) {
                    val updatedSong = song.copy(tags = song.tags.map { if (it == oldName) newName else it })
                    repository.updateSong(updatedSong)
                }
            }
        }
    }

    fun deleteTag(name: String) {
        if (name == "Favorites") return // Cannot delete Favorites
        viewModelScope.launch {
            repository.deleteTag(name)
            songs.value.forEach { song ->
                if (song.tags.contains(name)) {
                    val updatedSong = song.copy(tags = song.tags.filter { it != name })
                    repository.updateSong(updatedSong)
                }
            }
        }
    }

    fun updateSongTags(songId: String, newTags: List<String>) {
        val song = songs.value.find { it.id == songId } ?: return
        viewModelScope.launch {
            repository.updateSong(song.copy(tags = newTags))
        }
    }

    fun importSong(song: Song) {
        viewModelScope.launch {
            repository.insertSongs(listOf(song))
        }
    }

    fun clearCache() {
        viewModelScope.launch {
            songs.value.forEach { song ->
                repository.updateSong(song.copy(cacheStatus = CacheStatus()))
            }
        }
    }

    val filteredSongs: StateFlow<List<Song>> = combine(songs, snapshotFlow { _tagFilterStates.toList() }) { songList, filterStates ->
        val includedTags = filterStates.filter { it.state == TagState.INCLUDED }.map { it.name }.toSet()
        val excludedTags = filterStates.filter { it.state == TagState.EXCLUDED }.map { it.name }.toSet()
        
        songList.filter { song ->
            val songTags = song.tags.toSet()
            // 1. Must not contain any excluded tags
            if (songTags.any { it in excludedTags }) return@filter false
            
            // 2. Must contain ALL included tags
            if (includedTags.isNotEmpty()) {
                if (!songTags.containsAll(includedTags)) return@filter false
            }
            
            true
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun getFilteredSongs(): List<Song> {
        return filteredSongs.value
    }

    fun clearFilters() {
        _tagFilterStates.clear()
    }

    fun playSong(song: Song, queue: List<Song>? = null) {
        val player = controller
        if (player == null) {
            errorMessage = "Player not ready"
            return
        }
        
        errorMessage = null

        // Use provided queue OR the current filtered songs from the Home screen
        val finalQueue = queue ?: filteredSongs.value
        
        // Determine if we should refresh the entire queue.
        // We refresh if:
        // 1. A specific queue was provided.
        // 2. The current queue is empty.
        // 3. The requested song isn't in the current queue.
        // 4. We are playing from the filtered list (queue == null) but the filtered list context has changed.
        val currentQueueIds = currentQueue.map { it.id }.toSet()
        val finalQueueIds = finalQueue.map { it.id }.toSet()
        val shouldRefreshQueue = queue != null || 
                                 currentQueue.isEmpty() || 
                                 currentQueue.none { it.id == song.id } ||
                                 (queue == null && currentQueueIds != finalQueueIds)

        if (shouldRefreshQueue) {
            currentQueue.clear()
            currentQueue.addAll(finalQueue)

            val mediaItems = finalQueue.map { s ->
                MediaItem.Builder()
                    .setMediaId(s.id)
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(s.title)
                            .setArtist(s.artist)
                            .build()
                    )
                    .setUri("https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3")
                    .build()
            }
            player.setMediaItems(mediaItems)
            val startIndex = finalQueue.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
            player.seekTo(startIndex, 0L)
            player.prepare()
        } else {
            // Song is already in the current (possibly shuffled/reordered) queue
            val index = currentQueue.indexOfFirst { it.id == song.id }
            player.seekTo(index, 0L)
        }
        
        _currentSongId = song.id
        player.play()
    }

    val favoriteSyncSet = mutableStateListOf<String>()

    fun toggleFavorite(songId: String) {
        val song = songs.value.find { it.id == songId } ?: return
        val isNowFavorite = !song.isFavorite
        
        // Optimistic update
        if (isNowFavorite) favoriteSyncSet.add(songId) else favoriteSyncSet.remove(songId)
        
        viewModelScope.launch {
            try {
                val newTags = if (isNowFavorite) {
                    if (!song.tags.contains("Favorites")) song.tags + "Favorites" else song.tags
                } else {
                    song.tags.filter { it != "Favorites" }
                }
                repository.updateSong(song.copy(isFavorite = isNowFavorite, tags = newTags))
            } finally {
                favoriteSyncSet.remove(songId)
            }
        }
    }

    fun isSongFavorite(songId: String): Boolean {
        val song = songs.value.find { it.id == songId } ?: return false
        return if (favoriteSyncSet.contains(songId)) !song.isFavorite else song.isFavorite
    }

    fun seekForward() {
        controller?.let {
            it.seekTo(it.currentPosition + 10000)
            updateProgress()
        }
    }

    fun seekBackward() {
        controller?.let {
            it.seekTo((it.currentPosition - 10000).coerceAtLeast(0))
            updateProgress()
        }
    }

    fun togglePlayPause() {
        val player = controller ?: return
        if (player.isPlaying) player.pause() else player.play()
    }

    fun seekTo(position: Long) {
        controller?.seekTo(position)
        currentPosition = position
    }

    fun toggleRepeatMode() {
        val player = controller ?: return
        // Cycle: OFF -> ALL (Loop Queue) -> ONE (Loop Song)
        val nextMode = when (player.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        player.repeatMode = nextMode
        repeatMode = nextMode
    }

    fun shuffleQueue() {
        if (currentQueue.isEmpty()) return
        
        val current = currentSong.value
        val shuffledList = currentQueue.shuffled()
        
        currentQueue.clear()
        currentQueue.addAll(shuffledList)
        
        val mediaItems = shuffledList.map { s ->
            MediaItem.Builder()
                .setMediaId(s.id)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(s.title)
                        .setArtist(s.artist)
                        .build()
                )
                .setUri("https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3")
                .build()
        }
        
        controller?.let { player ->
            player.setMediaItems(mediaItems)
            val startIndex = shuffledList.indexOfFirst { it.id == current?.id }.coerceAtLeast(0)
            player.seekTo(startIndex, player.currentPosition)
            player.prepare()
        }
    }

    fun togglePlaybackSpeed() {
        val speeds = listOf(1.0f, 1.25f, 1.5f, 2.0f, 0.5f, 0.75f)
        val currentIndex = speeds.indexOf(playbackSpeed)
        val nextIndex = (currentIndex + 1) % speeds.size
        val nextSpeed = speeds[nextIndex]
        controller?.setPlaybackParameters(PlaybackParameters(nextSpeed))
        playbackSpeed = nextSpeed
    }

    fun updateProgress() {
        controller?.let {
            currentPosition = it.currentPosition
            duration = it.duration
        }
    }

    private var progressJob: kotlinx.coroutines.Job? = null
    private fun startProgressUpdate() {
        progressJob?.cancel()
        progressJob = viewModelScope.launch {
            while (isPlaying) {
                updateProgress()
                kotlinx.coroutines.delay(1000L)
            }
        }
    }

    fun skipNext() {
        controller?.seekToNext()
    }

    fun skipPrevious() {
        controller?.seekToPrevious()
    }

    fun removeFromQueue(songId: String) {
        val index = currentQueue.indexOfFirst { it.id == songId }
        if (index != -1) {
            currentQueue.removeAt(index)
            controller?.removeMediaItem(index)
        }
    }

    fun moveQueueItem(fromIndex: Int, toIndex: Int) {
        if (fromIndex == toIndex || fromIndex !in currentQueue.indices || toIndex !in currentQueue.indices) return
        
        val song = currentQueue.removeAt(fromIndex)
        currentQueue.add(toIndex, song)
        controller?.moveMediaItem(fromIndex, toIndex)
    }

    override fun onCleared() {
        super.onCleared()
        controllerFuture?.let {
            MediaController.releaseFuture(it)
        }
    }
}
