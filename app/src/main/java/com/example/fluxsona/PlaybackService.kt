package com.example.fluxsona

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.common.MediaItem
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

class PlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null
    
    private var bassBoost: BassBoost? = null
    private var equalizer: Equalizer? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(androidx.media3.common.AudioAttributes.DEFAULT, true)
            .setHandleAudioBecomingNoisy(true)
            .build()
            
        mediaSession = MediaSession.Builder(this, player)
            .setCallback(object : MediaSession.Callback {
                override fun onAddMediaItems(
                    mediaSession: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    mediaItems: List<MediaItem>
                ): ListenableFuture<List<MediaItem>> {
                    val updatedItems = mediaItems.map { item ->
                        item.buildUpon()
                            .setUri(item.requestMetadata.mediaUri ?: item.localConfiguration?.uri)
                            .build()
                    }
                    return Futures.immediateFuture(updatedItems)
                }
            })
            .build()
            
        // Initialize effects
        try {
            val audioSessionId = player.audioSessionId
            bassBoost = BassBoost(0, audioSessionId)
            equalizer = Equalizer(0, audioSessionId)
            loudnessEnhancer = LoudnessEnhancer(audioSessionId)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action != null) {
            when (action) {
                "SET_SOUND_DEFAULT" -> applyDefault()
                "SET_SOUND_BASS_BOOST" -> applyBassBoost()
                "SET_SOUND_VOICE_BOOST" -> applyVoiceBoost()
                "SET_VOLUME_BOOST" -> {
                    val volume = intent.getFloatExtra("VOLUME", 1.0f)
                    applyVolumeWithBoost(volume)
                }
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun applyDefault() {
        bassBoost?.setEnabled(false)
        equalizer?.setEnabled(false)
    }

    private fun applyBassBoost() {
        bassBoost?.let {
            it.setStrength(1000.toShort())
            it.setEnabled(true)
        }
        equalizer?.setEnabled(false)
    }

    private fun applyVoiceBoost() {
        bassBoost?.setEnabled(false)
        equalizer?.let { eq ->
            eq.setEnabled(true)
            val numBands = eq.numberOfBands
            for (i in 0 until numBands) {
                val centerFreq = eq.getCenterFreq(i.toShort())
                // Boost middle frequencies (human voice range usually around 500Hz - 4kHz)
                if (centerFreq in 500000..4000000) {
                    eq.setBandLevel(i.toShort(), 600.toShort()) // +6dB
                } else {
                    eq.setBandLevel(i.toShort(), 0.toShort())
                }
            }
        }
    }

    private fun applyVolumeWithBoost(volume: Float) {
        val player = mediaSession?.player ?: return
        try {
            if (volume <= 1.0f) {
                player.volume = volume
                loudnessEnhancer?.let {
                    if (it.enabled) it.setEnabled(false)
                }
            } else {
                player.volume = 1.0f
                loudnessEnhancer?.let {
                    // Boost up to 5.0 (500%) -> +20dB (2000mB)
                    // (volume - 1.0) / 4.0 * 2000
                    val gain = ((volume.coerceAtMost(5.0f) - 1.0f) / 4.0f * 2000f).toInt()
                    it.setTargetGain(gain)
                    if (!it.enabled) it.setEnabled(true)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player != null) {
            if (!player.playWhenReady || player.mediaItemCount == 0) {
                stopSelf()
            }
        }
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}
