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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.graphics.asImageBitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import net.melisma.relay.db.AppDatabase
import net.melisma.relay.db.MessageWithParts
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.coroutineScope
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
// remove duplicate AppDatabase import
import net.melisma.relay.data.MessageRepository
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
    LaunchedEffect(Unit) { AppLogger.d("PermissionsScreen first compose") }
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
			permissionsGranted = readGranted && receiveGranted && receiveMmsGranted && receiveWapGranted
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
		permissionsGranted = read && receive && receiveMms && receiveWap
        AppLogger.i("Initial permissions READ_SMS=$read, RECEIVE_SMS=$receive, RECEIVE_MMS=$receiveMms, RECEIVE_WAP_PUSH=$receiveWap, all=$permissionsGranted")
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(all = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (!permissionsGranted) {
            Text(text = "Permission status: Denied")
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
        }

        val scope = rememberCoroutineScope()
        // Centralize in ViewModel
        val viewModel = remember(context) { MainViewModel(context.applicationContext as android.app.Application) }

        if (permissionsGranted) {
            // Auto-ingest on first render (initial full sync handled in repository when last==0)
            LaunchedEffect("auto_ingest") {
                viewModel.ingestFromProviders()
            }
            // Periodic ingest every 10 seconds while in foreground
            val lifecycleOwner = LocalLifecycleOwner.current
            LaunchedEffect("periodic_ingest", permissionsGranted) {
                if (permissionsGranted) {
                    AppLogger.i("Starting periodic ingest while in foreground")
                    lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                        coroutineScope {
                            // Register content observers for sms/mms
                            val cr = context.contentResolver
                            val handler = Handler(Looper.getMainLooper())
                            val smsObserver = object : ContentObserver(handler) {
                                override fun onChange(selfChange: Boolean, uri: Uri?) {
                                    AppLogger.d("ContentObserver SMS changed uri=$uri")
                                    scope.launch { viewModel.ingestFromProviders() }
                                }
                            }
                            val mmsObserver = object : ContentObserver(handler) {
                                override fun onChange(selfChange: Boolean, uri: Uri?) {
                                    AppLogger.d("ContentObserver MMS changed uri=$uri")
                                    scope.launch { viewModel.ingestFromProviders() }
                                }
                            }
                            try {
                                cr.registerContentObserver(android.provider.Telephony.Sms.CONTENT_URI, true, smsObserver)
                                cr.registerContentObserver(android.provider.Telephony.Mms.CONTENT_URI, true, mmsObserver)
                                AppLogger.i("Registered content observers for SMS/MMS")
                            } catch (t: Throwable) {
                                AppLogger.e("Registering content observers failed", t)
                            }
                            while (true) {
                                try {
                                    AppLogger.d("Periodic ingest tick")
                                    viewModel.ingestFromProviders()
                                } catch (t: Throwable) {
                                    AppLogger.e("Periodic ingest failed", t)
                                }
                                delay(10_000L)
                            }
                        }
                    }
                }
            }
            Button(onClick = {
                AppLogger.i("Manual scan: SMS/MMS/RCS")
                scope.launch {
                    viewModel.ingestFromProviders()
                    AppLogger.i("Manual scan ingested into DB")
                }
            }) {
                Text("Scan SMS/MMS/RCS")
            }
        }

        

        if (permissionsGranted) {
            val itemsWithParts by viewModel.messages.collectAsState()
            LaunchedEffect(itemsWithParts.size) {
                AppLogger.d("Rendering messages list size=${itemsWithParts.size}")
            }
            LazyColumn {
                items(itemsWithParts) { row ->
                    val item = row.message
                    val ts = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(java.util.Date(item.timestamp))
                    Text(text = "$ts [${item.kind}] ${item.address ?: ""}: ${item.body ?: ""}")
                    val imgPart = row.parts.firstOrNull { it.isImage == true && it.data != null }
                    if (imgPart != null) {
                        val bmp = remember(imgPart.partId) { BitmapFactory.decodeByteArray(imgPart.data, 0, imgPart.data!!.size) }
                        if (bmp != null) {
                            Image(bitmap = bmp.asImageBitmap(), contentDescription = null, modifier = Modifier.height(100.dp))
                        }
                    }
                }
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