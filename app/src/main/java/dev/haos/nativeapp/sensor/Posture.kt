package dev.haos.nativeapp.sensor

import kotlin.math.abs
import kotlin.math.sqrt

/** How the phone is resting, from the direction of gravity. */
object Posture {
    const val FACE_UP = "face_up"
    const val FACE_DOWN = "face_down"
    const val UPRIGHT = "upright"
    const val UPSIDE_DOWN = "upside_down"
    const val LEFT = "on_left_side"
    const val RIGHT = "on_right_side"
    const val TILTED = "tilted"

    /**
     * Posture for a (smoothed) gravity vector. Near a boundary the [previous] posture is kept
     * unless gravity points clearly (>= 85%) along the new axis.
     */
    fun classify(x: Float, y: Float, z: Float, previous: String?): String {
        val g = sqrt(x * x + y * y + z * z)
        if (g < 1f) return previous ?: TILTED
        val candidate = when {
            abs(z) >= abs(x) && abs(z) >= abs(y) -> if (z >= 0) FACE_UP else FACE_DOWN
            abs(y) >= abs(x) -> if (y >= 0) UPRIGHT else UPSIDE_DOWN
            else -> if (x >= 0) RIGHT else LEFT
        }
        val dominant = when (candidate) {
            FACE_UP, FACE_DOWN -> abs(z)
            UPRIGHT, UPSIDE_DOWN -> abs(y)
            else -> abs(x)
        } / g
        if (dominant >= 0.85f) return candidate
        if (previous == null || previous == TILTED) return if (dominant >= 0.7f) candidate else TILTED
        return previous
    }
}

/** RMS of the phone's own motion (gravity removed) since the last [take]. */
class VibrationMeter {
    private var sumSq = 0.0
    private var count = 0

    @Synchronized fun add(linear: Float) { sumSq += linear.toDouble() * linear; count++ }

    @Synchronized fun take(): Float {
        val v = if (count == 0) 0f else sqrt(sumSq / count).toFloat()
        sumSq = 0.0; count = 0
        return v
    }
}
