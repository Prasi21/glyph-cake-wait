package com.nothinglondon.countdown

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

class DrumSfx {

    private val track: AudioTrack
    private val samples: ShortArray = synthKick()

    init {
        val bufferSize = samples.size * BYTES_PER_SAMPLE
        track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
        track.write(samples, 0, samples.size)
    }

    fun play() {
        try {
            if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                track.stop()
            }
            track.reloadStaticData()
            track.play()
        } catch (t: Throwable) {
            Log.w(TAG, "Unable to play drum", t)
        }
    }

    fun release() {
        try {
            track.stop()
        } catch (_: Throwable) {
        }
        try {
            track.release()
        } catch (_: Throwable) {
        }
    }

    private fun synthKick(): ShortArray {
        val count = (SAMPLE_RATE * DURATION_SECONDS).toInt()
        val out = ShortArray(count)
        var phase = 0.0
        for (i in 0 until count) {
            val t = i.toDouble() / SAMPLE_RATE
            val freq = END_FREQ_HZ + (START_FREQ_HZ - END_FREQ_HZ) * exp(-t * PITCH_DECAY)
            phase += 2.0 * PI * freq / SAMPLE_RATE
            val envelope = exp(-t * AMP_DECAY)
            out[i] = (sin(phase) * envelope * AMPLITUDE * Short.MAX_VALUE)
                .toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                .toShort()
        }
        return out
    }

    private companion object {
        private val TAG = DrumSfx::class.java.simpleName
        private const val SAMPLE_RATE = 44100
        private const val BYTES_PER_SAMPLE = 2
        private const val DURATION_SECONDS = 0.2
        private const val START_FREQ_HZ = 170.0
        private const val END_FREQ_HZ = 45.0
        private const val PITCH_DECAY = 20.0
        private const val AMP_DECAY = 24.0
        private const val AMPLITUDE = 0.9
    }
}
