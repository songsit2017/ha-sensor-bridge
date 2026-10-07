package dev.haos.nativeapp.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.haos.nativeapp.sensor.AccelerometerService
import dev.haos.nativeapp.sensor.SensorGroup

/** One switch per sensor group. Changes apply the next time reporting starts. */
@Composable
fun SensorsCard(vm: AppViewModel) {
    val context = LocalContext.current
    val running by AccelerometerService.running.collectAsStateWithLifecycle()
    val state = remember { SensorGroup.values().associateWith { mutableStateOf(vm.settings.isEnabled(it)) } }

    fun set(group: SensorGroup, on: Boolean) {
        state.getValue(group).value = on
        vm.settings.setEnabled(group, on)
    }

    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        set(SensorGroup.SOUND, granted)
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("เซนเซอร์ที่จะส่ง", style = MaterialTheme.typography.titleMedium)
            Text(
                if (running) "หยุดส่งก่อนถึงจะเปลี่ยนได้ — ปิดตัวที่ไม่ใช้เพื่อประหยัดแบต"
                else "ปิดตัวที่ไม่ใช้เพื่อประหยัดแบต",
                style = MaterialTheme.typography.bodySmall,
            )
            for (group in SensorGroup.values()) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        Text(group.label)
                        Text(group.hint, style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(
                        checked = state.getValue(group).value,
                        enabled = !running,
                        onCheckedChange = { on ->
                            if (group == SensorGroup.SOUND && on &&
                                ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) !=
                                PackageManager.PERMISSION_GRANTED
                            ) micPermission.launch(Manifest.permission.RECORD_AUDIO) else set(group, on)
                        },
                    )
                }
            }
        }
    }
}
