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
        val current = _messages.value
        _messages.value = current + item
    }
}


