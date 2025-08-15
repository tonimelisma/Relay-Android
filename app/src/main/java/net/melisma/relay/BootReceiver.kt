package net.melisma.relay

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        AppLogger.i("BootReceiver.onReceive action='$action'")
        if (Intent.ACTION_BOOT_COMPLETED == action) {
            MessageSyncWorker.schedule(context.applicationContext)
            AppLogger.i("BootReceiver scheduled MessageSyncWorker after boot")
        }
    }
}


