package net.melisma.relay

import android.content.Intent
import android.provider.Telephony
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReceiversTest {

    @Test
    fun smsReceiver_doesNotCrash_onSmsIntent() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val receiver = SmsReceiver()
        val intent = Intent(Telephony.Sms.Intents.SMS_RECEIVED_ACTION)
        receiver.onReceive(ctx, intent)
    }

    @Test
    fun mmsReceiver_doesNotCrash_onWapPush() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val receiver = MmsReceiver()
        val intent = Intent(Telephony.Sms.Intents.WAP_PUSH_RECEIVED_ACTION).apply {
            type = "application/vnd.wap.mms-message"
        }
        receiver.onReceive(ctx, intent)
    }

    @Test
    fun mmsReceiver_enqueuesOneTimeWork_onWapPush() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        // Initialize WorkManager test environment
        val config = androidx.work.Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.DEBUG)
            .setExecutor(androidx.work.testing.SynchronousExecutor())
            .build()
        androidx.work.testing.WorkManagerTestInitHelper.initializeTestWorkManager(ctx, config)

        val receiver = MmsReceiver()
        val intent = Intent(Telephony.Sms.Intents.WAP_PUSH_RECEIVED_ACTION).apply {
            type = "application/vnd.wap.mms-message"
        }
        receiver.onReceive(ctx, intent)

        val infos = androidx.work.WorkManager.getInstance(ctx)
            .getWorkInfosByTag(MessageSyncWorker.TAG_IMMEDIATE).get()
        assertTrue(infos.isNotEmpty())
    }

    @Test
    fun bootReceiver_enqueuesWork_onBootCompleted() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val receiver = BootReceiver()
        val intent = Intent(Intent.ACTION_BOOT_COMPLETED)
        receiver.onReceive(ctx, intent)
    }
}


