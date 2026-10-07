package dev.haos.nativeapp.sensor

import android.os.SystemClock

/**
 * Turns the accelerometer stream into discrete events: shake, face-down, double-tap, pick-up
 * and fall. Only the groups in [enabled] are evaluated. [onEvent] asks for an immediate report.
 */
class MotionEvents(private val enabled: Set<SensorGroup>, private val onEvent: () -> Unit) {
    @Volatile private var shakeUntil = 0L
    @Volatile private var tapUntil = 0L
    @Volatile private var pickUpUntil = 0L
    @Volatile private var fallUntil = 0L
    @Volatile var faceDown = false
        private set

    private val shakePeaks = ArrayDeque<Long>()
    private var lastPeakAt = 0L

    private var lastSpike = 0L
    private var prevSpike = 0L

    private var restingSince = 0L
    private var moveCount = 0
    private var wasResting = false

    private var freeFallSince = 0L
    private var fallArmedUntil = 0L

    val shaking get() = SystemClock.elapsedRealtime() < shakeUntil
    val doubleTapped get() = SystemClock.elapsedRealtime() < tapUntil
    val pickedUp get() = SystemClock.elapsedRealtime() < pickUpUntil
    val fell get() = SystemClock.elapsedRealtime() < fallUntil

    fun process(s: AccelSample) {
        val now = SystemClock.elapsedRealtime()
        if (SensorGroup.SHAKE in enabled) detectShake(s, now)
        if (SensorGroup.FACE_DOWN in enabled) detectFaceDown(s)
        if (SensorGroup.DOUBLE_TAP in enabled) detectDoubleTap(s, now)
        if (SensorGroup.PICK_UP in enabled) detectPickUp(s, now)
        if (SensorGroup.FALL in enabled) detectFall(s, now)
    }

    /** Shake = 3+ hard jolts within a second. */
    private fun detectShake(s: AccelSample, now: Long) {
        if (s.linear > SHAKE_LINEAR && now - lastPeakAt > SHAKE_MIN_GAP_MS) {
            lastPeakAt = now
            shakePeaks.addLast(now)
            while (shakePeaks.first() < now - SHAKE_WINDOW_MS) shakePeaks.removeFirst()
            if (shakePeaks.size >= SHAKE_PEAKS) {
                shakePeaks.clear()
                shakeUntil = now + HOLD_MS
                onEvent()
            }
        }
    }

    /** Hysteresis so it doesn't flicker around the threshold. */
    private fun detectFaceDown(s: AccelSample) {
        val down = if (faceDown) s.z < -5f else s.z < -7f
        if (down != faceDown) {
            faceDown = down
            onEvent()
        }
    }

    /** Two light spikes 100-500 ms apart, after at least 400 ms of quiet before the first. */
    private fun detectDoubleTap(s: AccelSample, now: Long) {
        if (s.linear < TAP_LINEAR || s.linear > SHAKE_LINEAR || now - lastSpike < TAP_DEBOUNCE_MS) return
        val gap = now - lastSpike
        val quietBefore = lastSpike - prevSpike >= 400L
        if (lastSpike != 0L && gap in 100L..500L && quietBefore) {
            tapUntil = now + HOLD_MS
            lastSpike = 0L
            prevSpike = 0L
            onEvent()
        } else {
            prevSpike = lastSpike
            lastSpike = now
        }
    }

    /** Picked up = resting at least 1.5 s, then sustained movement (8+ samples). */
    private fun detectPickUp(s: AccelSample, now: Long) {
        if (s.linear > MOVE_LINEAR) {
            moveCount++
            if (moveCount == 1) {
                wasResting = restingSince != 0L && now - restingSince >= REST_MS
                restingSince = 0L
            }
            if (moveCount >= MOVE_SAMPLES && wasResting) {
                wasResting = false
                pickUpUntil = now + HOLD_MS
                onEvent()
            }
        } else {
            moveCount = 0
            if (s.linear < REST_LINEAR && restingSince == 0L) restingSince = now
        }
    }

    /** Fall = near weightlessness for 120 ms, then a hard impact within a second. */
    private fun detectFall(s: AccelSample, now: Long) {
        val g = s.magnitude
        if (g < FREE_FALL_G) {
            if (freeFallSince == 0L) freeFallSince = now
        } else {
            if (freeFallSince != 0L && now - freeFallSince >= FREE_FALL_MS) fallArmedUntil = now + 1_000L
            freeFallSince = 0L
        }
        if (g > IMPACT_G && now < fallArmedUntil) {
            fallArmedUntil = 0L
            fallUntil = now + FALL_HOLD_MS
            onEvent()
        }
    }

    private companion object {
        const val SHAKE_LINEAR = 9f // m/s² of motion beyond gravity
        const val SHAKE_PEAKS = 3
        const val SHAKE_WINDOW_MS = 1_000L
        const val SHAKE_MIN_GAP_MS = 120L
        const val HOLD_MS = 3_000L
        const val TAP_LINEAR = 3f
        const val TAP_DEBOUNCE_MS = 80L
        const val REST_LINEAR = 0.35f
        const val REST_MS = 1_500L
        const val MOVE_LINEAR = 1.5f
        const val MOVE_SAMPLES = 8
        const val FREE_FALL_G = 3f
        const val FREE_FALL_MS = 120L
        const val IMPACT_G = 20f
        const val FALL_HOLD_MS = 10_000L
    }
}
