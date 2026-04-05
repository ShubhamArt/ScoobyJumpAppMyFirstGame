package com.game.scoobyjump

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import android.util.Log

class AudioManager(private val context: Context) {
    private var soundPool: SoundPool? = null
    private var mediaPlayer: MediaPlayer? = null
    private var summaryMediaPlayer: MediaPlayer? = null
    private var sparkleMediaPlayer: MediaPlayer? = null
    
    private var bgmWasPlaying = false
    private var summaryWasPlaying = false
    private var sparkleWasPlaying = false
    
    private var jumpSoundId = -1
    private var crashSoundId = -1
    private var gameOverFailSoundId = -1
    private var powerUpSoundId = -1
    private var landSoundId = -1
    private var shopOpenSoundId = -1
    
    var isSoundEnabled = true
        set(value) {
            field = value
            if (!value) {
                mediaPlayer?.pause()
                summaryMediaPlayer?.pause()
                sparkleMediaPlayer?.pause()
            }
        }

    init {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
            
        soundPool = SoundPool.Builder()
            .setMaxStreams(5)
            .setAudioAttributes(audioAttributes)
            .build()
            
        try {
            // Load sounds from res/raw natively
            jumpSoundId = soundPool?.load(context, R.raw.jump, 1) ?: -1
            crashSoundId = soundPool?.load(context, R.raw.gameover, 1) ?: -1
            gameOverFailSoundId = soundPool?.load(context, R.raw.gameover_fail, 1) ?: -1
            powerUpSoundId = soundPool?.load(context, R.raw.coin, 1) ?: -1
            landSoundId = soundPool?.load(context, R.raw.jump, 1) ?: -1 // Will pitch-down for Tap effect
            shopOpenSoundId = soundPool?.load(context, R.raw.shop_open, 1) ?: -1
        } catch (e: Exception) {
            Log.e("AudioManager", "Failed to load SFX", e)
        }
            
        // Initialize Background Music properly
        try {
            mediaPlayer = MediaPlayer.create(context, R.raw.bgm)
            mediaPlayer?.isLooping = true
            mediaPlayer?.setVolume(0.3f, 0.3f) // keep volume balanced and low
            
            summaryMediaPlayer = MediaPlayer.create(context, R.raw.gameover_loop)
            summaryMediaPlayer?.isLooping = true
            summaryMediaPlayer?.setVolume(0.4f, 0.4f)

            sparkleMediaPlayer = MediaPlayer.create(context, R.raw.ambient_sparkle)
            sparkleMediaPlayer?.isLooping = true
            sparkleMediaPlayer?.setVolume(0.3f, 0.3f)
        } catch (e: Exception) {
            Log.e("AudioManager", "Failed to load BGMs", e)
        }
            
        Log.d("AudioManager", "Initialized Audio System")
    }

    fun playJump() {
        if (!isSoundEnabled || jumpSoundId == -1) return
        soundPool?.play(jumpSoundId, 0.5f, 0.5f, 1, 0, 1f)
    }

    fun playCrash() {
        if (!isSoundEnabled || crashSoundId == -1) return
        soundPool?.play(crashSoundId, 0.8f, 0.8f, 1, 0, 1f)
    }

    fun playPowerUp() {
        if (!isSoundEnabled || powerUpSoundId == -1) return
        soundPool?.play(powerUpSoundId, 0.7f, 0.7f, 1, 0, 1f)
    }

    fun playLand() {
        if (!isSoundEnabled || landSoundId == -1) return
        // Pitch down to 0.5f to give it a dull "tap" sound instead of a high bounce
        soundPool?.play(landSoundId, 0.3f, 0.3f, 1, 0, 0.5f)
    }

    fun playShopOpen() {
        if (!isSoundEnabled || shopOpenSoundId == -1) return
        soundPool?.play(shopOpenSoundId, 0.8f, 0.8f, 1, 0, 1f)
    }

    fun playWhoosh() {
        if (!isSoundEnabled || jumpSoundId == -1) return
        // Pitch down jump sound to emulate a 'whoosh' or 'wind up'
        soundPool?.play(jumpSoundId, 0.4f, 0.4f, 1, 0, 0.7f)
    }

    fun playClick() {
        if (!isSoundEnabled || landSoundId == -1) return
        soundPool?.play(landSoundId, 0.3f, 0.3f, 1, 0, 0.4f)
    }

    fun playChaChing() {
        if (!isSoundEnabled || powerUpSoundId == -1) return
        soundPool?.play(powerUpSoundId, 0.9f, 0.9f, 1, 0, 1.2f)
    }

    fun playTick() {
        if (!isSoundEnabled || landSoundId == -1) return
        soundPool?.play(landSoundId, 0.4f, 0.4f, 1, 0, 1.3f)
    }

    fun playDing() {
        if (!isSoundEnabled || powerUpSoundId == -1) return
        soundPool?.play(powerUpSoundId, 0.8f, 0.8f, 1, 0, 1.5f)
    }

    fun pauseBGM() {
        mediaPlayer?.pause()
    }

    fun playGameOverSequence() {
        if (!isSoundEnabled) return
        mediaPlayer?.pause()
        soundPool?.play(gameOverFailSoundId, 1.0f, 1.0f, 1, 0, 1f)
        summaryMediaPlayer?.seekTo(0)
        summaryMediaPlayer?.start()
    }

    fun pauseGameOverSequence() {
        summaryMediaPlayer?.pause()
    }

    fun resumeBGM() {
        if (!isSoundEnabled) return
        mediaPlayer?.start()
    }

    fun startAmbientSparkle() {
        if (!isSoundEnabled) return
        sparkleMediaPlayer?.start()
    }

    fun stopAmbientSparkle() {
        sparkleMediaPlayer?.pause()
        sparkleMediaPlayer?.seekTo(0)
    }

    fun onAppPaused() {
        bgmWasPlaying = mediaPlayer?.isPlaying == true
        summaryWasPlaying = summaryMediaPlayer?.isPlaying == true
        sparkleWasPlaying = sparkleMediaPlayer?.isPlaying == true

        mediaPlayer?.pause()
        summaryMediaPlayer?.pause()
        sparkleMediaPlayer?.pause()
    }

    fun onAppResumed() {
        if (!isSoundEnabled) return
        if (bgmWasPlaying) mediaPlayer?.start()
        if (summaryWasPlaying) summaryMediaPlayer?.start()
        if (sparkleWasPlaying) sparkleMediaPlayer?.start()
    }

    fun release() {
        soundPool?.release()
        soundPool = null
        mediaPlayer?.release()
        mediaPlayer = null
        summaryMediaPlayer?.release()
        summaryMediaPlayer = null
        sparkleMediaPlayer?.release()
        sparkleMediaPlayer = null
    }
}
