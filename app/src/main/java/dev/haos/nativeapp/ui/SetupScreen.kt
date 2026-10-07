package dev.haos.nativeapp.ui

import android.net.Uri
import androidx.compose.foundation.background
import dev.haos.nativeapp.R
import dev.haos.nativeapp.BuildConfig
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.scale
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.haos.nativeapp.ha.SetupLink

@Composable
fun SetupScreen(vm: AppViewModel) {
    val busy by vm.busy.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    var url by rememberSaveable { mutableStateOf(vm.settings.baseUrl.ifEmpty { "http://homeassistant.local:8123" }) }
    var token by rememberSaveable { mutableStateOf("") }
    var deviceName by rememberSaveable { mutableStateOf(vm.settings.deviceName) }
    var advanced by rememberSaveable { mutableStateOf(false) }
    var scanning by rememberSaveable { mutableStateOf(false) }
    var scanError by rememberSaveable { mutableStateOf<String?>(null) }
    // An address from a scan or an invite link; nothing is opened until the user confirms the host.
    var pendingUrl by rememberSaveable { mutableStateOf<String?>(null) }
    val incoming by vm.setupUrl.collectAsStateWithLifecycle()
    LaunchedEffect(incoming) {
        incoming?.let { pendingUrl = it; vm.consumeSetupUrl() }
    }

    if (scanning) {
        QrScanner(
            onCode = { text ->
                scanning = false
                val parsed = SetupLink.parse(text)
                if (parsed != null) { scanError = null; pendingUrl = parsed }
                else scanError = "QR นี้ไม่ใช่ที่อยู่ Home Assistant"
            },
            onClose = { scanning = false },
        )
        return
    }

    pendingUrl?.let { found ->
        val host = Uri.parse(found).host ?: found
        AlertDialog(
            onDismissRequest = { pendingUrl = null },
            title = { Text("เชื่อมต่อกับเซิร์ฟเวอร์นี้?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(host, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "ตรวจให้แน่ใจว่าเป็น Home Assistant ของครอบครัวคุณ ถ้าไม่ใช่ อย่าใส่รหัสผ่าน",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    url = found
                    pendingUrl = null
                    vm.setDeviceName(deviceName)
                    vm.startLogin(found)
                }) { Text("ล็อกอิน") }
            },
            dismissButton = { TextButton(onClick = { pendingUrl = null }) { Text("ยกเลิก") } },
        )
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding(),
    ) {
        // Header: Home Assistant blue, with the app's mark (a sensor sending out readings).
        Column(
            Modifier.fillMaxWidth()
                .background(
                    Brush.verticalGradient(listOf(Color(0xFF03A9F4), Color(0xFF0277BD))),
                    RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp),
                )
                .statusBarsPadding()
                .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Image(
                painter = painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = null,
                modifier = Modifier.size(104.dp).scale(1.35f),
            )
            Text(
                "HA Sensor Bridge",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
            )
            Text(
                "ส่งค่าเซนเซอร์ของมือถือเข้า Home Assistant ของคุณเอง",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.9f),
                textAlign = TextAlign.Center,
            )
        }

        Column(
            Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionHeader("เริ่มต้นใช้งาน")
            AppCard(title = "เชื่อมต่อ Home Assistant", subtitle = "ใส่ที่อยู่เซิร์ฟเวอร์ แล้วล็อกอินด้วยบัญชี HA ของคุณเอง") {
                OutlinedButton(onClick = { scanError = null; scanning = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("สแกน QR จากสมาชิกที่ตั้งค่าแล้ว")
                }
                scanError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
                Text(
                    "หรือกรอกที่อยู่เอง",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("URL ของ HA") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = deviceName,
                    onValueChange = { deviceName = it },
                    label = { Text("ชื่อเครื่องที่จะแสดงใน HA") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }

                Button(
                    onClick = { vm.setDeviceName(deviceName); vm.startLogin(url) },
                    enabled = !busy && url.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    else Text("เข้าสู่ระบบด้วยบัญชี HA")
                }
                Text(
                    "จะเปิดหน้าล็อกอินของ HA ให้ใส่ชื่อผู้ใช้และรหัสผ่านของคุณเอง ไม่ต้องสร้าง token",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            SectionHeader("ความเป็นส่วนตัว")
            AppCard(title = "ข้อมูลของคุณอยู่ที่บ้านคุณ") {
                Promise("ข้อมูลเซนเซอร์ส่งไปที่เซิร์ฟเวอร์ HA ของคุณเท่านั้น")
                Promise("ไม่มี analytics และไม่มีเซิร์ฟเวอร์กลางของแอปนี้")
                Promise("ล็อกอินด้วยบัญชี HA ของคุณเอง token เก็บเข้ารหัสในเครื่อง")
            }

            TextButton(onClick = { advanced = !advanced }) {
                Text(if (advanced) "ซ่อนตัวเลือกขั้นสูง" else "ขั้นสูง: ใช้ Long-lived access token")
            }
            if (advanced) {
                AppCard(
                    title = "Long-lived access token",
                    subtitle = "สร้างได้ที่ HA → โปรไฟล์ → Security → Long-lived access tokens",
                ) {
                    OutlinedTextField(
                        value = token,
                        onValueChange = { token = it },
                        label = { Text("Long-lived access token") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedButton(
                        onClick = { vm.setDeviceName(deviceName); vm.connect(url, token) },
                        enabled = !busy && url.isNotBlank() && token.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("เชื่อมต่อด้วย token") }
                }
            }

            Text(
                "แอปเสริมอิสระสำหรับ Home Assistant ไม่ใช่แอปทางการของ Home Assistant\n" +
                    "เวอร์ชัน ${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp),
            )
            Spacer(Modifier.navigationBarsPadding())
        }
    }
}

@Composable
private fun Promise(text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
        Icon(
            Icons.Filled.CheckCircle, contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp).padding(top = 1.dp),
        )
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}
