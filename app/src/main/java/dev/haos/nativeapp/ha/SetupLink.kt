package dev.haos.nativeapp.ha

import android.net.Uri

/**
 * The family invite: a link that carries only the Home Assistant address, never a password or
 * token. Each person still signs in with their own HA account.
 */
object SetupLink {
    private const val SCHEME = "hasensorbridge"
    const val HOST = "setup"

    fun build(baseUrl: String): String = "$SCHEME://$HOST?url=${Uri.encode(baseUrl)}"

    /** Accepts the invite link or a plain http(s) address; returns the normalised HA address, or null. */
    fun parse(text: String): String? {
        val raw = text.trim()
        val candidate = if (raw.startsWith("$SCHEME://", ignoreCase = true)) {
            val link = Uri.parse(raw)
            if (link.host != HOST) return null
            link.getQueryParameter("url") ?: return null
        } else raw
        return normalize(candidate)
    }

    private fun normalize(value: String): String? {
        val trimmed = value.trim()
        val uri = Uri.parse(trimmed)
        val scheme = uri.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") return null
        if (uri.host.isNullOrBlank() || uri.userInfo != null) return null
        return trimmed.trimEnd('/')
    }
}
