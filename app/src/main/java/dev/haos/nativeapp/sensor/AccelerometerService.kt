package dev.haos.nativeapp.sensor

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import dev.haos.nativeapp.MainActivity
import dev.haos.nativeapp.data.Settings
import dev.haos.nativeapp.ha.AccelSensors
import dev.haos.nativeapp.ha.HaClient
import dev.haos.nativeapp.ha.HaException
import dev.haos.nativeapp.ha.SensorReading
import dev.haos.nativeapp.ha.Registrar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.max

/**
 * Foreground service that keeps reading the accelerometer while the screen is off and
 * pushes the latest values to Home Assistant every [Settings.reportIntervalMs].
 */
class AccelerometerService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Volatile private var latest: AccelSample? = null
    @Volatile private var peakLinear = 0f
    @Volatile private var shakeUntil = 0L
    @Volatile private var faceDown = false

    private val shakePeaks = ArrayDeque<Long>()
    private var lastPeakAt = 0L

    /** Lets a shake or flip be sent right away instead of waiting for the next interval. */
    private val sendNow = Channel<Unit>(Channel.CONFLATED)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (_running.value) return START_STICKY

        startInForeground()
        _running.value = true
        _lastError.value = null

        val settings = Settings(this)
        scope.launch {
            AccelerometerReader(this@AccelerometerService).samples().collect { s ->
                latest = s
                peakLinear = max(peakLinear, s.linear)
                detectEvents(s)
            }
        }
        scope.launch { reportLoop(settings) }
        return START_STICKY
    }

    private suspend fun reportLoop(settings: Settings) {
        while (scope.isActive) {
            try {
                val webhookId = Registrar.ensureRegistered(settings)
                val client = HaClient(settings.baseUrl, settings.token)
                while (scope.isActive) {
                    withTimeoutOrNull(settings.reportIntervalMs) { sendNow.receive() }
                    val sample = latest ?: continue
                    val peak = peakLinear
                    peakLinear = 0f
                    AccelSensors.update(
                        client, webhookId, settings.deviceId,
                        SensorReading(
                            sample = sample,
                            moving = peak >= settings.motionThreshold,
                            peakLinear = peak,
                            shaking = SystemClock.elapsedRealtime() < shakeUntil,
                            faceDown = faceDown,
                        ),
                    )
                    _lastReportAt.value = System.currentTimeMillis()
                    _lastError.value = null
                }
            } catch (e: HaException) {
                // 410 means the device was deleted in HA: register again on the next pass.
                if (e.message?.contains("410") == true) settings.clearRegistration()
                onError(e)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                onError(e)
            }
        }
    }

    /** Shake = 3+ hard jolts within a second. Face-down uses hysteresis so it doesn't flicker. */
    private fun detectEvents(s: AccelSample) {
        val now = SystemClock.elapsedRealtime()
        if (s.linear > SHAKE_LINEAR && now - lastPeakAt > SHAKE_MIN_GAP_MS) {
            lastPeakAt = now
            shakePeaks.addLast(now)
            while (shakePeaks.first() < now - SHAKE_WINDOW_MS) shakePeaks.removeFirst()
            if (shakePeaks.size >= SHAKE_PEAKS) {
                shakePeaks.clear()
                shakeUntil = now + SHAKE_HOLD_MS
                sendNow.trySend(Unit)
            }
        }
        val down = if (faceDown) s.z < -5f else s.z < -7f
        if (down != faceDown) {
            faceDown = down
            sendNow.trySend(Unit)
        }
    }

    private suspend fun onError(e: Exception) {
        Log.w(TAG, "Report failed", e)
        _lastError.value = e.message ?: e.javaClass.simpleName
        delay(RETRY_DELAY_MS)
    }

    private fun startInForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Accelerometer reporting", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, AccelerometerService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE
        )
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentTitle("Sending accelerometer to Home Assistant")
            .setOngoing(true)
            .setContentIntent(open)
            .addAction(0, "Stop", stop)
            .build()
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else 0
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
    }

    override fun onDestroy() {
        scope.cancel()
        _running.value = false
        super.onDestroy()
    }

    companion object {
        private const val TAG = "AccelService"
        private const val CHANNEL_ID = "accel"
        private const val NOTIFICATION_ID = 1
        private const val RETRY_DELAY_MS = 10_000L
        private const val SHAKE_LINEAR = 9f // m/s² of motion beyond gravity
        private const val SHAKE_PEAKS = 3
        private const val SHAKE_WINDOW_MS = 1_000L
        private const val SHAKE_MIN_GAP_MS = 120L
        private const val SHAKE_HOLD_MS = 3_000L
        private const val ACTION_STOP = "dev.haos.nativeapp.STOP"

        private val _running = MutableStateFlow(false)
        val running: StateFlow<Boolean> = _running.asStateFlow()

        private val _lastReportAt = MutableStateFlow<Long?>(null)
        val lastReportAt: StateFlow<Long?> = _lastReportAt.asStateFlow()

        private val _lastError = MutableStateFlow<String?>(null)
        val lastError: StateFlow<String?> = _lastError.asStateFlow()

        fun start(context: Context) =
            ContextCompat.startForegroundService(context, Intent(context, AccelerometerService::class.java))

        fun stop(context: Context) =
            context.stopService(Intent(context, AccelerometerService::class.java))
    }
}
