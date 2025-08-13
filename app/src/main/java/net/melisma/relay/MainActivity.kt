package net.melisma.relay

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import net.melisma.relay.ui.theme.RelayTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppLogger.i("MainActivity.onCreate")
        enableEdgeToEdge()
        setContent {
            RelayTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    PermissionsScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@Composable
private fun PermissionsScreen(modifier: Modifier = Modifier) {
    AppLogger.d("PermissionsScreen composed")
    var permissionsGranted by remember { mutableStateOf(false) }
    val context = LocalContext.current

    // Launcher for requesting multiple permissions
    val requestPermissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { results ->
            AppLogger.d("Permissions result: $results")
            val readGranted = results[Manifest.permission.READ_SMS] == true
            val receiveGranted = results[Manifest.permission.RECEIVE_SMS] == true
            val receiveMmsGranted = results[Manifest.permission.RECEIVE_MMS] == true
            val receiveWapGranted = results[Manifest.permission.RECEIVE_WAP_PUSH] == true
            permissionsGranted = readGranted && receiveGranted
            AppLogger.i("Permissions updated. READ_SMS=$readGranted, RECEIVE_SMS=$receiveGranted, RECEIVE_MMS=$receiveMmsGranted, RECEIVE_WAP_PUSH=$receiveWapGranted, all=$permissionsGranted")
        }
    )

    // Check current permission state on first composition
    LaunchedEffect(context) {
        AppLogger.d("Checking initial permission state")
        val read = ContextCompat.checkSelfPermission(
            context, Manifest.permission.READ_SMS
        ) == PackageManager.PERMISSION_GRANTED
        val receive = ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECEIVE_SMS
        ) == PackageManager.PERMISSION_GRANTED
        val receiveMms = ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECEIVE_MMS
        ) == PackageManager.PERMISSION_GRANTED
        val receiveWap = ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECEIVE_WAP_PUSH
        ) == PackageManager.PERMISSION_GRANTED
        permissionsGranted = read && receive
        AppLogger.i("Initial permissions READ_SMS=$read, RECEIVE_SMS=$receive, RECEIVE_MMS=$receiveMms, RECEIVE_WAP_PUSH=$receiveWap, all=$permissionsGranted")
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(all = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(text = "Permission status: ${if (permissionsGranted) "Granted" else "Denied"}")
        Button(onClick = {
            AppLogger.i("Requesting SMS permissions")
            requestPermissionsLauncher.launch(
                arrayOf(
                    Manifest.permission.READ_SMS,
                    Manifest.permission.RECEIVE_SMS,
                    Manifest.permission.RECEIVE_MMS,
                    Manifest.permission.RECEIVE_WAP_PUSH
                )
            )
        }) {
            Text("Request SMS/MMS Permissions")
        }

        val scope = rememberCoroutineScope()
        Button(onClick = {
            AppLogger.i("Manual scan: SMS/MMS/RCS")
            scope.launch {
                val sms = MessageScanner.scanSms(context.contentResolver)
                val mms = MessageScanner.scanMms(context.contentResolver)
                val rcs = MessageScanner.scanRcsHeuristics(context.contentResolver)
                AppLogger.i("Manual scan found sms=${sms.size} mms=${mms.size} rcs=${rcs.size}")
                (sms + mms + rcs).forEach { SmsInMemoryStore.addMessage(it) }
            }
        }) {
            Text("Scan SMS/MMS/RCS")
        }

        val items by SmsInMemoryStore.messages.collectAsState(initial = emptyList())
        AppLogger.d("Rendering messages list size=${items.size}")
        LazyColumn {
            items(items = items) { item ->
                val prefix = when (item.kind) {
                    MessageKind.SMS -> "SMS"
                    MessageKind.MMS -> "MMS"
                    MessageKind.RCS -> "RCS"
                }
                Text(text = "[$prefix] ${item.sender}: ${item.body}")
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PermissionsScreenPreview() {
    RelayTheme {
        PermissionsScreen()
    }
}