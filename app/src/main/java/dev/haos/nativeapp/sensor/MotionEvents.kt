package dev.haos.nativeapp.sensor

import android.os.SystemClock
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sqrt

/**
 * Turns the accelerometer stream into discrete events: shake, face-down, posture, double-tap,
 * pick-up and fall. Only the groups in [enabled] are evaluated. [onEvent] asks for an immediate
 * report; [log] receives the numbers behind each detection (see [DebugLog]).
 *
 * Everything is measured in milliseconds from the sample timestamps, never in sample counts,
 * because the sensor rate differs between phones (125 Hz on a Galaxy S25, 50 Hz elsewhere).
 */
class MotionEvents(
    private val enabled: Set<SensorGroup>,
    private val log: (String, String) -> Unit = { _, _ -> },
    private val onEvent: () -> Unit,
) {
    @Volatile private var shakeUntil = 0L
    @Volatile private var tapUntil = 0L
    @Volatile private var pickUpUntil = 0L
    @Volatile private var fallUntil = 0L
    @Volatile var posture: String? = null
        private set
    val faceDown get() = posture == Posture.FACE_DOWN

    val shaking get() = SystemClock.elapsedRealtime() < shakeUntil
    val doubleTapped get() = SystemClock.elapsedRealtime() < tapUntil
    val pickedUp get() = SystemClock.elapsedRealtime() < pickUpUntil
    val fell get() = SystemClock.elapsedRealtime() < fallUntil

    private var lastT = 0L
    private var smoothLinear = 0f
    private var gx = 0f
    private var gy = 0f
    private var gz = 0f

    // shake
    private val shakePeaks = ArrayDeque<Long>()
    private var lastPeakAt = 0L

    // rest / movement, shared by tap and pick-up
    private var restSince = 0L
    private var lastRestMs = 0L
    private var moveMs = 0f
    private var quietSince = 0L

    // double tap: two short isolated pulses, then quiet again
    private var inPulse = false
    private var pulseStart = 0L
    private var pulseMax = 0f
    private var pulseLastAbove = 0L
    private var firstPulseAt = 0L
    private var secondPulseEnd = 0L
    private var tapGap = 0L
    private var tapPeak = 0f

    // posture
    private var pendingPosture: String? = null
    private var pendingSince = 0L

    // fall
    private var freeFallSince = 0L
    private var fallArmedUntil = 0L

    fun process(s: AccelSample) {
        val now = s.timestampNs / 1_000_000L
        val dt = if (lastT == 0L) 0f else (now - lastT).coerceIn(0L, 200L).toFloat()
        if (lastT == 0L) { gx = s.x; gy = s.y; gz = s.z; smoothLinear = s.linear }
        lastT = now
        smoothLinear += (s.linear - smoothLinear) * alpha(dt, LINEAR_TAU_MS)
        val a = alpha(dt, GRAVITY_TAU_MS)
        gx += (s.x - gx) * a; gy += (s.y - gy) * a; gz += (s.z - gz) * a

        if (SensorGroup.SHAKE in enabled) detectShake(s, now)
        if (SensorGroup.FACE_DOWN in enabled || SensorGroup.POSTURE in enabled) detectPosture(s, now)
        // Judged before this sample can end the rest period: a tap's first sample already moves it.
        val restedBefore = restSince != 0L && now - restSince >= TAP_REST_BEFORE_MS
        val tapOn = SensorGroup.DOUBLE_TAP in enabled
        val pickOn = SensorGroup.PICK_UP in enabled
        if (tapOn || pickOn) trackRestAndMovement(dt, now)
        if (tapOn) detectDoubleTap(s, now, restedBefore)
        if (pickOn) detectPickUp(now)
        if (SensorGroup.FALL in enabled) detectFall(s, now)
    }

    private fun alpha(dtMs: Float, tauMs: Float) = 1f - exp(-dtMs / tauMs)

    /** Shake = several hard jolts within a second and a bit; walking and carrying stay below the bar. */
    private fun detectShake(s: AccelSample, now: Long) {
        if (s.linear > SHAKE_LINEAR && now - lastPeakAt > SHAKE_MIN_GAP_MS) {
            lastPeakAt = now
            shakePeaks.addLast(now)
            while (shakePeaks.first() < now - SHAKE_WINDOW_MS) shakePeaks.removeFirst()
            if (shakePeaks.size >= SHAKE_PEAKS) {
                log("SHAKE", "peaks=${shakePeaks.size} linear=%.2f".format(s.linear))
                shakePeaks.clear()
                shakeUntil = SystemClock.elapsedRealtime() + HOLD_MS
                onEvent()
            }
        }
    }

    /**
     * Posture comes from smoothed gravity and only changes after the phone has been calm and
     * pointing the same way for [POSTURE_STABLE_MS], so carrying it around doesn't make it flicker.
     */
    private fun detectPosture(s: AccelSample, now: Long) {
        val g = sqrt(gx * gx + gy * gy + gz * gz)
        val calm = smoothLinear < CALM_LINEAR && g in 8.3f..11.3f
        if (!calm) { pendingPosture = null; return }
        val candidate = Posture.classify(gx, gy, gz, posture)
        if (candidate == posture) { pendingPosture = null; return }
        if (candidate != pendingPosture) { pendingPosture = candidate; pendingSince = now; return }
        if (now - pendingSince >= POSTURE_STABLE_MS) {
            log("POSTURE", "$posture -> $candidate g=(%.1f, %.1f, %.1f)".format(gx, gy, gz))
            posture = candidate
            pendingPosture = null
            onEvent()
        }
    }

    /** Tracks how long the phone has been resting and how long it has been moving since. */
    private fun trackRestAndMovement(dt: Float, now: Long) {
        if (smoothLinear >= MOVE_LINEAR) {
            if (moveMs == 0f) {
                lastRestMs = if (restSince != 0L) now - restSince else 0L
                restSince = 0L
            }
            moveMs += dt
            quietSince = 0L
        } else if (smoothLinear < REST_LINEAR) {
            if (restSince == 0L) restSince = now
            if (quietSince == 0L) quietSince = now
            if (now - quietSince > MOVE_GRACE_MS) moveMs = 0f
        }
    }

    /**
     * Double tap = two short isolated pulses 100-600 ms apart, the first one landing on a phone
     * that was resting, and the phone quiet again right afterwards. Handling the phone (long,
     * ragged motion) is rejected by the pulse width and the quiet-after check. A pulse ends once
     * the signal stays under [PULSE_END_LINEAR] for [PULSE_GAP_MS]: its ring-down dips below
     * the line now and then without being over.
     */
    private fun detectDoubleTap(s: AccelSample, now: Long, restedBefore: Boolean) {
        val l = s.linear
        if (secondPulseEnd != 0L) {
            // Waiting for quiet after the second pulse; any further motion cancels it.
            if (l > PULSE_END_LINEAR) { resetTap(); return }
            if (now - secondPulseEnd >= TAP_QUIET_AFTER_MS) {
                log("DOUBLE_TAP", "gap_ms=$tapGap peak=%.1f".format(tapPeak))
                tapUntil = SystemClock.elapsedRealtime() + HOLD_MS
                resetTap()
                lastTapDone = now
                onEvent()
            }
            return
        }
        if (!inPulse) {
            if (l > TAP_PEAK && now - lastTapDone > TAP_COOLDOWN_MS) {
                if (firstPulseAt != 0L && now - firstPulseAt !in TAP_MIN_GAP_MS..TAP_MAX_GAP_MS) firstPulseAt = 0L
                if (firstPulseAt == 0L && !restedBefore) return
                inPulse = true; pulseStart = now; pulseLastAbove = now; pulseMax = l
            } else if (firstPulseAt != 0L && now - firstPulseAt > TAP_MAX_GAP_MS) {
                firstPulseAt = 0L
            }
            return
        }
        pulseMax = maxOf(pulseMax, l)
        if (l > PULSE_END_LINEAR) {
            pulseLastAbove = now
            if (now - pulseStart > TAP_MAX_PULSE_MS) { inPulse = false; firstPulseAt = 0L } // handling, not a tap
            return
        }
        if (now - pulseLastAbove < PULSE_GAP_MS) return
        inPulse = false
        if (pulseMax > TAP_MAX_PEAK) { firstPulseAt = 0L; return }
        if (firstPulseAt == 0L) {
            firstPulseAt = pulseStart
            tapPeak = pulseMax
        } else {
            tapGap = pulseStart - firstPulseAt
            tapPeak = maxOf(tapPeak, pulseMax)
            secondPulseEnd = pulseLastAbove
        }
    }

    private var lastTapDone = 0L

    private fun resetTap() {
        inPulse = false; firstPulseAt = 0L; secondPulseEnd = 0L
    }

    /** Picked up = resting at least a second, then sustained movement (taps are over in ~150 ms). */
    private fun detectPickUp(now: Long) {
        if (moveMs >= PICKUP_MOVE_MS && lastRestMs >= PICKUP_REST_MS && !inPulse && firstPulseAt == 0L && secondPulseEnd == 0L) {
            log("PICK_UP", "rested_ms=$lastRestMs moved_ms=${moveMs.toInt()}")
            lastRestMs = 0L
            pickUpUntil = SystemClock.elapsedRealtime() + HOLD_MS
            onEvent()
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
            log("FALL", "impact_g=%.1f".format(g / 9.81f))
            fallArmedUntil = 0L
            fallUntil = SystemClock.elapsedRealtime() + FALL_HOLD_MS
            onEvent()
        }
    }

    private companion object {
        const val LINEAR_TAU_MS = 80f
        const val GRAVITY_TAU_MS = 250f

        const val SHAKE_LINEAR = 12f // m/s² of motion beyond gravity (walking with it reaches ~9)
        const val SHAKE_PEAKS = 4
        const val SHAKE_WINDOW_MS = 1_200L
        const val SHAKE_MIN_GAP_MS = 100L
        const val HOLD_MS = 3_000L

        const val CALM_LINEAR = 2f
        const val POSTURE_STABLE_MS = 500L

        const val REST_LINEAR = 1.0f
        const val MOVE_LINEAR = 1.2f
        const val MOVE_GRACE_MS = 300L

        const val TAP_PEAK = 2.5f
        const val TAP_MAX_PEAK = 60f
        const val PULSE_END_LINEAR = 2f
        const val TAP_MAX_PULSE_MS = 200L
        const val PULSE_GAP_MS = 80L
        const val TAP_MIN_GAP_MS = 100L
        const val TAP_MAX_GAP_MS = 600L
        const val TAP_REST_BEFORE_MS = 500L
        const val TAP_QUIET_AFTER_MS = 250L
        const val TAP_COOLDOWN_MS = 800L

        const val PICKUP_REST_MS = 1_000L
        const val PICKUP_MOVE_MS = 500f

        const val FREE_FALL_G = 3f
        const val FREE_FALL_MS = 120L
        const val IMPACT_G = 20f
        const val FALL_HOLD_MS = 10_000L
    }
}
