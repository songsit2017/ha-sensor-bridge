package dev.haos.nativeapp.ha

import dev.haos.nativeapp.sensor.AccelSample
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
)

/**
 * The entities this app exposes to Home Assistant via mobile_app. They appear as e.g.
 * sensor.<device>_accelerometer_x and binary_sensor.<device>_shake.
 */
object AccelSensors {
    /** Bump whenever a sensor is added or changed so existing installs re-register. */
    const val VERSION = 2

    private data class Def(
        val key: String,
        val name: String,
        val type: String = "sensor",
        val icon: String,
        val unit: String? = null,
        val deviceClass: String? = null,
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
        val payload: JsonArray = buildJsonArray {
            add(number(deviceId, "accel_x", s.x))
            add(number(deviceId, "accel_y", s.y))
            add(number(deviceId, "accel_z", s.z))
            add(number(deviceId, "accel_magnitude", s.magnitude))
            add(number(deviceId, "tilt_pitch", s.pitch))
            add(number(deviceId, "tilt_roll", s.roll))
            add(flag(deviceId, "motion", r.moving, mapOf("peak_linear_acceleration" to round(r.peakLinear))))
            add(flag(deviceId, "shake", r.shaking))
            add(flag(deviceId, "face_down", r.faceDown))
        }
        client.webhook(webhookId, "update_sensor_states", payload)
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
