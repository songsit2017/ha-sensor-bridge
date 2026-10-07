package dev.haos.nativeapp.ha

import android.net.Uri
import dev.haos.nativeapp.data.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

data class OAuthTokens(val accessToken: String, val refreshToken: String?, val expiresInSec: Long)

/**
 * "Log in with your Home Assistant account": HA's OAuth2/IndieAuth flow, the same one the
 * Companion app uses. The user signs in on HA's own page, HA redirects back with a one-time
 * code, and the code is exchanged for a short-lived access token plus a long-lived refresh
 * token. Each person therefore signs in with their own HA user.
 */
object HaAuth {
    /**
     * IndieAuth requires the client_id to be a URL. For a custom-scheme redirect_uri, HA
     * fetches that URL and looks for `<link rel="redirect_uri" href="...">`, so this page
     * (auth-client.html in the repo) declares our redirect. It must be publicly reachable
     * from the HA server, which is why the repo has to be public.
     */
    const val CLIENT_ID =
        "https://raw.githubusercontent.com/songsit2017/ha-sensor-bridge/main/auth-client.html"
    const val REDIRECT_URI = "hasensorbridge://auth-callback"
    const val REDIRECT_SCHEME = "hasensorbridge"

    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val lock = Mutex()
    private var cachedAccess: String? = null
    private var cachedExpiresAt = 0L

    fun authorizeUrl(baseUrl: String, state: String): Uri =
        Uri.parse("$baseUrl/auth/authorize").buildUpon()
            .appendQueryParameter("client_id", CLIENT_ID)
            .appendQueryParameter("redirect_uri", REDIRECT_URI)
            .appendQueryParameter("state", state)
            .build()

    suspend fun exchangeCode(baseUrl: String, code: String): OAuthTokens = tokenRequest(
        baseUrl,
        FormBody.Builder()
            .add("grant_type", "authorization_code")
            .add("code", code)
            .add("client_id", CLIENT_ID)
            .build(),
    ).also { remember(it) }

    /** Access token to use right now: refreshed automatically, or the long-lived token. */
    suspend fun accessToken(settings: Settings): String = lock.withLock {
        val refresh = settings.refreshToken
        if (refresh.isEmpty()) return@withLock settings.token

        val cached = cachedAccess
        if (cached != null && System.currentTimeMillis() < cachedExpiresAt - 60_000) return@withLock cached

        val tokens = tokenRequest(
            settings.baseUrl,
            FormBody.Builder()
                .add("grant_type", "refresh_token")
                .add("refresh_token", refresh)
                .add("client_id", CLIENT_ID)
                .build(),
        )
        remember(tokens)
        tokens.accessToken
    }

    fun client(settings: Settings): HaClient = HaClient(settings.baseUrl) { accessToken(settings) }

    fun forget() {
        cachedAccess = null
        cachedExpiresAt = 0
    }

    private fun remember(t: OAuthTokens) {
        cachedAccess = t.accessToken
        cachedExpiresAt = System.currentTimeMillis() + t.expiresInSec * 1000
    }

    private suspend fun tokenRequest(baseUrl: String, form: FormBody): OAuthTokens =
        withContext(Dispatchers.IO) {
            val request = Request.Builder().url("$baseUrl/auth/token").post(form).build()
            http.newCall(request).execute().use { r ->
                val text = r.body?.string().orEmpty()
                if (!r.isSuccessful) throw HaException("เข้าสู่ระบบไม่สำเร็จ (HTTP ${r.code}): ${text.take(160)}")
                val json = Json.parseToJsonElement(text).jsonObject
                OAuthTokens(
                    accessToken = json["access_token"]?.jsonPrimitive?.content
                        ?: throw HaException("HA ไม่ส่ง access_token"),
                    refreshToken = json["refresh_token"]?.jsonPrimitive?.content,
                    expiresInSec = json["expires_in"]?.jsonPrimitive?.content?.toLongOrNull() ?: 1800,
                )
            }
        }
}
