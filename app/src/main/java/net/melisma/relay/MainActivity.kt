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
import kotlinx.coroutines.coroutineScope
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
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
        // Background sync scheduling is handled by BootReceiver and onStart() health check
    }

    override fun onStart() {
        super.onStart()
        checkSyncWorkerStatus()
        checkStandbyBucketAndNotify()
    }

    private fun checkSyncWorkerStatus() {
        val wm = androidx.work.WorkManager.getInstance(this)
        wm.getWorkInfosForUniqueWorkLiveData(MessageSyncWorker.UNIQUE_WORK_NAME)
            .observe(this, androidx.lifecycle.Observer { workInfos ->
                if (workInfos == null || workInfos.isEmpty()) {
                    AppLogger.w("Sync work not scheduled. Enqueuing now.")
                    MessageSyncWorker.schedule(applicationContext)
                    return@Observer
                }
                val info = workInfos.first()
                when (info.state) {
                    androidx.work.WorkInfo.State.CANCELLED -> {
                        AppLogger.w("Sync work was cancelled. Rescheduling.")
                        MessageSyncWorker.schedule(applicationContext)
                    }
                    androidx.work.WorkInfo.State.ENQUEUED -> AppLogger.d("Sync work enqueued and healthy")
                    androidx.work.WorkInfo.State.RUNNING -> AppLogger.d("Sync work running")
                    else -> Unit
                }
            })
    }

    

    private fun checkStandbyBucketAndNotify() {
        val ctx = this
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            val usm = getSystemService(android.content.Context.USAGE_STATS_SERVICE) as android.app.usage.UsageStatsManager
            val bucket = usm.appStandbyBucket
            val label = when (bucket) {
                android.app.usage.UsageStatsManager.STANDBY_BUCKET_ACTIVE -> "Active"
                android.app.usage.UsageStatsManager.STANDBY_BUCKET_WORKING_SET -> "Working Set"
                android.app.usage.UsageStatsManager.STANDBY_BUCKET_FREQUENT -> "Frequent"
                android.app.usage.UsageStatsManager.STANDBY_BUCKET_RARE -> "Rare"
                android.app.usage.UsageStatsManager.STANDBY_BUCKET_RESTRICTED -> "Restricted"
                else -> "Unknown"
            }
            AppLogger.i("Current standby bucket: $label")
            if (label == "Rare" || label == "Restricted") {
                try {
                    android.widget.Toast.makeText(ctx, "Background sync may be limited ($label)", android.widget.Toast.LENGTH_LONG).show()
                } catch (_: Throwable) { }
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
        // Last sync time
        run {
            val ts = LocalContext.current.getSharedPreferences("app_meta", android.content.Context.MODE_PRIVATE)
                .getLong("lastSyncSuccessTs", 0L)
            val text = if (ts > 0L) {
                val fmt = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
                "Last synced: ${fmt.format(java.util.Date(ts))}"
            } else {
                "Last synced: —"
            }
            Text(text = text)
        }
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
                val t0 = System.currentTimeMillis()
                AppLogger.d("Auto-ingest kickoff @${t0}")
                viewModel.ingestFromProviders()
            }
            // Foreground ingest via content observers only (no polling)
            val lifecycleOwner = LocalLifecycleOwner.current
            LaunchedEffect("observers_only", permissionsGranted) {
                if (permissionsGranted) {
                    lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                        coroutineScope {
                            val cr = context.contentResolver
                            val handler = Handler(Looper.getMainLooper())
                            val smsObserver = object : ContentObserver(handler) {
                                override fun onChange(selfChange: Boolean, uri: Uri?) {
                                    AppLogger.d("ContentObserver SMS changed uri=$uri ts=${System.currentTimeMillis()}")
                                    scope.launch { viewModel.ingestFromProviders() }
                                }
                            }
                            val mmsObserver = object : ContentObserver(handler) {
                                override fun onChange(selfChange: Boolean, uri: Uri?) {
                                    AppLogger.d("ContentObserver MMS changed uri=$uri ts=${System.currentTimeMillis()}")
                                    scope.launch { viewModel.ingestFromProviders() }
                                }
                            }
                            // Best-effort: observe Samsung RCS if accessible and allowed by gate
                            val rcsObserver = object : ContentObserver(handler) {
                                override fun onChange(selfChange: Boolean, uri: Uri?) {
                                    AppLogger.d("ContentObserver RCS changed uri=$uri ts=${System.currentTimeMillis()}")
                                    scope.launch { viewModel.ingestFromProviders() }
                                }
                            }
                            try {
                                cr.registerContentObserver(android.provider.Telephony.Sms.CONTENT_URI, true, smsObserver)
                                cr.registerContentObserver(android.provider.Telephony.Mms.CONTENT_URI, true, mmsObserver)
                                if (ImProviderGate.shouldUseIm(context.applicationContext)) {
                                    try {
                                        cr.registerContentObserver(Uri.parse("content://im/chat"), true, rcsObserver)
                                    } catch (t: Throwable) {
                                        AppLogger.w("RCS content observer registration failed: ${t.message}")
                                        ImProviderGate.markUnavailable(context.applicationContext)
                                    }
                                } else {
                                    AppLogger.w("RCS observer skipped: IM provider gate is disabled")
                                }
                                AppLogger.i("Registered content observers for SMS/MMS/RCS where available")
                            } catch (t: Throwable) {
                                AppLogger.e("Registering content observers failed", t)
                            }
                        }
                    }
                }
            }
        }

        

        if (permissionsGranted) {
            val messages by viewModel.messages.collectAsState()
            LaunchedEffect(messages.size) {
                AppLogger.d("Rendering messages list size=${messages.size}")
            }
            LazyColumn {
                items(messages) { message ->
                    val ts = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(java.util.Date(message.timestamp))
                    Text(text = "$ts [${message.kind}] ${message.sender}: ${message.body ?: ""}")
                    val imgPart = message.parts.firstOrNull { it.type == MessagePartType.IMAGE && it.data != null }
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
 