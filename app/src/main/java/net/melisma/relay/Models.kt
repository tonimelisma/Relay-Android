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
    val mmsContentType: String? = null // MMS ct_t when available
)


