package net.melisma.relay

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import kotlinx.coroutines.launch
import net.melisma.relay.data.MessageRepository
import net.melisma.relay.db.AppDatabase

class MmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        val type = intent.type
        AppLogger.d("MmsReceiver.onReceive action='$action' type='$type'")
        if (Telephony.Sms.Intents.WAP_PUSH_RECEIVED_ACTION == action &&
            type == "application/vnd.wap.mms-message") {
            AppLogger.i("MmsReceiver received WAP_PUSH → enqueue one-time sync")
            try {
                MessageSyncWorker.enqueueOneTime(context.applicationContext)
            } catch (t: Throwable) {
                AppLogger.e("MmsReceiver enqueue failed", t)
            }
        }
    }
}


