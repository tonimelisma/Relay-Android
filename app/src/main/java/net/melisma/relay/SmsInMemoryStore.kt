package net.melisma.relay

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class SmsItem(
    val sender: String,
    val body: String,
    val timestamp: Long
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


