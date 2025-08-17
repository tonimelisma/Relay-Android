package net.melisma.relay

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ImProviderGateTest {

    @Test
    fun prime_then_markUnavailable_persistsFalse() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        // Prime (will detect false in Robolectric environment)
        ImProviderGate.prime(ctx)
        // Should be false or true depending on environment; we enforce false after markUnavailable
        ImProviderGate.markUnavailable(ctx)
        assertFalse(ImProviderGate.shouldUseIm(ctx))
    }

    @Test
    fun shouldUseImOrCachedFalse_isFalseByDefault() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        // Fresh process: prime sets cache; in Robolectric no IM provider exists
        ImProviderGate.prime(ctx)
        assertFalse(ImProviderGate.shouldUseImOrCachedFalse())
    }
}


