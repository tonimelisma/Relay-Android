package net.melisma.relay

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import net.melisma.relay.db.AppDatabase
import net.melisma.relay.db.MessageEntity
import net.melisma.relay.db.MessageWithParts
import net.melisma.relay.db.MmsPartEntity
import net.melisma.relay.db.MmsAddrEntity
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MessageDaoFlowTest {

    private fun newInMemoryDb(): AppDatabase {
        val app = ApplicationProvider.getApplicationContext<Application>()
        return androidx.room.Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @Test
    fun observeMessagesWithParts_emitsEmpty_thenOneAfterInsert() = runTest {
        val db = newInMemoryDb()
        val dao = db.messageDao()

        val emissions = mutableListOf<List<MessageWithParts>>()
        val collectJob = this.launch {
            dao.observeMessagesWithParts().take(2).toList(emissions)
        }

        // First emission should be empty
        val first = dao.observeMessagesWithParts().first()
        assertEquals(0, first.size)

        // Insert one message (no parts)
        val msg = MessageEntity(
            id = "id-1",
            kind = MessageKind.SMS.name,
            providerId = 1L,
            msgBox = 1,
            threadId = 10L,
            address = "+123",
            body = "hello",
            timestamp = 1000L,
            dateSent = 900L,
            read = 1,
            smsJson = "{}",
            mmsJson = null,
            convJson = null
        )
        dao.insertMessage(msg)

        collectJob.join()
        assertEquals(2, emissions.size)
        assertEquals(0, emissions[0].size)
        assertEquals(1, emissions[1].size)
        assertEquals("id-1", emissions[1][0].message.id)
        db.close()
    }

    @Test
    fun observeMessagesWithParts_includesInsertedPart() = runTest {
        val db = newInMemoryDb()
        val dao = db.messageDao()

        val msgId = "id-2"
        val msg = MessageEntity(
            id = msgId,
            kind = MessageKind.MMS.name,
            providerId = 2L,
            msgBox = 1,
            threadId = 20L,
            address = "+456",
            body = "mms",
            timestamp = 2000L,
            dateSent = 1500L,
            read = 0,
            smsJson = null,
            mmsJson = "{}",
            convJson = null
        )
        dao.insertMessage(msg)

        val part = MmsPartEntity(
            partId = "p1",
            messageId = msgId,
            seq = 0,
            ct = "text/plain",
            text = "part-text",
            data = null,
            dataPath = null,
            name = null,
            chset = null,
            cid = null,
            cl = null,
            cttS = null,
            cttT = null,
            cd = null,
            fn = null,
            isImage = false
        )
        dao.insertParts(listOf(part))

        val withParts = dao.observeMessagesWithParts().first()
        assertEquals(1, withParts.size)
        assertEquals(1, withParts[0].parts.size)
        assertEquals("part-text", withParts[0].parts[0].text)
        db.close()
    }
    
    @Test
    fun observeMessagesWithPartsAndAddrs_includesAllData() = runTest {
        val db = newInMemoryDb()
        val dao = db.messageDao()

        val msgId = "id-3"
        val msg = MessageEntity(
            id = msgId,
            kind = MessageKind.MMS.name,
            providerId = 3L,
            msgBox = 1,
            threadId = 30L,
            address = "+789",
            body = "mms with parts and addresses",
            timestamp = 3000L,
            dateSent = 2500L,
            read = 1,
            smsJson = null,
            mmsJson = "{}",
            convJson = null
        )
        dao.insertMessage(msg)

        val part = MmsPartEntity(
            partId = "p2",
            messageId = msgId,
            seq = 0,
            ct = "image/jpeg",
            text = null,
            data = null,
            dataPath = "/path/to/image.jpg",
            name = "image.jpg",
            chset = null,
            cid = "img1",
            cl = null,
            cttS = null,
            cttT = null,
            cd = null,
            fn = "image.jpg",
            isImage = true
        )
        dao.insertParts(listOf(part))

        val addr = MmsAddrEntity(
            messageId = msgId,
            address = "+123456789",
            type = 137, // From
            charset = null
        )
        dao.insertAddrs(listOf(addr))

        val withPartsAndAddrs = dao.observeMessagesWithPartsAndAddrs().first()
        assertEquals(1, withPartsAndAddrs.size)
        assertEquals(1, withPartsAndAddrs[0].parts.size)
        assertEquals(1, withPartsAndAddrs[0].addresses.size)
        assertEquals("image/jpeg", withPartsAndAddrs[0].parts[0].ct)
        assertEquals("+123456789", withPartsAndAddrs[0].addresses[0].address)
        
        // Test domain conversion
        val domainItem = withPartsAndAddrs[0].toDomain()
        assertEquals(1, domainItem.parts.size)
        assertEquals(1, domainItem.addresses.size)
        assertEquals(MessagePartType.IMAGE, domainItem.parts[0].type)
        assertEquals("From", domainItem.addresses[0].type)
        
        db.close()
    }
}


