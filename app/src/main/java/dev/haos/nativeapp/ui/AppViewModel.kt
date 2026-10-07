package dev.haos.nativeapp.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.haos.nativeapp.data.Settings
import dev.haos.nativeapp.ha.HaClient
import dev.haos.nativeapp.ha.Registrar
import dev.haos.nativeapp.update.InstallResult
import dev.haos.nativeapp.update.UpdateInfo
import dev.haos.nativeapp.update.Updater
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val info: UpdateInfo) : UpdateState
    data class Downloading(val progress: Float) : UpdateState
    data class Failed(val message: String) : UpdateState
}

class AppViewModel(app: Application) : AndroidViewModel(app) {
    val settings = Settings(app)
    private val updater = Updater(app)

    private val _update = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val update: StateFlow<UpdateState> = _update.asStateFlow()

    init {
        // Look for a new release every time the app opens; failures stay quiet until asked.
        checkForUpdate(silent = true)
    }

    fun checkForUpdate(silent: Boolean = false) {
        if (_update.value is UpdateState.Checking || _update.value is UpdateState.Downloading) return
        viewModelScope.launch {
            _update.value = UpdateState.Checking
            _update.value = try {
                updater.check()?.let { UpdateState.Available(it) } ?: UpdateState.UpToDate
            } catch (e: Exception) {
                if (silent) UpdateState.Idle else UpdateState.Failed(e.message ?: e.javaClass.simpleName)
            }
        }
    }

    fun downloadAndInstall(info: UpdateInfo) {
        viewModelScope.launch {
            _update.value = UpdateState.Downloading(0f)
            try {
                val file = updater.download(info) { _update.value = UpdateState.Downloading(it) }
                _update.value = UpdateState.Available(info)
                if (updater.install(file) == InstallResult.NEEDS_PERMISSION) {
                    _update.value = UpdateState.Failed("อนุญาตให้แอปนี้ติดตั้งแอปได้ แล้วกดอัปเดตอีกครั้ง")
                }
            } catch (e: Exception) {
                _update.value = UpdateState.Failed(e.message ?: e.javaClass.simpleName)
            }
        }
    }

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
