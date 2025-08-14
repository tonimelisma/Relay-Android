package net.melisma.relay

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.melisma.relay.data.MessageRepository
import net.melisma.relay.db.AppDatabase

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        AppLogger.d("SmsReceiver.onReceive action='${intent.action}'")
        if (Telephony.Sms.Intents.SMS_RECEIVED_ACTION == intent.action) {
            AppLogger.i("SmsReceiver handling SMS_RECEIVED → trigger DB ingest")
            val pending = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val db = AppDatabase.getInstance(context)
                    val repo = MessageRepository(db.messageDao())
                    repo.ingestFromProviders(context.contentResolver)
                    AppLogger.i("SmsReceiver DB ingest complete")
                } catch (t: Throwable) {
                    AppLogger.e("SmsReceiver DB ingest failed", t)
                } finally {
                    pending.finish()
                }
            }
        }
    }
}


