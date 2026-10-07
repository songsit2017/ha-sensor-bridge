package dev.haos.nativeapp.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.haos.nativeapp.data.Settings
import dev.haos.nativeapp.ha.HaClient
import dev.haos.nativeapp.ha.Registrar
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AppViewModel(app: Application) : AndroidViewModel(app) {
    val settings = Settings(app)

    private val _configured = MutableStateFlow(settings.isConfigured)
    val configured: StateFlow<Boolean> = _configured.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    /** Validates URL + token, registers the phone with mobile_app, then saves. */
    fun connect(baseUrl: String, token: String) {
        viewModelScope.launch {
            _busy.value = true
            _error.value = null
            try {
                val url = baseUrl.trim().trimEnd('/')
                HaClient(url, token.trim()).ping()
                if (url != settings.baseUrl) settings.clearRegistration()
                settings.baseUrl = url
                settings.token = token
                Registrar.ensureRegistered(settings)
                _configured.value = true
            } catch (e: Exception) {
                _error.value = "เชื่อมต่อไม่สำเร็จ: ${e.message}"
            } finally {
                _busy.value = false
            }
        }
    }

    fun signOut() {
        settings.token = ""
        settings.clearRegistration()
        _configured.value = false
    }

    fun updateReporting(intervalMs: Long, threshold: Float) {
        settings.reportIntervalMs = intervalMs
        settings.motionThreshold = threshold
    }
}
