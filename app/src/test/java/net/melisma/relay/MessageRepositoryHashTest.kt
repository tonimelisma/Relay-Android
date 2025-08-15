package net.melisma.relay

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import net.melisma.relay.data.MessageRepository
import net.melisma.relay.db.MessageDao
import net.melisma.relay.db.MessageEntity
import net.melisma.relay.db.MessageWithParts
import net.melisma.relay.db.MmsAddrEntity
import net.melisma.relay.db.MmsPartEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageRepositoryHashTest {

    private class DummyDao : MessageDao {
        override suspend fun insertMessage(message: MessageEntity) {}
        override suspend fun insertMessages(messages: List<MessageEntity>) {}
        override suspend fun insertParts(parts: List<MmsPartEntity>) {}
        override suspend fun insertAddrs(addrs: List<MmsAddrEntity>) {}
        override fun observeMessages(): Flow<List<MessageEntity>> = emptyFlow()
        override fun observeMessagesWithParts(): Flow<List<MessageWithParts>> = emptyFlow()
        override suspend fun clearAll() {}
        override suspend fun getMaxTimestampForKind(kind: String): Long? = null
        override suspend fun getMaxProviderIdForKind(kind: String): Long? = null
        override suspend fun insertBatch(messages: List<MessageEntity>, parts: List<MmsPartEntity>, addrs: List<MmsAddrEntity>) {}
    }

    @Test
    fun hashFor_isDeterministicAndHex64() {
        val repo = MessageRepository(DummyDao())
        val method = MessageRepository::class.java.getDeclaredMethod("hashFor", String::class.java, String::class.java, String::class.java, Long::class.javaPrimitiveType)
        method.isAccessible = true

        val h1 = method.invoke(repo, "SMS", "12345", "Hello", 1700000000000L) as String
        val h2 = method.invoke(repo, "SMS", "12345", "Hello", 1700000000000L) as String
        val h3 = method.invoke(repo, "SMS", "12345", "Hello!", 1700000000000L) as String

        assertEquals(h1, h2)
        assertNotEquals(h1, h3)
        assertEquals(64, h1.length)
        assertTrue(h1.matches(Regex("[0-9a-f]{64}")))
    }
}


