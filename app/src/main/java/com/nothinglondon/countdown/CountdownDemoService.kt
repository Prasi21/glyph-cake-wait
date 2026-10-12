package com.nothinglondon.countdown

import android.content.Context
import android.util.Log
import com.nothing.ketchum.GlyphMatrixFrame
import com.nothing.ketchum.GlyphMatrixManager
import com.nothing.ketchum.GlyphMatrixObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.floor

class CountdownDemoService : GlyphMatrixService("Countdown-Demo") {

    private lateinit var bgScope: CoroutineScope
    private var runJob: Job? = null
    private var running = false
    private var suppressChange = false
    private var metronome: Metronome? = null

    override fun performOnServiceConnected(
        context: Context,
        glyphMatrixManager: GlyphMatrixManager
    ) {
        bgScope = CoroutineScope(Dispatchers.Default)
        metronome = Metronome(BPM, SUBDIVISIONS_PER_BEAT)
        try {
            glyphMatrixManager.setGlyphMatrixTimeout(false)
        } catch (t: Throwable) {
            Log.w(TAG, "Unable to disable glyph matrix timeout", t)
        }
        render(READY_VALUE, MAX_BRIGHTNESS)
    }

    override fun performOnServiceDisconnected(context: Context) {
        runJob?.cancel()
        runJob = null
        running = false
        suppressChange = false
        metronome?.release()
        metronome = null
        if (::bgScope.isInitialized) {
            bgScope.cancel()
        }
    }

    override fun onTouchPointPressed() {
        if (running) {
            stopTimer()
            suppressChange = true
        } else {
            suppressChange = false
        }
    }

    override fun onTouchPointLongPress() {
        if (suppressChange) return
        if (!running) {
            startTimer()
        }
    }

    override fun onTouchPointReleased() {
        suppressChange = false
    }

    private fun startTimer() {
        runJob?.cancel()
        running = true
        runJob = bgScope.launch {
            val metro = metronome ?: return@launch
            val startWallNanos = System.nanoTime()
            metro.start()
            var startLogged = false
            var lastWholeSecond = -1
            while (isActive) {
                val elapsed = metro.elapsedSeconds()
                if (!startLogged && elapsed > 0.0) {
                    probeStart(startWallNanos)
                    startLogged = true
                }
                val wholeSecond = floor(elapsed).toInt()
                if (wholeSecond != lastWholeSecond) {
                    probeBeat(wholeSecond, elapsed)
                    lastWholeSecond = wholeSecond
                }
                val remaining = READY_VALUE - elapsed
                render(remaining, fadeBrightness(remaining))
                delay(FRAME_INTERVAL_MS)
            }
        }
    }

    private fun stopTimer() {
        val elapsed = metronome?.elapsedSeconds() ?: 0.0
        runJob?.cancel()
        runJob = null
        running = false
        metronome?.stop()
        render(READY_VALUE - elapsed, MAX_BRIGHTNESS)
    }

    private fun fadeBrightness(value: Double): Int {
        val progress = ((value - FADE_OUT_VALUE) / (READY_VALUE - FADE_OUT_VALUE))
            .coerceIn(0.0, 1.0)
        return (MAX_BRIGHTNESS * progress).toInt()
    }

    private fun render(value: Double, brightness: Int) {
        val manager = glyphMatrixManager ?: return
        val textObject = GlyphMatrixObject.Builder()
            .setText("%.2f".format(value))
            .setPosition(TEXT_POSITION_X, TEXT_POSITION_Y)
            .setBrightness(brightness)
            .build()
        val frame = GlyphMatrixFrame.Builder()
            .addTop(textObject)
            .build(applicationContext)
        try {
            manager.setMatrixFrame(frame.render())
        } catch (t: Throwable) {
            Log.w(TAG, "Unable to render frame", t)
        }
    }

    private fun probeStart(startWallNanos: Long) {
        if (!VERIFY_TIMING) return
        val latencyMs = (System.nanoTime() - startWallNanos) / 1_000_000.0
        Log.d(PROBE_TAG, "audio output started ${"%.1f".format(latencyMs)} ms after trigger")
    }

    private fun probeBeat(beatIndex: Int, elapsed: Double) {
        if (!VERIFY_TIMING) return
        val idealMs = beatIndex * BEAT_INTERVAL_SECONDS * 1000
        val actualMs = elapsed * 1000
        Log.d(
            PROBE_TAG,
            "beat=$beatIndex ideal=${"%.1f".format(idealMs)}ms audio=${"%.1f".format(actualMs)}ms delta=${"%.1f".format(actualMs - idealMs)}ms"
        )
    }

    private companion object {
        private val TAG = CountdownDemoService::class.java.simpleName
        private const val PROBE_TAG = "CountdownTiming"
        private const val VERIFY_TIMING = true

        private const val READY_VALUE = 10.0
        private const val FADE_OUT_VALUE = 6.0
        private const val MAX_BRIGHTNESS = 255
        private const val BPM = 60.0
        private const val SUBDIVISIONS_PER_BEAT = 2
        private const val BEAT_INTERVAL_SECONDS = 60.0 / BPM
        private const val FRAME_INTERVAL_MS = 16L
        private const val TEXT_POSITION_X = 3
        private const val TEXT_POSITION_Y = 10
    }
}
