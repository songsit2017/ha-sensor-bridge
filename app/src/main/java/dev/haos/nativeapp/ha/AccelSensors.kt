package dev.haos.nativeapp.ha

import dev.haos.nativeapp.sensor.AccelSample
import dev.haos.nativeapp.sensor.Gyro
import dev.haos.nativeapp.sensor.HeadingReader
import dev.haos.nativeapp.sensor.SensorGroup
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.Locale

/** One snapshot of everything the bridge reports to HA. */
data class SensorReading(
    val sample: AccelSample,
    val moving: Boolean,
    val peakLinear: Float,
    val shaking: Boolean,
    val faceDown: Boolean,
    val doubleTap: Boolean = false,
    val pickUp: Boolean = false,
    val fall: Boolean = false,
    /** Null when the sensor is switched off or missing: it is then left out of the update. */
    val gyro: Gyro? = null,
    val heading: Float? = null,
    val soundDb: Float? = null,
    val magneticUt: Float? = null,
    val vibration: Float? = null,
    val posture: String? = null,
    val enabled: Set<SensorGroup> = SensorGroup.values().toSet(),
)

/**
 * The entities this app exposes to Home Assistant via mobile_app. They appear as e.g.
 * sensor.<device>_accelerometer_x and binary_sensor.<device>_shake.
 */
object AccelSensors {
    /** Bump whenever a sensor is added or changed so existing installs re-register. */
    const val VERSION = 4

    private data class Def(
        val key: String,
        val name: String,
        val type: String = "sensor",
        val icon: String,
        val unit: String? = null,
        val deviceClass: String? = null,
        val text: Boolean = false,
    )

    private val defs = listOf(
        Def("accel_x", "Accelerometer X", icon = "mdi:axis-x-arrow", unit = "m/s²"),
        Def("accel_y", "Accelerometer Y", icon = "mdi:axis-y-arrow", unit = "m/s²"),
        Def("accel_z", "Accelerometer Z", icon = "mdi:axis-z-arrow", unit = "m/s²"),
        Def("accel_magnitude", "Acceleration", icon = "mdi:speedometer", unit = "m/s²"),
        Def("tilt_pitch", "Tilt pitch", icon = "mdi:phone-rotate-portrait", unit = "°"),
        Def("tilt_roll", "Tilt roll", icon = "mdi:phone-rotate-landscape", unit = "°"),
        Def("motion", "Motion", type = "binary_sensor", icon = "mdi:vibrate", deviceClass = "moving"),
        Def("shake", "Shake", type = "binary_sensor", icon = "mdi:cellphone-wireless", deviceClass = "vibration"),
        Def("face_down", "Face down", type = "binary_sensor", icon = "mdi:cellphone-arrow-down"),
        Def("gyro_x", "Gyroscope X", icon = "mdi:rotate-3d-variant", unit = "°/s"),
        Def("gyro_y", "Gyroscope Y", icon = "mdi:rotate-3d-variant", unit = "°/s"),
        Def("gyro_z", "Gyroscope Z", icon = "mdi:rotate-3d-variant", unit = "°/s"),
        Def("gyro_magnitude", "Rotation rate", icon = "mdi:rotate-orbit", unit = "°/s"),
        Def("heading", "Compass heading", icon = "mdi:compass", unit = "°"),
        Def("compass_direction", "Compass direction", icon = "mdi:compass-outline", text = true),
        Def("double_tap", "Double tap", type = "binary_sensor", icon = "mdi:gesture-double-tap"),
        Def("pick_up", "Picked up", type = "binary_sensor", icon = "mdi:hand-back-right"),
        Def("fall", "Fall", type = "binary_sensor", icon = "mdi:arrow-down-bold-box", deviceClass = "problem"),
        Def("magnetic_field", "Magnetic field", icon = "mdi:magnet", unit = "µT"),
        Def("vibration", "Vibration level", icon = "mdi:vibrate", unit = "m/s²"),
        Def("posture", "Device posture", icon = "mdi:phone-rotate-portrait", text = true),
        Def("sound_level", "Sound level", icon = "mdi:microphone", unit = "dB"),
    )

