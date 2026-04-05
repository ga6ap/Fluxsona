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
}
