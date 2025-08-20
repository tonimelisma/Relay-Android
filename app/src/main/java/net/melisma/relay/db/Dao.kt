package net.melisma.relay.db

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import net.melisma.relay.toDomain

@Dao
interface MessageDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMessage(message: MessageEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMessages(messages: List<MessageEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertParts(parts: List<MmsPartEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAddrs(addrs: List<MmsAddrEntity>)

    @Query("SELECT * FROM messages ORDER BY timestamp DESC")
    fun observeMessages(): Flow<List<MessageEntity>>

    @Transaction
    @Query("SELECT * FROM messages ORDER BY timestamp DESC")
    fun observeMessagesWithParts(): Flow<List<MessageWithParts>>
    
    @Transaction
    @Query("SELECT * FROM messages ORDER BY timestamp DESC")
    fun observeMessagesWithPartsAndAddrs(): Flow<List<MessageWithPartsAndAddrs>>

    @Query("DELETE FROM messages")
    suspend fun clearAll()

    @Query("SELECT MAX(timestamp) FROM messages WHERE kind = :kind")
    suspend fun getMaxTimestampForKind(kind: String): Long?

    @Query("SELECT MAX(providerId) FROM messages WHERE kind = :kind")
    suspend fun getMaxProviderIdForKind(kind: String): Long?

    @Transaction
    suspend fun insertBatch(messages: List<MessageEntity>, parts: List<MmsPartEntity>, addrs: List<MmsAddrEntity> = emptyList()) {
        if (messages.isNotEmpty()) insertMessages(messages)
        if (parts.isNotEmpty()) insertParts(parts)
        if (addrs.isNotEmpty()) insertAddrs(addrs)
    }
}

data class MessageWithParts(
    @Embedded val message: MessageEntity,
    @Relation(parentColumn = "id", entityColumn = "messageId")
    val parts: List<MmsPartEntity>
)

data class MessageWithPartsAndAddrs(
    @Embedded val message: MessageEntity,
    @Relation(parentColumn = "id", entityColumn = "messageId")
    val parts: List<MmsPartEntity>,
    @Relation(parentColumn = "id", entityColumn = "messageId")
    val addresses: List<MmsAddrEntity>
) {
    fun toDomain(): net.melisma.relay.SmsItem {
        val domainParts = parts.map { it.toDomain() }
        val domainAddresses = addresses.mapNotNull { it.toDomain() }
        
        // Build SMIL layout if there's a SMIL part
        val smilPart = domainParts.firstOrNull { 
            it.contentType?.equals("application/smil", ignoreCase = true) == true && !it.text.isNullOrBlank() 
        }
        val smilLayout = smilPart?.text?.let { smilXml ->
            try {
                val presentation = net.melisma.relay.MessageScanner.parseSmil(smilXml)
                presentation.toLayout(domainParts)
            } catch (e: Exception) {
                null
            }
        }
        
        return net.melisma.relay.SmsItem(
            sender = message.address ?: "<unknown>",
            body = message.body,
            timestamp = message.timestamp,
            kind = net.melisma.relay.MessageKind.valueOf(message.kind),
            providerId = message.providerId,
            threadId = message.threadId,
            read = message.read,
            dateSent = message.dateSent,
            subject = message.subject,
            mmsContentType = message.mmsContentType,
            msgBox = message.msgBox,
            smsType = message.msgBox, // Same as msgBox for compatibility
            status = message.status,
            serviceCenter = message.serviceCenter,
            protocol = message.protocol,
            seen = message.seen,
            locked = message.locked,
            errorCode = message.errorCode,
            addresses = domainAddresses,
            parts = domainParts,
            smilLayout = smilLayout
        )
    }
}


