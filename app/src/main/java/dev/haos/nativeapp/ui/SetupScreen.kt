package dev.haos.nativeapp.ui

import androidx.compose.foundation.background
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
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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

@Composable
fun SetupScreen(vm: AppViewModel) {
    val busy by vm.busy.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    var url by rememberSaveable { mutableStateOf(vm.settings.baseUrl.ifEmpty { "http://homeassistant.local:8123" }) }
    var token by rememberSaveable { mutableStateOf("") }
    var deviceName by rememberSaveable { mutableStateOf(vm.settings.deviceName) }
    var advanced by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(top = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                Modifier.size(72.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Home, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(36.dp))
            }
            Text("HA Sensor Bridge", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Text(
                "ส่งค่าเซนเซอร์ของมือถือเข้า Home Assistant ของคุณเอง ใช้คู่กับแอป Companion ได้เลย",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }

        AppCard(title = "ข้อมูลของคุณอยู่ที่บ้านคุณ") {
            Promise("ข้อมูลเซนเซอร์ส่งไปที่เซิร์ฟเวอร์ HA ของคุณเท่านั้น")
            Promise("ไม่มี analytics และไม่มีเซิร์ฟเวอร์กลางของแอปนี้")
            Promise("ล็อกอินด้วยบัญชี HA ของคุณเอง token เก็บเข้ารหัสในเครื่อง")
        }

        AppCard(title = "เชื่อมต่อ Home Assistant") {
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
