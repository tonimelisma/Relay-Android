package net.melisma.relay

import android.app.ActivityManager
import android.app.Application
import android.app.ApplicationExitInfo
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class RelayApp : Application() {
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    override fun onCreate() {
        super.onCreate()
        AppLogger.init(this)
        // Prime IM provider availability cache once per process start
        ImProviderGate.prime(this)
        logHistoricalExitReasons()
    }

    private fun logHistoricalExitReasons() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val am = getSystemService(ACTIVITY_SERVICE) as ActivityManager
                val reasons = am.getHistoricalProcessExitReasons(packageName, 0, 0)
                if (reasons.isNotEmpty()) {
                    val lastLoggedTs = getSharedPreferences("app_meta", MODE_PRIVATE)
                        .getLong("lastExitLoggedTs", 0L)
                    val mostRecent = reasons.maxByOrNull { it.timestamp }
                    if (mostRecent != null && mostRecent.timestamp > lastLoggedTs) {
                        val reason = mostRecent.reason
                        val description = mostRecent.description
                        val timestamp = mostRecent.timestamp
                        AppLogger.w("App exited at $timestamp, reason=$reason, description=$description", tag = "AppExitTracker")
                        if (reason == ApplicationExitInfo.REASON_CRASH ||
                            reason == ApplicationExitInfo.REASON_CRASH_NATIVE ||
                            reason == ApplicationExitInfo.REASON_ANR
                        ) {
                            try {
                                val trace = mostRecent.traceInputStream?.bufferedReader().use { it?.readText() }
                                if (!trace.isNullOrBlank()) {
                                    AppLogger.e("Exit trace available:\n$trace", tag = "AppExitTracker")
                                }
                            } catch (t: Throwable) {
                                AppLogger.e("Error reading exit trace", t, tag = "AppExitTracker")
                            }
                        }
                        getSharedPreferences("app_meta", MODE_PRIVATE)
                            .edit()
                            .putLong("lastExitLoggedTs", mostRecent.timestamp)
                            .apply()
                    }
                }
            }
        } catch (t: Throwable) {
            AppLogger.e("Failed to log historical exit reasons", t, tag = "AppExitTracker")
        }
    }
}


