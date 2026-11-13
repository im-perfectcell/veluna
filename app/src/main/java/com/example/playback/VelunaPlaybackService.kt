package com.example.playback

import android.app.PendingIntent
import android.content.Intent
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.example.MainActivity
import kotlin.math.roundToInt

class VelunaPlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private lateinit var player: ExoPlayer
    private var equalizer: Equalizer? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        instance = this

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        player = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .build()

        player.addListener(object : Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                attachAudioEffects(audioSessionId)
            }
        })

        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(pendingIntent)
            .build()

        attachAudioEffects(player.audioSessionId)
        onServiceReady?.invoke(player)
    }

    private fun attachAudioEffects(sessionId: Int) {
        if (sessionId == C.AUDIO_SESSION_ID_UNSET || sessionId == 0) return
        runCatching {
            equalizer?.release()
            equalizer = Equalizer(0, sessionId)
        }
        runCatching {
            loudnessEnhancer?.release()
            loudnessEnhancer = LoudnessEnhancer(sessionId)
        }
        applyEqualizerState(lastEqEnabled, lastEqBands)
        applyLoudnessNormalization(lastLoudnessDb)
    }

    fun applyEqualizerState(enabled: Boolean, bandsDb10: List<Float>) {
        lastEqEnabled = enabled
        lastEqBands = bandsDb10
        val eq = equalizer ?: return
        runCatching {
            eq.enabled = enabled
            if (!enabled) return
            val numBands = eq.numberOfBands.toInt()
            val range = eq.bandLevelRange
            val minMb = range[0].toFloat()
            val maxMb = range[1].toFloat()
            for (bandIdx in 0 until numBands) {
                val centerHz = eq.getCenterFreq(bandIdx.toShort()) / 1000
                val uiIdx = when {
                    centerHz <= 45 -> 0
                    centerHz <= 90 -> 1
                    centerHz <= 180 -> 2
                    centerHz <= 350 -> 3
                    centerHz <= 750 -> 4
                    centerHz <= 1500 -> 5
                    centerHz <= 3000 -> 6
                    centerHz <= 6000 -> 7
                    centerHz <= 12000 -> 8
                    else -> 9
                }
                val db = bandsDb10.getOrElse(uiIdx) { 0f }
                val targetMb = (db * 100f).coerceIn(minMb, maxMb).roundToInt().toShort()
                eq.setBandLevel(bandIdx.toShort(), targetMb)
            }
        }
    }

    fun applyLoudnessNormalization(loudnessDb: Double?) {
        lastLoudnessDb = loudnessDb
        val enhancer = loudnessEnhancer ?: return
        runCatching {
            if (loudnessDb == null || !loudnessNormEnabled) {
                enhancer.enabled = false
            } else {
                // Target -14 LUFS roughly
                val gainDb = (-14.0 - loudnessDb).coerceIn(-6.0, 8.0)
                if (gainDb > 0.5) {
                    enhancer.setTargetGain((gainDb * 100).roundToInt())
                    enhancer.enabled = true
                } else {
                    enhancer.enabled = false
                }
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        val p = mediaSession?.player
        if (p == null || !p.playWhenReady || p.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        instance = null
        runCatching { equalizer?.release() }
        runCatching { loudnessEnhancer?.release() }
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }

    companion object {
        @Volatile
        var instance: VelunaPlaybackService? = null
        var onServiceReady: ((ExoPlayer) -> Unit)? = null
        var lastEqEnabled: Boolean = false
        var lastEqBands: List<Float> = List(10) { 0f }
        var lastLoudnessDb: Double? = null
        var loudnessNormEnabled: Boolean = true
    }
}
