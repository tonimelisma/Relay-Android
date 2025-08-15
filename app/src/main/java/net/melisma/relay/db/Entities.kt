package net.melisma.relay.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "messages",
    indices = [Index(value = ["timestamp"], unique = false), Index(value = ["threadId"], unique = false)]
)
data class MessageEntity(
    @PrimaryKey val id: String,
    val kind: String, // SMS|MMS|RCS
    val providerId: Long?,
    val msgBox: Int?,
    val threadId: Long?,
    val address: String?,
    val body: String?,
    val timestamp: Long,
    val dateSent: Long?,
    val read: Int?,
    val status: Int? = null,
    val serviceCenter: String? = null,
    val protocol: Int? = null,
    val seen: Int? = null,
    val locked: Int? = null,
    val errorCode: Int? = null,
    val subject: String? = null,
    val mmsContentType: String? = null,
    val synced: Int? = 0,
    val smsJson: String?,
    val mmsJson: String?,
    val convJson: String?
)

@Entity(
    tableName = "mms_parts",
    foreignKeys = [
        ForeignKey(
            entity = MessageEntity::class,
            parentColumns = ["id"],
            childColumns = ["messageId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["messageId"])]
)
data class MmsPartEntity(
    @PrimaryKey val partId: String,
    val messageId: String,
    val seq: Int?,
    val ct: String?,
    val text: String?,
    val data: ByteArray?,
    val dataPath: String?,
    val name: String?,
    val chset: String?,
    val cid: String?,
    val cl: String?,
    val cttS: String?,
    val cttT: String?,
    val cd: String?,
    val fn: String?,
    val isImage: Boolean?
)

@Entity(
    tableName = "mms_addr",
    foreignKeys = [
        ForeignKey(
            entity = MessageEntity::class,
            parentColumns = ["id"],
            childColumns = ["messageId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["messageId"])]
)
data class MmsAddrEntity(
    @PrimaryKey(autoGenerate = true) val rowId: Long = 0,
    val messageId: String,
    val address: String?,
    val type: Int?,
    val charset: String?
)