    private fun uniqueId(deviceId: String, key: String) = "${deviceId}_$key"

    /** Registers every sensor. HA treats re-registration of an existing unique_id as an update. */
    suspend fun registerAll(client: HaClient, webhookId: String, deviceId: String) {
        for (d in defs) {
            val data = buildJsonObject {
                put("type", d.type)
                put("unique_id", uniqueId(deviceId, d.key))
                put("name", d.name)
                put("icon", d.icon)
                d.deviceClass?.let { put("device_class", it) }
                if (d.type == "binary_sensor") {
                    put("state", false)
                } else if (d.text) {
                    put("state", "unknown")
                } else {
                    put("state", 0.0)
                    put("state_class", "measurement")
                    d.unit?.let { put("unit_of_measurement", it) }
                }
            }
            client.webhook(webhookId, "register_sensor", data)
        }
    }

    suspend fun update(client: HaClient, webhookId: String, deviceId: String, r: SensorReading) {
        val s = r.sample
        val on = r.enabled
        val payload: JsonArray = buildJsonArray {
            if (SensorGroup.ACCEL in on) {
                add(number(deviceId, "accel_x", s.x))
                add(number(deviceId, "accel_y", s.y))
                add(number(deviceId, "accel_z", s.z))
                add(number(deviceId, "accel_magnitude", s.magnitude))
                add(flag(deviceId, "motion", r.moving, mapOf("peak_linear_acceleration" to round(r.peakLinear))))
            }
            if (SensorGroup.TILT in on) {
                add(number(deviceId, "tilt_pitch", s.pitch))
                add(number(deviceId, "tilt_roll", s.roll))
            }
            // Event sensors always report; a switched-off one simply stays "off".
            add(flag(deviceId, "shake", r.shaking))
            add(flag(deviceId, "face_down", r.faceDown))
            add(flag(deviceId, "double_tap", r.doubleTap))
            add(flag(deviceId, "pick_up", r.pickUp))
            add(flag(deviceId, "fall", r.fall))
            r.gyro?.let {
                add(number(deviceId, "gyro_x", it.x))
                add(number(deviceId, "gyro_y", it.y))
                add(number(deviceId, "gyro_z", it.z))
                add(number(deviceId, "gyro_magnitude", it.magnitude))
            }
            r.heading?.let {
                add(number(deviceId, "heading", it))
                add(text(deviceId, "compass_direction", HeadingReader.direction(it)))
            }
            r.magneticUt?.let { add(number(deviceId, "magnetic_field", it)) }
            r.vibration?.let { add(number(deviceId, "vibration", it)) }
            r.posture?.let { add(text(deviceId, "posture", it)) }
            r.soundDb?.let { add(number(deviceId, "sound_level", it)) }
        }
        client.webhook(webhookId, "update_sensor_states", payload)
    }

    private fun text(deviceId: String, key: String, value: String): JsonObject = buildJsonObject {
        put("type", "sensor")
        put("unique_id", uniqueId(deviceId, key))
        put("icon", defs.first { it.key == key }.icon)
        put("state", value)
    }

    private fun number(deviceId: String, key: String, value: Float): JsonObject = buildJsonObject {
        put("type", "sensor")
        put("unique_id", uniqueId(deviceId, key))
        put("icon", defs.first { it.key == key }.icon)
        put("state", round(value))
    }

    private fun flag(
        deviceId: String,
        key: String,
        on: Boolean,
        attributes: Map<String, Double> = emptyMap(),
    ): JsonObject = buildJsonObject {
        put("type", "binary_sensor")
        put("unique_id", uniqueId(deviceId, key))
        put("icon", defs.first { it.key == key }.icon)
        put("state", on)
        if (attributes.isNotEmpty()) put("attributes", buildJsonObject {
            attributes.forEach { (k, v) -> put(k, v) }
        })
    }

    private fun round(v: Float): Double = String.format(Locale.US, "%.3f", v).toDouble()
}
