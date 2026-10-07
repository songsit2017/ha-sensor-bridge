package dev.haos.nativeapp.ui

import android.os.Build
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.haos.nativeapp.BuildConfig
import dev.haos.nativeapp.sensor.DebugLog

/** Records sensor numbers around detected events so the thresholds can be tuned on real data. */
@Composable
fun LogCard(vm: AppViewModel) {
    val context = LocalContext.current
    var on by remember { mutableStateOf(vm.settings.logEnabled) }
    var size by remember { mutableLongStateOf(DebugLog.sizeBytes()) }
    var lastMark by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { DebugLog.init(context); size = DebugLog.sizeBytes() }

    AppCard(title = "บันทึก Log เพื่อปรับเกณฑ์") {
        run {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                Text(
                    "เก็บเฉพาะตัวเลขเซนเซอร์ในเครื่อง ไม่มี token/URL/เสียง และไม่ส่งออกจนกว่าจะกดแชร์",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Switch(checked = on, onCheckedChange = {
                    on = it
                    DebugLog.setEnabled(context, it)
                    size = DebugLog.sizeBytes()
                })
            }
            if (on) {
                Text("ทำท่าจริง แล้วกดปุ่มบอกว่าเพิ่งทำอะไร (กดหลังทำเสร็จ)", style = MaterialTheme.typography.bodySmall)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (label in MARKS) OutlinedButton(onClick = {
                        DebugLog.mark(label)
                        lastMark = label
                        size = DebugLog.sizeBytes()
                    }) { Text(label) }
                }
                lastMark?.let { Text("จดแล้ว: $it", style = MaterialTheme.typography.bodySmall) }
                Text("ขนาด Log ${size / 1024} KB", style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        DebugLog.share(
                            context,
                            "HA Sensor Bridge ${BuildConfig.VERSION_NAME} (code ${BuildConfig.VERSION_CODE}) " +
                                "${Build.MANUFACTURER} ${Build.MODEL} Android ${Build.VERSION.RELEASE}\n" +
                                "sensors=${vm.settings.enabledGroups().joinToString { it.key }} " +
                                "interval_ms=${vm.settings.reportIntervalMs} threshold=${vm.settings.motionThreshold}",
                        )
                    }) { Text("แชร์ Log") }
                    OutlinedButton(onClick = { DebugLog.clear(); size = DebugLog.sizeBytes() }) { Text("ล้าง Log") }
                }
            }
        }
    }
}

private val MARKS = listOf("เขย่า", "แตะสองที", "หยิบขึ้น", "ทำตก", "วางคว่ำ", "วางหงาย", "เดินถือ", "ไม่ได้ทำอะไร")
