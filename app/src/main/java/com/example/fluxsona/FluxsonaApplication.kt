package com.example.fluxsona

import android.app.Application
import android.util.Log
import com.example.fluxsona.di.AppContainer
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.ffmpeg.FFmpeg

class FluxsonaApplication : Application() {
    lateinit var container: AppContainer

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)

        Thread {
            try {
                YoutubeDL.getInstance().init(this)
                FFmpeg.getInstance().init(this)
                Log.d("Fluxsona", "yt-dlp version: ${YoutubeDL.getInstance().version(this)}")
                // Update yt-dlp to the latest version to avoid 403 errors
                val updateResult = YoutubeDL.getInstance().updateYoutubeDL(this)
                Log.d("Fluxsona", "yt-dlp update result: $updateResult")
            } catch (e: Exception) {
                Log.e("Fluxsona", "Failed to initialize or update yt-dlp", e)
            }
        }.start()
    }
}
