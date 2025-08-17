package net.melisma.relay

data class SyncPartDTO(
    val partId: String,
    val contentType: String?,
    val filename: String?,
    val text: String?,
    val base64Data: String?
)

data class SyncMessageDTO(
    val id: String,
    val kind: String,
    val providerId: Long?,
    val threadId: Long?,
    val address: String?,
    val body: String?,
    val timestamp: Long,
    val dateSent: Long?,
    val read: Int?,
    val subject: String?,
    val mmsContentType: String?,
    val msgBox: Int?,
    val status: Int?,
    val serviceCenter: String?,
    val protocol: Int?,
    val seen: Int?,
    val locked: Int?,
    val errorCode: Int?,
    val parts: List<SyncPartDTO> = emptyList()
)


