package dev.haos.nativeapp.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.haos.nativeapp.sensor.AccelSample
import dev.haos.nativeapp.sensor.AccelerometerReader
import dev.haos.nativeapp.sensor.AccelerometerService
import java.text.DateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AccelScreen(vm: AppViewModel) {
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

    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { AccelerometerService.start(context) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        UpdateCard(vm)

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("ค่าความเร่งตอนนี้ (m/s²)", style = MaterialTheme.typography.titleMedium)
                if (!reader.isAvailable) {
                    Text("เครื่องนี้ไม่มีเซนเซอร์ความเร่ง")
                } else sample?.let { s ->
                    Axis("X", s.x); Axis("Y", s.y); Axis("Z", s.z)
                    Axis("|a|", s.magnitude)
                    Axis("เคลื่อนที่", s.linear)
                    Axis("เอียงหน้า-หลัง", s.pitch)
                    Axis("เอียงซ้าย-ขวา", s.roll)
                    Text(if (s.z < -7f) "คว่ำหน้าจออยู่" else "หงายหน้าจออยู่")
                }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("ส่งค่าเข้า Home Assistant", style = MaterialTheme.typography.titleMedium)
                Text("ส่งทุก ${"%.1f".format(Locale.US, intervalSec)} วินาที")
                Slider(
                    value = intervalSec, onValueChange = { intervalSec = it },
                    valueRange = 0.5f..30f, enabled = !running,
                )
                Text("เกณฑ์ตรวจจับการเคลื่อนที่ ${"%.1f".format(Locale.US, threshold)} m/s²")
                Slider(
                    value = threshold, onValueChange = { threshold = it },
                    valueRange = 0.2f..10f, enabled = !running,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (running) {
                        OutlinedButton(onClick = { AccelerometerService.stop(context) }) { Text("หยุดส่ง") }
                    } else {
                        Button(onClick = {
                            vm.updateReporting((intervalSec * 1000).toLong(), threshold)
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                AccelerometerService.start(context)
                            }
                        }) { Text("เริ่มส่ง") }
                    }
                }
                lastReport?.let {
                    Text("ส่งล่าสุด ${DateFormat.getTimeInstance().format(Date(it))}")
                }
                lastError?.let { Text("ผิดพลาด: $it", color = MaterialTheme.colorScheme.error) }
            }
        }
    }
}

@Composable
private fun Axis(label: String, value: Float) {
    Text(
        "%-10s %8.3f".format(Locale.US, label, value),
        fontFamily = FontFamily.Monospace,
    )
}
