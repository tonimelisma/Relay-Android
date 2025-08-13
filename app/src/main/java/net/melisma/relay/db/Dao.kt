package net.melisma.relay.db

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

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

    @Query("DELETE FROM messages")
    suspend fun clearAll()

    @Query("SELECT MAX(timestamp) FROM messages WHERE kind = :kind")
    suspend fun getMaxTimestampForKind(kind: String): Long?

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


