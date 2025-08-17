package net.melisma.relay

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import net.melisma.relay.data.MessageRepository
import net.melisma.relay.db.MessageDao
import net.melisma.relay.db.MessageEntity
import net.melisma.relay.db.MessageWithParts
import net.melisma.relay.db.MmsAddrEntity
import net.melisma.relay.db.MmsPartEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MessageRepositoryJsonTest {

    private class DummyDao : MessageDao {
        var insertBatchCalls: Int = 0
        override suspend fun insertMessage(message: MessageEntity) {}
        override suspend fun insertMessages(messages: List<MessageEntity>) {}
        override suspend fun insertParts(parts: List<MmsPartEntity>) {}
        override suspend fun insertAddrs(addrs: List<MmsAddrEntity>) {}
        override fun observeMessages(): Flow<List<MessageEntity>> = emptyFlow()
        override fun observeMessagesWithParts(): Flow<List<MessageWithParts>> = emptyFlow()
        override suspend fun clearAll() {}
        override suspend fun getMaxTimestampForKind(kind: String): Long? = null
        override suspend fun getMaxProviderIdForKind(kind: String): Long? = null
        override suspend fun insertBatch(messages: List<MessageEntity>, parts: List<MmsPartEntity>, addrs: List<MmsAddrEntity>) {
            insertBatchCalls++
        }
    }

    @Test
    fun toRawJson_escapesQuotesProperly_and_includesExpectedKeys() {
        val repo = MessageRepository(DummyDao())
        val method = MessageRepository::class.java.getDeclaredMethod(
            "toRawJson",
            SmsItem::class.java
        )
        method.isAccessible = true

        val item = SmsItem(
            sender = "Alice \"A\"",
            body = "Hello \"world\"",
            timestamp = 1700000000000L,
            kind = MessageKind.SMS,
            providerId = 123L,
            threadId = 1L,
            read = 1,
            dateSent = 1700000000001L
        )

        val json = method.invoke(repo, item) as String
        assertTrue(json.startsWith("{"))
        assertTrue(json.endsWith("}"))
        assertTrue(json.contains("\"kind\":\"SMS\""))
        assertTrue(json.contains("\"providerId\":\"123\""))
        assertTrue(json.contains("\"body\":\"Hello \\\"world\\\"\""))
        assertTrue(json.contains("\"sender\":\"Alice \\\"A\\\"\""))
    }

    @Test
    fun ingestFromProviders_withEmptyProviders_doesNotInsert() = runBlocking {
        val dao = DummyDao()
        val repo = MessageRepository(dao)
        val context = ApplicationProvider.getApplicationContext<Context>()

        repo.ingestFromProviders(context.contentResolver)

        // No data found → no insertBatch calls
        assertEquals(0, dao.insertBatchCalls)
    }
}


