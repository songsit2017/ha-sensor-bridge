package dev.haos.nativeapp.ha

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

data class Registration(val webhookId: String)

class HaException(message: String) : IOException(message)

/**
 * Talks to Home Assistant's REST API (bearer token from [tokenProvider]) and to the
 * mobile_app webhook (which needs no token once the device is registered).
 */
class HaClient(
    private val baseUrl: String,
    private val tokenProvider: suspend () -> String,
) {
    /** Fixed token, e.g. a long-lived access token. */
    constructor(baseUrl: String, token: String) : this(baseUrl, { token })

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    /** GET /api/ — returns HA's "API running." message; used to validate URL + token. */
    suspend fun ping(): String = get("/api/").let {
        json.parseToJsonElement(it).jsonObject["message"]?.jsonPrimitive?.content ?: it
    }

    /**
     * Registers this phone as a device through the mobile_app integration, exactly as the
     * Companion app does. HA then shows it under Settings → Devices with our sensors.
     */
    suspend fun registerDevice(
        deviceId: String,
        deviceName: String,
        manufacturer: String,
        model: String,
        osVersion: String,
        appVersion: String,
    ): Registration {
        val body = buildJsonObject {
            put("device_id", deviceId)
            put("app_id", "dev.haos.nativeapp")
            put("app_name", "HA Sensor Bridge")
            put("app_version", appVersion)
            put("device_name", deviceName)
            put("manufacturer", manufacturer)
            put("model", model)
            put("os_name", "Android")
            put("os_version", osVersion)
            put("supports_encryption", false)
        }
        val response = post("/api/mobile_app/registrations", body, auth = true)
        val webhookId = json.parseToJsonElement(response).jsonObject["webhook_id"]
            ?.jsonPrimitive?.content
            ?: throw HaException("Registration response had no webhook_id")
        return Registration(webhookId)
    }

    /** POST to /api/webhook/{id}. [type] is e.g. "register_sensor" or "update_sensor_states". */
    suspend fun webhook(webhookId: String, type: String, data: JsonElement): String {
        val body = buildJsonObject {
            put("type", type)
            put("data", data)
        }
        return post("/api/webhook/$webhookId", body, auth = false)
    }

    private suspend fun get(path: String): String = execute(
        Request.Builder().url(baseUrl + path).header("Authorization", "Bearer ${tokenProvider()}").get().build()
    )

    private suspend fun post(path: String, body: JsonObject, auth: Boolean): String {
        val builder = Request.Builder()
            .url(baseUrl + path)
            .post(json.encodeToString(JsonObject.serializer(), body).toRequestBody(jsonType))
        if (auth) builder.header("Authorization", "Bearer ${tokenProvider()}")
        return execute(builder.build())
    }

    private suspend fun execute(request: Request): String = withContext(Dispatchers.IO) {
        http.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            when {
                response.code == 401 -> throw HaException("Token was rejected (401)")
                response.code == 410 -> throw HaException("Device registration is gone (410)")
                !response.isSuccessful -> throw HaException("HTTP ${response.code}: ${text.take(200)}")
            }
            text
        }
    }

}
