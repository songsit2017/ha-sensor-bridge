package dev.haos.nativeapp.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import dev.haos.nativeapp.BuildConfig
import dev.haos.nativeapp.ha.HaException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val downloadUrl: String,
    /** Hex SHA-256 of the APK as reported by GitHub, when available. */
    val sha256: String?,
)

enum class InstallResult { STARTED, NEEDS_PERMISSION }

/**
 * Self-updater: reads the latest GitHub Release of this app's (public) repo, downloads the
 * APK and hands it to the system installer. Android only installs it if it is signed with
 * the same key as the installed app, so a tampered download can't replace the app.
 */
class Updater(private val context: Context) {
    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * Returns the newer release, or null if this build is already the latest. GitHub's API allows
     * only 60 anonymous calls an hour per address, so when it refuses the check falls back to
     * following the public "latest release" redirect, which has no such limit.
     */
    suspend fun check(): UpdateInfo? = try {
        checkViaApi()
    } catch (e: HaException) {
        if (e.message?.contains("404") == true || e.message?.contains("Release") == true) throw e
        checkViaRedirect()
    } catch (e: java.io.IOException) {
        checkViaRedirect()
    }

    private suspend fun checkViaRedirect(): UpdateInfo? = withContext(Dispatchers.IO) {
        val client = http.newBuilder().followRedirects(false).build()
        val request = Request.Builder().url("https://github.com/$REPO/releases/latest").build()
        val location = client.newCall(request).execute().use { r -> r.header("Location").orEmpty() }
        val tag = location.substringAfter("/releases/tag/", "").substringBefore('?').trim('/')
        val code = tag.removePrefix("v").toIntOrNull() ?: throw HaException("ตรวจเวอร์ชันล่าสุดไม่ได้")
        if (code <= BuildConfig.VERSION_CODE) return@withContext null
        UpdateInfo(
            versionCode = code,
            versionName = "0.1.$code",
            downloadUrl = "https://github.com/$REPO/releases/download/$tag/ha-sensor-bridge.apk",
            sha256 = null, // not in the redirect; Android still verifies the signing key on install
        )
    }

    private suspend fun checkViaApi(): UpdateInfo? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://api.github.com/repos/$REPO/releases/latest")
            .header("Accept", "application/vnd.github+json")
            .build()
        val body = http.newCall(request).execute().use { r ->
            when {
                r.code == 404 -> throw HaException("ไม่พบ Release (repo ต้องเป็น public และมี Release แล้ว)")
                !r.isSuccessful -> throw HaException("GitHub HTTP ${r.code}")
            }
            r.body?.string().orEmpty()
        }
        val release = Json.parseToJsonElement(body).jsonObject
        val tag = release["tag_name"]?.jsonPrimitive?.content.orEmpty()
        // CI tags releases v<versionCode>.
        val remoteCode = tag.removePrefix("v").toIntOrNull()
            ?: throw HaException("Release tag ไม่ถูกต้อง: $tag")
        if (remoteCode <= BuildConfig.VERSION_CODE) return@withContext null

        val apk = release["assets"]?.jsonArray
            ?.map { it.jsonObject }
            ?.firstOrNull { it["name"]?.jsonPrimitive?.content?.endsWith(".apk") == true }
            ?: throw HaException("Release นี้ไม่มีไฟล์ APK")
        UpdateInfo(
            versionCode = remoteCode,
            versionName = release["name"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() } ?: tag,
            downloadUrl = apk["browser_download_url"]!!.jsonPrimitive.content,
            sha256 = apk["digest"]?.jsonPrimitive?.content?.removePrefix("sha256:"),
        )
    }

    /** Downloads the APK into the app cache, reporting progress 0..1, and verifies its hash. */
    suspend fun download(info: UpdateInfo, onProgress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, "update.apk")
        val digest = MessageDigest.getInstance("SHA-256")

        http.newCall(Request.Builder().url(info.downloadUrl).build()).execute().use { r ->
            if (!r.isSuccessful) throw HaException("ดาวน์โหลดไม่สำเร็จ HTTP ${r.code}")
            val body = r.body ?: throw HaException("ดาวน์โหลดไม่สำเร็จ")
            val total = body.contentLength()
            var done = 0L
            body.byteStream().use { input ->
                file.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        digest.update(buf, 0, n)
                        done += n
                        if (total > 0) onProgress(done.toFloat() / total)
                    }
                }
            }
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        if (info.sha256 != null && !actual.equals(info.sha256, ignoreCase = true)) {
            file.delete()
            throw HaException("ไฟล์อัปเดตเสียหาย (checksum ไม่ตรง)")
        }
        file
    }

    /** Opens the system installer. The first time, the user must allow installs from this app. */
    fun install(file: File): InstallResult {
        if (!context.packageManager.canRequestPackageInstalls()) {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return InstallResult.NEEDS_PERMISSION
        }
        val uri = FileProvider.getUriForFile(context, "$APPLICATION_ID.fileprovider", file)
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        return InstallResult.STARTED
    }

    private companion object {
        const val REPO = "songsit2017/ha-sensor-bridge"
        const val APPLICATION_ID = "dev.haos.nativeapp"
    }
}
