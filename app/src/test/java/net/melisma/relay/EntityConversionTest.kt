package net.melisma.relay

import net.melisma.relay.db.MmsPartEntity
import net.melisma.relay.db.MmsAddrEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EntityConversionTest {

    @Test
    fun messagePart_toEntity_convertsCorrectly() {
        val part = MessagePart(
            partId = 123L,
            messageId = 456L,
            seq = 1,
            contentType = "image/jpeg",
            text = null,
            data = null,
            dataPath = "/path/to/image.jpg",
            name = "image.jpg",
            charset = "utf-8",
            contentId = "img1",
            contentLocation = "image.jpg",
            contentTransferSize = "1024",
            contentTransferType = "binary",
            contentDisposition = "attachment",
            filename = "image.jpg",
            isImage = true,
            type = MessagePartType.IMAGE,
            isAttachment = true,
            size = 1024L
        )
        
        val entity = part.toEntity("msg-123")
        
        assertEquals("123", entity.partId)
        assertEquals("msg-123", entity.messageId)
        assertEquals(1, entity.seq)
        assertEquals("image/jpeg", entity.ct)
        assertEquals(null, entity.text)
        assertEquals(null, entity.data)
        assertEquals("/path/to/image.jpg", entity.dataPath)
        assertEquals("image.jpg", entity.name)
        assertEquals("utf-8", entity.chset)
        assertEquals("img1", entity.cid)
        assertEquals("image.jpg", entity.cl)
        assertEquals("1024", entity.cttS)
        assertEquals("binary", entity.cttT)
        assertEquals("attachment", entity.cd)
        assertEquals("image.jpg", entity.fn)
        assertEquals(true, entity.isImage)
    }
    
    @Test
    fun mmsPartEntity_toDomain_convertsCorrectly() {
        val entity = MmsPartEntity(
            partId = "456",
            messageId = "msg-456",
            seq = 2,
            ct = "text/plain",
            text = "Hello world",
            data = null,
            dataPath = null,
            name = "text.txt",
            chset = "utf-8",
            cid = "txt1",
            cl = "text.txt",
            cttS = "100",
            cttT = "text",
            cd = "inline",
            fn = "text.txt",
            isImage = false
        )
        
        val domain = entity.toDomain()
        
        assertEquals(456L, domain.partId)
        assertEquals(-1L, domain.messageId) // Can't parse "msg-456" as Long
        assertEquals(2, domain.seq)
        assertEquals("text/plain", domain.contentType)
        assertEquals("Hello world", domain.text)
        assertEquals(null, domain.data)
        assertEquals(null, domain.dataPath)
        assertEquals("text.txt", domain.name)
        assertEquals("utf-8", domain.charset)
        assertEquals("txt1", domain.contentId)
        assertEquals("text.txt", domain.contentLocation)
        assertEquals("100", domain.contentTransferSize)
        assertEquals("text", domain.contentTransferType)
        assertEquals("inline", domain.contentDisposition)
        assertEquals("text.txt", domain.filename)
        assertEquals(false, domain.isImage)
        assertEquals(MessagePartType.TEXT, domain.type)
        assertEquals(false, domain.isAttachment)
    }
    
    @Test
    fun messageAddress_toEntity_convertsCorrectly() {
        val address = MessageAddress("test@example.com", "To")
        val entity = address.toEntity("msg-789")
        
        assertEquals("msg-789", entity.messageId)
        assertEquals("test@example.com", entity.address)
        assertEquals(151, entity.type) // To = 151
        assertEquals(null, entity.charset)
    }
    
    @Test
    fun mmsAddrEntity_toDomain_convertsCorrectly() {
        val entity = MmsAddrEntity(
            messageId = "msg-999",
            address = "sender@example.com",
            type = 137, // From
            charset = "utf-8"
        )
        
        val domain = entity.toDomain()
        
        assertEquals("sender@example.com", domain?.address)
        assertEquals("From", domain?.type)
    }
    
    @Test
    fun mmsAddrEntity_toDomain_handlesUnknownType() {
        val entity = MmsAddrEntity(
            messageId = "msg-999",
            address = "unknown@example.com",
            type = 999, // Unknown type
            charset = null
        )
        
        val domain = entity.toDomain()
        assertEquals(null, domain) // Should return null for unknown types
    }
    
    @Test
    fun messagePart_helperMethods_workCorrectly() {
        val part = MessagePart(
            partId = 1L,
            messageId = 2L,
            contentType = "image/jpeg",
            text = null,
            filename = "image.jpg",
            name = "backup.jpg",
            dataPath = "/path/to/file.jpg",
            type = MessagePartType.IMAGE,
            isAttachment = true
        )
        
        assertEquals("image.jpg", part.getBestFilename())
        assertEquals("/path/to/file.jpg", part.getBestFilePath())
    }
    
    @Test
    fun messagePart_helperMethods_fallbackCorrectly() {
        val part = MessagePart(
            partId = 1L,
            messageId = 2L,
            contentType = "image/jpeg",
            text = null,
            filename = null,
            name = "fallback.jpg",
            dataPath = null,
            type = MessagePartType.IMAGE,
            isAttachment = true
        )
        
        assertEquals("fallback.jpg", part.getBestFilename())
        assertEquals(null, part.getBestFilePath())
    }
}