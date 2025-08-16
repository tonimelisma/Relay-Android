package net.melisma.relay

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import net.melisma.relay.data.MessageRepository
import net.melisma.relay.db.AppDatabase
import java.util.concurrent.TimeUnit

class MessageSyncWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        AppLogger.i("MessageSyncWorker.doWork start @${System.currentTimeMillis()}")
        return try {
            val db = AppDatabase.getInstance(applicationContext)
            val repo = MessageRepository(db.messageDao())
            repo.ingestFromProviders(applicationContext.contentResolver)
            AppLogger.i("MessageSyncWorker.doWork success @${System.currentTimeMillis()}")
            try {
                applicationContext.getSharedPreferences("app_meta", Context.MODE_PRIVATE)
                    .edit()
                    .putLong("lastSyncSuccessTs", System.currentTimeMillis())
                    .apply()
            } catch (_: Throwable) { }
            Result.success()
        } catch (t: Throwable) {
            AppLogger.e("MessageSyncWorker.doWork failed", t)
            Result.retry()
        }
    }

    companion object {
        const val UNIQUE_WORK_NAME = "message-sync-worker"

        fun schedule(appContext: Context) {
            val request = PeriodicWorkRequestBuilder<MessageSyncWorker>(15, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(appContext)
                .enqueueUniquePeriodicWork(
                    UNIQUE_WORK_NAME,
                    ExistingPeriodicWorkPolicy.UPDATE,
                    request
                )
            AppLogger.i("Scheduled MessageSyncWorker every ~15 minutes @${System.currentTimeMillis()}")
        }
    }
}


