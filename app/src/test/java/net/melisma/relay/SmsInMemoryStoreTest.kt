package net.melisma.relay

import org.junit.Assert.assertEquals
import org.junit.Test

class SmsInMemoryStoreTest {
    @Test
    fun addMessage_appendsToList() {
        val startSize = SmsInMemoryStore.messages.value.size
        SmsInMemoryStore.addMessage(
            SmsItem(sender = "12345", body = "hello", timestamp = 0L)
        )
        assertEquals(startSize + 1, SmsInMemoryStore.messages.value.size)
    }
}


