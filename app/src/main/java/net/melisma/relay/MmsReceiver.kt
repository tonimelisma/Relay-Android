package net.melisma.relay

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony

class MmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        val type = intent.type
        AppLogger.d("MmsReceiver.onReceive action='$action' type='$type'")
        if (Telephony.Sms.Intents.WAP_PUSH_RECEIVED_ACTION == action &&
            type == "application/vnd.wap.mms-message") {
            AppLogger.i("MmsReceiver received WAP_PUSH for MMS notification")
            // We don't download parts; just surface a minimal entry
            val now = System.currentTimeMillis()
            SmsInMemoryStore.addMessage(
                SmsItem(
                    sender = "<mms>",
                    body = "MMS received",
                    timestamp = now,
                    kind = MessageKind.MMS
                )
            )
            AppLogger.i("MmsReceiver appended placeholder MMS item to store")
        }
    }
}


