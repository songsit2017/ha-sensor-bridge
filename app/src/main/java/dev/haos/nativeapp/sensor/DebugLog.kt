package dev.haos.nativeapp.sensor

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import dev.haos.nativeapp.data.Settings
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.Executors

/**
 * Optional on-device log for tuning the detectors. It only ever holds sensor numbers and
 * event names: no HA address, token or audio. Off by default; the file is capped (two
 * rotating ~1 MB files) and only leaves the phone when the user shares it.
 */
object DebugLog {
    @Volatile var enabled = false

    private class Point(val t: Long, val x: Float, val y: Float, val z: Float, val linear: Float)

    private const val MAX_FILE_BYTES = 1_000_000L
    private const val RING_SIZE = 300 // ~6 s at the game sampling rate
    private const val TRACE_COOLDOWN_MS = 2_000L

    private val ring = ArrayDeque<Point>(RING_SIZE + 1)
    private val io = Executors.newSingleThreadExecutor()
    private val clock = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS").withZone(ZoneId.systemDefault())
    private var dir: File? = null
    private var lastTraceAt = 0L

    /** Safe to call repeatedly. */
    fun init(context: Context) {
        dir = File(context.applicationContext.filesDir, "logs").apply { mkdirs() }
        enabled = Settings(context).logEnabled
    }

    fun setEnabled(context: Context, on: Boolean) {
        Settings(context).logEnabled = on
        init(context)
        if (on) log("LOG", "enabled")
    }

    fun sample(s: AccelSample) {
        if (!enabled) return
        synchronized(ring) {
            ring.addLast(Point(System.currentTimeMillis(), s.x, s.y, s.z, s.linear))
            if (ring.size > RING_SIZE) ring.removeFirst()
        }
    }

    fun log(tag: String, message: String) {
        if (!enabled) return
        val line = "${clock.format(Instant.now())} $tag $message\n"
        val d = dir ?: return
        io.execute {
            runCatching {
                val f = File(d, "sensor.log")
                if (f.length() > MAX_FILE_BYTES) {
                    File(d, "sensor.log.1").delete()
                    f.renameTo(File(d, "sensor.log.1"))
                }
                File(d, "sensor.log").appendText(line)
            }
        }
    }

    /** A detected event: logs the numbers behind it plus the raw accelerometer trace leading up to it. */
    fun event(tag: String, message: String) {
        if (!enabled) return
        log(tag, message)
        val now = System.currentTimeMillis()
        if (now - lastTraceAt < TRACE_COOLDOWN_MS) return
        lastTraceAt = now
        val points = synchronized(ring) { ring.toList() }
        if (points.isEmpty()) return
        val sb = StringBuilder("trace t_ms x y z linear\n")
        val t0 = points.last().t
        for (p in points) {
            sb.append(p.t - t0).append(' ')
                .append("%.2f %.2f %.2f %.2f".format(java.util.Locale.US, p.x, p.y, p.z, p.linear)).append('\n')
        }
        log("TRACE", "after $tag (${points.size} samples, t=0 is now)\n" + sb.toString().trimEnd())
    }

    /** The user says what they just did, so detections can be compared with what really happened. */
    fun mark(label: String) {
        if (!enabled) return
        lastTraceAt = 0L
        event("MARK", label)
    }

    fun sizeBytes(): Long = dir?.listFiles()?.sumOf { it.length() } ?: 0L

    fun clear() {
        dir?.listFiles()?.forEach { it.delete() }
        if (enabled) log("LOG", "cleared")
    }

    /** Opens the share sheet with both log files joined, oldest first. */
    fun share(context: Context, header: String) {
        val d = dir ?: return
        val out = File(context.cacheDir, "export").apply { mkdirs() }.resolve("ha-sensor-bridge-log.txt")
        out.writeText(header + "\n")
        for (name in listOf("sensor.log.1", "sensor.log")) {
            val f = File(d, name)
            if (f.exists()) out.appendText(f.readText())
        }
        val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", out)
        context.startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_STREAM, uri)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                null,
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
