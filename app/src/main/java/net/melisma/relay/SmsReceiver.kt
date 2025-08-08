package net.melisma.relay

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        AppLogger.d("SmsReceiver.onReceive action='${intent.action}'")
        if (Telephony.Sms.Intents.SMS_RECEIVED_ACTION == intent.action) {
            AppLogger.i("SmsReceiver handling SMS_RECEIVED")
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            AppLogger.d("SmsReceiver parsed ${messages.size} messages from intent")
            val now = System.currentTimeMillis()
            for (msg in messages) {
                val sender = msg.displayOriginatingAddress ?: ""
                val body = msg.messageBody ?: ""
                AppLogger.d("SmsReceiver message part from '${sender}' length=${body.length}")
                SmsInMemoryStore.addMessage(
                    SmsItem(
                        sender = sender,
                        body = body,
                        timestamp = now
                    )
                )
            }
            AppLogger.i("SmsReceiver finished enqueuing messages into store")
        }
    }
}


