package dev.haos.nativeapp.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import dev.haos.nativeapp.sensor.BatterySettings

@Composable
fun BatteryCard() {
    val context = LocalContext.current
    var exempt by remember { mutableStateOf(BatterySettings.isExempt(context)) }
    val vendor = remember { BatterySettings.detectVendor() }

    // Re-check when returning from the system dialog or settings page.
    LifecycleResumeEffect(Unit) {
        exempt = BatterySettings.isExempt(context)
        onPauseOrDispose { }
    }

    AppCard(
        title = "กันระบบปิดแอปตอนประหยัดแบต",
        subtitle = if (exempt) "ยกเว้นแล้ว แอปทำงานเบื้องหลังได้ต่อเนื่อง" else null,
    ) {
        Text(
            "แอปทำงานเบื้องหลังแบบเงียบ ไม่ขึ้นแจ้งเตือน Android จะแสดงแอปนี้ในรายการ \"แอปที่กำลังทำงาน\" เท่านั้น",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!exempt) {
            Text(
                "ระบบอาจหยุดแอปตอนปิดจอหรือประหยัดแบต ค่าที่ส่งเข้า HA จะหยุดไป",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(onClick = { BatterySettings.requestExemption(context) }) { Text("ขอยกเว้นการประหยัดแบต") }
        }
        vendor?.let {
            Text("เครื่อง ${it.label} มีตัวจำกัดเพิ่มอีกชั้น", style = MaterialTheme.typography.titleSmall)
            Text(it.steps, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick = { BatterySettings.openVendorSettings(context, it) }) {
                Text("เปิดหน้าตั้งค่าของ ${it.label.substringBefore(' ')}")
            }
        }
        OutlinedButton(onClick = { BatterySettings.openAppInfo(context) }) { Text("เปิดหน้าข้อมูลแอป") }
    }
}
