package dev.haos.nativeapp.sensor

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import dev.haos.nativeapp.MainActivity
import dev.haos.nativeapp.data.Settings
import dev.haos.nativeapp.ha.AccelSensors
import dev.haos.nativeapp.ha.HaAuth
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
    @Volatile private var gyro: Gyro? = null
    @Volatile private var heading: Float? = null
    @Volatile private var soundDb: Float? = null
    @Volatile private var magnetic: Float? = null
    @Volatile private var posture: String? = null
    private val vibrationMeter = VibrationMeter()
    private var enabled: Set<SensorGroup> = emptySet()
    private lateinit var events: MotionEvents

    /** Lets a shake or flip be sent right away instead of waiting for the next interval. */
    private val sendNow = Channel<Unit>(Channel.CONFLATED)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            Settings(this).reportingEnabled = false // the user stopped it: don't restart on boot
            stopSelf()
            return START_NOT_STICKY
        }
        if (_running.value) return START_STICKY

        val settings = Settings(this)
        enabled = settings.enabledGroups()
        val micOk = intent?.getBooleanExtra(EXTRA_MIC_OK, false) == true &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        var useMic = SensorGroup.SOUND in enabled && micOk
        if (!startInForeground(useMic)) useMic = false
        if (!useMic) enabled = enabled - SensorGroup.SOUND
        _running.value = true
        _lastError.value = null

        settings.reportingEnabled = true
        DebugLog.init(this)
        DebugLog.log(
            "START",
            "sensors=${enabled.joinToString { it.key }} interval_ms=${settings.reportIntervalMs} threshold=${settings.motionThreshold} mic=$useMic",
        )
        events = MotionEvents(enabled, DebugLog::event) { sendNow.trySend(Unit) }
        scope.launch {
            AccelerometerReader(this@AccelerometerService).samples().collect { s ->
                latest = s
                peakLinear = max(peakLinear, s.linear)
                DebugLog.sample(s)
                events.process(s)
                if (SensorGroup.VIBRATION in enabled) vibrationMeter.add(s.linear)
                if (SensorGroup.POSTURE in enabled) {
                    val next = Posture.of(s, posture)
                    if (next != posture) { DebugLog.log("POSTURE", "$posture -> $next z=%.2f".format(s.z)); posture = next; sendNow.trySend(Unit) }
                }
            }
        }
        if (SensorGroup.GYRO in enabled) scope.launch { GyroReader.samples(this@AccelerometerService).collect { gyro = it } }
        if (SensorGroup.COMPASS in enabled) scope.launch { HeadingReader.samples(this@AccelerometerService).collect { heading = it } }
        if (SensorGroup.MAGNETIC in enabled) scope.launch { MagneticReader.samples(this@AccelerometerService).collect { magnetic = it } }
        if (useMic) scope.launch { SoundMeter.levels().collect { soundDb = it } }
        scope.launch { reportLoop(settings) }
        return START_STICKY
    }

    private suspend fun reportLoop(settings: Settings) {
        while (scope.isActive) {
            try {
                val webhookId = Registrar.ensureRegistered(settings)
                val client = HaAuth.client(settings)
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
                            shaking = events.shaking,
                            faceDown = events.faceDown,
                            doubleTap = events.doubleTapped,
                            pickUp = events.pickedUp,
                            fall = events.fell,
                            gyro = gyro,
                            heading = heading,
                            soundDb = soundDb,
                            magneticUt = magnetic,
                            vibration = if (SensorGroup.VIBRATION in enabled) vibrationMeter.take() else null,
                            posture = posture,
                            enabled = enabled,
                        ),
                    )
                    DebugLog.log(
                        "REPORT",
                        "linear_peak=%.2f motion=${peak >= settings.motionThreshold} shake=${events.shaking} tap=${events.doubleTapped} pick=${events.pickedUp} fall=${events.fell} posture=$posture".format(peak),
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

    private suspend fun onError(e: Exception) {
        Log.w(TAG, "Report failed", e)
        DebugLog.log("ERROR", e.message ?: e.javaClass.simpleName)
        _lastError.value = e.message ?: e.javaClass.simpleName
        delay(RETRY_DELAY_MS)
    }

    /** Returns false when the microphone type was refused and the service started without it. */
    private fun startInForeground(mic: Boolean): Boolean {
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
        fun typeFor(withMic: Boolean): Int = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE ->
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE or
                    (if (withMic) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0)
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && withMic -> ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            else -> 0
        }
        return try {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, typeFor(mic))
            true
        } catch (e: SecurityException) {
            if (!mic) throw e
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, typeFor(false))
            false
        }
    }

    override fun onDestroy() {
        DebugLog.log("STOP", "service destroyed")
        scope.cancel()
        _running.value = false
        super.onDestroy()
    }

    companion object {
        private const val TAG = "AccelService"
        private const val CHANNEL_ID = "accel"
        private const val NOTIFICATION_ID = 1
        private const val RETRY_DELAY_MS = 10_000L
        private const val EXTRA_MIC_OK = "mic_ok"
        private const val ACTION_STOP = "dev.haos.nativeapp.STOP"

        private val _running = MutableStateFlow(false)
        val running: StateFlow<Boolean> = _running.asStateFlow()

        private val _lastReportAt = MutableStateFlow<Long?>(null)
        val lastReportAt: StateFlow<Long?> = _lastReportAt.asStateFlow()

        private val _lastError = MutableStateFlow<String?>(null)
        val lastError: StateFlow<String?> = _lastError.asStateFlow()

        /** [allowMic] must be false when starting from the background (boot): Android refuses mic access there. */
        fun start(context: Context, allowMic: Boolean = true) =
            ContextCompat.startForegroundService(
                context,
                Intent(context, AccelerometerService::class.java).putExtra(EXTRA_MIC_OK, allowMic),
            )

        fun stop(context: Context) {
            Settings(context).reportingEnabled = false
            context.stopService(Intent(context, AccelerometerService::class.java))
        }
    }
}
