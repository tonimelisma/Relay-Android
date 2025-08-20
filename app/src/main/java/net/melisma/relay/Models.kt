package net.melisma.relay

enum class MessageKind { SMS, MMS, RCS }

data class SmsItem(
    val sender: String,
    val body: String?,
    val timestamp: Long,
    val kind: MessageKind = MessageKind.SMS,
    val providerId: Long? = null, // _id from provider row when available
    val threadId: Long? = null,
    val read: Int? = null,
    val dateSent: Long? = null,
    val subject: String? = null, // MMS subject when available
    val mmsContentType: String? = null, // MMS ct_t when available
    // Additional SMS/MMS metadata for 1:1 mirroring
    val msgBox: Int? = null,
    val smsType: Int? = null,
    val status: Int? = null,
    val serviceCenter: String? = null,
    val protocol: Int? = null,
    val seen: Int? = null,
    val locked: Int? = null,
    val errorCode: Int? = null,
    val addresses: List<MessageAddress> = emptyList(),
    // MMS-only: full parts listing (text + attachments). Non-UI usage; UI may render later.
    val parts: List<MessagePart> = emptyList()
    ,
    val smilLayout: SmilLayout? = null
)

enum class MessagePartType { TEXT, IMAGE, VIDEO, AUDIO, VCARD, OTHER }

data class MessageAddress(
    val address: String,
    val type: String // From | To | Cc | Bcc
)

data class MessagePart(
    val partId: Long,
    val messageId: Long,
    val seq: Int? = null,
    val contentType: String?, // ct in database
    val text: String?,
    val data: ByteArray? = null, // Legacy blob storage
    val dataPath: String? = null, // File path for attachments
    val name: String?,
    val charset: String? = null, // chset in database
    val contentId: String? = null, // cid in database
    val contentLocation: String? = null, // cl in database
    val contentTransferSize: String? = null, // cttS in database
    val contentTransferType: String? = null, // cttT in database
    val contentDisposition: String? = null, // cd in database
    val filename: String? = null, // fn in database
    val isImage: Boolean? = null,
    // Computed properties for convenience
    val type: MessagePartType = MessagePartType.OTHER,
    val isAttachment: Boolean = false,
    val size: Long? = null
) {
    // Helper to get the best available filename
    fun getBestFilename(): String? = filename ?: name
    
    // Helper to get the best available file path
    fun getBestFilePath(): String? = dataPath
    
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as MessagePart
        return partId == other.partId && messageId == other.messageId
    }
    
    override fun hashCode(): Int {
        return 31 * partId.hashCode() + messageId.hashCode()
    }
}

data class SmilPresentation(
    val slides: List<SmilSlide>
) {
    // Convert to simple layout for backward compatibility
    fun toLayout(parts: List<MessagePart>): SmilLayout {
        val byCidOrCl = parts.associateBy { it.contentId ?: it.contentLocation }
        val order = mutableListOf<Long>()
        slides.forEach { slide ->
            slide.items.forEach { item ->
                val key = item.src
                val matched = if (key != null) byCidOrCl[key] else null
                if (matched != null) order.add(matched.partId)
            }
        }
        return SmilLayout(order)
    }
}

data class SmilSlide(
    val items: List<SmilItem>,
    val region: String? = null,
    val durationMs: Long? = null
)

data class SmilItem(
    val type: String, // image | video | audio | text
    val src: String?,
    val region: String? = null,
    val text: String? = null
)

data class SmilLayout(
    val partOrder: List<Long>
)

// Extension functions for entity conversion
fun MessagePart.toEntity(messageId: String): net.melisma.relay.db.MmsPartEntity {
    return net.melisma.relay.db.MmsPartEntity(
        partId = this.partId.toString(),
        messageId = messageId,
        seq = this.seq,
        ct = this.contentType,
        text = this.text,
        data = this.data,
        dataPath = this.dataPath,
        name = this.name,
        chset = this.charset,
        cid = this.contentId,
        cl = this.contentLocation,
        cttS = this.contentTransferSize,
        cttT = this.contentTransferType,
        cd = this.contentDisposition,
        fn = this.filename,
        isImage = this.isImage
    )
}

fun net.melisma.relay.db.MmsPartEntity.toDomain(): MessagePart {
    val ct = this.ct
    val type = when {
        ct == null -> MessagePartType.OTHER
        ct.startsWith("image/") -> MessagePartType.IMAGE
        ct.startsWith("video/") -> MessagePartType.VIDEO
        ct.startsWith("audio/") -> MessagePartType.AUDIO
        ct.equals("text/vcard", ignoreCase = true) || ct.equals("text/x-vcard", ignoreCase = true) -> MessagePartType.VCARD
        ct.startsWith("text/") -> MessagePartType.TEXT
        else -> MessagePartType.OTHER
    }
    
    val isAttachment = ct != null && !ct.startsWith("text/") && ct != "application/smil"
    
    return MessagePart(
        partId = this.partId.toLongOrNull() ?: -1L,
        messageId = this.messageId.toLongOrNull() ?: -1L,
        seq = this.seq,
        contentType = this.ct,
        text = this.text,
        data = this.data,
        dataPath = this.dataPath,
        name = this.name,
        charset = this.chset,
        contentId = this.cid,
        contentLocation = this.cl,
        contentTransferSize = this.cttS,
        contentTransferType = this.cttT,
        contentDisposition = this.cd,
        filename = this.fn,
        isImage = this.isImage,
        type = type,
        isAttachment = isAttachment,
        size = null // Not stored in database currently
    )
}

fun MessageAddress.toEntity(messageId: String): net.melisma.relay.db.MmsAddrEntity {
    val typeInt = when (this.type) {
        "From" -> 137
        "To" -> 151
        "Cc" -> 130
        "Bcc" -> 129
        else -> null
    }
    
    return net.melisma.relay.db.MmsAddrEntity(
        messageId = messageId,
        address = this.address,
        type = typeInt,
        charset = null
    )
}

fun net.melisma.relay.db.MmsAddrEntity.toDomain(): MessageAddress? {
    val typeString = when (this.type) {
        137 -> "From"
        151 -> "To"
        130 -> "Cc"
        129 -> "Bcc"
        else -> return null
    }
    
    return this.address?.let { addr ->
        MessageAddress(addr, typeString)
    }
}


