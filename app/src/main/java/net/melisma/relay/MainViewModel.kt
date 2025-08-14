package net.melisma.relay

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import net.melisma.relay.data.MessageRepository
import net.melisma.relay.db.AppDatabase
import net.melisma.relay.db.MessageWithParts

import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val messageRepository: MessageRepository

    val messages: StateFlow<List<MessageWithParts>>

    init {
        val messageDao = AppDatabase.getInstance(application).messageDao()
        messageRepository = MessageRepository(messageDao)
        messages = messageRepository.observeMessagesWithParts()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    }

    fun ingestFromProviders() {
        viewModelScope.launch {
            messageRepository.ingestFromProviders(getApplication<Application>().contentResolver)
        }
    }
}
