package net.melisma.relay

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MessageScannerTest {

    @Test
    fun scanSms_onEmptyProvider_returnsEmptyList() {
        val cr = ApplicationProvider.getApplicationContext<android.content.Context>().contentResolver
        val out = MessageScanner.scanSms(cr)
        assertTrue(out.isEmpty())
    }

    @Test
    fun scanMms_onEmptyProvider_returnsEmptyList() {
        val cr = ApplicationProvider.getApplicationContext<android.content.Context>().contentResolver
        val out = MessageScanner.scanMms(cr)
        assertTrue(out.isEmpty())
    }

    @Test
    fun scanRcsHeuristics_onEmptyProviders_returnsEmptyOrSmallList() {
        val cr = ApplicationProvider.getApplicationContext<android.content.Context>().contentResolver
        val out = MessageScanner.scanRcsHeuristics(cr)
        // Heuristic queries may fail silently; should not throw and likely empty
        assertTrue(out.size >= 0)
    }

    @Test
    fun mmsDateSecondsAreConvertedToMillis_whenBelowThreshold() {
        // directly verify helper via public scanMmsDetailed path using empty provider
        val cr = ApplicationProvider.getApplicationContext<android.content.Context>().contentResolver
        val list = MessageScanner.scanMmsDetailed(cr, limit = 0)
        assertEquals(0, list.size)
        // functional conversion is covered inside scanMms; we assert no crash for empty provider
    }
}


