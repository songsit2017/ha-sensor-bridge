package dev.haos.nativeapp.data

import android.content.Context
import androidx.core.content.edit
import dev.haos.nativeapp.sensor.SensorGroup
import java.util.UUID

/**
 * Connection settings and mobile_app registration state.
 *
 * The token is stored encrypted with a key held in the Android Keystore (see [TokenCipher]).
 */
class Settings(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = context.getSharedPreferences("haos_native", Context.MODE_PRIVATE)

    var baseUrl: String
        get() = prefs.getString(KEY_BASE_URL, "") ?: ""
        set(value) = prefs.edit { putString(KEY_BASE_URL, value.trim().trimEnd('/')) }

    var token: String
        get() = prefs.getString(KEY_TOKEN_ENC, null)?.let(TokenCipher::decrypt) ?: ""
        set(value) = prefs.edit {
            val trimmed = value.trim()
            if (trimmed.isEmpty()) remove(KEY_TOKEN_ENC) else putString(KEY_TOKEN_ENC, TokenCipher.encrypt(trimmed))
        }

    /** OAuth refresh token from "log in with HA account"; empty when using a long-lived token. */
    var refreshToken: String
        get() = prefs.getString(KEY_REFRESH_ENC, null)?.let(TokenCipher::decrypt) ?: ""
        set(value) = prefs.edit {
            val trimmed = value.trim()
            if (trimmed.isEmpty()) remove(KEY_REFRESH_ENC) else putString(KEY_REFRESH_ENC, TokenCipher.encrypt(trimmed))
        }

    /** Random value sent to /auth/authorize and checked when the browser redirects back. */
    var pendingAuthState: String?
        get() = prefs.getString(KEY_AUTH_STATE, null)
        set(value) = prefs.edit { if (value == null) remove(KEY_AUTH_STATE) else putString(KEY_AUTH_STATE, value) }

    /** Webhook id returned by /api/mobile_app/registrations; null until registered. */
    var webhookId: String?
        get() = prefs.getString(KEY_WEBHOOK_ID, null)
        set(value) = prefs.edit { putString(KEY_WEBHOOK_ID, value) }

    /** Version of the sensor set last registered in HA; lets new sensors register after an app update. */
    var registeredSensorVersion: Int
        get() = prefs.getInt(KEY_SENSOR_VERSION, 0)
        set(value) = prefs.edit { putInt(KEY_SENSOR_VERSION, value) }

    /** How often accelerometer values are pushed to HA. */
    var reportIntervalMs: Long
        get() = prefs.getLong(KEY_INTERVAL, 2_000L)
        set(value) = prefs.edit { putLong(KEY_INTERVAL, value.coerceAtLeast(500L)) }

    /** Linear acceleration (m/s², gravity removed) above which motion is reported. */
    var motionThreshold: Float
        get() = prefs.getFloat(KEY_THRESHOLD, 1.5f)
        set(value) = prefs.edit { putFloat(KEY_THRESHOLD, value) }

    /** True unless the user pressed stop; the app and the boot receiver start the service from this. */
    /** Whether the tuning log is being recorded; off unless the user turns it on. */
    var logEnabled: Boolean
        get() = prefs.getBoolean(KEY_LOG, false)
        set(value) = prefs.edit { putBoolean(KEY_LOG, value) }

    var reportingEnabled: Boolean
        get() = prefs.getBoolean(KEY_REPORTING, true)
        set(value) = prefs.edit { putBoolean(KEY_REPORTING, value) }

    fun isEnabled(group: SensorGroup): Boolean = prefs.getBoolean("sensor_${group.key}", group.defaultOn)

    fun setEnabled(group: SensorGroup, on: Boolean) = prefs.edit { putBoolean("sensor_${group.key}", on) }

    fun enabledGroups(): Set<SensorGroup> = SensorGroup.values().filter(::isEnabled).toSet()

    /** Name shown in HA. Blank means "use the name the owner set on the phone". */
    var deviceName: String
        get() = prefs.getString(KEY_DEVICE_NAME, null)?.takeIf { it.isNotBlank() } ?: DeviceName.detect(appContext)
        set(value) = prefs.edit { putString(KEY_DEVICE_NAME, value.trim()) }

    /** Name HA currently has, so a changed name can be sent with update_registration. */
    var syncedDeviceName: String?
        get() = prefs.getString(KEY_SYNCED_NAME, null)
        set(value) = prefs.edit { if (value == null) remove(KEY_SYNCED_NAME) else putString(KEY_SYNCED_NAME, value) }

    /** Stable per-install id; also used as the prefix for sensor unique_ids. */
    val deviceId: String
        get() = prefs.getString(KEY_DEVICE_ID, null) ?: UUID.randomUUID().toString().also {
            prefs.edit { putString(KEY_DEVICE_ID, it) }
        }

    val isConfigured: Boolean
        get() = baseUrl.isNotEmpty() && (refreshToken.isNotEmpty() || token.isNotEmpty())

    fun clearRegistration() = prefs.edit {
        remove(KEY_WEBHOOK_ID)
        remove(KEY_SENSOR_VERSION)
        remove(KEY_SYNCED_NAME)
    }

    private companion object {
        const val KEY_BASE_URL = "base_url"
        const val KEY_TOKEN_ENC = "token_enc"
        const val KEY_REFRESH_ENC = "refresh_token_enc"
        const val KEY_AUTH_STATE = "auth_state"
        const val KEY_WEBHOOK_ID = "webhook_id"
        const val KEY_SENSOR_VERSION = "sensor_set_version"
        const val KEY_INTERVAL = "report_interval_ms"
        const val KEY_THRESHOLD = "motion_threshold"
        const val KEY_REPORTING = "reporting_enabled"
        const val KEY_LOG = "debug_log"
        const val KEY_DEVICE_NAME = "device_name"
        const val KEY_SYNCED_NAME = "synced_device_name"
        const val KEY_DEVICE_ID = "device_id"
    }
}
