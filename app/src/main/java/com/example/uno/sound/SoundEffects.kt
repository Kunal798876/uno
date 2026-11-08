package com.example.uno.sound

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlinx.coroutines.*
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

class SoundEffects(context: Context) {

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        vibratorManager?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    private var toneGenerator: ToneGenerator? = try {
        ToneGenerator(AudioManager.STREAM_MUSIC, 70)
    } catch (_: Exception) {
        null
    }

    private var bgmTrack: AudioTrack? = null
    private val soundScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    var isMuted: Boolean = false
        set(value) {
            field = value
            if (value) {
                stopBgm()
            } else if (isMusicOn) {
                startBgm()
            }
        }

    var isMusicOn: Boolean = true
        set(value) {
            field = value
            if (value && !isMuted) startBgm() else stopBgm()
        }

    init {
        // Pre-generate modern casual game BGM buffer in background
        soundScope.launch {
            try {
                prepareBgmTrack()
                if (isMusicOn && !isMuted) {
                    startBgm()
                }
            } catch (_: Exception) {
                // AudioTrack fallback
            }
        }
    }

    /**
     * Synthesizes a modern upbeat casual game soundtrack with warm vibraphone chords,
     * bouncy walking bass, and playful melody (UNO Mobile / Casual Arcade style).
     */
    private fun prepareBgmTrack() {
        if (bgmTrack != null) return

        val sampleRate = 22050
        val bpm = 116.0
        val beatDuration = 60.0 / bpm
        val totalBeats = 16 // 4 bars of 4/4
        val totalSeconds = totalBeats * beatDuration
        val numSamples = (totalSeconds * sampleRate).toInt()
        val pcm = ShortArray(numSamples)

        // Musical frequencies (Hz)
        val noteFreqs = mapOf(
            "C2" to 65.41, "E2" to 82.41, "F2" to 87.31, "G2" to 98.00, "A2" to 110.00,
            "C3" to 130.81, "E3" to 164.81, "F3" to 174.61, "G3" to 196.00, "A3" to 220.00,
            "B3" to 246.94, "C4" to 261.63, "D4" to 293.66, "E4" to 329.63, "F4" to 349.23,
            "G4" to 392.00, "A4" to 440.00, "B4" to 493.88, "C5" to 523.25, "D5" to 587.33,
            "E5" to 659.25, "G5" to 783.99
        )

        // 4-Bar Chord Progression: Cmaj7 -> Gsus4 -> Am7 -> Fadd9
        // Cheerful casual groove notes: (timeInBeats, durationBeats, note, volume, instrument: 0=pluck, 1=bass)
        data class NoteEvent(val beat: Double, val dur: Double, val freq: Double, val vol: Double, val type: Int)
        val events = mutableListOf<NoteEvent>()

        fun addNote(beat: Double, dur: Double, name: String, vol: Double = 0.25, type: Int = 0) {
            noteFreqs[name]?.let { freq ->
                events.add(NoteEvent(beat, dur, freq, vol, type))
            }
        }

        // Bassline (Bouncy casual game synth bass)
        // Bar 1: C
        addNote(0.0, 0.7, "C2", 0.45, 1)
        addNote(1.0, 0.4, "C3", 0.35, 1)
        addNote(2.0, 0.7, "C2", 0.45, 1)
        addNote(3.0, 0.4, "G2", 0.35, 1)
        // Bar 2: G
        addNote(4.0, 0.7, "G2", 0.45, 1)
        addNote(5.0, 0.4, "D3", 0.35, 1)
        addNote(6.0, 0.7, "G2", 0.45, 1)
        addNote(7.0, 0.4, "B2", 0.35, 1)
        // Bar 3: Am
        addNote(8.0, 0.7, "A2", 0.45, 1)
        addNote(9.0, 0.4, "E3", 0.35, 1)
        addNote(10.0, 0.7, "A2", 0.45, 1)
        addNote(11.0, 0.4, "G2", 0.35, 1)
        // Bar 4: F
        addNote(12.0, 0.7, "F2", 0.45, 1)
        addNote(13.0, 0.4, "C3", 0.35, 1)
        addNote(14.0, 0.7, "F2", 0.45, 1)
        addNote(15.0, 0.4, "G2", 0.35, 1)

        // Melodic Chords & Playful Marimba Plucks
        // Bar 1 (C)
        addNote(0.0, 1.5, "C4", 0.22, 0)
        addNote(0.0, 1.5, "E4", 0.22, 0)
        addNote(0.0, 1.5, "G4", 0.22, 0)
        addNote(1.5, 0.8, "G4", 0.28, 0)
        addNote(2.5, 0.8, "E5", 0.32, 0)
        addNote(3.0, 0.8, "D5", 0.28, 0)
        addNote(3.5, 0.5, "C5", 0.25, 0)

        // Bar 2 (G)
        addNote(4.0, 1.5, "B3", 0.20, 0)
        addNote(4.0, 1.5, "D4", 0.20, 0)
        addNote(4.0, 1.5, "G4", 0.22, 0)
        addNote(5.0, 0.8, "D5", 0.28, 0)
        addNote(6.0, 0.8, "B4", 0.26, 0)
        addNote(7.0, 0.8, "G4", 0.24, 0)
        addNote(7.5, 0.5, "A4", 0.25, 0)

        // Bar 3 (Am)
        addNote(8.0, 1.5, "A3", 0.22, 0)
        addNote(8.0, 1.5, "C4", 0.22, 0)
        addNote(8.0, 1.5, "E4", 0.22, 0)
        addNote(9.0, 0.8, "C5", 0.30, 0)
        addNote(10.0, 0.8, "E5", 0.34, 0)
        addNote(11.0, 0.8, "C5", 0.28, 0)
        addNote(11.5, 0.5, "A4", 0.25, 0)

        // Bar 4 (F)
        addNote(12.0, 1.5, "F3", 0.22, 0)
        addNote(12.0, 1.5, "A3", 0.22, 0)
        addNote(12.0, 1.5, "C4", 0.22, 0)
        addNote(13.0, 0.8, "A4", 0.28, 0)
        addNote(14.0, 0.8, "C5", 0.30, 0)
        addNote(14.5, 0.8, "D5", 0.30, 0)
        addNote(15.0, 0.9, "G4", 0.28, 0)

        // Render events into PCM
        val doubleBuffer = DoubleArray(numSamples)

        for (event in events) {
            val startSample = (event.beat * beatDuration * sampleRate).toInt()
            val noteDurationSamples = (event.dur * beatDuration * sampleRate).toInt()
            val endSample = (startSample + noteDurationSamples).coerceAtMost(numSamples)

            val freq = event.freq
            val isBass = event.type == 1

            for (i in startSample until endSample) {
                val t = (i - startSample).toDouble() / sampleRate
                val rel = (i - startSample).toDouble() / noteDurationSamples

                // ADSR envelope: fast attack, warm exponential decay
                val env = if (rel < 0.05) {
                    rel / 0.05
                } else {
                    exp(-rel * if (isBass) 3.5 else 4.0)
                }

                // Waveform: Sine + warm 2nd/3rd harmonics
                val fundamental = sin(2.0 * PI * freq * t)
                val h2 = 0.35 * sin(2.0 * PI * freq * 2.0 * t)
                val h3 = if (isBass) 0.15 * sin(2.0 * PI * freq * 3.0 * t) else 0.08 * sin(2.0 * PI * freq * 3.0 * t)
                val sampleValue = (fundamental + h2 + h3) * env * event.vol

                doubleBuffer[i] += sampleValue
            }
        }

        // Normalize and convert to 16-bit PCM
        var maxPeak = 0.01
        for (v in doubleBuffer) {
            val absV = kotlin.math.abs(v)
            if (absV > maxPeak) maxPeak = absV
        }
        val scale = 26000.0 / maxPeak

        for (i in 0 until numSamples) {
            pcm[i] = (doubleBuffer[i] * scale).toInt().coerceIn(-32767, 32767).toShort()
        }

        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()

        val format = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(sampleRate)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .build()

        val bufferSize = numSamples * 2
        bgmTrack = AudioTrack(
            attributes,
            format,
            bufferSize,
            AudioTrack.MODE_STATIC,
            AudioManager.AUDIO_SESSION_ID_GENERATE
        )

        bgmTrack?.write(pcm, 0, numSamples)
        bgmTrack?.setLoopPoints(0, numSamples, -1)
    }

