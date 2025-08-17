package net.melisma.relay

import androidx.test.core.app.ApplicationProvider
import net.melisma.relay.data.MessageRepository
import net.melisma.relay.db.AppDatabase
import net.melisma.relay.db.MessageEntity
import net.melisma.relay.db.MessageWithParts
import net.melisma.relay.db.MmsPartEntity
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SyncDtoTest {
    @Test
    fun repository_toSyncDto_base64EncodesBinary() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val dao = AppDatabase.getInstance(ctx).messageDao()
        val repo = MessageRepository(dao)

        val msg = MessageEntity(
            id = "id-sync",
            kind = MessageKind.MMS.name,
            providerId = 10L,
            msgBox = 1,
            threadId = 5L,
            address = "+100",
            body = "hello",
            timestamp = 1000L,
            dateSent = null,
            read = 1,
            status = null,
            serviceCenter = null,
            protocol = null,
            seen = null,
            locked = null,
            errorCode = null,
            subject = null,
            mmsContentType = null,
            synced = 0,
            smsJson = null,
            mmsJson = null,
            convJson = null
        )
        val part = MmsPartEntity(
            partId = "p1",
            messageId = msg.id,
            seq = 0,
            ct = "image/jpeg",
            text = null,
            data = byteArrayOf(1,2,3,4),
            dataPath = null,
            name = null,
            chset = null,
            cid = null,
            cl = null,
            cttS = null,
            cttT = null,
            cd = null,
            fn = "a.jpg",
            isImage = true
        )
        val dto = repo.toSyncDto(MessageWithParts(message = msg, parts = listOf(part)))
        assertEquals(1, dto.parts.size)
        // 1,2,3,4 -> AQIDBA==
        assertEquals("AQIDBA==", dto.parts[0].base64Data)
    }
}


