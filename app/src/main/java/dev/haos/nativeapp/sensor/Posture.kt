package dev.haos.nativeapp.sensor

import kotlin.math.abs
import kotlin.math.sqrt

/** How the phone is resting, from the direction of gravity. Keeps the previous value near a boundary. */
object Posture {
    const val FACE_UP = "face_up"
    const val FACE_DOWN = "face_down"
    const val UPRIGHT = "upright"
    const val UPSIDE_DOWN = "upside_down"
    const val LEFT = "on_left_side"
    const val RIGHT = "on_right_side"
    const val TILTED = "tilted"

    fun of(s: AccelSample, previous: String?): String {
        val candidate = when {
            abs(s.z) >= abs(s.x) && abs(s.z) >= abs(s.y) -> if (s.z >= 0) FACE_UP else FACE_DOWN
            abs(s.y) >= abs(s.x) -> if (s.y >= 0) UPRIGHT else UPSIDE_DOWN
            else -> if (s.x >= 0) RIGHT else LEFT
        }
        val g = s.magnitude
        // Hold the old posture unless gravity points clearly (>~70%) along the new axis.
        val dominant = when (candidate) {
            FACE_UP, FACE_DOWN -> abs(s.z)
            UPRIGHT, UPSIDE_DOWN -> abs(s.y)
            else -> abs(s.x)
        }
        if (g < 3f) return previous ?: TILTED // free fall: gravity is unreadable
        if (dominant / g >= 0.85f) return candidate
        if (previous == null || previous == TILTED) return if (dominant / g >= 0.7f) candidate else TILTED
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
