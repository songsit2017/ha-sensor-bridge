package dev.haos.nativeapp.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.haos.nativeapp.data.Settings
import dev.haos.nativeapp.ha.HaAuth
import dev.haos.nativeapp.ha.HaClient
import dev.haos.nativeapp.ha.Registrar
import dev.haos.nativeapp.ha.SetupLink
import dev.haos.nativeapp.ui.theme.ThemeMode
import dev.haos.nativeapp.update.InstallResult
import dev.haos.nativeapp.update.UpdateInfo
import dev.haos.nativeapp.update.Updater
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

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

    private val _themeMode = MutableStateFlow(ThemeMode.fromKey(settings.themeMode))
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    fun setThemeMode(mode: ThemeMode) {
        settings.themeMode = mode.key
        _themeMode.value = mode
    }

    private val _setupUrl = MutableStateFlow<String?>(null)
    /** An HA address that arrived from an invite link, waiting for the user to confirm it. */
    val setupUrl: StateFlow<String?> = _setupUrl.asStateFlow()

    fun handleSetupLink(uri: Uri) {
        SetupLink.parse(uri.toString())?.let { _setupUrl.value = it }
    }

    fun consumeSetupUrl() {
        _setupUrl.value = null
    }

    private val _nameStatus = MutableStateFlow<String?>(null)
    val nameStatus: StateFlow<String?> = _nameStatus.asStateFlow()

    init {
        // Look for a new release every time the app opens; failures stay quiet until asked.
        checkForUpdate(silent = true)
        // Push a changed device name (or newly added sensors) to HA for already-registered phones.
        if (settings.isConfigured) viewModelScope.launch { runCatching { Registrar.ensureRegistered(settings) } }
    }

    /** Name to show in HA; used on the sign-in screen before the phone is registered. */
    fun setDeviceName(name: String) {
        settings.deviceName = name
    }

    /** Rename an already-registered phone in HA. */
    fun renameDevice(name: String) {
        settings.deviceName = name
        viewModelScope.launch {
            _nameStatus.value = try {
                Registrar.ensureRegistered(settings)
                "บันทึกชื่อแล้ว"
            } catch (e: Exception) {
                "บันทึกไม่สำเร็จ: ${e.message}"
            }
        }
    }

    /** A silent check never changes what's on screen unless it finds something, so it can run on every resume. */
    fun checkForUpdate(silent: Boolean = false) {
        val current = _update.value
        if (current is UpdateState.Checking || current is UpdateState.Downloading) return
        if (silent && current is UpdateState.Available) return
        viewModelScope.launch {
            if (!silent) _update.value = UpdateState.Checking
            val result = try {
                updater.check()?.let { UpdateState.Available(it) } ?: UpdateState.UpToDate
            } catch (e: Exception) {
                if (silent) null else UpdateState.Failed(e.message ?: e.javaClass.simpleName)
            }
            if (result != null) _update.value = result
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

    private fun normalizeUrl(raw: String) = raw.trim().trimEnd('/')

    /** Opens HA's own login page in the browser; the result returns through [handleAuthCallback]. */
    fun startLogin(baseUrl: String) {
        val url = normalizeUrl(baseUrl)
        if (url != settings.baseUrl) settings.clearRegistration()
        settings.baseUrl = url
        val state = UUID.randomUUID().toString()
        settings.pendingAuthState = state
        _error.value = null
        try {
            getApplication<Application>().startActivity(
                Intent(Intent.ACTION_VIEW, HaAuth.authorizeUrl(url, state)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: Exception) {
            _error.value = "เปิดหน้าล็อกอินไม่ได้: ${e.message}"
        }
    }

    /** Called when the browser redirects to hasensorbridge://auth-callback?code=...&state=... */
    fun handleAuthCallback(uri: Uri) {
        if (uri.scheme != HaAuth.REDIRECT_SCHEME) return
        val expected = settings.pendingAuthState
        settings.pendingAuthState = null
        val code = uri.getQueryParameter("code")
        if (expected == null || uri.getQueryParameter("state") != expected || code == null) {
            _error.value = "ล็อกอินไม่สำเร็จ: ข้อมูลตอบกลับจาก HA ไม่ถูกต้อง"
            return
        }
        viewModelScope.launch {
            _busy.value = true
            _error.value = null
            try {
                val tokens = HaAuth.exchangeCode(settings.baseUrl, code)
                settings.token = ""
                settings.refreshToken = tokens.refreshToken ?: throw IllegalStateException("HA ไม่ส่ง refresh token")
                val client = HaAuth.client(settings)
                client.ping()
                Registrar.ensureRegistered(settings)
                settings.reportingEnabled = true // a fresh sign-in starts sending right away
                _configured.value = true
            } catch (e: Exception) {
                settings.refreshToken = ""
                HaAuth.forget()
                _error.value = "ล็อกอินไม่สำเร็จ: ${e.message}"
            } finally {
                _busy.value = false
            }
        }
    }

    /** Advanced: connect with a long-lived access token instead of logging in. */
    fun connect(baseUrl: String, token: String) {
        viewModelScope.launch {
            _busy.value = true
            _error.value = null
            try {
                val url = normalizeUrl(baseUrl)
                HaClient(url, token.trim()).ping()
                if (url != settings.baseUrl) settings.clearRegistration()
                settings.baseUrl = url
                settings.refreshToken = ""
                HaAuth.forget()
                settings.token = token
                Registrar.ensureRegistered(settings)
                settings.reportingEnabled = true // a fresh sign-in starts sending right away
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
        settings.refreshToken = ""
        HaAuth.forget()
        settings.clearRegistration()
        _configured.value = false
    }

    fun updateReporting(intervalMs: Long, threshold: Float) {
        settings.reportIntervalMs = intervalMs
        settings.motionThreshold = threshold
    }
}
