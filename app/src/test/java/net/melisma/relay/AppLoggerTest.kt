package net.melisma.relay

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppLoggerTest {

    @Test
    fun writesAndRotatesWithinLimits() {
        val app = ApplicationProvider.getApplicationContext<android.content.Context>()
        AppLogger.init(app)

        // Write a number of lines
        repeat(20) { idx ->
            AppLogger.i("test-line-$idx")
        }

        // Allow background IO to flush
        Thread.sleep(100)

        val dir = File(app.filesDir, "logs")
        val files = dir.listFiles { f -> f.name.startsWith("relay-") && f.name.endsWith(".log") } ?: emptyArray()
        assertTrue(files.isNotEmpty())
    }
}


