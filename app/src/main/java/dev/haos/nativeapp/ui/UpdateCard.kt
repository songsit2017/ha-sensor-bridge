package dev.haos.nativeapp.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.haos.nativeapp.BuildConfig

@Composable
fun UpdateCard(vm: AppViewModel) {
    val state by vm.update.collectAsStateWithLifecycle()

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("เวอร์ชัน ${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})", style = MaterialTheme.typography.titleMedium)
            when (val s = state) {
                UpdateState.Idle -> OutlinedButton(onClick = { vm.checkForUpdate() }) { Text("ตรวจสอบอัปเดต") }
                UpdateState.Checking -> Text("กำลังตรวจสอบอัปเดต...")
                UpdateState.UpToDate -> {
                    Text("เป็นเวอร์ชันล่าสุดแล้ว")
                    OutlinedButton(onClick = { vm.checkForUpdate() }) { Text("ตรวจสอบอีกครั้ง") }
                }
                is UpdateState.Available -> {
                    Text("มีเวอร์ชันใหม่: ${s.info.versionName}")
                    Button(onClick = { vm.downloadAndInstall(s.info) }) { Text("ดาวน์โหลดและติดตั้ง") }
                }
                is UpdateState.Downloading -> {
                    Text("กำลังดาวน์โหลด ${(s.progress * 100).toInt()}%")
                    LinearProgressIndicator(progress = { s.progress }, modifier = Modifier.fillMaxWidth())
                }
                is UpdateState.Failed -> {
                    Text(s.message, color = MaterialTheme.colorScheme.error)
                    OutlinedButton(onClick = { vm.checkForUpdate() }) { Text("ลองอีกครั้ง") }
                }
            }
        }
    }
}
