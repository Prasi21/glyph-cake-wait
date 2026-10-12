package com.nothinglondon.countdown

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTimestamp
import android.media.AudioTrack
import android.util.Log
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

private const val SAMPLE_RATE = 44100
private const val BYTES_PER_SAMPLE = 2

class Metronome(bpm: Double, subdivisionsPerBeat: Int) {

    private val periodFrames: Int = (SAMPLE_RATE * 60.0 / bpm).toInt()
    private val pattern: ShortArray
    private val track: AudioTrack

    @Volatile
    private var feeding = false
    private var feeder: Thread? = null

    init {
        pattern = ShortArray(periodFrames)
        mixInto(pattern, 0, synthDrum())
        mixInto(pattern, periodFrames / subdivisionsPerBeat, synthCymbal())

        val minBuffer = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val bufferBytes = maxOf(minBuffer, periodFrames * BYTES_PER_SAMPLE * BUFFER_PERIODS)

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
            .setBufferSizeInBytes(bufferBytes)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
    }

    fun start() {
        try {
            track.flush()
            track.setVolume(1f)
            feeding = true
            track.play()
            feeder = Thread {
                while (feeding) {
                    if (track.write(pattern, 0, pattern.size) < 0) {
                        break
                    }
                }
            }.apply {
                name = "metronome-feeder"
                priority = Thread.MAX_PRIORITY
                start()
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Unable to start metronome", t)
        }
    }

    /**
     * Elapsed time according to the audio hardware clock, derived from the
     * number of frames already presented to the output. Because both the beat
     * sounds and the display read from this same clock, they line up.
     */
    fun elapsedSeconds(): Double = audibleFrames().toDouble() / SAMPLE_RATE

    /**
     * The audio frame currently being heard, derived from the presentation
     * timestamp. This compensates for the mixer buffer that makes the playback
     * head run ahead of the output.
     */
    private fun audibleFrames(): Long {
        val timestamp = AudioTimestamp()
        if (track.getTimestamp(timestamp)) {
            val nanosSincePresented = System.nanoTime() - timestamp.nanoTime
            return timestamp.framePosition + nanosSincePresented * SAMPLE_RATE / NANOS_PER_SECOND
        }
        return framesPlayed()
    }

    private fun framesPlayed(): Long = track.playbackHeadPosition.toLong() and 0xFFFFFFFFL

    fun stop() {
        feeding = false
        try {
            track.pause()
            track.flush()
        } catch (t: Throwable) {
            Log.w(TAG, "Unable to stop metronome", t)
        }
        feeder?.let { thread ->
            runCatching { thread.join(FEEDER_JOIN_MS) }
        }
        feeder = null
        try {
            track.stop()
        } catch (_: Throwable) {
        }
    }

    fun setVolume(volume: Float) {
        runCatching { track.setVolume(volume.coerceIn(0f, 1f)) }
    }

    fun release() {
        stop()
        try {
            track.release()
        } catch (_: Throwable) {
        }
    }

    private fun mixInto(buffer: ShortArray, offset: Int, samples: ShortArray) {
        val count = minOf(samples.size, buffer.size - offset)
        for (i in 0 until count) {
            buffer[offset + i] = (buffer[offset + i] + samples[i])
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                .toShort()
        }
    }

    private companion object {
        private const val TAG = "Metronome"
        private const val BUFFER_PERIODS = 4
        private const val FEEDER_JOIN_MS = 250L
        private const val NANOS_PER_SECOND = 1_000_000_000L

        private fun synthDrum(): ShortArray {
            val count = (SAMPLE_RATE * DRUM_DURATION_SECONDS).toInt()
            val out = ShortArray(count)
            var phase = 0.0
            for (i in 0 until count) {
                val t = i.toDouble() / SAMPLE_RATE
                val freq = DRUM_END_FREQ_HZ + (DRUM_START_FREQ_HZ - DRUM_END_FREQ_HZ) * exp(-t * DRUM_PITCH_DECAY)
                phase += 2.0 * PI * freq / SAMPLE_RATE
                out[i] = pcm(sin(phase) * exp(-t * DRUM_AMP_DECAY) * DRUM_AMPLITUDE)
            }
            return out
        }

        private fun synthCymbal(): ShortArray {
            val count = (SAMPLE_RATE * CYMBAL_DURATION_SECONDS).toInt()
            val out = ShortArray(count)
            var previous = 0.0
            for (i in 0 until count) {
                val t = i.toDouble() / SAMPLE_RATE
                val noise = Random.nextDouble(-1.0, 1.0)
                val highPassed = noise - previous
                previous = noise
                out[i] = pcm(highPassed * exp(-t * CYMBAL_DECAY) * CYMBAL_AMPLITUDE)
            }
            return out
        }

        private fun pcm(value: Double): Short =
            (value * Short.MAX_VALUE)
                .toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                .toShort()

        private const val DRUM_DURATION_SECONDS = 0.2
        private const val DRUM_START_FREQ_HZ = 170.0
        private const val DRUM_END_FREQ_HZ = 45.0
        private const val DRUM_PITCH_DECAY = 20.0
        private const val DRUM_AMP_DECAY = 24.0
        private const val DRUM_AMPLITUDE = 0.9

        private const val CYMBAL_DURATION_SECONDS = 0.12
        private const val CYMBAL_DECAY = 45.0
        private const val CYMBAL_AMPLITUDE = 0.55
    }
}
