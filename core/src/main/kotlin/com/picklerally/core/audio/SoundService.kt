package com.picklerally.core.audio

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.audio.AudioDevice
import com.picklerally.core.physics.ShotType
import com.picklerally.core.rules.FaultReason
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

interface SoundService {
    var isMuted: Boolean
    fun playMenuClick()
    fun playEnterCourt()
    fun playPaddleHit(shotType: ShotType, isOpponent: Boolean)
    fun playBounce(zSpeed: Float)
    fun playFault(reason: FaultReason)
    fun playPointScored(scoredByLocal: Boolean)
    fun playMatchWin(localWon: Boolean)
    fun dispose()
}

/**
 * Pure libGDX procedural synthesizer backed by [AudioDevice] on a background coroutine
 * so paddle pops, bounces, fault buzzers, and win chimes work out of the box without
 * requiring external binary sound files.
 */
class GdxSoundService : SoundService {
    override var isMuted: Boolean = false

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val sampleRate = 22050
    private val device: AudioDevice? by lazy {
        runCatching { Gdx.audio?.newAudioDevice(sampleRate, true) }.getOrNull()
    }

    override fun playMenuClick() = enqueueTone(540f, 360f, 0.045f, 0.16f)

    override fun playEnterCourt() {
        enqueueArpeggio(listOf(392f, 523.25f, 659.25f), 0.07f, 0.18f)
    }

    override fun playPaddleHit(shotType: ShotType, isOpponent: Boolean) {
        val base = when (shotType) {
            ShotType.DINK -> 420f
            ShotType.LOB -> 510f
            ShotType.SERVE -> 470f
            ShotType.DRIVE -> 660f
        }
        val freq = if (isOpponent) base * 0.88f else base
        val gain = if (shotType == ShotType.DRIVE) 0.28f else 0.18f
        enqueueTone(freq, freq * 0.52f, 0.07f, gain)
    }

    override fun playBounce(zSpeed: Float) {
        if (zSpeed < 2.0f) return
        val gain = (0.06f + zSpeed * 0.008f).coerceAtMost(0.18f)
        enqueueTone(310f, 140f, 0.045f, gain)
    }

    override fun playFault(reason: FaultReason) {
        val start = if (reason == FaultReason.INTO_NET) 190f else 270f
        enqueueTone(start, 115f, 0.18f, 0.22f)
    }

    override fun playPointScored(scoredByLocal: Boolean) {
        if (scoredByLocal) {
            enqueueArpeggio(listOf(523.25f, 659.25f), 0.065f, 0.18f)
        } else {
            enqueueTone(330f, 240f, 0.12f, 0.15f)
        }
    }

    override fun playMatchWin(localWon: Boolean) {
        val notes = if (localWon) {
            listOf(523.25f, 659.25f, 783.99f, 1046.5f)
        } else {
            listOf(392f, 349.23f, 293.66f)
        }
        enqueueArpeggio(notes, 0.09f, 0.22f)
    }

    private fun enqueueTone(startFreq: Float, endFreq: Float, durationSec: Float, peakAmp: Float) {
        if (isMuted) return
        scope.launch {
            val out = device ?: return@launch
            val totalSamples = (sampleRate * durationSec).toInt().coerceAtLeast(64)
            val pcm = ShortArray(totalSamples)
            var phase = 0.0
            for (i in 0 until totalSamples) {
                val t = i.toFloat() / totalSamples
                val freq = startFreq + (endFreq - startFreq) * t
                phase += 2.0 * PI * freq / sampleRate
                val env = exp(-t * 4.5f)
                val sample = sin(phase) * peakAmp * env * Short.MAX_VALUE
                pcm[i] = sample.toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }
            runCatching { out.writeSamples(pcm, 0, pcm.size) }
        }
    }

    private fun enqueueArpeggio(freqs: List<Float>, noteDurSec: Float, peakAmp: Float) {
        if (isMuted) return
        scope.launch {
            val out = device ?: return@launch
            val samplesPerNote = (sampleRate * noteDurSec).toInt().coerceAtLeast(64)
            val pcm = ShortArray(samplesPerNote * freqs.size)
            var offset = 0
            for (freq in freqs) {
                var phase = 0.0
                for (i in 0 until samplesPerNote) {
                    val t = i.toFloat() / samplesPerNote
                    phase += 2.0 * PI * freq / sampleRate
                    val env = exp(-t * 3.8f)
                    val sample = sin(phase) * peakAmp * env * Short.MAX_VALUE
                    pcm[offset + i] = sample.toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                }
                offset += samplesPerNote
            }
            runCatching { out.writeSamples(pcm, 0, pcm.size) }
        }
    }

    override fun dispose() {
        scope.cancel()
        runCatching { device?.dispose() }
    }
}
