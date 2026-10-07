package dev.haos.nativeapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.haos.nativeapp.sensor.AccelerometerService
import dev.haos.nativeapp.ui.AccelScreen
import dev.haos.nativeapp.ui.AppViewModel
import dev.haos.nativeapp.ui.SetupScreen

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
                Surface {
                    val configured by vm.configured.collectAsStateWithLifecycle()
                    if (configured) BridgeScaffold(vm)
                    else Scaffold { p -> Surface(Modifier.padding(p)) { SetupScreen(vm) } }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BridgeScaffold(vm: AppViewModel) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sensor Bridge") },
                actions = {
                    TextButton(onClick = {
                        AccelerometerService.stop(vm.getApplication<android.app.Application>())
                        vm.signOut()
                    }) { Text("ออกจากระบบ") }
                },
            )
        },
    ) { padding ->
        Surface(Modifier.padding(padding)) { AccelScreen(vm) }
    }
}
