package dev.haos.nativeapp.ha

import android.os.Build
import dev.haos.nativeapp.BuildConfig
import dev.haos.nativeapp.data.Settings
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Ensures this phone is registered with mobile_app, named correctly, with its sensors created. */
object Registrar {

    suspend fun ensureRegistered(settings: Settings): String {
        val client = HaAuth.client(settings)
        val name = settings.deviceName
        val webhookId = settings.webhookId ?: client.registerDevice(
            deviceId = settings.deviceId,
            deviceName = name,
            manufacturer = Build.MANUFACTURER,
            model = Build.MODEL,
            osVersion = Build.VERSION.RELEASE,
            appVersion = BuildConfig.VERSION_NAME,
        ).webhookId.also {
            settings.webhookId = it
            settings.registeredSensorVersion = 0
            settings.syncedDeviceName = name
        }
        if (settings.registeredSensorVersion != AccelSensors.VERSION) {
            AccelSensors.registerAll(client, webhookId, settings.deviceId)
            settings.registeredSensorVersion = AccelSensors.VERSION
        }
        // Phones registered earlier (or renamed since) get the new name without re-registering.
        if (settings.syncedDeviceName != name) {
            client.webhook(webhookId, "update_registration", buildJsonObject {
                put("device_name", name)
                put("manufacturer", Build.MANUFACTURER)
                put("model", Build.MODEL)
                put("os_version", Build.VERSION.RELEASE)
                put("app_version", BuildConfig.VERSION_NAME)
            })
            settings.syncedDeviceName = name
        }
        return webhookId
    }
}
