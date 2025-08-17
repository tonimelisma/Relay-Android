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
    val contentType: String?,
    // Local file path for stored attachment (if copied)
    val localUriPath: String? = null,
    val filename: String?,
    val text: String?,
    val isAttachment: Boolean,
    val type: MessagePartType = MessagePartType.OTHER,
    val size: Long? = null,
    val contentId: String? = null,
    val contentLocation: String? = null
)

data class SmilPresentation(
    val slides: List<SmilSlide>
)

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


