package net.melisma.relay

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class MessageKind { SMS, MMS, RCS }

data class SmsItem(
    val sender: String,
    val body: String,
    val timestamp: Long,
    val kind: MessageKind = MessageKind.SMS,
    val providerId: Long? = null, // _id from provider row when available
    val threadId: Long? = null,
    val read: Int? = null,
    val dateSent: Long? = null,
    val subject: String? = null, // MMS subject when available
    val mmsContentType: String? = null // MMS ct_t when available
)

object SmsInMemoryStore {
    private val _messages: MutableStateFlow<List<SmsItem>> = MutableStateFlow(emptyList())
    val messages: StateFlow<List<SmsItem>> = _messages

    fun addMessage(item: SmsItem) {
        AppLogger.d("SmsInMemoryStore.addMessage called with sender='${item.sender}', ts=${item.timestamp}")
        val current = _messages.value
        AppLogger.d("SmsInMemoryStore current size=${current.size}")
        val updated = current + item
        _messages.value = updated
        AppLogger.i("SmsInMemoryStore updated size=${updated.size}")
    }
}


