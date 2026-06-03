package com.example.fluxsona.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
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
import com.example.fluxsona.data.model.DownloadTask
import com.example.fluxsona.data.model.Tag
import com.example.fluxsona.data.model.TagState
import com.example.fluxsona.data.repository.MusicRepository
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.example.fluxsona.R
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import android.content.ContentValues
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import java.io.ByteArrayOutputStream
import java.net.URL
import java.util.UUID

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
            }.sortedWith(compareByDescending<Tag> { it.name == "Favourite" }.thenBy { it.name })
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
        private set

    var currentPosition by mutableLongStateOf(0L)
        private set
    var duration by mutableLongStateOf(0L)
        private set
    var repeatMode by mutableIntStateOf(Player.REPEAT_MODE_OFF)
        private set
    var isShuffleEnabled by mutableStateOf(false)
        private set
    var playbackSpeed by mutableFloatStateOf(1.0f)
        private set

    var isPlayerExpanded by mutableStateOf(false)
    var isQueueMaximized by mutableStateOf(false)

    var darkMode by mutableStateOf("system")
        private set

    var sortMode by mutableStateOf("DATE_ADDED_DESC")
        private set

    var shuffleSeed by mutableLongStateOf(System.currentTimeMillis())
        private set

    var searchQuery by mutableStateOf("")

    var cookiesFilePath by mutableStateOf<String?>(null)
        private set

    var skipForwardSeconds by mutableStateOf(10)
        private set
    var skipBackwardSeconds by mutableStateOf(10)
        private set

    val currentQueue = mutableStateListOf<Song>()

    var isAutoTagging by mutableStateOf(false)
        private set

    var isRestoring by mutableStateOf(false)
        private set

    var pendingImportUri by mutableStateOf<Uri?>(null)

    var currentSoundOption by mutableStateOf("Default")
        private set

    val customAuthorFilters = mutableStateListOf<String>()
    val customTitleFilters = mutableStateListOf<String>()

    var cacheFilterAudio by mutableStateOf(TagState.NONE)
    var cacheFilterThumbnail by mutableStateOf(TagState.NONE)
    var cacheFilterLyrics by mutableStateOf(TagState.NONE)

    var volume by mutableFloatStateOf(1.0f)
        private set
    var isMuted by mutableStateOf(false)
        private set
    var lyricsTextSize by mutableFloatStateOf(18f)
        private set

    var updateInfo by mutableStateOf<UpdateInfo?>(null)
        private set
    var isCheckingForUpdates by mutableStateOf(false)
        private set
    var updateProgress by mutableFloatStateOf(0f)
        private set
    var isDownloadingUpdate by mutableStateOf(false)
        private set

    data class UpdateInfo(
        val versionCode: Int,
        val versionName: String,
        val downloadUrl: String,
        val releaseNotes: String? = null
    )

    fun cycleAudioFilter() {
        cacheFilterAudio = when (cacheFilterAudio) {
            TagState.NONE -> TagState.INCLUDED
            TagState.INCLUDED -> TagState.EXCLUDED
            else -> TagState.NONE
        }
    }

    fun cycleThumbnailFilter() {
        cacheFilterThumbnail = when (cacheFilterThumbnail) {
            TagState.NONE -> TagState.INCLUDED
            TagState.INCLUDED -> TagState.EXCLUDED
            else -> TagState.NONE
        }
    }

    fun cycleLyricsFilter() {
        cacheFilterLyrics = when (cacheFilterLyrics) {
            TagState.NONE -> TagState.INCLUDED
            TagState.INCLUDED -> TagState.EXCLUDED
            else -> TagState.NONE
        }
    }

    private var _errorMessage by mutableStateOf<String?>(null)
    var errorTrigger by mutableLongStateOf(0L)
        private set

    var errorMessage: String?
        get() = _errorMessage
        set(value) { 
            _errorMessage = value 
            if (value != null) errorTrigger++
        }

    fun clearErrorMessage() {
        _errorMessage = null
    }

    private var restorationJob: Job? = null

    fun saveLyrics(song: Song, context: Context, lyricsText: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val lyricsDir = File(context.filesDir, "lyrics").apply { if (!exists()) mkdirs() }
                val lyricsFile = File(lyricsDir, "${song.id}.txt")
                lyricsFile.writeText(lyricsText)

                val updatedSong = song.copy(
                    localLyricsPath = lyricsFile.absolutePath,
                    cacheStatus = song.cacheStatus.copy(hasLyrics = true)
                )
                repository.updateSong(updatedSong)

                // Update current queue if needed
                val queueIndex = currentQueue.indexOfFirst { it.id == song.id }
                if (queueIndex != -1) {
                    currentQueue[queueIndex] = updatedSong
                }
            }
        }
    }

    fun getLyrics(song: Song): String? {
        return song.localLyricsPath?.let { path ->
            val file = File(path)
            if (file.exists()) file.readText() else null
        }
    }

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private val controller: MediaController?
        get() = if (controllerFuture?.isDone == true) controllerFuture?.get() else null

    private val _appStats = MutableStateFlow<Map<String, Any>>(emptyMap())
    val appStats: StateFlow<Map<String, Any>> = _appStats.asStateFlow()

    fun updateStats(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val musicDir = File(context.filesDir, "music")
            val thumbDir = File(context.filesDir, "thumbnails")
            val lyricsDir = File(context.filesDir, "lyrics")

            val mp3Files = musicDir.listFiles { _, name -> name.endsWith(".mp3") } ?: emptyArray()
            val thumbFiles = thumbDir.listFiles { _, name -> name.endsWith(".jpg") || name.endsWith(".png") } ?: emptyArray()
            val lyricsFiles = lyricsDir.listFiles { _, name -> name.endsWith(".txt") || name.endsWith(".lrc") || name.endsWith(".srt") || name.endsWith(".vtt") } ?: emptyArray()

            val mp3Size = mp3Files.sumOf { it.length() }
            val thumbSize = thumbFiles.sumOf { it.length() }
            val lyricsSize = lyricsFiles.sumOf { it.length() }

            val currentSongs = songs.value
            val totalSongs = currentSongs.size
            val installedSongs = currentSongs.count { it.cacheStatus.hasAudio }

            _appStats.value = mapOf(
                "mp3Size" to mp3Size,
                "thumbSize" to thumbSize,
                "lyricsSize" to lyricsSize,
                "totalSongs" to totalSongs,
                "installedSongs" to installedSongs
            )
        }
    }

    init {
        viewModelScope.launch {
            repository.insertTag("Favourite")
        }
        viewModelScope.launch {
            songs.collect { list ->
                // Basic counts update
                val current = _appStats.value.toMutableMap()
                current["totalSongs"] = list.size
                current["installedSongs"] = list.count { it.cacheStatus.hasAudio }
                _appStats.value = current
            }
        }
    }

    fun initController(context: Context) {
        val sessionToken = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture?.addListener({
            val player = controller ?: return@addListener
            
            player.addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlayingNow: Boolean) {
                    isPlaying = isPlayingNow
                    if (isPlayingNow) {
                        startProgressUpdate()
                    }
                }

                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    currentSongId = mediaItem?.mediaId
                    updateProgress()
                }

                override fun onPlaybackStateChanged(state: Int) {
                    if (state == Player.STATE_READY || state == Player.STATE_BUFFERING) {
                        duration = player.duration.coerceAtLeast(0L)
                        updateProgress()
                    }
                }

                override fun onRepeatModeChanged(mode: Int) {
                    repeatMode = mode
                }

                override fun onShuffleModeEnabledChanged(enabled: Boolean) {
                    isShuffleEnabled = enabled
                    viewModelScope.launch {
                        repository.saveShuffleEnabled(enabled)
                    }
                }

                override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
                    playbackSpeed = playbackParameters.speed
                }

                override fun onVolumeChanged(playerVolume: Float) {
                    // Only update from player if we are NOT in boost mode
                    if (!isMuted && volume <= 1.0f && volume != playerVolume) {
                        volume = playerVolume
                        viewModelScope.launch { repository.saveVolume(playerVolume) }
                    }
                }

                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    errorMessage = context.getString(R.string.msg_playback_error, error.message)
                }
            })

            // Sync initial state
            isPlaying = player.isPlaying
            currentSongId = player.currentMediaItem?.mediaId
            duration = player.duration.coerceAtLeast(0L)
            repeatMode = player.repeatMode
            isShuffleEnabled = player.shuffleModeEnabled
            playbackSpeed = player.playbackParameters.speed
            volume = player.volume

            // Sync currentQueue if app was closed but service kept playing
            if (currentQueue.isEmpty() && player.mediaItemCount > 0) {
                viewModelScope.launch {
                    // Wait for songs to be loaded from repository
                    songs.filter { it.isNotEmpty() }.first()
                    
                    val syncedQueue = mutableListOf<Song>()
                    for (i in 0 until player.mediaItemCount) {
                        val mediaItem = player.getMediaItemAt(i)
                        songs.value.find { it.id == mediaIdToId(mediaItem.mediaId) }?.let { 
                            syncedQueue.add(it)
                        }
                    }
                    if (syncedQueue.isNotEmpty()) {
                        currentQueue.clear()
                        currentQueue.addAll(syncedQueue)
                        // Use a temporary sort mode to show the synced queue on Home screen
                        sortMode = "SYNCED_QUEUE"
                    }
                }
            }

            viewModelScope.launch {
                repository.darkMode.collect {
                    darkMode = it
                }
            }
            viewModelScope.launch {
                repository.sortMode.collect {
                    if (sortMode != "SYNCED_QUEUE") {
                        sortMode = it
                    }
                }
            }
            viewModelScope.launch {
                repository.cookiesFilePath.collect {
                    cookiesFilePath = it
                }
            }
            viewModelScope.launch {
                currentSong.collect { song ->
                    if (song != null) {
                        val songDuration = song.duration.toLongOrNull() ?: 0L
                        // If player duration isn't available yet, use metadata
                        if (duration <= 0) {
                            duration = songDuration
                        }
                        updateProgress()
                    }
                }
            }
            viewModelScope.launch {
                repository.skipForwardSeconds.collect {
                    skipForwardSeconds = it
                }
            }
            viewModelScope.launch {
                repository.skipBackwardSeconds.collect {
                    skipBackwardSeconds = it
                }
            }
            viewModelScope.launch {
                repository.shuffleEnabled.collect {
                    if (player.shuffleModeEnabled != it) {
                        player.shuffleModeEnabled = it
                    }
                }
            }
            
            viewModelScope.launch {
                repository.volume.collect {
                    if (!isMuted) {
                        volume = it
                        val intent = Intent(context, PlaybackService::class.java).apply {
                            action = "SET_VOLUME_BOOST"
                            putExtra("VOLUME", it)
                        }
                        context.startService(intent)
                    }
                }
            }
            viewModelScope.launch {
                repository.soundOption.collect {
                    currentSoundOption = it
                    // Apply it without saving again to avoid loops
                    val intent = Intent(context, PlaybackService::class.java).apply {
                        action = when (it) {
                            "Bass Boost" -> "SET_SOUND_BASS_BOOST"
                            "Voice Boost" -> "SET_SOUND_VOICE_BOOST"
                            else -> "SET_SOUND_DEFAULT"
                        }
                    }
                    context.startService(intent)
                }
            }
            viewModelScope.launch {
                repository.lyricsTextSize.collect {
                    lyricsTextSize = it
                }
            }
            checkForUpdates(context)
        }, MoreExecutors.directExecutor())
    }

    fun updateLyricsTextSize(size: Float) {
        viewModelScope.launch {
            repository.saveLyricsTextSize(size)
        }
    }

    private fun mediaIdToId(mediaId: String?): String? = mediaId

    fun updateSkipForwardSeconds(seconds: Int) {
        viewModelScope.launch {
            repository.saveSkipForwardSeconds(seconds)
        }
    }

    fun updateSkipBackwardSeconds(seconds: Int) {
        viewModelScope.launch {
            repository.saveSkipBackwardSeconds(seconds)
        }
    }

    fun updateDarkMode(mode: String) {
        viewModelScope.launch {
            repository.saveDarkMode(mode)
        }
    }

    fun updateSortMode(mode: String) {
        sortMode = mode
        viewModelScope.launch {
            repository.saveSortMode(mode)
        }
    }

    fun reshuffle() {
        shuffleSeed = System.currentTimeMillis()
    }

    fun saveCookiesFile(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val cookiesDir = File(context.filesDir, "cookies")
                if (!cookiesDir.exists()) cookiesDir.mkdirs()

                val fileName = "cookies_${System.currentTimeMillis()}.txt"
                val destFile = File(cookiesDir, fileName)

                inputStream?.use { input ->
                    destFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }

                val path = destFile.absolutePath
                repository.saveCookiesFilePath(path)

            } catch (e: Exception) {
                viewModelScope.launch(Dispatchers.Main) {
                    errorMessage = "Failed to save cookies: ${e.message}"
                }
            }
        }
    }

    fun clearCookiesFile() {
        viewModelScope.launch {
            cookiesFilePath?.let { path ->
                File(path).delete()
            }
            repository.saveCookiesFilePath(null)
        }
    }

    fun updateSearchQuery(query: String) {
        if (sortMode == "SYNCED_QUEUE") {
            sortMode = "DATE_ADDED_DESC" // Fallback to default when searching
        }
        searchQuery = query
    }

    fun cycleTagState(tagName: String) {
        if (sortMode == "SYNCED_QUEUE") {
            sortMode = "DATE_ADDED_DESC"
        }
        val currentState = _tagFilterStates.find { it.name == tagName }?.state ?: TagState.NONE
        val nextState = when (currentState) {
            TagState.NONE -> TagState.INCLUDED
            TagState.INCLUDED -> TagState.EXCLUDED
            TagState.EXCLUDED -> TagState.DISJUNCTION
            TagState.DISJUNCTION -> TagState.NONE
        }

        val existing = _tagFilterStates.find { it.name == tagName }
        if (existing != null) {
            val index = _tagFilterStates.indexOf(existing)
            _tagFilterStates[index] = existing.copy(state = nextState)
        } else {
            _tagFilterStates.add(Tag(tagName, nextState))
        }
    }

    fun addTag(name: String, category: String = "Default") {
        viewModelScope.launch {
            repository.insertTag(name, category)
        }
    }

    fun renameTag(oldName: String, newName: String, category: String = "Default") {
        if (oldName == "Favourite") return
        viewModelScope.launch {
            // Update all songs containing this tag
            val updatedSongs = songs.value.filter { it.tags.contains(oldName) }.map { song ->
                song.copy(tags = song.tags.map { if (it == oldName) newName else it })
            }
            updatedSongs.forEach { repository.updateSong(it) }

            // Delete old tag and insert new one
            repository.deleteTag(oldName)
            repository.insertTag(newName, category)
        }
    }

    fun deleteCategory(category: String) {
        if (category == "Default") return
        viewModelScope.launch {
            val dbTags = repository.tags.first()
            dbTags.forEach { tag ->
                if (tag.category == category) {
                    repository.updateTagCategory(tag.name, "Default")
                }
            }
        }
    }

    fun assignTagsToCategory(tagNames: List<String>, category: String) {
        viewModelScope.launch {
            tagNames.forEach { name ->
                repository.updateTagCategory(name, category)
            }
        }
    }

    fun deleteTag(name: String) {
        if (name == "Favourite") return
        viewModelScope.launch {
            // Remove from all songs
            val updatedSongs = songs.value.filter { it.tags.contains(name) }.map { song ->
                song.copy(tags = song.tags.filter { it != name })
            }
            updatedSongs.forEach { repository.updateSong(it) }
            repository.deleteTag(name)
        }
    }

    fun toggleTagSelection(tagName: String) {
        if (tagName == "Favourite") return
        if (selectedTagNames.contains(tagName)) {
            selectedTagNames.remove(tagName)
        } else {
            selectedTagNames.add(tagName)
        }
    }

    fun selectAllTags() {
        val allNames = tags.value.filter { it.name != "Favourite" }.map { it.name }
        selectedTagNames.clear()
        selectedTagNames.addAll(allNames)
    }

    fun clearTagSelection() {
        selectedTagNames.clear()
    }

    fun setBatchTagState(state: TagState) {
        val selected = selectedTagNames.toList()
        selected.forEach { tagName ->
            val existing = _tagFilterStates.find { it.name == tagName }
            if (existing != null) {
                val index = _tagFilterStates.indexOf(existing)
                _tagFilterStates[index] = existing.copy(state = state)
            } else {
                _tagFilterStates.add(Tag(tagName, state))
            }
        }
    }

    fun cycleBatchTagState() {
        val selected = selectedTagNames.toList()
        selected.forEach { tagName ->
            cycleTagState(tagName)
        }
    }

    fun negateBatchTagState() {
        val selected = selectedTagNames.toList()
        selected.forEach { tagName ->
            val existing = _tagFilterStates.find { it.name == tagName }
            if (existing != null) {
                val nextState = when (existing.state) {
                    TagState.INCLUDED -> TagState.EXCLUDED
                    TagState.EXCLUDED -> TagState.INCLUDED
                    TagState.DISJUNCTION -> TagState.EXCLUDED
                    else -> TagState.INCLUDED
                }
                val index = _tagFilterStates.indexOf(existing)
                _tagFilterStates[index] = existing.copy(state = nextState)
            } else {
                _tagFilterStates.add(Tag(tagName, TagState.INCLUDED))
            }
        }
    }

    fun deleteSelectedTags() {
        viewModelScope.launch {
            val tagsToDelete = selectedTagNames.toList()
            clearTagSelection()
            if (tagsToDelete.isEmpty()) return@launch

            val affectedSongs = songs.value.filter { song ->
                song.tags.any { it in tagsToDelete }
            }
            affectedSongs.forEach { song ->
                val updatedSong = song.copy(tags = song.tags.filter { it !in tagsToDelete })
                repository.updateSong(updatedSong)
            }
            tagsToDelete.forEach { repository.deleteTag(it) }
        }
    }

    fun updateSongTags(songId: String, tags: List<String>) {
        val song = songs.value.find { it.id == songId } ?: return
        viewModelScope.launch {
            val updatedSong = song.copy(tags = tags)
            repository.updateSong(updatedSong)

            // Sync with currentQueue if the song is present there
            val queueIndices = currentQueue.withIndex().filter { it.value.id == songId }.map { it.index }
            queueIndices.forEach { index ->
                currentQueue[index] = updatedSong
            }
        }
    }

    fun setSoundOption(context: Context, option: String) {
        viewModelScope.launch { repository.saveSoundOption(option) }
    }

    fun importSong(song: Song) {
        viewModelScope.launch {
            repository.insertSongs(listOf(song))
        }
    }

    var totalDownloadProgress by mutableStateOf(0f)
        private set

    private var finishedDownloadsCount = 0
    private var totalDownloadsInSession = 0

    val downloadQueue = mutableStateListOf<DownloadTask>()

    val selectedSongIds = mutableStateListOf<String>()
    val selectedTagNames = mutableStateListOf<String>()

    private var _lastSelectionActionId by mutableStateOf<String?>(null)
    private var _lastSelectionActionWasAdded by mutableStateOf(false)
    private var _lastSelectionActionTime by mutableStateOf(0L)

    private suspend fun downloadAndImportInternal(task: DownloadTask, context: Context) {
        try {
            // Handle existing song or fetch new metadata
            var currentSong: Song? = if (task.existingSongId != null) {
                songs.value.find { it.id == task.existingSongId }
            } else {
                null
            }

            if (currentSong != null) {
                // If it's a restore or re-add, double check local disk BEFORE triggering the download engine
                val musicDir = File(context.filesDir, "music")
                val thumbDir = File(context.filesDir, "thumbnails")
                val lyricsDir = File(context.filesDir, "lyrics")

                val hasAudioOnDisk = File(musicDir, "${currentSong!!.id}.mp3").exists() || currentSong!!.localAudioPath?.let { File(it).exists() } == true
                val hasThumbOnDisk = File(thumbDir, "${currentSong!!.id}.jpg").exists() || currentSong!!.localThumbnailPath?.let { File(it).exists() } == true
                val hasLyricsOnDisk = lyricsDir.listFiles()?.any { it.name.startsWith(currentSong!!.id) } == true || currentSong!!.localLyricsPath?.let { File(it).exists() } == true

                // Update metadata and disk paths before deciding to download
                val audioPath = if (hasAudioOnDisk && currentSong!!.localAudioPath == null) File(musicDir, "${currentSong!!.id}.mp3").absolutePath else currentSong!!.localAudioPath
                val thumbPath = if (hasThumbOnDisk && currentSong!!.localThumbnailPath == null) File(thumbDir, "${currentSong!!.id}.jpg").absolutePath else currentSong!!.localThumbnailPath
                val lyricsPath = if (hasLyricsOnDisk && currentSong!!.localLyricsPath == null) lyricsDir.listFiles()?.find { it.name.startsWith(currentSong!!.id) }?.absolutePath else currentSong!!.localLyricsPath

                currentSong = currentSong!!.copy(
                    localAudioPath = audioPath,
                    localThumbnailPath = thumbPath,
                    localLyricsPath = lyricsPath,
                    cacheStatus = currentSong!!.cacheStatus.copy(
                        hasAudio = hasAudioOnDisk,
                        hasThumbnail = hasThumbOnDisk,
                        hasLyrics = hasLyricsOnDisk
                    )
                )
                repository.updateSong(currentSong!!)
            }

            // deciding whether to fetch metadata
            val needsLyricsDownload = task.downloadLyrics && (currentSong == null || (!currentSong!!.cacheStatus.hasLyrics && !currentSong!!.cacheStatus.failedLyrics))
            val needsThumbDownload = task.downloadThumbnail && (currentSong == null || (!currentSong!!.cacheStatus.hasThumbnail && !currentSong!!.cacheStatus.failedThumbnail))
            val needsAudioDownload = task.downloadAudio && (currentSong == null || (!currentSong!!.cacheStatus.hasAudio && !currentSong!!.cacheStatus.failedAudio))

            if (currentSong == null || (needsLyricsDownload && (currentSong!!.title.contains("Unknown") || currentSong!!.artist.contains("Unknown"))) || (needsThumbDownload && currentSong!!.thumbnailUri == null)) {
                updateTaskOperation(task.id, "Fetching metadata...")
                withContext(Dispatchers.IO) {
                    val requestInfo = YoutubeDLRequest(task.url).apply {
                        addOption("--dump-single-json")
                        addOption("--flat-playlist")
                        cookiesFilePath?.let { addOption("--cookies", it) }
                    }
                    val response = YoutubeDL.getInstance().execute(requestInfo)
                    val rootNode = JsonParser.parseString(response.out).asJsonObject

                    if (rootNode.has("entries")) {
                        val entries = rootNode.getAsJsonArray("entries")
                        val totalSongs = entries.size()
                        if (totalSongs == 0) {
                            viewModelScope.launch(Dispatchers.Main) { downloadQueue.removeAll { it.id == task.id } }
                            return@withContext
                        }

                        // Speed up lookup: create a Set of existing IDs
                        val existingIds = songs.value.map { it.id }.toSet()

                        withContext(Dispatchers.Main) {
                            val currentIndex = downloadQueue.indexOfFirst { it.id == task.id }
                            if (currentIndex != -1) {
                                val newTasks = mutableListOf<DownloadTask>()
                                // Process entries in default order
                                for (i in 0 until totalSongs) {
                                    val entry = entries.get(i).asJsonObject
                                    val videoId = entry.get("id").asString
                                    
                                    if (!existingIds.contains(videoId)) {
                                        val entryUrl = if (entry.has("url") && entry.get("url").asJsonPrimitive.isString && entry.get("url").asString.startsWith("http")) {
                                            entry.get("url").asString
                                        } else {
                                            "https://www.youtube.com/watch?v=$videoId"
                                        }
                                        val entryTitle = if (entry.has("title")) entry.get("title").asString else "Song ${i+1}"

                                        newTasks.add(DownloadTask(
                                            url = entryUrl,
                                            title = entryTitle,
                                            downloadAudio = task.downloadAudio,
                                            downloadThumbnail = task.downloadThumbnail,
                                            downloadLyrics = task.downloadLyrics
                                        ))
                                    }
                                }
                                totalDownloadsInSession = totalDownloadsInSession - 1 + newTasks.size
                                downloadQueue.addAll(currentIndex + 1, newTasks)
                                downloadQueue.removeAt(currentIndex)
                                calculateTotalProgress()
                            }
                        }
                        return@withContext
                    } else {
                        // Extract metadata
                        val videoId = rootNode.get("id")?.asString ?: UUID.randomUUID().toString()
                        val videoTitle = rootNode.get("title")?.asString ?: "Unknown Title"
                        val uploader = rootNode.get("uploader")?.asString ?: "Unknown Artist"
                        val durationMs = ((rootNode.get("duration")?.asLong ?: 0L) * 1000L).toString()
                        val thumbUrl = extractThumbnailUrl(rootNode)

                        var artist = rootNode.get("artist")?.asString
                        var track = rootNode.get("track")?.asString

                        if (artist == null || track == null) {
                            if (videoTitle.contains(" - ")) {
                                val parts = videoTitle.split(" - ", limit = 2)
                                artist = parts[0].trim()
                                track = parts[1].trim().replace(Regex("(?i)\\s*[\\(\\[].*(official|lyrics|video|audio).*[\\)\\]]"), "").trim()
                            }
                        }

                        val finalArtist = artist ?: uploader
                        val finalTitle = track ?: videoTitle

                        if (currentSong == null) {
                            currentSong = Song(
                                id = videoId,
                                title = finalTitle,
                                artist = finalArtist,
                                duration = durationMs,
                                thumbnailUri = if (task.downloadThumbnail) thumbUrl else null,
                                originalUrl = task.url,
                                cacheStatus = CacheStatus(hasAudio = false, hasThumbnail = false)
                            )
                            repository.insertSongs(listOf(currentSong!!))
                        } else {
                            currentSong = currentSong!!.copy(
                                title = if (currentSong!!.title.contains("Unknown") || currentSong!!.title.isBlank()) finalTitle else currentSong!!.title,
                                artist = if (currentSong!!.artist.contains("Unknown") || currentSong!!.artist.isBlank()) finalArtist else currentSong!!.artist,
                                duration = durationMs,
                                thumbnailUri = currentSong!!.thumbnailUri ?: thumbUrl
                            )
                            repository.updateSong(currentSong!!)
                        }
                    }
                }
            }

            if (currentSong != null) {
                // Final check: if everything requested is already on disk, skip the download step entirely
                val finalNeedsLyrics = task.downloadLyrics && !currentSong!!.cacheStatus.hasLyrics && !currentSong!!.cacheStatus.failedLyrics
                val finalNeedsThumb = task.downloadThumbnail && !currentSong!!.cacheStatus.hasThumbnail && !currentSong!!.cacheStatus.failedThumbnail
                val finalNeedsAudio = task.downloadAudio && !currentSong!!.cacheStatus.hasAudio && !currentSong!!.cacheStatus.failedAudio
                
                if (finalNeedsLyrics || finalNeedsThumb || finalNeedsAudio) {
                    performMediaDownload(currentSong!!, task, context)
                }
            }
                
            withContext(Dispatchers.Main) {
                val currentTask = downloadQueue.find { it.id == task.id }
                if (currentTask?.error == null) {
                    finishedDownloadsCount++
                    downloadQueue.removeAll { it.id == task.id }
                }
                calculateTotalProgress()
            }
        } catch (e: Exception) {
            updateTaskError(task.id, e.message ?: "Unknown error")
            Log.e("Fluxsona", "yt-dlp error", e)
        }
    }

    private suspend fun performMediaDownload(song: Song, task: DownloadTask, context: Context, completedCount: Int = 0, totalSongs: Int = 1, fallbackThumbPath: String? = null) {
        val musicDir = File(context.filesDir, "music").apply { if (!exists()) mkdirs() }
        val thumbDir = File(context.filesDir, "thumbnails").apply { if (!exists()) mkdirs() }
        val lyricsDir = File(context.filesDir, "lyrics").apply { if (!exists()) mkdirs() }
        var currentSong = song

        if (task.downloadThumbnail && !currentSong.cacheStatus.failedThumbnail) {
            val thumbFile = File(thumbDir, "${currentSong.id}.jpg")
            if (thumbFile.exists()) {
                currentSong = currentSong.copy(
                    localThumbnailPath = thumbFile.absolutePath,
                    cacheStatus = currentSong.cacheStatus.copy(hasThumbnail = true, failedThumbnail = false)
                )
                repository.updateSong(currentSong)
            } else {
                updateTaskOperation(task.id, "Downloading thumbnail...")
                val thumbUrl = currentSong.thumbnailUri

                if (thumbUrl != null && thumbUrl.startsWith("http")) {
                    try {
                        withContext(Dispatchers.IO) {
                            URL(thumbUrl).openStream().use { input ->
                                thumbFile.outputStream().use { output ->
                                    input.copyTo(output)
                                }
                            }
                        }
                        currentSong = currentSong.copy(
                            localThumbnailPath = thumbFile.absolutePath,
                            cacheStatus = currentSong.cacheStatus.copy(hasThumbnail = true, failedThumbnail = false)
                        )
                        repository.updateSong(currentSong)
                    } catch (e: Exception) {
                        Log.e("Fluxsona", "Failed to download thumbnail", e)
                        currentSong = currentSong.copy(
                            cacheStatus = currentSong.cacheStatus.copy(failedThumbnail = true)
                        )
                        repository.updateSong(currentSong)
                    }
                } else if (fallbackThumbPath != null) {
                    currentSong = currentSong.copy(
                        localThumbnailPath = fallbackThumbPath,
                        cacheStatus = currentSong.cacheStatus.copy(hasThumbnail = true, failedThumbnail = false)
                    )
                    repository.updateSong(currentSong)
                }
            }
        }

        if (task.downloadLyrics && !currentSong.cacheStatus.failedLyrics) {
            val existingLyricsFile = lyricsDir.listFiles()?.find {
                it.name.startsWith(currentSong.id) &&
                (it.name.endsWith(".srt") || it.name.endsWith(".vtt") || it.name.endsWith(".lrc") || it.name.endsWith(".txt"))
            }
            if (existingLyricsFile != null) {
                currentSong = currentSong.copy(
                    localLyricsPath = existingLyricsFile.absolutePath,
                    cacheStatus = currentSong.cacheStatus.copy(hasLyrics = true, failedLyrics = false)
                )
                repository.updateSong(currentSong)
            } else {
                updateTaskOperation(task.id, "Searching lyrics...")
                try {
                    withContext(Dispatchers.IO) {
                        val lyrics = fetchLyricsFromApis(currentSong.id, currentSong.artist, currentSong.title, currentSong.duration.toLongOrNull() ?: 0L)
                        if (lyrics != null) {
                            val lyricsFile = File(lyricsDir, "${currentSong.id}.lrc")
                            lyricsFile.writeText(cleanLyricsText(lyrics))
                            currentSong = currentSong.copy(
                                localLyricsPath = lyricsFile.absolutePath,
                                cacheStatus = currentSong.cacheStatus.copy(hasLyrics = true, failedLyrics = false)
                            )
                            repository.updateSong(currentSong)
                        } else {
                            // Fallback to captions
                            updateTaskOperation(task.id, "Downloading lyrics (Captions)...")
                            val request = YoutubeDLRequest(currentSong.originalUrl!!).apply {
                                addOption("--write-subs")
                                addOption("--sub-lang", "en.*")
                                addOption("--skip-download")
                                addOption("-o", File(lyricsDir, currentSong.id).absolutePath)
                                cookiesFilePath?.let { addOption("--cookies", it) }
                            }
                            YoutubeDL.getInstance().execute(request)
                            val resultFile = lyricsDir.listFiles()?.find {
                                it.name.startsWith(currentSong.id) &&
                                (it.name.endsWith(".srt") || it.name.endsWith(".vtt") || it.name.endsWith(".lrc"))
                            }
                            if (resultFile != null) {
                                val rawContent = resultFile.readText()
                                val cleanedContent = cleanLyricsText(rawContent)
                                resultFile.writeText(cleanedContent)
                                currentSong = currentSong.copy(
                                    localLyricsPath = resultFile.absolutePath,
                                    cacheStatus = currentSong.cacheStatus.copy(hasLyrics = true, failedLyrics = false)
                                )
                                repository.updateSong(currentSong)
                            } else {
                                currentSong = currentSong.copy(
                                    cacheStatus = currentSong.cacheStatus.copy(failedLyrics = true)
                                )
                                repository.updateSong(currentSong)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("Fluxsona", "Failed to download lyrics", e)
                    currentSong = currentSong.copy(
                        cacheStatus = currentSong.cacheStatus.copy(failedLyrics = true)
                    )
                    viewModelScope.launch { repository.updateSong(currentSong) }
                }
            }
        }

        if (task.downloadAudio && !currentSong.cacheStatus.failedAudio) {
            val audioFile = File(musicDir, "${currentSong.id}.mp3")
            val existingAudioFile = if (audioFile.exists()) audioFile else musicDir.listFiles()?.find { it.name.startsWith(currentSong.id) && it.name.endsWith(".mp3") }

            if (existingAudioFile != null && existingAudioFile.exists()) {
                currentSong = currentSong.copy(
                    localAudioPath = existingAudioFile.absolutePath,
                    cacheStatus = currentSong.cacheStatus.copy(hasAudio = true, failedAudio = false)
                )
                repository.updateSong(currentSong)
                updateStats(context)
                updateTaskProgress(task.id, (completedCount + 1f) / totalSongs)
            } else {
                updateTaskOperation(task.id, "Downloading audio...")
                val downloadRequest = YoutubeDLRequest(currentSong.originalUrl!!).apply {
                    addOption("--extract-audio")
                    addOption("--audio-format", "mp3")
                    addOption("--no-mtime")
                    addOption("-o", audioFile.absolutePath)
                    addOption("--ffmpeg-location", context.applicationInfo.nativeLibraryDir)
                    cookiesFilePath?.let { addOption("--cookies", it) }
                }

                try {
                    yield()
                    withContext(Dispatchers.IO) {
                        YoutubeDL.getInstance().execute(downloadRequest) { progress, _, _ ->
                            val safeProgress = if (progress < 0) 0f else progress / 100f
                            val currentTotalProgress = (completedCount + safeProgress) / totalSongs
                            updateTaskProgress(task.id, currentTotalProgress)
                        }
                    }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    updateTaskError(task.id, "Audio error: ${e.message}")
                    Log.e("Fluxsona", "Failed to download audio for ${currentSong.id}", e)
                }

                val finalFile = if (audioFile.exists()) audioFile else musicDir.listFiles()?.find { it.name.startsWith(currentSong.id) && it.name.endsWith(".mp3") } ?: audioFile
                if (finalFile.exists()) {
                    currentSong = currentSong.copy(
                        localAudioPath = finalFile.absolutePath,
                        cacheStatus = currentSong.cacheStatus.copy(hasAudio = true, failedAudio = false)
                    )
                    repository.updateSong(currentSong)
                    updateStats(context)
                } else {
                    currentSong = currentSong.copy(
                        cacheStatus = currentSong.cacheStatus.copy(failedAudio = true)
                    )
                    repository.updateSong(currentSong)
                }
            }
        }
    }

    private suspend fun fetchLyricsFromApis(videoId: String, artist: String, title: String, durationMs: Long): String? {
        // 1. YT Music (Direct source, often has synced or official lyrics)
        val ytMusic = fetchLyricsFromYtMusic(videoId)
        if (ytMusic != null) return ytMusic

        // 2. LRCLIB (Best quality, includes synced lyrics)
        val lrclib = fetchLyricsFromLrclib(artist, title, durationMs)
        if (lrclib != null) return lrclib

        // 3. NetEase Music (Massive database, often has translated lyrics)
        val netease = fetchLyricsFromNetEase(artist, title)
        if (netease != null) return netease

        // 4. ChartLyrics (Great for Western music)
        val chartLyrics = fetchLyricsFromChartLyrics(artist, title)
        if (chartLyrics != null) return chartLyrics

        // 5. Lyrics.ovh (Simple plain text fallback)
        return fetchLyricsFromLyricsOvh(artist, title)
    }

    private suspend fun fetchLyricsFromYtMusic(videoId: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                // 1. Get lyrics browse ID using next endpoint
                val nextUrl = "https://music.youtube.com/youtubei/v1/next?prettyPrint=false"
                val nextPayload = JsonObject().apply {
                    addProperty("videoId", videoId)
                    add("context", JsonObject().apply {
                        add("client", JsonObject().apply {
                            addProperty("clientName", "WEB_REMIX")
                            addProperty("clientVersion", "1.20230522.01.00")
                        })
                    })
                }

                val nextResponse = postJson(nextUrl, nextPayload) ?: return@withContext null
                val browseId = findLyricsBrowseId(nextResponse) ?: return@withContext null

                // 2. Get lyrics content using browse endpoint
                val browseUrl = "https://music.youtube.com/youtubei/v1/browse?prettyPrint=false"
                val browsePayload = JsonObject().apply {
                    addProperty("browseId", browseId)
                    add("context", JsonObject().apply {
                        add("client", JsonObject().apply {
                            addProperty("clientName", "WEB_REMIX")
                            addProperty("clientVersion", "1.20230522.01.00")
                        })
                    })
                }

                val browseResponse = postJson(browseUrl, browsePayload) ?: return@withContext null
                findLyricsText(browseResponse)
            } catch (e: Exception) {
                Log.e("Fluxsona", "Failed to fetch from YT Music for $videoId", e)
                null
            }
        }
    }

    private fun postJson(urlStr: String, payload: JsonObject): JsonObject? {
        return try {
            val url = URL(urlStr)
            val conn = url.openConnection() as java.net.HttpURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/113.0.0.0 Safari/537.36")

            conn.outputStream.use { os ->
                os.write(payload.toString().toByteArray())
            }

            if (conn.responseCode == 200) {
                conn.inputStream.bufferedReader().use { JsonParser.parseString(it.readText()).asJsonObject }
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun findLyricsBrowseId(obj: JsonObject): String? {
        return try {
            val tabs = obj.getAsJsonObject("contents")
                ?.getAsJsonObject("singleColumnMusicWatchNextResultsRenderer")
                ?.getAsJsonObject("tabbedRenderer")
                ?.getAsJsonObject("watchNextTabbedResultsRenderer")
                ?.getAsJsonArray("tabs") ?: return null

            for (tab in tabs) {
                val renderer = tab.asJsonObject.getAsJsonObject("tabRenderer") ?: continue
                if (renderer.get("title")?.asString == "Lyrics") {
                    return renderer.getAsJsonObject("endpoint")
                        ?.getAsJsonObject("browseEndpoint")
                        ?.get("browseId")?.asString
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    private fun findLyricsText(obj: JsonObject): String? {
        return try {
            val runs = obj.getAsJsonObject("contents")
                ?.getAsJsonObject("sectionListRenderer")
                ?.getAsJsonArray("contents")
                ?.get(0)?.asJsonObject
                ?.getAsJsonObject("musicDescriptionShelfRenderer")
                ?.getAsJsonObject("description")
                ?.getAsJsonArray("runs") ?: return null

            val sb = StringBuilder()
            for (run in runs) {
                sb.append(run.asJsonObject.get("text").asString)
            }
            sb.toString().trim().ifBlank { null }
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun fetchLyricsFromNetEase(artist: String, title: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                // Search for the song
                val searchUrlStr = "http://music.163.com/api/search/get/web?s=${Uri.encode("$artist $title")}&type=1&limit=1"
                val searchConn = URL(searchUrlStr).openConnection() as java.net.HttpURLConnection
                searchConn.connectTimeout = 5000
                if (searchConn.responseCode == 200) {
                    val searchResp = searchConn.inputStream.bufferedReader().use { it.readText() }
                    val searchJson = JsonParser.parseString(searchResp).asJsonObject
                    val result = searchJson.get("result")?.asJsonObject
                    val songs = result?.get("songs")?.asJsonArray
                    if (songs != null && songs.size() > 0) {
                        val songId = songs.get(0).asJsonObject.get("id").asLong

                        // Get lyrics by ID
                        val lyricUrlStr = "http://music.163.com/api/song/lyric?os=pc&id=$songId&lv=-1&kv=-1&tv=-1"
                        val lyricConn = URL(lyricUrlStr).openConnection() as java.net.HttpURLConnection
                        if ( lyricConn.responseCode == 200) {
                            val lyricResp = lyricConn.inputStream.bufferedReader().use { it.readText() }
                            val lyricJson = JsonParser.parseString(lyricResp).asJsonObject
                            return@withContext lyricJson.get("lrc")?.asJsonObject?.get("lyric")?.asString
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("Fluxsona", "Failed to fetch from NetEase", e)
            }
            null
        }
    }

    private suspend fun fetchLyricsFromChartLyrics(artist: String, title: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                val urlStr = "http://api.chartlyrics.com/apiv1.asmx/SearchLyricDirect?artist=${Uri.encode(artist)}&song=${Uri.encode(title)}"
                val conn = URL(urlStr).openConnection() as java.net.HttpURLConnection
                conn.connectTimeout = 5000
                if (conn.responseCode == 200) {
                    val xml = conn.inputStream.bufferedReader().use { it.readText() }
                    // Simple XML parsing for <Lyric> tag
                    val lyricStart = xml.indexOf("<Lyric>") + 7
                    val lyricEnd = xml.indexOf("</Lyric>")
                    if (lyricStart > 6 && lyricEnd > lyricStart) {
                        val lyrics = xml.substring(lyricStart, lyricEnd)
                        if (lyrics.isNotBlank() && lyrics != "null") return@withContext lyrics
                    }
                }
            } catch (e: Exception) {
                Log.e("Fluxsona", "Failed to fetch from ChartLyrics", e)
            }
            null
        }
    }

    private suspend fun fetchLyricsFromLyricsOvh(artist: String, title: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                val urlStr = "https://api.lyrics.ovh/v1/${Uri.encode(artist)}/${Uri.encode(title)}"
                val url = URL(urlStr)
                val connection = url.openConnection() as java.net.HttpURLConnection
                connection.connectTimeout = 5000
                connection.readTimeout = 5000
                if (connection.responseCode == 200) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    val json = JsonParser.parseString(response).asJsonObject
                    return@withContext json.get("lyrics")?.asString
                }
            } catch (e: Exception) {
                Log.e("Fluxsona", "Failed to fetch from Lyrics.ovh", e)
            }
            null
        }
    }

    private suspend fun fetchLyricsFromLrclib(artist: String, title: String, durationMs: Long): String? {
        return withContext(Dispatchers.IO) {
            try {
                val durationSec = durationMs / 1000
                // Try exact match first
                val urlStr = "https://lrclib.net/api/get?artist_name=${Uri.encode(artist)}&track_name=${Uri.encode(title)}&duration=$durationSec"
                val url = URL(urlStr)
                val connection = url.openConnection() as java.net.HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 5000
                connection.readTimeout = 5000

                if (connection.responseCode == 200) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    val json = JsonParser.parseString(response).asJsonObject
                    return@withContext json.get("syncedLyrics")?.asString ?: json.get("plainLyrics")?.asString
                } else {
                    // Try search as fallback
                    val searchUrlStr = "https://lrclib.net/api/search?q=${Uri.encode("$artist $title")}"
                    val searchUrl = URL(searchUrlStr)
                    val searchConn = searchUrl.openConnection() as java.net.HttpURLConnection
                    searchConn.connectTimeout = 5000
                    searchConn.readTimeout = 5000
                    if (searchConn.responseCode == 200) {
                        val searchResponse = searchConn.inputStream.bufferedReader().use { it.readText() }
                        val results = JsonParser.parseString(searchResponse).asJsonArray
                        if (results.size() > 0) {
                            val bestMatch = results.get(0).asJsonObject
                            return@withContext bestMatch.get("syncedLyrics")?.asString ?: bestMatch.get("plainLyrics")?.asString
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("Fluxsona", "Failed to fetch lyrics from LRCLIB", e)
            }
            null
        }
    }

    private fun cleanLyricsText(text: String): String {
        // Regex for [mm:ss.ss] or [mm:ss] or [hh:mm:ss]
        val timestampRegex = Regex("\\[\\d{1,2}:\\d{2}(?:\\.\\d{1,3})?]")
        // Regex for SRT/VTT time ranges: 00:00:00.000 --> 00:00:00.000
        val vttTimeRegex = Regex("\\d{2}:\\d{2}:\\d{2}\\.\\d{3} --> \\d{2}:\\d{2}:\\d{2}\\.\\d{3}")
        
        return text.lines().map { line ->
            var cleaned = line.replace(timestampRegex, "")
            cleaned = cleaned.replace(vttTimeRegex, "")

            // Remove typical VTT/SRT headers and numbering
            if (cleaned.trim().lowercase() == "webvtt" ||
                cleaned.trim().toIntOrNull() != null ||
                cleaned.trim().startsWith("kind:") ||
                cleaned.trim().startsWith("language:")) {
                ""
            } else {
                cleaned.trim()
            }
        }.filter { it.isNotBlank() }.joinToString("\n")
    }

    private fun updateTaskProgress(taskId: String, progress: Float, operation: String? = null) {
        viewModelScope.launch(Dispatchers.Main) {
            val index = downloadQueue.indexOfFirst { it.id == taskId }
            if (index != -1) {
                val lastProgress = downloadQueue[index].progress
                val newProgress = if (progress < 0) lastProgress else progress.coerceIn(0f, 1f)

                if (newProgress >= lastProgress || operation != null) {
                    downloadQueue[index] = downloadQueue[index].copy(
                        progress = newProgress,
                        currentOperation = operation ?: downloadQueue[index].currentOperation
                    )
                    calculateTotalProgress()
                }
            }
        }
    }

    private fun updateTaskOperation(taskId: String, operation: String, title: String? = null) {
        viewModelScope.launch(Dispatchers.Main) {
            val index = downloadQueue.indexOfFirst { it.id == taskId }
            if (index != -1) {
                downloadQueue[index] = downloadQueue[index].copy(
                    currentOperation = operation,
                    title = title ?: downloadQueue[index].title
                )
            }
        }
    }

    private fun updateTaskError(taskId: String, error: String) {
        viewModelScope.launch(Dispatchers.Main) {
            val index = downloadQueue.indexOfFirst { it.id == taskId }
            if (index != -1) {
                downloadQueue[index] = downloadQueue[index].copy(error = error, isDownloading = false, currentOperation = "Error")
            }
        }
    }

    private fun calculateTotalProgress() {
        if (totalDownloadsInSession == 0) {
            totalDownloadProgress = 0f
            return
        }
        val totalProgress = (finishedDownloadsCount + downloadQueue.sumOf { it.progress.toDouble() }).toFloat()
        totalDownloadProgress = (totalProgress / totalDownloadsInSession).coerceIn(0f, 1f)

        if (downloadQueue.isEmpty()) {
            finishedDownloadsCount = 0
            totalDownloadsInSession = 0
        }
    }

    fun addToDownloadQueue(
        url: String,
        context: Context,
        downloadAudio: Boolean = true,
        downloadThumbnail: Boolean = true,
        downloadLyrics: Boolean = true,
        songId: String? = null,
        title: String? = null
    ) {
        if (downloadQueue.any { it.url == url && it.existingSongId == songId }) return

        if (downloadQueue.isEmpty()) {
            finishedDownloadsCount = 0
            totalDownloadsInSession = 0
        }

        totalDownloadsInSession++
        downloadQueue.add(DownloadTask(
            url = url,
            title = title ?: "Queued...",
            downloadAudio = downloadAudio,
            downloadThumbnail = downloadThumbnail,
            downloadLyrics = downloadLyrics,
            existingSongId = songId
        ))
        processDownloadQueue(context)
    }

    fun addBatchToDownloadQueue(tasks: List<DownloadTask>, context: Context) {
        if (tasks.isEmpty()) return

        // Optimize lookup with a Set of URL+ID keys
        val existingKeys = downloadQueue.map { "${it.url}|${it.existingSongId}" }.toSet()
        val newTasks = tasks.filter { task ->
            "${task.url}|${task.existingSongId}" !in existingKeys
        }

        if (newTasks.isEmpty()) return

        if (downloadQueue.isEmpty()) {
            finishedDownloadsCount = 0
            totalDownloadsInSession = 0
        }

        totalDownloadsInSession += newTasks.size
        downloadQueue.addAll(newTasks)
        processDownloadQueue(context)
    }

    private var isProcessingQueue = false
    private var currentDownloadJob: Job? = null
    private fun processDownloadQueue(context: Context) {
        if (isProcessingQueue) return
        viewModelScope.launch {
            isProcessingQueue = true
            while (downloadQueue.any { !it.isDownloading && it.error == null }) {
                val nextTask = downloadQueue.firstOrNull { !it.isDownloading && it.error == null } ?: break
                val index = downloadQueue.indexOf(nextTask)
                if (index == -1) break
                downloadQueue[index] = nextTask.copy(isDownloading = true)

                try {
                    coroutineScope {
                        val downloadJob = launch {
                            downloadAndImportInternal(nextTask, context)
                        }
                        currentDownloadJob = downloadJob
                        downloadJob.join()
                    }
                } catch (e: CancellationException) {
                    Log.d("Fluxsona", "Download task cancelled: ${nextTask.url}")
                } catch (e: Exception) {
                    Log.e("Fluxsona", "Error in download queue loop", e)
                } finally {
                    currentDownloadJob = null
                }
            }
            isProcessingQueue = false
        }
    }

    fun removeFromDownloadQueue(taskId: String) {
        val task = downloadQueue.find { it.id == taskId }
        if (task?.isDownloading == true) {
            currentDownloadJob?.cancel()
        }
        val removed = downloadQueue.removeAll { it.id == taskId }
        if (removed) {
            if (totalDownloadsInSession > 0) totalDownloadsInSession--
            calculateTotalProgress()
        }
    }

    fun removeBatchFromDownloadQueue(predicate: (DownloadTask) -> Boolean) {
        val tasksToRemove = downloadQueue.filter(predicate)
        if (tasksToRemove.isEmpty()) return

        val containsCurrent = tasksToRemove.any { it.isDownloading }
        if (containsCurrent) {
            currentDownloadJob?.cancel()
        }

        val countBefore = downloadQueue.size
        downloadQueue.removeAll(predicate)
        val countAfter = downloadQueue.size
        val removedCount = countBefore - countAfter

        if (removedCount > 0) {
            totalDownloadsInSession = (totalDownloadsInSession - removedCount).coerceAtLeast(0)
            if (downloadQueue.isEmpty()) {
                finishedDownloadsCount = 0
                totalDownloadsInSession = 0
            }
            calculateTotalProgress()
        }
    }

    fun toggleSelection(songId: String) {
        val now = System.currentTimeMillis()
        if (songId == _lastSelectionActionId && now - _lastSelectionActionTime < 500) {
            return
        }

        if (selectedSongIds.contains(songId)) {
            selectedSongIds.remove(songId)
            _lastSelectionActionWasAdded = false
        } else {
            selectedSongIds.add(songId)
            _lastSelectionActionWasAdded = true
        }
        _lastSelectionActionId = songId
        _lastSelectionActionTime = now
    }

    fun applySelectionState(songId: String, shouldSelect: Boolean) {
        if (shouldSelect) {
            if (!selectedSongIds.contains(songId)) {
                selectedSongIds.add(songId)
            }
        } else {
            selectedSongIds.remove(songId)
        }
    }

    fun isLastActionId(songId: String): Boolean = _lastSelectionActionId == songId
    fun wasLastActionAdded(): Boolean = _lastSelectionActionWasAdded
    fun clearLastSelectionAction() { _lastSelectionActionId = null }

    fun toggleSelectionSingle(songId: String) {
        if (selectedSongIds.size == 1 && selectedSongIds.contains(songId)) {
            selectedSongIds.clear()
        } else {
            selectedSongIds.clear()
            selectedSongIds.add(songId)
        }
    }

    fun selectAllFiltered() {
        val ids = filteredSongs.value.map { it.id }
        selectedSongIds.clear()
        selectedSongIds.addAll(ids)
    }

    fun clearSelection() {
        selectedSongIds.clear()
    }

    fun reverseSelectedOrder() {
        if (selectedSongIds.size < 2) return
        viewModelScope.launch(Dispatchers.IO) {
            val selectedSongs = songs.value.filter { it.id in selectedSongIds }
                .sortedBy { it.dateAdded } // Get current order based on dateAdded
            
            if (selectedSongs.isEmpty()) return@launch

            val timestamps = selectedSongs.map { it.dateAdded }
            val reversedTimestamps = timestamps.reversed()

            val updatedSongs = selectedSongs.mapIndexed { index, song ->
                song.copy(dateAdded = reversedTimestamps[index])
            }

            repository.updateSongs(updatedSongs)
        }
    }

    fun refreshSongs(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val musicDir = File(context.filesDir, "music").apply { if (!exists()) mkdirs() }
            val thumbDir = File(context.filesDir, "thumbnails").apply { if (!exists()) mkdirs() }
            val lyricsDir = File(context.filesDir, "lyrics").apply { if (!exists()) mkdirs() }

            val musicFiles = musicDir.listFiles()?.filter { !it.isDirectory } ?: emptyList()
            val thumbFiles = thumbDir.listFiles()?.filter { !it.isDirectory } ?: emptyList()
            val lyricsFiles = lyricsDir.listFiles()?.filter { !it.isDirectory } ?: emptyList()

            var updatedCount = 0
            val currentSongs = songs.value
            val updatedSongs = mutableListOf<Song>()

            val audioExts = listOf("mp3", "m4a", "webm", "aac", "flac", "opus", "ogg")
            val thumbExts = listOf("jpg", "jpeg", "png", "webp")
            val lyricsExts = listOf("lrc", "txt", "srt", "vtt")

            currentSongs.forEach { song ->
                ensureActive()
                // 1. Check Audio
                var audioPath = song.localAudioPath
                if (audioPath == null || !File(audioPath).exists()) {
                    audioPath = musicFiles.find {
                        (it.name.startsWith(song.id) || it.name.contains(song.id)) && it.extension.lowercase() in audioExts
                    }?.absolutePath
                }
                val hasAudio = audioPath != null && File(audioPath).exists()

                // 2. Check Thumbnail
                var thumbPath = song.localThumbnailPath
                if (thumbPath == null || !File(thumbPath).exists()) {
                    thumbPath = thumbFiles.find {
                        (it.name.startsWith(song.id) || it.name.contains(song.id)) && it.extension.lowercase() in thumbExts
                    }?.absolutePath
                }
                val hasThumb = thumbPath != null && File(thumbPath).exists()

                // 3. Check Lyrics
                var lyricsPath = song.localLyricsPath
                if (lyricsPath == null || !File(lyricsPath).exists()) {
                    lyricsPath = lyricsFiles.find {
                        (it.name.startsWith(song.id) || it.name.contains(song.id)) && it.extension.lowercase() in lyricsExts
                    }?.absolutePath
                }
                val hasLyrics = lyricsPath != null && File(lyricsPath).exists()

                if (audioPath != song.localAudioPath ||
                    thumbPath != song.localThumbnailPath ||
                    lyricsPath != song.localLyricsPath ||
                    hasAudio != song.cacheStatus.hasAudio ||
                    hasThumb != song.cacheStatus.hasThumbnail ||
                    hasLyrics != song.cacheStatus.hasLyrics
                ) {
                    updatedSongs.add(song.copy(
                        localAudioPath = audioPath,
                        localThumbnailPath = thumbPath,
                        localLyricsPath = lyricsPath,
                        cacheStatus = song.cacheStatus.copy(
                            hasAudio = hasAudio,
                            hasThumbnail = hasThumb,
                            hasLyrics = hasLyrics,
                            failedAudio = if (hasAudio) false else song.cacheStatus.failedAudio,
                            failedThumbnail = if (hasThumb) false else song.cacheStatus.failedThumbnail,
                            failedLyrics = if (hasLyrics) false else song.cacheStatus.failedLyrics
                        )
                    ))
                }
            }

            if (updatedSongs.isNotEmpty()) {
                repository.updateSongs(updatedSongs)
                updatedCount = updatedSongs.size
                withContext(Dispatchers.Main) {
                    updatedSongs.forEach { updated ->
                        val idx = currentQueue.indexOfFirst { it.id == updated.id }
                        if (idx != -1) currentQueue[idx] = updated
                    }
                }
            }

            updateStats(context)
            withContext(Dispatchers.Main) {
                errorMessage = context.getString(R.string.msg_recount_success, updatedCount)
            }
        }
    }

    fun syncQueueWithFiltered() {
        val song = currentSong.value ?: return
        val player = controller ?: return
        val finalQueue = filteredSongs.value

        if (finalQueue.isEmpty()) return

        currentQueue.clear()
        currentQueue.addAll(finalQueue)

        val mediaItems = finalQueue.map { s ->
            val uriString = s.localAudioPath?.let { Uri.fromFile(File(it)).toString() }
                ?: "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3"
            val uri = Uri.parse(uriString)

            MediaItem.Builder()
                .setMediaId(s.id)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(s.title)
                        .setArtist(s.artist)
                        .setArtworkUri(s.localThumbnailPath?.let { Uri.fromFile(File(it)) } ?: s.thumbnailUri?.let { Uri.parse(it) })
                        .build()
                )
                .setUri(uri)
                .setRequestMetadata(
                    MediaItem.RequestMetadata.Builder()
                        .setMediaUri(uri)
                        .build()
                )
                .build()
        }

        val startIndex = finalQueue.indexOfFirst { it.id == song.id }
        if (startIndex != -1) {
            player.setMediaItems(mediaItems, startIndex, player.currentPosition)
        } else {
            // Current song not in new list, start from the first song in new list at position 0
            player.setMediaItems(mediaItems, 0, 0L)
        }
        player.prepare()
    }

    fun restoreMissingData(context: Context, restoreAudio: Boolean, restoreThumbnails: Boolean, restoreLyrics: Boolean = false, specificSongIds: List<String>? = null) {
        restorationJob?.cancel()
        isRestoring = true
        restorationJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val musicDir = File(context.filesDir, "music").apply { if (!exists()) mkdirs() }
                val thumbDir = File(context.filesDir, "thumbnails").apply { if (!exists()) mkdirs() }
                val lyricsDir = File(context.filesDir, "lyrics").apply { if (!exists()) mkdirs() }

                val musicFiles = musicDir.listFiles()?.filter { !it.isDirectory } ?: emptyList()
                val thumbFiles = thumbDir.listFiles()?.filter { !it.isDirectory } ?: emptyList()
                val lyricsFiles = lyricsDir.listFiles()?.filter { !it.isDirectory } ?: emptyList()

                val allSongs = if (specificSongIds != null) {
                    songs.value.filter { it.id in specificSongIds }
                } else {
                    songs.value
                }.sortedByDescending { it.dateAdded }
                val updatedSongs = mutableListOf<Song>()
                
                val audioExts = listOf("mp3", "m4a", "webm", "aac", "flac", "opus", "ogg")
                val thumbExts = listOf("jpg", "jpeg", "png", "webp")
                val lyricsExts = listOf("lrc", "txt", "srt", "vtt")

                allSongs.forEach { song ->
                    ensureActive()
                    if (song.originalUrl == null) return@forEach

                    var audioPath = song.localAudioPath
                    var hasAudio = song.cacheStatus.hasAudio
                    if (restoreAudio && !hasAudio) {
                        val foundFile = musicFiles.find { 
                            (it.name.startsWith(song.id) || it.name.contains(song.id)) && it.extension.lowercase() in audioExts 
                        }
                        if (foundFile != null) {
                            audioPath = foundFile.absolutePath
                            hasAudio = true
                        }
                    }

                    var thumbPath = song.localThumbnailPath
                    var hasThumb = song.cacheStatus.hasThumbnail
                    if (restoreThumbnails && !hasThumb) {
                        val foundFile = thumbFiles.find { 
                            (it.name.startsWith(song.id) || it.name.contains(song.id)) && it.extension.lowercase() in thumbExts 
                        }
                        if (foundFile != null) {
                            thumbPath = foundFile.absolutePath
                            hasThumb = true
                        }
                    }

                    var lyricsPath = song.localLyricsPath
                    var hasLyrics = song.cacheStatus.hasLyrics
                    if (restoreLyrics && !hasLyrics) {
                        val foundFile = lyricsFiles.find { 
                            (it.name.startsWith(song.id) || it.name.contains(song.id)) && it.extension.lowercase() in lyricsExts 
                        }
                        if (foundFile != null) {
                            lyricsPath = foundFile.absolutePath
                            hasLyrics = true
                        }
                    }

                    if (hasAudio != song.cacheStatus.hasAudio || hasThumb != song.cacheStatus.hasThumbnail || hasLyrics != song.cacheStatus.hasLyrics) {
                        updatedSongs.add(song.copy(
                            localAudioPath = audioPath,
                            localThumbnailPath = thumbPath,
                            localLyricsPath = lyricsPath,
                            cacheStatus = song.cacheStatus.copy(
                                hasAudio = hasAudio,
                                hasThumbnail = hasThumb,
                                hasLyrics = hasLyrics,
                                failedAudio = if (hasAudio) false else song.cacheStatus.failedAudio,
                                failedThumbnail = if (hasThumb) false else song.cacheStatus.failedThumbnail,
                                failedLyrics = if (hasLyrics) false else song.cacheStatus.failedLyrics
                            )
                        ))
                    }
                }

                if (updatedSongs.isNotEmpty()) {
                    repository.updateSongs(updatedSongs)
                    withContext(Dispatchers.Main) {
                        // Update current queue for found items
                        updatedSongs.forEach { updated ->
                            val idx = currentQueue.indexOfFirst { it.id == updated.id }
                            if (idx != -1) currentQueue[idx] = updated
                        }
                    }
                }

                val newTasks = mutableListOf<DownloadTask>()
                
                allSongs.forEach { song ->
                    ensureActive()
                    if (song.originalUrl == null) return@forEach
                    
                    // Use the potentially updated state from our earlier disk check
                    val currentUpdated = updatedSongs.find { it.id == song.id } ?: song
                    
                    val actualNeedsAudio = restoreAudio && !currentUpdated.cacheStatus.hasAudio && !currentUpdated.cacheStatus.failedAudio
                    val actualNeedsThumb = restoreThumbnails && !currentUpdated.cacheStatus.hasThumbnail && !currentUpdated.cacheStatus.failedThumbnail
                    val actualNeedsLyrics = restoreLyrics && !currentUpdated.cacheStatus.hasLyrics && !currentUpdated.cacheStatus.failedLyrics
                    
                    if (actualNeedsAudio || actualNeedsThumb || actualNeedsLyrics) {
                        newTasks.add(DownloadTask(
                            url = song.originalUrl,
                            title = song.title,
                            downloadAudio = actualNeedsAudio,
                            downloadThumbnail = actualNeedsThumb,
                            downloadLyrics = actualNeedsLyrics,
                            existingSongId = song.id
                        ))
                    }
                }

                if (newTasks.isNotEmpty()) {
                    withContext(Dispatchers.Main) {
                        addBatchToDownloadQueue(newTasks, context)
                    }
                }
            } catch (e: CancellationException) {
                // Task was canceled
            } catch (e: Exception) {
                Log.e("Fluxsona", "Restoration error", e)
            } finally {
                isRestoring = false
                restorationJob = null
            }
        }
    }

    fun cancelRestoration() {
        restorationJob?.cancel()
        isRestoring = false
        restorationJob = null
    }

    fun redownloadSong(song: Song, context: Context, restoreAudio: Boolean, restoreThumbnails: Boolean, restoreLyrics: Boolean = false) {
        val url = song.originalUrl ?: return

        val actualNeedsAudio = restoreAudio && (song.localAudioPath == null || !File(song.localAudioPath).exists())
        val actualNeedsThumb = restoreThumbnails && (song.localThumbnailPath == null || !File(song.localThumbnailPath).exists())
        val actualNeedsLyrics = restoreLyrics && (song.localLyricsPath == null || !File(song.localLyricsPath).exists())
        
        if (actualNeedsAudio || actualNeedsThumb || actualNeedsLyrics) {
            addToDownloadQueue(
                url = url,
                context = context,
                downloadAudio = actualNeedsAudio,
                downloadThumbnail = actualNeedsThumb,
                downloadLyrics = actualNeedsLyrics,
                songId = song.id,
                title = song.title
            )
        }
    }

    fun deleteSelectedSongs(context: Context, deleteAudio: Boolean, deleteThumbnails: Boolean, deleteLyrics: Boolean) {
        viewModelScope.launch {
            val idsToDelete = selectedSongIds.toList()
            clearSelection()
            idsToDelete.forEach { id ->
                songs.value.find { it.id == id }?.let { deleteSongInternal(it, context, deleteAudio, deleteThumbnails, deleteLyrics) }
            }
        }
    }

    fun clearCacheForSelectedSongs(clearAudio: Boolean, clearThumbnails: Boolean, clearLyrics: Boolean = false, clearFailed: Boolean = false) {
        viewModelScope.launch {
            val idsToClear = selectedSongIds.toList()
            withContext(Dispatchers.IO) {
                idsToClear.forEach { id ->
                    songs.value.find { it.id == id }?.let { song ->
                        clearSongCacheInternal(song, clearAudio, clearThumbnails, clearLyrics, clearFailed)
                    }
                }
            }
        }
    }

    fun addTagsToSelected(tagNames: List<String>) {
        viewModelScope.launch {
            val idsToUpdate = selectedSongIds.toList()
            val allSongs = songs.value
            idsToUpdate.forEach { id ->
                allSongs.find { it.id == id }?.let { song ->
                    val newTags = (song.tags + tagNames).distinct()
                    if (newTags != song.tags) {
                        updateSongTags(id, newTags)
                    }
                }
            }
        }
    }

    fun removeTagsFromSelected(tagNames: List<String>) {
        viewModelScope.launch {
            val idsToUpdate = selectedSongIds.toList()
            val allSongs = songs.value
            val tagSet = tagNames.toSet()
            idsToUpdate.forEach { id ->
                allSongs.find { it.id == id }?.let { song ->
                    val newTags = song.tags.filter { it !in tagSet }
                    if (newTags != song.tags) {
                        updateSongTags(id, newTags)
                    }
                }
            }
        }
    }


    private fun extractThumbnailUrl(element: JsonElement?): String? {
        if (element == null || element.isJsonNull) return null
        if (element.isJsonObject) {
            val obj = element.asJsonObject
            if (obj.has("thumbnails") && obj.get("thumbnails").isJsonArray) {
                val array = obj.getAsJsonArray("thumbnails")
                if (array.size() > 0) {
                    var bestUrl: String? = null
                    var maxWidth = 0
                    for (i in 0 until array.size()) {
                        val thumbElement = array.get(i)
                        if (thumbElement.isJsonObject) {
                            val thumb = thumbElement.asJsonObject
                            val width = if (thumb.has("width")) thumb.get("width").asInt else 0
                            if (width >= maxWidth) {
                                maxWidth = width
                                if (thumb.has("url")) bestUrl = thumb.get("url").asString
                            }
                        }
                    }
                    if (bestUrl != null) return bestUrl
                }
            }
            if (obj.has("thumbnail") && obj.get("thumbnail").isJsonPrimitive) return obj.get("thumbnail").asString
            if (obj.has("entries")) return extractThumbnailUrl(obj.get("entries"))
            if (obj.has("items")) return extractThumbnailUrl(obj.get("items"))
        } else if (element.isJsonArray) {
            val array = element.asJsonArray
            if (array.size() > 0) return extractThumbnailUrl(array.get(0))
        }
        return null
    }

    fun deleteSong(song: Song, context: Context, deleteAudio: Boolean, deleteThumbnails: Boolean, deleteLyrics: Boolean) {
        viewModelScope.launch {
            deleteSongInternal(song, context, deleteAudio, deleteThumbnails, deleteLyrics)
        }
    }

    private suspend fun deleteSongInternal(song: Song, context: Context, deleteAudio: Boolean, deleteThumbnails: Boolean, deleteLyrics: Boolean) {
        try {
            withContext(Dispatchers.IO) {
                if (deleteAudio) {
                    song.localAudioPath?.let { path -> File(path).let { if (it.exists()) it.delete() } }
                }
                if (deleteThumbnails) {
                    song.localThumbnailPath?.let { path -> File(path).let { if (it.exists()) it.delete() } }
                }
                if (deleteLyrics) {
                    song.localLyricsPath?.let { path -> File(path).let { if (it.exists()) it.delete() } }
                }
                repository.deleteSong(song)
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                errorMessage = context.getString(R.string.msg_delete_failed, e.message)
            }
        }
    }

    private var autoTagJob: Job? = null

    fun autoTagSong(song: Song) {
        autoTagJob = viewModelScope.launch {
            isAutoTagging = true
            autoTagSongsInternal(listOf(song))
            isAutoTagging = false
            autoTagJob = null
        }
    }

    fun autoTagSelectedSongs() {
        autoTagJob = viewModelScope.launch {
            isAutoTagging = true
            val songsToTag = songs.value.filter { selectedSongIds.contains(it.id) }
            autoTagSongsInternal(songsToTag)
            isAutoTagging = false
            autoTagJob = null
            clearSelection()
        }
    }

    fun autoTagAllSongs(context: Context) {
        autoTagJob = viewModelScope.launch {
            isAutoTagging = true
            autoTagSongsInternal(songs.value)
            isAutoTagging = false
            autoTagJob = null
            withContext(Dispatchers.Main) {
                errorMessage = context.getString(R.string.msg_auto_tag_success, songs.value.size)
            }
        }
    }

    fun stopAutoTagging() {
        autoTagJob?.cancel()
        autoTagJob = null
        isAutoTagging = false
    }

    private suspend fun autoTagSongsInternal(songsToTag: List<Song>) {
        if (songsToTag.isEmpty()) return
        withContext(Dispatchers.IO) {
            songsToTag.forEach { song ->
                ensureActive()
                try {
                    val newTags = mutableSetOf<String>()

                    // 1. YouTube Metadata
                    val url = song.originalUrl ?: return@forEach
                    val request = YoutubeDLRequest(url).apply {
                        addOption("--dump-single-json")
                        addOption("--no-warnings")
                        cookiesFilePath?.let { addOption("--cookies", it) }
                    }
                    val response = YoutubeDL.getInstance().execute(request)
                    val rootNode = JsonParser.parseString(response.out).asJsonObject

                    // Accurate Language Detection (Check root and nested)
                    val rawLang = listOf("language", "language_res", "asr_hint")
                        .mapNotNull { key -> if (rootNode.has(key) && !rootNode.get(key).isJsonNull) rootNode.get(key).asString else null }
                        .firstOrNull { it.isNotBlank() }

                    rawLang?.let {
                        val code = it.split("-")[0].split("_")[0].uppercase()
                        if (code.length in 2..3) newTags.add(code)
                    }

                    // YouTube Genres & Subgenres
                    val uploader = rootNode.get("uploader")?.asString?.lowercase() ?: ""

                    if (rootNode.has("genre") && !rootNode.get("genre").isJsonNull) {
                        processRawGenre(rootNode.get("genre").asString, song.artist, song.title, uploader).forEach { newTags.add(it) }
                    }
                    if (rootNode.has("categories")) {
                        rootNode.getAsJsonArray("categories").forEach {
                            processRawGenre(it.asString, song.artist, song.title, uploader).forEach { tag -> newTags.add(tag) }
                        }
                    }
                    if (rootNode.has("tags")) {
                        rootNode.getAsJsonArray("tags").forEach {
                            processRawGenre(it.asString, song.artist, song.title, uploader).forEach { tag -> newTags.add(tag) }
                        }
                    }
                    if (rootNode.has("keywords")) {
                        rootNode.getAsJsonArray("keywords").forEach {
                            processRawGenre(it.asString, song.artist, song.title, uploader).forEach { tag -> newTags.add(tag) }
                        }
                    }

                    // 2. Deezer API (Standard Genres & Subgenres)
                    fetchGenresFromDeezer(song.artist, song.title).forEach {
                        processRawGenre(it, song.artist, song.title, uploader).forEach { tag -> newTags.add(tag) }
                    }

                    // 3. MusicBrainz (Community Rich Tags)
                    fetchGenresFromMusicBrainz(song.artist, song.title).forEach {
                        processRawGenre(it, song.artist, song.title, uploader).forEach { tag -> newTags.add(tag) }
                    }

                    // 4. iTunes API (Final fallback)
                    if (newTags.none { it.length > 3 }) {
                        fetchGenreFromITunes(song.artist, song.title)?.let {
                            processRawGenre(it, song.artist, song.title, uploader).forEach { tag -> newTags.add(tag) }
                        }
                    }

                    if (newTags.isNotEmpty()) {
                        val uniqueNewTags = newTags.filter { it !in song.tags.toSet() }
                        if (uniqueNewTags.isNotEmpty()) {
                            val updatedTags = (song.tags + uniqueNewTags).distinct()
                            uniqueNewTags.forEach { repository.insertTag(it) }
                            val updatedSong = song.copy(tags = updatedTags)
                            repository.updateSong(updatedSong)
                            withContext(Dispatchers.Main) {
                                val queueIndices = currentQueue.withIndex().filter { it.value.id == song.id }.map { it.index }
                                queueIndices.forEach { currentQueue[it] = updatedSong }
                            }
                        }
                    }
                    yield()
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    Log.e("Fluxsona", "Failed to auto-tag song: ${song.title}", e)
                }
            }
        }
    }

    private fun processRawGenre(raw: String, artist: String, title: String, uploader: String = ""): List<String> {
        // 1. Split by common delimiters: /, &, |, +, "and", comma
        val initialParts = raw.split(Regex("(?i)\\s*(?:/|&|\\||\\+|,|\\s+and\\s+)\\s*"))
        val result = mutableSetOf<String>()

        val multiWordGenres = setOf(
            "hip hop", "heavy metal", "hard rock", "soft rock", "punk rock", "indie rock",
            "pop rock", "alt rock", "progressive rock", "psychedelic rock", "electronic music",
            "dance music", "new wave", "soul music", "rhythm and blues", "lo fi", "k pop",
            "j pop", "synth wave", "vapor wave", "hyper pop", "hard style", "drum and bass",
            "visual kei", "future funk", "city pop"
        )

        initialParts.forEach { part ->
            val cleaned = part.trim()
            if (cleaned.isBlank()) return@forEach

            // 2. If it's a known multi-word genre, keep it together
            if (multiWordGenres.contains(cleaned.lowercase())) {
                if (isValidGenreTag(cleaned, artist, title, uploader)) {
                    result.add(normalizeGenreName(cleaned))
                }
            } else if (cleaned.contains(" ")) {
                // 3. If it has spaces and isn't a known compound genre, split it
                // e.g. "Rock Pop" -> "Rock", "Pop"
                cleaned.split(" ").forEach { word ->
                    val subCleaned = word.trim()
                    if (isValidGenreTag(subCleaned, artist, title, uploader)) {
                        result.add(normalizeGenreName(subCleaned))
                    }
                }
            } else {
                // 4. Single word or already handled
                if (isValidGenreTag(cleaned, artist, title, uploader)) {
                    result.add(normalizeGenreName(cleaned))
                }
            }
        }
        return result.toList()
    }

    private fun normalizeGenreName(genre: String): String {
        val lower = genre.lowercase()
        return when {
            lower == "alternativo" || lower == "alternative rock" || lower == "alt-rock" -> "Alternative"
            lower == "hip-hop" || lower == "hiphop" -> "Hip Hop"
            lower == "r&b" || lower == "rhythm and blues" -> "R&B"
            lower == "lo-fi" -> "Lo-Fi"
            lower == "kpop" -> "K-Pop"
            lower == "jpop" -> "J-Pop"
            else -> genre.split(" ").joinToString(" ") { word ->
                word.replaceFirstChar { it.uppercase() }
            }
        }
    }

    private fun isValidGenreTag(tag: String, artist: String, title: String, uploader: String = ""): Boolean {
        val lowerTag = tag.lowercase().trim()
        val lowerArtist = artist.lowercase().trim()
        val lowerTitle = title.lowercase().trim()
        val lowerUploader = uploader.lowercase().trim()

        if (lowerTag.length < 2 || lowerTag.length > 25) return false

        // 1. STRICT: No Names or Titles
        if (lowerTag == lowerArtist || lowerTag == lowerTitle || lowerTag == lowerUploader) return false
        if (lowerArtist.contains(lowerTag) || lowerTitle.contains(lowerTag) || lowerUploader.contains(lowerTag)) return false
        if (lowerTag.contains(lowerArtist) || lowerTag.contains(lowerTitle) || lowerTag.contains(lowerUploader)) return false

        // 2. STRICT: No word from Artist, Title or Uploader
        val artistWords = lowerArtist.split(Regex("[\\s\\-\\(\\)\\[\\]\\.]+")).filter { it.length > 2 }
        val titleWords = lowerTitle.split(Regex("[\\s\\-\\(\\)\\[\\]\\.]+")).filter { it.length > 2 }
        val uploaderWords = lowerUploader.split(Regex("[\\s\\-\\(\\)\\[\\]\\.]+")).filter { it.length > 2 }

        if (artistWords.any { lowerTag.contains(it) || it.contains(lowerTag) }) return false
        if (titleWords.any { lowerTag.contains(it) || it.contains(lowerTag) }) return false
        if (uploaderWords.any { lowerTag.contains(it) || it.contains(lowerTag) }) return false

        // 3. Junk & Theme Filter
        val junk = listOf(
            "official", "video", "audio", "lyrics", "full", "hd", "high", "definition", "4k",
            "vevo", "records", "music", "topic", "channel", "subscribe", "sub", "original",
            "remastered", "mix", "live", "version", "hq", "song", "songs", "track", "tracks",
            "hit", "hits", "best", "top", "new", "latest", "popular", "trending", "playlist",
            "album", "single", "release", "promo", "teaser", "extended", "radio", "edit",
            "remix", "bootleg", "cover", "nightcore", "daycore", "reverb", "slowed", "sped",
            "gaming", "chill", "relaxing", "workout", "gym", "study", "sleep", "lofi beat",
            "relaxing music", "meditation", "background", "instrumental"
        )
        if (junk.any { lowerTag == it || lowerTag.contains(it) }) return false

        // 4. Nationality/Language adjective filter
        val descriptives = listOf(
            "russian", "japanese", "korean", "chinese", "english", "american", "british", "german", "french", "spanish", "latin", "indian", "hindi", "thai", "vietnamese", "brazilian", "portuguese"
        )
        if (descriptives.any { lowerTag.startsWith(it) && lowerTag.length > it.length + 1 }) return false

        if (lowerTag.all { it.isDigit() }) return false

        return true
    }

    private suspend fun fetchGenreFromITunes(artist: String, title: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                val query = Uri.encode("$artist $title")
                val url = URL("https://itunes.apple.com/search?term=$query&entity=song&limit=1")
                val connection = url.openConnection() as java.net.HttpURLConnection
                connection.connectTimeout = 5000
                if (connection.responseCode == 200) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    val json = JsonParser.parseString(response).asJsonObject
                    val results = json.getAsJsonArray("results")
                    if (results != null && results.size() > 0) {
                        return@withContext results.get(0).asJsonObject.get("primaryGenreName")?.asString
                    }
                }
            } catch (e: Exception) {
                Log.e("Fluxsona", "iTunes API error", e)
            }
            null
        }
    }

    private suspend fun fetchGenresFromDeezer(artist: String, title: String): List<String> {
        return withContext(Dispatchers.IO) {
            try {
                val query = Uri.encode("artist:\"$artist\" track:\"$title\"")
                val url = URL("https://api.deezer.com/search?q=$query&limit=1")
                val connection = url.openConnection() as java.net.HttpURLConnection
                if (connection.responseCode == 200) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    val json = JsonParser.parseString(response).asJsonObject
                    val data = json.getAsJsonArray("data")
                    if (data != null && data.size() > 0) {
                        val albumId = data.get(0).asJsonObject.getAsJsonObject("album").get("id").asLong
                        val albumUrl = URL("https://api.deezer.com/album/$albumId")
                        val albumConn = albumUrl.openConnection() as java.net.HttpURLConnection
                        if (albumConn.responseCode == 200) {
                            val albumResp = albumConn.inputStream.bufferedReader().use { it.readText() }
                            val albumJson = JsonParser.parseString(albumResp).asJsonObject
                            val genres = albumJson.getAsJsonObject("genres")?.getAsJsonArray("data")
                            if (genres != null) {
                                return@withContext genres.map { it.asJsonObject.get("name").asString }
                            }
                        }
                    }
                }
            } catch (e: Exception) {}
            emptyList()
        }
    }

    private suspend fun fetchGenresFromMusicBrainz(artist: String, title: String): List<String> {
        return withContext(Dispatchers.IO) {
            try {
                val query = Uri.encode("artist:\"$artist\" AND recording:\"$title\"")
                val url = URL("https://musicbrainz.org/ws/2/recording?query=$query&fmt=json&limit=1")
                val connection = url.openConnection() as java.net.HttpURLConnection
                connection.setRequestProperty("User-Agent", "Fluxsona/1.1 ( b.rslav@example.com )")
                if (connection.responseCode == 200) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    val json = JsonParser.parseString(response).asJsonObject
                    val recordings = json.getAsJsonArray("recordings")
                    if (recordings != null && recordings.size() > 0) {
                        val recording = recordings.get(0).asJsonObject
                        val genres = mutableListOf<String>()
                        recording.getAsJsonArray("genres")?.forEach { genres.add(it.asJsonObject.get("name").asString) }
                        recording.getAsJsonArray("tags")?.forEach {
                            val tag = it.asJsonObject.get("name").asString
                            if (tag.length < 20) genres.add(tag)
                        }
                        return@withContext genres.distinct()
                    }
                }
            } catch (e: Exception) {}
            emptyList()
        }
    }

    fun clearCache(context: Context, clearAudio: Boolean, clearThumbnails: Boolean, clearLyrics: Boolean = false, clearFailed: Boolean = false) {
        viewModelScope.launch { withContext(Dispatchers.IO) { songs.value.forEach { clearSongCacheInternal(it, clearAudio, clearThumbnails, clearLyrics, clearFailed) } } }
    }

    fun clearSongCache(song: Song, clearAudio: Boolean, clearThumbnails: Boolean, clearLyrics: Boolean = false, clearFailed: Boolean = false) {
        viewModelScope.launch { withContext(Dispatchers.IO) { clearSongCacheInternal(song, clearAudio, clearThumbnails, clearLyrics, clearFailed) } }
    }

    private suspend fun clearSongCacheInternal(song: Song, clearAudio: Boolean, clearThumbnails: Boolean, clearLyrics: Boolean = false, clearFailed: Boolean = false) {
        var newAudioPath = song.localAudioPath
        var newThumbPath = song.localThumbnailPath
        var newLyricsPath = song.localLyricsPath
        var newThumbnailUri = song.thumbnailUri

        var failedAudio = song.cacheStatus.failedAudio
        var failedThumbnail = song.cacheStatus.failedThumbnail
        var failedLyrics = song.cacheStatus.failedLyrics

        if (clearAudio) { song.localAudioPath?.let { File(it).let { if (it.exists()) it.delete() } }; newAudioPath = null }
        if (clearThumbnails) { song.localThumbnailPath?.let { File(it).let { if (it.exists()) it.delete() } }; newThumbPath = null; if (song.thumbnailUri?.startsWith("http") != true) newThumbnailUri = null }
        if (clearLyrics) { song.localLyricsPath?.let { File(it).let { if (it.exists()) it.delete() } }; newLyricsPath = null }

        if (clearFailed) {
            failedAudio = false
            failedThumbnail = false
            failedLyrics = false
        }

        val updatedSong = song.copy(
            localAudioPath = newAudioPath,
            localThumbnailPath = newThumbPath,
            localLyricsPath = newLyricsPath,
            thumbnailUri = newThumbnailUri,
            cacheStatus = song.cacheStatus.copy(
                hasAudio = if (clearAudio) false else song.cacheStatus.hasAudio,
                hasThumbnail = if (clearThumbnails) false else song.cacheStatus.hasThumbnail,
                hasLyrics = if (clearLyrics) false else song.cacheStatus.hasLyrics,
                failedAudio = failedAudio,
                failedThumbnail = failedThumbnail,
                failedLyrics = failedLyrics
            )
        )
        repository.updateSong(updatedSong)
        withContext(Dispatchers.Main) {
            val queueIndices = currentQueue.withIndex().filter { it.value.id == song.id }.map { it.index }
            queueIndices.forEach { currentQueue[it] = updatedSong }
        }
    }

    val filteredSongs: StateFlow<List<Song>> = combine(
        songs,
        snapshotFlow { _tagFilterStates.toList() },
        snapshotFlow { sortMode },
        snapshotFlow { searchQuery },
        snapshotFlow { shuffleSeed },
        snapshotFlow { cacheFilterAudio },
        snapshotFlow { cacheFilterThumbnail },
        snapshotFlow { cacheFilterLyrics },
        snapshotFlow { customAuthorFilters.toList() },
        snapshotFlow { customTitleFilters.toList() },
        snapshotFlow { currentQueue.toList() }
    ) { args: Array<*> ->
        @Suppress("UNCHECKED_CAST")
        val songList = args[0] as List<Song>
        @Suppress("UNCHECKED_CAST")
        val filterStates = args[1] as List<Tag>
        val currentSort = args[2] as String
        val query = args[3] as String
        val seed = args[4] as Long
        val cAudio = args[5] as TagState
        val cThumb = args[6] as TagState
        val cLyrics = args[7] as TagState
        @Suppress("UNCHECKED_CAST")
        val authorFilters = args[8] as List<String>
        @Suppress("UNCHECKED_CAST")
        val titleFilters = args[9] as List<String>
        @Suppress("UNCHECKED_CAST")
        val queueList = args[10] as List<Song>

        if (currentSort == "SYNCED_QUEUE") {
            return@combine queueList
        }

        val includedTags = filterStates.filter { it.state == TagState.INCLUDED }.map { it.name }.toSet()
        val excludedTags = filterStates.filter { it.state == TagState.EXCLUDED }.map { it.name }.toSet()
        val disjunctionTags = filterStates.filter { it.state == TagState.DISJUNCTION }.map { it.name }.toSet()

        val filtered = songList.filter { song ->
            if (query.isNotBlank()) {
                val lowerQuery = query.lowercase()
                if (!song.title.lowercase().contains(lowerQuery) && !song.artist.lowercase().contains(lowerQuery)) return@filter false
            }

            if (authorFilters.isNotEmpty()) {
                if (!authorFilters.all { song.artist.lowercase().contains(it.lowercase()) }) return@filter false
            }

            if (titleFilters.isNotEmpty()) {
                if (!titleFilters.all { song.title.lowercase().contains(it.lowercase()) }) return@filter false
            }

            // Cache Filters
            if (cAudio == TagState.INCLUDED && !song.cacheStatus.hasAudio) return@filter false
            if (cAudio == TagState.EXCLUDED && song.cacheStatus.hasAudio) return@filter false
            
            if (cThumb == TagState.INCLUDED && !song.cacheStatus.hasThumbnail) return@filter false
            if (cThumb == TagState.EXCLUDED && song.cacheStatus.hasThumbnail) return@filter false
            
            if (cLyrics == TagState.INCLUDED && !song.cacheStatus.hasLyrics) return@filter false
            if (cLyrics == TagState.EXCLUDED && song.cacheStatus.hasLyrics) return@filter false

            val songTags = song.tags.toSet()
            if (songTags.any { it in excludedTags }) return@filter false
            if (includedTags.isNotEmpty() && !songTags.containsAll(includedTags)) return@filter false
            if (disjunctionTags.isNotEmpty() && !songTags.any { it in disjunctionTags }) return@filter false
            true
        }

        when (currentSort) {
            "SHUFFLE" -> filtered.shuffled(kotlin.random.Random(seed))
            "TITLE_ASC" -> filtered.sortedBy { it.title.lowercase() }
            "TITLE_DESC" -> filtered.sortedByDescending { it.title.lowercase() }
            "ARTIST_ASC" -> filtered.sortedBy { it.artist.lowercase() }
            "ARTIST_DESC" -> filtered.sortedByDescending { it.artist.lowercase() }
            "DURATION_ASC" -> filtered.sortedBy { it.duration.toLongOrNull() ?: 0L }
            "DURATION_DESC" -> filtered.sortedByDescending { it.duration.toLongOrNull() ?: 0L }
            "DATE_ADDED_ASC" -> filtered.sortedBy { it.dateAdded }
            "DATE_ADDED_DESC" -> filtered.sortedByDescending { it.dateAdded }
            else -> filtered
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun getFilteredSongs(): List<Song> = filteredSongs.value
    fun clearFilters() {
        _tagFilterStates.clear()
        cacheFilterAudio = TagState.NONE
        cacheFilterThumbnail = TagState.NONE
        cacheFilterLyrics = TagState.NONE
        customAuthorFilters.clear()
        customTitleFilters.clear()
    }

    fun playSong(context: Context, song: Song, queue: List<Song>? = null) {
        if (song.localAudioPath == null || !File(song.localAudioPath).exists()) { errorMessage = "Audio file not found. Please restore data from the menu."; return }
        val player = controller ?: run { errorMessage = context.getString(R.string.msg_player_not_ready); return }
        errorMessage = null
        val finalQueue = (queue ?: filteredSongs.value).map { if (it.id == song.id) song else it }
        val shouldRefreshQueue = currentQueue.isEmpty() || currentQueue.none { it.id == song.id } || currentQueue.map { it.id } != finalQueue.map { it.id } || currentQueue.find { it.id == song.id }?.localAudioPath != song.localAudioPath
        if (shouldRefreshQueue) {
            currentQueue.clear(); currentQueue.addAll(finalQueue)
            val mediaItems = finalQueue.map { s ->
                val uri = Uri.parse(s.localAudioPath?.let { Uri.fromFile(File(it)).toString() } ?: "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3")
                MediaItem.Builder().setMediaId(s.id).setMediaMetadata(MediaMetadata.Builder().setTitle(s.title).setArtist(s.artist).setArtworkUri(s.localThumbnailPath?.let { Uri.fromFile(File(it)) } ?: s.thumbnailUri?.let { Uri.parse(it) }).build()).setUri(uri).setRequestMetadata(MediaItem.RequestMetadata.Builder().setMediaUri(uri).build()).build()
            }
            player.setMediaItems(mediaItems); player.seekTo(finalQueue.indexOfFirst { it.id == song.id }.coerceAtLeast(0), 0L); player.prepare()
        } else player.seekTo(currentQueue.indexOfFirst { it.id == song.id }, 0L)
        _currentSongId = song.id; player.play()
    }

    fun toggleFavorite(songId: String) {
        val song = songs.value.find { it.id == songId } ?: return
        val isNowFavorite = !song.isFavorite
        viewModelScope.launch {
            repository.updateFavorite(songId, isNowFavorite)
            val currentTags = song.tags.toMutableList().apply { if (isNowFavorite) { if (!contains("Favourite")) add("Favourite") } else remove("Favourite") }
            val updatedSong = song.copy(tags = currentTags, isFavorite = isNowFavorite)
            repository.updateSong(updatedSong)
            currentQueue.withIndex().filter { it.value.id == songId }.forEach { currentQueue[it.index] = updatedSong }
        }
    }

    fun isSongFavorite(songId: String): Boolean = songs.value.find { it.id == songId }?.isFavorite ?: false
    fun seekForward() { controller?.let { val target = it.currentPosition + (skipForwardSeconds * 1000); it.seekTo(target.coerceAtMost(it.duration)); currentPosition = it.currentPosition } }
    fun seekBackward() { controller?.let { val target = it.currentPosition - (skipBackwardSeconds * 1000); it.seekTo(target.coerceAtLeast(0)); currentPosition = it.currentPosition } }
    fun togglePlayPause() { controller?.let { if (it.isPlaying) it.pause() else it.play() } }
    fun seekTo(position: Long) { controller?.let { it.seekTo(position); currentPosition = position } }
    fun toggleRepeatMode() { controller?.let { it.repeatMode = when (it.repeatMode) { Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL; Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE; else -> Player.REPEAT_MODE_OFF } } }
    fun toggleShuffleMode() {
        controller?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled }
    }
    fun togglePlaybackSpeed() { controller?.let { it.setPlaybackSpeed(when (playbackSpeed) { 1.0f -> 1.25f; 1.25f -> 1.5f; 1.5f -> 2.0f; 2.0f -> 0.5f; 0.5f -> 0.75f; else -> 1.0f }) } }
    fun setVolumeValue(value: Float) { 
        viewModelScope.launch { repository.saveVolume(value) }
    }
    fun toggleMute(context: Context) { 
        isMuted = !isMuted
        val targetVolume = if (isMuted) 0f else volume
        val intent = Intent(context, PlaybackService::class.java).apply {
            action = "SET_VOLUME_BOOST"
            putExtra("VOLUME", targetVolume)
        }
        context.startService(intent)
    }
    fun updateProgress() { controller?.let { currentPosition = it.currentPosition.coerceAtLeast(0L); duration = it.duration.coerceAtLeast(0L) } }
    private var progressJob: Job? = null
    fun startProgressUpdate() { progressJob?.cancel(); progressJob = viewModelScope.launch { while (isPlaying) { updateProgress(); delay(200) } } }
    fun skipNext() { controller?.seekToNext() }
    fun skipPrevious() { controller?.seekToPrevious() }
    fun removeFromQueue(songId: String) { currentQueue.indexOfFirst { it.id == songId }.let { if (it != -1) { currentQueue.removeAt(it); controller?.removeMediaItem(it) } } }
    fun clearQueue() { 
        currentQueue.clear()
        controller?.clearMediaItems()
        currentSongId = null 
        isPlayerExpanded = false
        isQueueMaximized = false
    }

    fun checkForUpdates(context: Context) {
        if (isCheckingForUpdates) return
        isCheckingForUpdates = true
        val appContext = context.applicationContext
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Replace with your actual update manifest URL (e.g. GitHub raw file)
                val updateUrl = "https://raw.githubusercontent.com/ga6ap/Fluxsona/master/update.json"
                val json = URL(updateUrl).readText()
                val info = Gson().fromJson(json, UpdateInfo::class.java)
                
                // Get current version code dynamically
                val packageInfo = appContext.packageManager.getPackageInfo(appContext.packageName, 0)
                val currentVersionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    packageInfo.longVersionCode.toInt()
                } else {
                    @Suppress("DEPRECATION")
                    packageInfo.versionCode
                }
                Log.d("FluxsonaUpdate", "Current version: $currentVersionCode, Remote version: ${info.versionCode}")
                Log.d("FluxsonaUpdate", "Current version: $currentVersionCode, Remote version: ${info.versionCode}")
                if (info.versionCode > currentVersionCode) {
                    withContext(Dispatchers.Main) {
                        updateInfo = info
                    }
                }
            } catch (e: Exception) {
                Log.e("FluxsonaUpdate", "Failed to check for updates", e)
            } finally {
                isCheckingForUpdates = false
            }
        }
    }

    fun downloadAndInstallUpdate(context: Context) {
        val info = updateInfo ?: return
        if (isDownloadingUpdate) return
        isDownloadingUpdate = true
        updateProgress = 0f
        
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val url = URL(info.downloadUrl)
                val connection = url.openConnection()
                connection.connect()
                val fileLength = connection.contentLength
                
                val apkFile = File(context.externalCacheDir, "update.apk")
                url.openStream().use { input ->
                    FileOutputStream(apkFile).use { output ->
                        val data = ByteArray(8192)
                        var total = 0L
                        var count: Int
                        while (input.read(data).also { count = it } != -1) {
                            total += count
                            if (fileLength > 0) {
                                withContext(Dispatchers.Main) {
                                    updateProgress = total.toFloat() / fileLength
                                }
                            }
                            output.write(data, 0, count)
                        }
                    }
                }
                
                withContext(Dispatchers.Main) {
                    isDownloadingUpdate = false
                    installApk(context, apkFile)
                }
            } catch (e: Exception) {
                Log.e("FluxsonaUpdate", "Failed to download update", e)
                withContext(Dispatchers.Main) {
                    isDownloadingUpdate = false
                }
            }
        }
    }

    private fun installApk(context: Context, file: File) {
        val intent = Intent(Intent.ACTION_VIEW)
        val uri = androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        intent.setDataAndType(uri, "application/vnd.android.package-archive")
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    fun dismissUpdate() {
        updateInfo = null
    }
    fun moveQueueItem(fromIndex: Int, toIndex: Int) { if (fromIndex in currentQueue.indices && toIndex in currentQueue.indices) { val item = currentQueue.removeAt(fromIndex); currentQueue.add(toIndex, item); controller?.moveMediaItem(fromIndex, toIndex) } }
    fun moveDownloadQueueItem(fromIndex: Int, toIndex: Int) { if (fromIndex in downloadQueue.indices && toIndex in downloadQueue.indices) { val item = downloadQueue.removeAt(fromIndex); downloadQueue.add(toIndex, item) } }

    fun exportData(context: Context, includeFiles: Boolean = false) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val songList = songs.value; val tagList = repository.tags.first()
                    val exportSongs = if (includeFiles) songList.map { it.copy(cacheStatus = it.cacheStatus.copy(hasAudio = it.localAudioPath?.let { File(it).exists() } ?: false, hasThumbnail = it.localThumbnailPath?.let { File(it).exists() } ?: false, hasLyrics = it.localLyricsPath?.let { File(it).exists() } ?: false)) } else songList
                    val data = mapOf("songs" to exportSongs, "tags" to tagList)
                    val json = Gson().toJson(data); val timestamp = System.currentTimeMillis()
                    val fileName = if (includeFiles) "fluxsona_backup_$timestamp.zip" else "fluxsona_backup_$timestamp.flxn"
                    val tempFile = File(context.cacheDir, fileName)
                    if (includeFiles) {
                        ZipOutputStream(FileOutputStream(tempFile)).use { zos ->
                            zos.putNextEntry(ZipEntry("data.json")); zos.write(json.toByteArray()); zos.closeEntry()
                            songList.forEach { song ->
                                song.localAudioPath?.let { File(it).let { if (it.exists()) { zos.putNextEntry(ZipEntry("music/${it.name}")); it.inputStream().use { it.copyTo(zos) }; zos.closeEntry() } } }
                                song.localThumbnailPath?.let { File(it).let { if (it.exists()) { zos.putNextEntry(ZipEntry("thumbnails/${it.name}")); it.inputStream().use { it.copyTo(zos) }; zos.closeEntry() } } }
                                song.localLyricsPath?.let { File(it).let { if (it.exists()) { zos.putNextEntry(ZipEntry("lyrics/${it.name}")); it.inputStream().use { it.copyTo(zos) }; zos.closeEntry() } } }
                            }
                        }
                    } else FileOutputStream(tempFile).use { it.write(json.toByteArray()) }
                    val subFolder = if (includeFiles) "Media" else "Meta data only"
                    val relativePath = "${Environment.DIRECTORY_DOCUMENTS}/Fluxsona Backups/$subFolder"

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val contentValues = ContentValues().apply {
                            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                            put(MediaStore.MediaColumns.MIME_TYPE, if (includeFiles) "application/zip" else "application/x-fluxsona")
                            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                        }
                        // Use the generic Files collection for Documents folder
                        val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                        context.contentResolver.insert(collection, contentValues)?.let { uri ->
                            context.contentResolver.openOutputStream(uri).use { outputStream ->
                                tempFile.inputStream().use { input ->
                                    outputStream?.let { input.copyTo(it) }
                                }
                            }
                            withContext(Dispatchers.Main) {
                                errorMessage = "Backup saved to Documents/Fluxsona Backups/$subFolder"
                            }
                        } ?: throw Exception("Failed to create MediaStore entry")
                    } else {
                        val exportDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "Fluxsona Backups/$subFolder")
                        if (!exportDir.exists()) exportDir.mkdirs()
                        val exportFile = File(exportDir, fileName)
                        tempFile.inputStream().use { input -> exportFile.outputStream().use { input.copyTo(it) } }
                        withContext(Dispatchers.Main) { errorMessage = "Backup saved to Documents/Fluxsona Backups/$subFolder" }
                    }
                    tempFile.delete()
                }
            } catch (e: Exception) { errorMessage = context.getString(R.string.msg_export_failed, e.message) }
        }
    }

    fun importData(context: Context, fileUri: Uri) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val fileName = context.contentResolver.query(fileUri, null, null, null, null)?.use { it.moveToFirst(); it.getString(it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)) } ?: "backup"
                    val musicDir = File(context.filesDir, "music").apply { if (!exists()) mkdirs() }
                    val thumbDir = File(context.filesDir, "thumbnails").apply { if (!exists()) mkdirs() }
                    val lyricsDir = File(context.filesDir, "lyrics").apply { if (!exists()) mkdirs() }
                    val json = if (fileName.endsWith(".zip")) {
                        var jsonData = ""
                        context.contentResolver.openInputStream(fileUri).use { ZipInputStream(it).use { zis ->
                            var entry = zis.nextEntry
                            while (entry != null) {
                                when {
                                    entry.name == "data.json" -> jsonData = zis.bufferedReader().readText()
                                    entry.name.startsWith("music/") -> File(musicDir, entry.name.removePrefix("music/")).outputStream().use { zis.copyTo(it) }
                                    entry.name.startsWith("thumbnails/") -> File(thumbDir, entry.name.removePrefix("thumbnails/")).outputStream().use { zis.copyTo(it) }
                                    entry.name.startsWith("lyrics/") -> File(lyricsDir, entry.name.removePrefix("lyrics/")).outputStream().use { zis.copyTo(it) }
                                }
                                zis.closeEntry(); entry = zis.nextEntry
                            }
                        } }
                        jsonData
                    } else context.contentResolver.openInputStream(fileUri)?.bufferedReader()?.use { it.readText() } ?: ""
                    val data: Map<String, JsonElement> = Gson().fromJson(json, object : TypeToken<Map<String, JsonElement>>() {}.type)
                    val importedSongs: List<Song> = Gson().fromJson(data["songs"], object : TypeToken<List<Song>>() {}.type)
                    val importedTags: List<Tag> = Gson().fromJson(data["tags"], object : TypeToken<List<Tag>>() {}.type)
                    val finalSongs = importedSongs.map { song ->
                        val audioFile = File(musicDir, File(song.localAudioPath ?: "").name); val thumbFile = File(thumbDir, File(song.localThumbnailPath ?: "").name); val lyricsFile = File(lyricsDir, File(song.localLyricsPath ?: "").name)
                        val audioExists = audioFile.exists() && song.localAudioPath != null; val thumbExists = thumbFile.exists() && song.localThumbnailPath != null; val lyricsExists = lyricsFile.exists() && song.localLyricsPath != null
                        song.copy(localAudioPath = if (audioExists) audioFile.absolutePath else null, localThumbnailPath = if (thumbExists) thumbFile.absolutePath else null, localLyricsPath = if (lyricsExists) lyricsFile.absolutePath else null, cacheStatus = song.cacheStatus.copy(hasAudio = audioExists, hasThumbnail = thumbExists, hasLyrics = lyricsExists))
                    }
                    repository.insertSongs(finalSongs); importedTags.forEach { try { repository.insertTag(it.name, it.category) } catch (e: Exception) {} }
                    withContext(Dispatchers.Main) { errorMessage = context.getString(R.string.msg_import_success, finalSongs.size, importedTags.size) }
                }
            } catch (e: Exception) { errorMessage = context.getString(R.string.msg_import_failed, e.message) }
        }
    }

    override fun onCleared() { super.onCleared(); controllerFuture?.let { MediaController.releaseFuture(it) } }
}
