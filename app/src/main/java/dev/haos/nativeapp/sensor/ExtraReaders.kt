package dev.haos.nativeapp.sensor

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlin.math.log10
import kotlin.math.sqrt

/** Rotation rate around each axis in degrees per second. */
data class Gyro(val x: Float, val y: Float, val z: Float) {
    val magnitude: Float get() = sqrt(x * x + y * y + z * z)
}

object GyroReader {
    fun samples(context: Context): Flow<Gyro> = callbackFlow {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = sm.getDefaultSensor(Sensor.TYPE_GYROSCOPE) ?: run { close(); return@callbackFlow }
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                val k = 180f / Math.PI.toFloat()
                trySend(Gyro(e.values[0] * k, e.values[1] * k, e.values[2] * k))
            }
            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
        }
        sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        awaitClose { sm.unregisterListener(listener) }
    }
}

/** Strength of the surrounding magnetic field in µT (about 25-65 in open air; magnets spike it). */
object MagneticReader {
    fun samples(context: Context): Flow<Float> = callbackFlow {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = sm.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD) ?: run { close(); return@callbackFlow }
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                trySend(sqrt(e.values[0] * e.values[0] + e.values[1] * e.values[1] + e.values[2] * e.values[2]))
            }
            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
        }
        sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        awaitClose { sm.unregisterListener(listener) }
    }
}

object HeadingReader {
    private val names = listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")

    fun direction(heading: Float) = names[((heading + 22.5f) / 45f).toInt() % 8]

    /** Compass heading in degrees (0 = north, clockwise), 0..360, relative to the phone's top edge. */
    fun samples(context: Context): Flow<Float> = callbackFlow {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) ?: run { close(); return@callbackFlow }
        val rot = FloatArray(9)
        val orient = FloatArray(3)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                SensorManager.getRotationMatrixFromVector(rot, e.values)
                SensorManager.getOrientation(rot, orient)
                val deg = Math.toDegrees(orient[0].toDouble()).toFloat()
                trySend((deg + 360f) % 360f)
            }
            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
        }
        sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        awaitClose { sm.unregisterListener(listener) }
    }
}

/**
 * Ambient loudness in approximate dB. Only an RMS level is computed from each buffer;
 * the audio itself is discarded immediately and never stored or sent anywhere.
 * The dB scale is uncalibrated (phone microphones differ), so treat it as relative.
 */
object SoundMeter {
    private const val RATE = 16_000
    private const val OFFSET_DB = 90.0 // maps full scale to roughly 90 dB SPL

    @SuppressLint("MissingPermission") // the caller checks RECORD_AUDIO before collecting
    fun levels(): Flow<Float> = flow {
        val chunk = RATE / 2 // 0.5 s per reading
        val minBuf = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val rec = AudioRecord(
            MediaRecorder.AudioSource.MIC, RATE, AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT, maxOf(minBuf, chunk * 2),
        )
        if (rec.state != AudioRecord.STATE_INITIALIZED) { rec.release(); return@flow }
        val buf = ShortArray(chunk)
        try {
            rec.startRecording()
            while (true) {
                val n = rec.read(buf, 0, buf.size)
                if (n <= 0) continue
                var sum = 0.0
                for (i in 0 until n) sum += buf[i].toDouble() * buf[i]
                val rms = sqrt(sum / n).coerceAtLeast(1.0)
                emit((20 * log10(rms / 32768.0) + OFFSET_DB).coerceIn(0.0, 120.0).toFloat())
            }
        } finally {
            rec.stop()
            rec.release()
        }
    }.flowOn(Dispatchers.IO)
}
