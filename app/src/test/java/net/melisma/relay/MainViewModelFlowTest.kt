package net.melisma.relay

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import net.melisma.relay.data.MessageRepository
import net.melisma.relay.db.MessageDao
import net.melisma.relay.db.MessageEntity
import net.melisma.relay.db.MessageWithParts
import net.melisma.relay.db.MmsAddrEntity
import net.melisma.relay.db.MmsPartEntity
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private class StubDaoFlow(
    initial: List<MessageWithParts> = emptyList(),
    initialWithAddrs: List<net.melisma.relay.db.MessageWithPartsAndAddrs> = emptyList()
) : MessageDao {
    private val state = MutableStateFlow(initial)
    private val stateWithAddrs = MutableStateFlow(initialWithAddrs)
    override suspend fun insertMessage(message: MessageEntity) {}
    override suspend fun insertMessages(messages: List<MessageEntity>) {}
    override suspend fun insertParts(parts: List<MmsPartEntity>) {}
    override suspend fun insertAddrs(addrs: List<MmsAddrEntity>) {}
    override fun observeMessages(): Flow<List<MessageEntity>> = MutableStateFlow(emptyList())
    override fun observeMessagesWithParts(): Flow<List<MessageWithParts>> = state
    override fun observeMessagesWithPartsAndAddrs(): Flow<List<net.melisma.relay.db.MessageWithPartsAndAddrs>> = stateWithAddrs
    override suspend fun clearAll() {}
    override suspend fun getMaxTimestampForKind(kind: String): Long? = null
    override suspend fun getMaxProviderIdForKind(kind: String): Long? = null
    override suspend fun insertBatch(messages: List<MessageEntity>, parts: List<MmsPartEntity>, addrs: List<MmsAddrEntity>) {}
}

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MessageRepositoryFlowTest {

    @Test
    fun repository_observeMessagesWithParts_propagatesDaoFlow() = runTest {
        val msg = MessageEntity(
            id = "id-flow",
            kind = MessageKind.SMS.name,
            providerId = 5L,
            msgBox = 1,
            threadId = 2L,
            address = "+199",
            body = "flow",
            timestamp = 1234L,
            dateSent = null,
            read = 1,
            smsJson = "{}",
            mmsJson = null,
            convJson = null
        )
        val dao = StubDaoFlow(listOf(MessageWithParts(msg, emptyList())))
        val repo = MessageRepository(dao)

        val list = repo.observeMessagesWithParts().first()
        assertEquals(1, list.size)
        assertEquals("id-flow", list[0].message.id)
    }
    
    @Test
    fun repository_observeDomainMessages_propagatesDaoFlowAsDomain() = runTest {
        val msg = MessageEntity(
            id = "id-domain",
            kind = MessageKind.SMS.name,
            providerId = 6L,
            msgBox = 1,
            threadId = 3L,
            address = "+299",
            body = "domain flow",
            timestamp = 5678L,
            dateSent = null,
            read = 0,
            smsJson = "{}",
            mmsJson = null,
            convJson = null
        )
        val withPartsAndAddrs = net.melisma.relay.db.MessageWithPartsAndAddrs(msg, emptyList(), emptyList())
        val dao = StubDaoFlow(initialWithAddrs = listOf(withPartsAndAddrs))
        val repo = MessageRepository(dao)

        val domainList = repo.observeDomainMessages().first()
        assertEquals(1, domainList.size)
        assertEquals("+299", domainList[0].sender) // Note: sender comes from address field
        assertEquals("domain flow", domainList[0].body)
        assertEquals(MessageKind.SMS, domainList[0].kind)
    }
}


