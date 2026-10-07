package dev.haos.nativeapp.ha

import android.os.Build
import dev.haos.nativeapp.BuildConfig
import dev.haos.nativeapp.data.Settings

/** Ensures this phone is registered with mobile_app and its sensors exist in HA. */
object Registrar {

    suspend fun ensureRegistered(settings: Settings): String {
        val client = HaAuth.client(settings)
        val webhookId = settings.webhookId ?: client.registerDevice(
            deviceId = settings.deviceId,
            deviceName = Build.MODEL,
            manufacturer = Build.MANUFACTURER,
            model = Build.MODEL,
            osVersion = Build.VERSION.RELEASE,
            appVersion = BuildConfig.VERSION_NAME,
        ).webhookId.also {
            settings.webhookId = it
            settings.registeredSensorVersion = 0
        }
        if (settings.registeredSensorVersion != AccelSensors.VERSION) {
            AccelSensors.registerAll(client, webhookId, settings.deviceId)
            settings.registeredSensorVersion = AccelSensors.VERSION
        }
        return webhookId
    }
}
