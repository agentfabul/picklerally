package com.picklerally.app.game

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.min
import kotlin.math.sin

class SoundFxEngine {
    private enum class Wave { SINE, TRIANGLE, SQUARE, SAWTOOTH }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    var isMuted: Boolean = false

    fun playMenuClick() {
        if (isMuted) return
        playTone(Wave.TRIANGLE, 540f, 360f, 0.055f, 0.22f)
    }

    fun playEnterCourt() {
        if (isMuted) return
        playNotes(Wave.TRIANGLE, listOf(392f, 523.25f, 659.25f), 0.055f, 0.16f, 0.28f)
    }

    fun playPaddleHit(shotType: ShotType, isBot: Boolean) {
        if (isMuted) return
        val baseFreq = when (shotType) {
            ShotType.DINK -> 420f
            ShotType.LOB -> 510f
            ShotType.SERVE -> 470f
            ShotType.DRIVE -> 660f
        }
        val freq = if (isBot) baseFreq * 0.88f else baseFreq
        val vol = if (shotType == ShotType.DRIVE) 0.38f else 0.28f
        val wave = if (shotType == ShotType.DRIVE) Wave.SQUARE else Wave.TRIANGLE
        playTone(wave, freq, freq * 0.52f, 0.075f, vol)
    }

    fun playBounce(zSpeed: Float) {
        if (isMuted || zSpeed < 2f) return
        val vol = min(0.28f, 0.06f + zSpeed * 0.01f)
        playTone(Wave.SINE, 320f, 140f, 0.05f, vol)
    }

    fun playPointEnd(reason: String) {
        if (isMuted) return
        val startFreq = if (reason.contains("Net")) 190f else 280f
        playTone(Wave.SAWTOOTH, startFreq, 125f, 0.2f, 0.22f)
    }

    fun playPointScored(scoredByLocal: Boolean) {
        if (isMuted) return
        val notes = if (scoredByLocal) listOf(523.25f, 659.25f) else listOf(330f, 247f)
        playNotes(Wave.TRIANGLE, notes, 0.06f, 0.14f, 0.26f)
    }

    fun playMatchVictory(localWon: Boolean) {
        if (isMuted) return
        val notes = if (localWon) listOf(523.25f, 659.25f, 783.99f, 1046.5f) else listOf(392f, 349.23f, 293.66f)
        playNotes(Wave.TRIANGLE, notes, 0.085f, 0.22f, 0.30f)
    }

    private fun playTone(
        wave: Wave,
        startFreq: Float,
        endFreq: Float,
        durationSec: Float,
        peakVolume: Float,
    ) {
        scope.launch {
            val sampleRate = 22050
            val count = (sampleRate * durationSec).toInt().coerceAtLeast(1)
            val buffer = ShortArray(count)
            var phase = 0.0
            val ratio = (endFreq / startFreq).toDouble()
            for (i in 0 until count) {
                val t = i.toDouble() / count
                val freq = startFreq * exp(t * ln(ratio))
                phase += 2.0 * PI * freq / sampleRate
                val env = if (t < 0.08) t / 0.08 else (1.0 - t) / 0.92
                val raw = sampleWave(wave, phase)
                buffer[i] = (raw * env * peakVolume * Short.MAX_VALUE).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }
            emitPcm(buffer, sampleRate)
        }
    }

    private fun playNotes(
        wave: Wave,
        freqs: List<Float>,
        stepDelaySec: Float,
        noteDurSec: Float,
        peakVolume: Float,
    ) {
        scope.launch {
            val sampleRate = 22050
            val totalSec = stepDelaySec * (freqs.size - 1) + noteDurSec
            val count = (sampleRate * totalSec).toInt().coerceAtLeast(1)
            val mix = DoubleArray(count)

            freqs.forEachIndexed { idx, freq ->
                val startSample = (idx * stepDelaySec * sampleRate).toInt()
                val noteSamples = (noteDurSec * sampleRate).toInt().coerceAtLeast(1)
                var phase = 0.0
                for (j in 0 until noteSamples) {
                    val outIdx = startSample + j
                    if (outIdx >= count) break
                    val t = j.toDouble() / noteSamples
                    phase += 2.0 * PI * freq / sampleRate
                    val env = if (t < 0.08) t / 0.08 else (1.0 - t) / 0.92
                    mix[outIdx] += sampleWave(wave, phase) * env * peakVolume
                }
            }

            val buffer = ShortArray(count) { i ->
                (mix[i] * Short.MAX_VALUE).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }
            emitPcm(buffer, sampleRate)
        }
    }

    private fun sampleWave(wave: Wave, phase: Double): Double {
        val norm = (phase / (2.0 * PI)) % 1.0
        return when (wave) {
            Wave.SINE -> sin(phase)
            Wave.TRIANGLE -> 1.0 - 4.0 * kotlin.math.abs(norm - 0.5)
            Wave.SQUARE -> if (norm < 0.5) 1.0 else -1.0
            Wave.SAWTOOTH -> 2.0 * norm - 1.0
        }
    }

    private fun emitPcm(samples: ShortArray, sampleRate: Int) {
        runCatching {
            val byteLen = samples.size * 2
            val minBuf = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
            ).coerceAtLeast(byteLen)
            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build(),
                )
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(minBuf)
                .build()
            track.write(samples, 0, samples.size)
            track.play()
            Thread.sleep((samples.size * 1000L / sampleRate) + 40L)
            track.stop()
            track.release()
        }
    }
}
