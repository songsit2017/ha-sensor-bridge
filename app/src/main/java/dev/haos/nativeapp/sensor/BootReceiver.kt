package dev.haos.nativeapp.sensor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.haos.nativeapp.data.Settings

/** Brings the reporting service back after a reboot or an app update, if the user had it on. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val settings = Settings(context)
        if (settings.isConfigured && settings.reportingEnabled) AccelerometerService.start(context, allowMic = false) // no mic from the background
    }
}
