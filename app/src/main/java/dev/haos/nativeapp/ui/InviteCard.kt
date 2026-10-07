package dev.haos.nativeapp.ui

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.haos.nativeapp.ha.SetupLink

/** Shows a QR code with this Home Assistant's address so another family member can join by scanning. */
@Composable
fun InviteCard(vm: AppViewModel) {
    var show by remember { mutableStateOf(false) }
    val baseUrl = vm.settings.baseUrl
    val host = remember(baseUrl) { Uri.parse(baseUrl).host ?: baseUrl }

    AppCard(
        title = "ชวนสมาชิกครอบครัว",
        subtitle = "ให้สมาชิกสแกน QR เพื่อใส่ที่อยู่ HA ให้อัตโนมัติ ไม่ต้องพิมพ์",
    ) {
        Text(
            "QR มีแค่ที่อยู่ของ HA ไม่มีรหัสผ่านหรือ token สมาชิกแต่ละคนล็อกอินด้วยบัญชี HA ของตัวเอง",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FilledTonalButton(onClick = { show = true }, enabled = baseUrl.isNotBlank()) { Text("แสดง QR") }
    }

    if (show) {
        val bitmap = remember(baseUrl) { QrCode.bitmap(SetupLink.build(baseUrl)).asImageBitmap() }
        val localOnly = remember(baseUrl) {
            baseUrl.startsWith("http://", ignoreCase = true) || host.endsWith(".local", ignoreCase = true)
        }
        AlertDialog(
            onDismissRequest = { show = false },
            confirmButton = { TextButton(onClick = { show = false }) { Text("ปิด") } },
            title = { Text("QR สำหรับสมาชิก") },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = "QR code ของ $host",
                        modifier = Modifier.size(240.dp).background(Color.White).padding(8.dp),
                    )
                    Text(host, style = MaterialTheme.typography.titleSmall)
                    Text(
                        "เปิดแอปนี้ในมือถือของสมาชิก กด \"สแกน QR\" แล้วล็อกอินด้วยบัญชี HA ของเขา",
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                    )
                    if (localOnly) {
                        Text(
                            "ที่อยู่นี้ใช้ได้เฉพาะตอนอยู่ในบ้านหรือต่อ VPN เท่านั้น",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            },
        )
    }
}
