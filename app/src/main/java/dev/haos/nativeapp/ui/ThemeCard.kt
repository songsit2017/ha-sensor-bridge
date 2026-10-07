package dev.haos.nativeapp.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.unit.dp
import dev.haos.nativeapp.ui.theme.ThemeMode

@Composable
fun ThemeCard(vm: AppViewModel) {
    val mode by vm.themeMode.collectAsStateWithLifecycle()
    AppCard(title = "ธีม", subtitle = "เลือกโหมดสว่างหรือมืด หรือให้ตามระบบ") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (option in ThemeMode.values()) {
                FilterChip(
                    selected = mode == option,
                    onClick = { vm.setThemeMode(option) },
                    label = { Text(option.label) },
                )
            }
        }
    }
}
