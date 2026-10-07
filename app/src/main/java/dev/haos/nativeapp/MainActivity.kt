package dev.haos.nativeapp

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import android.net.Uri
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.haos.nativeapp.ha.SetupLink
import dev.haos.nativeapp.sensor.AccelerometerService
import dev.haos.nativeapp.ui.AccelScreen
import dev.haos.nativeapp.ui.AppViewModel
import dev.haos.nativeapp.ui.SetupScreen
import dev.haos.nativeapp.ui.theme.AppTheme

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Cold start via the login redirect (the process was killed while the browser was open).
        if (savedInstanceState == null) route(intent?.data)
        setContent {
            val themeMode by vm.themeMode.collectAsStateWithLifecycle()
            AppTheme(themeMode) {
                Surface {
                    val configured by vm.configured.collectAsStateWithLifecycle()
                    if (configured) BridgeScaffold(vm)
                    else Scaffold { p -> Surface(Modifier.padding(p)) { SetupScreen(vm) } }
                }
            }
        }
    }

    // singleTask: the login redirect from the browser arrives here.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        route(intent.data)
    }

    /** The invite QR and HA's login redirect share a scheme; the host tells them apart. */
    private fun route(uri: Uri?) {
        if (uri == null) return
        if (uri.host == SetupLink.HOST) vm.handleSetupLink(uri) else vm.handleAuthCallback(uri)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BridgeScaffold(vm: AppViewModel) {
    var menuOpen by remember { mutableStateOf(false) }
    var confirmSignOut by remember { mutableStateOf(false) }
    val host = remember(vm.settings.baseUrl) { Uri.parse(vm.settings.baseUrl).host ?: vm.settings.baseUrl }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Sensor Bridge", style = MaterialTheme.typography.titleLarge)
                        Text(host, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "เมนู")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("ออกจากระบบ") },
                                onClick = { menuOpen = false; confirmSignOut = true },
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        Surface(Modifier.padding(padding)) { AccelScreen(vm) }
    }

    if (confirmSignOut) {
        AlertDialog(
            onDismissRequest = { confirmSignOut = false },
            title = { Text("ออกจากระบบ?") },
            text = { Text("จะหยุดส่งข้อมูลและลบการล็อกอินออกจากเครื่องนี้ ต้องล็อกอินใหม่ถึงจะส่งต่อได้") },
            confirmButton = {
                TextButton(onClick = {
                    confirmSignOut = false
                    AccelerometerService.stop(vm.getApplication<android.app.Application>())
                    vm.signOut()
                }) { Text("ออกจากระบบ") }
            },
            dismissButton = { TextButton(onClick = { confirmSignOut = false }) { Text("ยกเลิก") } },
        )
    }
}
