package com.example.fluxsona.di

import android.content.Context
import androidx.room.Room
import com.example.fluxsona.data.local.MusicDatabase
import com.example.fluxsona.data.local.UserPreferences
import com.example.fluxsona.data.repository.MusicRepository

class AppContainer(context: Context) {
    private val database: MusicDatabase by lazy {
        Room.databaseBuilder(
            context,
            MusicDatabase::class.java,
            MusicDatabase.DATABASE_NAME
        ).build()
    }

    private val userPreferences: UserPreferences by lazy {
        UserPreferences(context)
    }

    val repository: MusicRepository by lazy {
        MusicRepository(database, userPreferences)
    }
}
