package dev.haos.nativeapp.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun DeviceNameCard(vm: AppViewModel) {
    var name by remember { mutableStateOf(vm.settings.deviceName) }
    val status by vm.nameStatus.collectAsStateWithLifecycle()

    AppCard(title = "ชื่อเครื่องใน Home Assistant", subtitle = "เซนเซอร์ทั้งหมดจะขึ้นต้นด้วยชื่อนี้") {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        FilledTonalButton(onClick = { vm.renameDevice(name) }, enabled = name.isNotBlank()) { Text("บันทึกชื่อ") }
        status?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
    }
}
