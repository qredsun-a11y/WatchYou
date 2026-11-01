package com.watchyou.media

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.util.Log
import com.watchyou.R
import com.watchyou.util.Logger

class SoundPlayer(private val context: Context) {
    companion object {
        private const val MAX_STREAMS = 1
        private const val SOUND_PRIORITY = 1
        private const val SOUND_QUALITY = 0
        private const val COOLDOWN_MS = 3000L
    }

    private var soundPool: SoundPool? = null
    private var soundId = -1
    private var lastPlayTime = 0L

    init {
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        soundPool = SoundPool.Builder()
            .setMaxStreams(MAX_STREAMS)
            .setAudioAttributes(attributes)
            .build()
        soundPool!!.setOnLoadCompleteListener { _, sampleId, status ->
            if (status == 0) {
                soundId = sampleId
                Logger.d("SoundPlayer", "Sound loaded: $sampleId")
            } else {
                Logger.e("SoundPlayer", "Failed to load sound, status: $status")
            }
        }
        loadSound()
    }

    private fun loadSound() {
        soundPool!!.load(context, R.raw.alert, SOUND_PRIORITY)
    }

    fun playAlert() {
        val now = System.currentTimeMillis()
        if (now - lastPlayTime < COOLDOWN_MS) {
            Logger.d("SoundPlayer", "Sound cooled down, skipping")
            return
        }
        lastPlayTime = now

        if (soundId != -1) {
            soundPool!!.play(soundId, 1f, 1f, SOUND_PRIORITY, 0, 1f)
            Logger.d("SoundPlayer", "Alert sound played")
        } else {
            Logger.w("SoundPlayer", "Sound not loaded yet")
        }
    }

    fun release() {
        soundPool?.release()
        soundPool = null
        soundId = -1
    }
}
