package net.melisma.relay

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MessageSyncWorkerTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        val config = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.DEBUG)
            .setExecutor(SynchronousExecutor())
            .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, config)
    }

    @Test
    fun schedule_enqueuesUniquePeriodicWork() {
        MessageSyncWorker.schedule(context)
        val workManager = WorkManager.getInstance(context)
        val infos = workManager.getWorkInfosForUniqueWork(MessageSyncWorker.UNIQUE_WORK_NAME).get()
        assertTrue(infos.isNotEmpty())
        val state = infos.first().state
        assertTrue(state == WorkInfo.State.ENQUEUED || state == WorkInfo.State.RUNNING)
    }

    @Test
    fun doWork_completesSuccessfully() {
        val worker = TestListenableWorkerBuilder<MessageSyncWorker>(context).build()
        val result = worker.startWork().get()
        assertEquals(ListenableWorker.Result.success()::class, result::class)
    }
}


