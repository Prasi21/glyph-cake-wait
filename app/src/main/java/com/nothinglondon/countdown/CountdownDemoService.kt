package com.nothinglondon.countdown

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
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
import kotlin.math.ceil

class CountdownDemoService : GlyphMatrixService("Countdown-Demo") {

    private lateinit var bgScope: CoroutineScope
    private var runJob: Job? = null
    private var running = false
    private var suppressChange = false
    private var metronome: Metronome? = null
    private var flashJob: Job? = null

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
        renderSprite()
    }

    override fun performOnServiceDisconnected(context: Context) {
        runJob?.cancel()
        runJob = null
        flashJob?.cancel()
        flashJob = null
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
        flashJob?.cancel()
        flashJob = null
        runJob?.cancel()
        running = true
        runJob = bgScope.launch {
            val metro = metronome ?: return@launch
            metro.start()
            while (isActive) {
                val elapsed = metro.elapsedSeconds()
                val remaining = READY_VALUE - elapsed
                render(ceil(remaining).toInt().toString(), fadeBrightness(remaining))
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
        flashScore("%+.2f".format(READY_VALUE - elapsed))
    }

    private fun flashScore(score: String) {
        flashJob?.cancel()
        flashJob = bgScope.launch {
            repeat(FLASH_COUNT) {
                render(score, MAX_BRIGHTNESS)
                delay(FLASH_ON_MS)
                render(score, 0)
                delay(FLASH_OFF_MS)
            }
            render(score, MAX_BRIGHTNESS)
        }
    }

    private fun fadeBrightness(value: Double): Int {
        val progress = ((value - FADE_OUT_VALUE) / (READY_VALUE - FADE_OUT_VALUE))
            .coerceIn(0.0, 1.0)
        return (MAX_BRIGHTNESS * progress).toInt()
    }

    private fun renderSprite() {
        val manager = glyphMatrixManager ?: return
        val pixels = IntArray(MATRIX_SIZE * MATRIX_SIZE) { i ->
            val g = READY_SPRITE[i].coerceIn(0, 255)
            Color.rgb(g, g, g)
        }
        val bitmap = Bitmap.createBitmap(MATRIX_SIZE, MATRIX_SIZE, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(pixels, 0, MATRIX_SIZE, 0, 0, MATRIX_SIZE, MATRIX_SIZE)
        val sprite = GlyphMatrixObject.Builder()
            .setImageSource(bitmap)
            .setBrightness(MAX_BRIGHTNESS)
            .build()
        val frame = GlyphMatrixFrame.Builder()
            .addTop(sprite)
            .build(applicationContext)
        try {
            manager.setMatrixFrame(frame.render())
        } catch (t: Throwable) {
            Log.w(TAG, "Unable to render sprite", t)
        }
    }

    private fun centerX(text: String): Int {
        var width = 0
        text.forEachIndexed { index, c ->
            if (index > 0) width += LETTER_SPACING
            width += when (c) {
                '1' -> 3
                '.' -> 1
                '+' -> 5
                '-' -> 3
                else -> 4
            }
        }
        return (MATRIX_SIZE - width) / 2
    }

    private fun render(text: String, brightness: Int) {
        val manager = glyphMatrixManager ?: return
        val textObject = GlyphMatrixObject.Builder()
            .setText(text)
            .setPosition(centerX(text), TEXT_POSITION_Y)
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

    private companion object {
        private val TAG = CountdownDemoService::class.java.simpleName
        private const val READY_VALUE = 10.0
        private const val FADE_OUT_VALUE = 6.0
        private const val MAX_BRIGHTNESS = 255
        private const val BPM = 60.0
        private const val SUBDIVISIONS_PER_BEAT = 2
        private const val FRAME_INTERVAL_MS = 16L
        private const val FLASH_COUNT = 6
        private const val FLASH_ON_MS = 140L
        private const val FLASH_OFF_MS = 90L
        private const val MATRIX_SIZE = 25
        private const val LETTER_SPACING = 1
        private const val TEXT_POSITION_Y = 10
    }
}
