package dev.haos.nativeapp.data

import android.content.Context
import android.os.Build
import android.provider.Settings as AndroidSettings

/** The name the phone's owner gave the device, falling back to the model if none is set. */
object DeviceName {
    fun detect(context: Context): String {
        val resolver = context.contentResolver
        val candidates = listOf<() -> String?>(
            { AndroidSettings.Global.getString(resolver, AndroidSettings.Global.DEVICE_NAME) },
            { AndroidSettings.Secure.getString(resolver, "bluetooth_name") },
        )
        return candidates
            .firstNotNullOfOrNull { read -> runCatching(read).getOrNull()?.trim()?.takeIf { it.isNotEmpty() } }
            ?: Build.MODEL
    }
}
