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
import kotlin.time.DurationUnit
import kotlin.time.TimeMark
import kotlin.time.TimeSource

class CountdownDemoService : GlyphMatrixService("Countdown-Demo") {

    private lateinit var bgScope: CoroutineScope
    private var runJob: Job? = null
    private var startMark: TimeMark? = null
    private var running = false
    private var drum: DrumSfx? = null

    override fun performOnServiceConnected(
        context: Context,
        glyphMatrixManager: GlyphMatrixManager
    ) {
        bgScope = CoroutineScope(Dispatchers.Default)
        drum = DrumSfx()
        try {
            glyphMatrixManager.setGlyphMatrixTimeout(false)
        } catch (t: Throwable) {
            Log.w(TAG, "Unable to disable glyph matrix timeout", t)
        }
        render(READY_VALUE)
    }

    override fun performOnServiceDisconnected(context: Context) {
        runJob?.cancel()
        runJob = null
        startMark = null
        running = false
        drum?.release()
        drum = null
        if (::bgScope.isInitialized) {
            bgScope.cancel()
        }
    }

    override fun onTouchPointLongPress() {
        if (running) {
            stopTimer()
        } else {
            startTimer()
        }
    }

    override fun onTouchPointPressed() {}

    override fun onTouchPointReleased() {}

    private fun startTimer() {
        runJob?.cancel()
        running = true
        val mark = TimeSource.Monotonic.markNow()
        startMark = mark
        runJob = bgScope.launch {
            var nextDrumAt = 0.0
            while (isActive) {
                val elapsed = mark.elapsedNow().toDouble(DurationUnit.SECONDS)
                if (elapsed >= nextDrumAt) {
                    drum?.play()
                    nextDrumAt += DRUM_INTERVAL_SECONDS
                }
                render(READY_VALUE - elapsed)
                delay(FRAME_INTERVAL_MS)
            }
        }
    }

    private fun stopTimer() {
        val mark = startMark
        runJob?.cancel()
        runJob = null
        running = false
        startMark = null
        if (mark != null) {
            render(READY_VALUE - mark.elapsedNow().toDouble(DurationUnit.SECONDS))
        }
    }

    private fun render(value: Double) {
        val manager = glyphMatrixManager ?: return
        val textObject = GlyphMatrixObject.Builder()
            .setText("%.2f".format(value))
            .setPosition(TEXT_POSITION_X, TEXT_POSITION_Y)
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

    private companion object {
        private val TAG = CountdownDemoService::class.java.simpleName
        private const val READY_VALUE = 10.0
        private const val FRAME_INTERVAL_MS = 33L
        private const val DRUM_INTERVAL_SECONDS = 1.0
        private const val TEXT_POSITION_X = 3
        private const val TEXT_POSITION_Y = 10
    }
}
