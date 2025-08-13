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
}

data class MessageWithParts(
    @Embedded val message: MessageEntity,
    @Relation(parentColumn = "id", entityColumn = "messageId")
    val parts: List<MmsPartEntity>
)


