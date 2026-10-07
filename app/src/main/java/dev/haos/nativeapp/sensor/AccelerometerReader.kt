package dev.haos.nativeapp.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlin.math.atan2
import kotlin.math.sqrt

data class AccelSample(
    /** Raw accelerometer axes in m/s² (gravity included). */
    val x: Float,
    val y: Float,
    val z: Float,
    /** Magnitude of acceleration with gravity removed by a low-pass filter. */
    val linear: Float,
    val timestampNs: Long,
) {
    val magnitude: Float get() = sqrt(x * x + y * y + z * z)

    /** Forward/back tilt in degrees: 0 flat on a table, ±90 held upright (portrait). */
    val pitch: Float get() = Math.toDegrees(atan2(y, sqrt(x * x + z * z)).toDouble()).toFloat()

    /** Sideways tilt in degrees: 0 flat, ±90 resting on its long edge. */
    val roll: Float get() = Math.toDegrees(atan2(x, z).toDouble()).toFloat()
}

/** Streams accelerometer samples as a Flow; the sensor listener lives as long as the collector. */
class AccelerometerReader(context: Context) {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val sensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    val isAvailable: Boolean get() = sensor != null

    fun samples(samplingPeriodUs: Int = SensorManager.SENSOR_DELAY_GAME): Flow<AccelSample> = callbackFlow {
        val accel = sensor ?: run { close(); return@callbackFlow }
        val gravity = FloatArray(3)
        var primed = false

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val (x, y, z) = Triple(event.values[0], event.values[1], event.values[2])
                if (!primed) {
                    gravity[0] = x; gravity[1] = y; gravity[2] = z
                    primed = true
                }
                // Low-pass filter isolates gravity; the remainder is the phone's own motion.
                gravity[0] = ALPHA * gravity[0] + (1 - ALPHA) * x
                gravity[1] = ALPHA * gravity[1] + (1 - ALPHA) * y
                gravity[2] = ALPHA * gravity[2] + (1 - ALPHA) * z
                val lx = x - gravity[0]
                val ly = y - gravity[1]
                val lz = z - gravity[2]
                trySend(AccelSample(x, y, z, sqrt(lx * lx + ly * ly + lz * lz), event.timestamp))
            }

            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
        }
        sensorManager.registerListener(listener, accel, samplingPeriodUs)
        awaitClose { sensorManager.unregisterListener(listener) }
    }

    private companion object {
        const val ALPHA = 0.8f
    }
}
