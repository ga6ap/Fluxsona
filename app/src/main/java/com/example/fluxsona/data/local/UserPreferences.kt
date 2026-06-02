package com.example.fluxsona.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class UserPreferences(private val context: Context) {
    companion object {
        val REPEAT_MODE = intPreferencesKey("repeat_mode")
        val SHUFFLE_ENABLED = booleanPreferencesKey("shuffle_enabled")
        val LAST_PLAYED_SONG_ID = stringPreferencesKey("last_played_song_id")
        val PLAYBACK_SPEED = floatPreferencesKey("playback_speed")
        val DARK_MODE = stringPreferencesKey("dark_mode")
        val SORT_MODE = stringPreferencesKey("sort_mode")
        val COOKIES_FILE_PATH = stringPreferencesKey("cookies_file_path")
        val SKIP_FORWARD_SECONDS = intPreferencesKey("skip_forward_seconds")
        val SKIP_BACKWARD_SECONDS = intPreferencesKey("skip_backward_seconds")
        val VOLUME = floatPreferencesKey("volume")
        val SOUND_OPTION = stringPreferencesKey("sound_option")
        val LYRICS_TEXT_SIZE = floatPreferencesKey("lyrics_text_size")
    }

    val lyricsTextSize: Flow<Float> = context.dataStore.data.map { it[LYRICS_TEXT_SIZE] ?: 18f }

    suspend fun saveLyricsTextSize(size: Float) {
        context.dataStore.edit { it[LYRICS_TEXT_SIZE] = size }
    }

    val volume: Flow<Float> = context.dataStore.data.map { it[VOLUME] ?: 1.0f }
    val soundOption: Flow<String> = context.dataStore.data.map { it[SOUND_OPTION] ?: "Default" }

    suspend fun saveVolume(value: Float) {
        context.dataStore.edit { it[VOLUME] = value }
    }

    suspend fun saveSoundOption(option: String) {
        context.dataStore.edit { it[SOUND_OPTION] = option }
    }

    val skipForwardSeconds: Flow<Int> = context.dataStore.data.map { it[SKIP_FORWARD_SECONDS] ?: 10 }
    val skipBackwardSeconds: Flow<Int> = context.dataStore.data.map { it[SKIP_BACKWARD_SECONDS] ?: 10 }

    suspend fun saveSkipForwardSeconds(seconds: Int) {
        context.dataStore.edit { it[SKIP_FORWARD_SECONDS] = seconds }
    }

    suspend fun saveSkipBackwardSeconds(seconds: Int) {
        context.dataStore.edit { it[SKIP_BACKWARD_SECONDS] = seconds }
    }

    val darkMode: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[DARK_MODE] ?: "system" // "system", "dark", "light"
    }

    val sortMode: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[SORT_MODE] ?: "DATE_ADDED_DESC"
    }

    val cookiesFilePath: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[COOKIES_FILE_PATH]
    }

    val repeatMode: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[REPEAT_MODE] ?: 0 // Player.REPEAT_MODE_OFF
    }

    val shuffleEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[SHUFFLE_ENABLED] ?: false
    }

    val lastPlayedSongId: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[LAST_PLAYED_SONG_ID]
    }

    val playbackSpeed: Flow<Float> = context.dataStore.data.map { preferences ->
        preferences[PLAYBACK_SPEED] ?: 1.0f
    }

    suspend fun saveRepeatMode(mode: Int) {
        context.dataStore.edit { preferences ->
            preferences[REPEAT_MODE] = mode
        }
    }

    suspend fun saveShuffleEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[SHUFFLE_ENABLED] = enabled
        }
    }

    suspend fun saveLastPlayedSongId(songId: String) {
        context.dataStore.edit { preferences ->
            preferences[LAST_PLAYED_SONG_ID] = songId
        }
    }

    suspend fun savePlaybackSpeed(speed: Float) {
        context.dataStore.edit { preferences ->
            preferences[PLAYBACK_SPEED] = speed
        }
    }

    suspend fun saveDarkMode(mode: String) {
        context.dataStore.edit { preferences ->
            preferences[DARK_MODE] = mode
        }
    }

    suspend fun saveSortMode(mode: String) {
        context.dataStore.edit { preferences ->
            preferences[SORT_MODE] = mode
        }
    }

    suspend fun saveCookiesFilePath(path: String?) {
        context.dataStore.edit { preferences ->
            if (path == null) {
                preferences.remove(COOKIES_FILE_PATH)
            } else {
                preferences[COOKIES_FILE_PATH] = path
            }
        }
    }
}
