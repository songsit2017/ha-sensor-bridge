package dev.haos.nativeapp.ui

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.haos.nativeapp.sensor.AccelSample
import dev.haos.nativeapp.sensor.AccelerometerReader
import dev.haos.nativeapp.sensor.AccelerometerService
import dev.haos.nativeapp.ui.theme.StatusColors
import java.text.DateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AccelScreen(vm: AppViewModel, tab: MainTab) {
    val context = LocalContext.current
    val reader = remember { AccelerometerReader(context) }
    var sample by remember { mutableStateOf<AccelSample?>(null) }
    val running by AccelerometerService.running.collectAsStateWithLifecycle()
    val lastReport by AccelerometerService.lastReportAt.collectAsStateWithLifecycle()
    val lastError by AccelerometerService.lastError.collectAsStateWithLifecycle()

    var intervalSec by remember { mutableFloatStateOf(vm.settings.reportIntervalMs / 1000f) }
    var threshold by remember { mutableFloatStateOf(vm.settings.motionThreshold) }

    // Live preview while this screen is open, independent of the reporting service.
    LaunchedEffect(reader) {
        if (reader.isAvailable) reader.samples().collect { sample = it }
    }

    // The service's notification is silent and minimised, so the app never asks to post notifications:
    // on Android 13+ that leaves it out of the shade entirely while the service keeps running.
    fun start() {
        vm.updateReporting((intervalSec * 1000).toLong(), threshold)
        AccelerometerService.start(context)
    }

    // Look for a new release every time the app comes to the front.
    LifecycleResumeEffect(Unit) {
        vm.checkForUpdate(silent = true)
        onPauseOrDispose { }
    }

    // Start sending as soon as the app opens, unless the user pressed stop.
    LaunchedEffect(Unit) {
        if (!vm.settings.reportingEnabled || AccelerometerService.running.value) return@LaunchedEffect
        start()
    }

    // One scroll position per tab, so switching tabs doesn't throw the reader back to the top.
    val homeScroll = rememberScrollState()
    val sensorsScroll = rememberScrollState()
    val familyScroll = rememberScrollState()
    val settingsScroll = rememberScrollState()
    val scroll = when (tab) {
        MainTab.HOME -> homeScroll
        MainTab.SENSORS -> sensorsScroll
        MainTab.FAMILY -> familyScroll
        MainTab.SETTINGS -> settingsScroll
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(scroll).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        when (tab) {
            MainTab.HOME -> {
                UpdateBanner(vm)
                StatusCard(
                    vm = vm,
                    running = running,
                    lastReport = lastReport,
                    lastError = lastError,
                    onStart = ::start,
                    onStop = { AccelerometerService.stop(context) },
                )

                SectionHeader("ค่าสดตอนนี้")
                AppCard(title = "ความเร่งและการเอียง", subtitle = "อ่านจากเซนเซอร์ในเครื่องแบบเรียลไทม์") {
                    val s = sample
                    if (!reader.isAvailable) {
                        Text("เครื่องนี้ไม่มีเซนเซอร์ความเร่ง")
                    } else if (s == null) {
                        Text("กำลังอ่านค่า...", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        KeyValueRow("X / Y / Z (m/s²)", "%.2f / %.2f / %.2f".format(Locale.US, s.x, s.y, s.z), mono = true)
                        KeyValueRow("ความเร่งรวม", "%.2f".format(Locale.US, s.magnitude), mono = true)
                        KeyValueRow("การเคลื่อนที่", "%.2f".format(Locale.US, s.linear), mono = true)
                        KeyValueRow("เอียงหน้า-หลัง", "%.1f°".format(Locale.US, s.pitch), mono = true)
                        KeyValueRow("เอียงซ้าย-ขวา", "%.1f°".format(Locale.US, s.roll), mono = true)
                        KeyValueRow("หน้าจอ", if (s.z < -7f) "คว่ำอยู่" else "หงายอยู่")
                    }
                }

                PrivacyFooter()
            }

            MainTab.SENSORS -> {
                SensorsCard(vm)

                SectionHeader("การส่งข้อมูล")
                AppCard(
                    title = "ความถี่และความไว",
                    subtitle = if (running) "หยุดส่งก่อนถึงจะเปลี่ยนได้" else null,
                ) {
                    Text("ส่งทุก ${"%.1f".format(Locale.US, intervalSec)} วินาที", style = MaterialTheme.typography.bodyMedium)
                    Slider(
                        value = intervalSec, onValueChange = { intervalSec = it },
                        valueRange = 0.5f..30f, enabled = !running,
                    )
                    Text(
                        "เกณฑ์ตรวจจับการเคลื่อนที่ ${"%.1f".format(Locale.US, threshold)} m/s²",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Slider(
                        value = threshold, onValueChange = { threshold = it },
                        valueRange = 0.2f..10f, enabled = !running,
                    )
                }
            }

            MainTab.FAMILY -> {
                DeviceNameCard(vm)

                SectionHeader("ชวนสมาชิก")
                InviteCard(vm)
            }

            MainTab.SETTINGS -> {
                SectionHeader("การทำงานเบื้องหลัง")
                BatteryCard()

                SectionHeader("การแสดงผล")
                ThemeCard(vm)

                SectionHeader("แอป")
                UpdateCard(vm)

                SectionHeader("ขั้นสูง")
                LogCard(vm)
            }
        }
    }
}

enum class MainTab(val label: String) {
    HOME("หน้าหลัก"),
    SENSORS("เซนเซอร์"),
    FAMILY("ครอบครัว"),
    SETTINGS("ตั้งค่า"),
}

@Composable
private fun PrivacyFooter() {
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            Icons.Filled.Lock, contentDescription = null,
            modifier = Modifier.size(16.dp).padding(top = 2.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "  ข้อมูลเซนเซอร์ส่งไปที่ Home Assistant ของคุณเท่านั้น ไม่มี analytics และไม่มีเซิร์ฟเวอร์กลาง " +
                "(ติดต่อ GitHub เฉพาะตอนตรวจอัปเดต)",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

private enum class LinkState { LIVE, CONNECTING, PROBLEM, STOPPED }

@Composable
private fun StatusCard(
    vm: AppViewModel,
    running: Boolean,
    lastReport: Long?,
    lastError: String?,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    val state = when {
        !running -> LinkState.STOPPED
        lastError != null -> LinkState.PROBLEM
        lastReport == null -> LinkState.CONNECTING
        else -> LinkState.LIVE
    }
    val ok = StatusColors.ok()
    val warn = StatusColors.warning()
    val muted = MaterialTheme.colorScheme.outline
    val (color, icon: ImageVector, title) = when (state) {
        LinkState.LIVE -> Triple(ok, Icons.Filled.CheckCircle, "กำลังส่งข้อมูลเข้า Home Assistant")
        LinkState.CONNECTING -> Triple(ok, Icons.Filled.PlayArrow, "กำลังเชื่อมต่อ...")
        LinkState.PROBLEM -> Triple(warn, Icons.Filled.Warning, "ส่งไม่สำเร็จ กำลังลองใหม่")
        LinkState.STOPPED -> Triple(muted, Icons.Filled.PlayArrow, "หยุดส่งอยู่")
    }
    val host = remember(vm.settings.baseUrl) { Uri.parse(vm.settings.baseUrl).host ?: vm.settings.baseUrl }

    AppCard(container = MaterialTheme.colorScheme.secondaryContainer) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(28.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
                lastError?.takeIf { state == LinkState.PROBLEM }?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
            StatusDot(color, pulsing = state == LinkState.LIVE || state == LinkState.CONNECTING)
        }
        KeyValueRow("ปลายทาง", host)
        KeyValueRow("ชื่อเครื่อง", vm.settings.deviceName)
        KeyValueRow(
            "ส่งล่าสุด",
            lastReport?.let { DateFormat.getTimeInstance().format(Date(it)) } ?: "—",
        )
        if (running) {
            OutlinedButton(onClick = onStop, modifier = Modifier.fillMaxWidth()) { Text("หยุดส่ง") }
        } else {
            Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) { Text("เริ่มส่ง") }
        }
    }
}

/** Shown at the very top as soon as a newer release is found, so nobody has to go and press "check". */
@Composable
private fun UpdateBanner(vm: AppViewModel) {
    val state by vm.update.collectAsStateWithLifecycle()
    when (val s = state) {
        is UpdateState.Available -> AppCard(container = MaterialTheme.colorScheme.primaryContainer) {
            Text(
                "มีเวอร์ชันใหม่ ${s.info.versionName}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Button(onClick = { vm.downloadAndInstall(s.info) }, modifier = Modifier.fillMaxWidth()) { Text("อัปเดตเลย") }
        }
        is UpdateState.Downloading -> AppCard(container = MaterialTheme.colorScheme.primaryContainer) {
            Text(
                "กำลังดาวน์โหลดอัปเดต ${(s.progress * 100).toInt()}%",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            LinearProgressIndicator(progress = { s.progress }, modifier = Modifier.fillMaxWidth())
        }
        else -> Unit
    }
}