    fun startBgm() {
        if (isMuted || !isMusicOn) return
        try {
            if (bgmTrack == null) {
                prepareBgmTrack()
            }
            bgmTrack?.let { track ->
                if (track.playState != AudioTrack.PLAYSTATE_PLAYING) {
                    track.play()
                }
            }
        } catch (_: Exception) {}
    }

    fun stopBgm() {
        try {
            bgmTrack?.let { track ->
                if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                    track.pause()
                    track.reloadStaticData()
                }
            }
        } catch (_: Exception) {}
    }

    fun pauseBgm() {
        try {
            bgmTrack?.let { track ->
                if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                    track.pause()
                }
            }
        } catch (_: Exception) {}
    }

    fun resumeBgm() {
        if (!isMuted && isMusicOn) {
            try {
                bgmTrack?.let { track ->
                    if (track.playState != AudioTrack.PLAYSTATE_PLAYING) {
                        track.play()
                    }
                } ?: startBgm()
            } catch (_: Exception) {}
        }
    }

    fun playCardPlay() {
        if (!isMuted) {
            toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 45)
        }
        vibrate(25)
    }

    fun playCardDraw() {
        if (!isMuted) {
            toneGenerator?.startTone(ToneGenerator.TONE_PROP_ACK, 60)
        }
        vibrate(30)
    }

    fun playCardSwoop() {
        if (!isMuted) {
            toneGenerator?.startTone(ToneGenerator.TONE_PROP_PROMPT, 50)
        }
        vibrate(20)
    }

    fun playBuzzer() {
        if (!isMuted) {
            toneGenerator?.startTone(ToneGenerator.TONE_PROP_NACK, 160)
        }
        vibrate(100)
    }

    fun playPenalty() {
        if (!isMuted) {
            toneGenerator?.startTone(ToneGenerator.TONE_CDMA_SOFT_ERROR_LITE, 220)
        }
        vibrate(120)
    }

    fun playTimerTick() {
        if (!isMuted) {
            toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP2, 30)
        }
        vibrate(15)
    }

    fun playUnoCall() {
        if (!isMuted) {
            toneGenerator?.startTone(ToneGenerator.TONE_DTMF_D, 240)
        }
        vibrate(120)
    }

    fun playWinFanfare() {
        if (!isMuted) {
            toneGenerator?.startTone(ToneGenerator.TONE_CDMA_HIGH_L, 450)
        }
        vibrate(200)
    }

    fun playDefeatSound() {
        if (!isMuted) {
            toneGenerator?.startTone(ToneGenerator.TONE_CDMA_LOW_L, 350)
        }
        vibrate(150)
    }

    private fun vibrate(millis: Long) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(millis, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(millis)
            }
        } catch (_: Exception) {
            // Ignore vibration permissions/hardware absences
        }
    }

    fun release() {
        stopBgm()
        try {
            bgmTrack?.release()
            bgmTrack = null
        } catch (_: Exception) {}
        try {
            toneGenerator?.release()
            toneGenerator = null
        } catch (_: Exception) {}
    }
}
