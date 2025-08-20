package net.melisma.relay

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import net.melisma.relay.data.MessageRepository
import net.melisma.relay.db.AppDatabase
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val messageRepository: MessageRepository

    val messages: StateFlow<List<SmsItem>>

    init {
        val messageDao = AppDatabase.getInstance(application).messageDao()
        messageRepository = MessageRepository(messageDao)
        val t0 = System.currentTimeMillis()
        messages = messageRepository.observeDomainMessages()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
        AppLogger.i("MainViewModel messages flow initialized in ${System.currentTimeMillis() - t0}ms")
        AppLogger.i("MainViewModel initialized; messages flow subscribed")
    }

    fun ingestFromProviders() {
        viewModelScope.launch {
            AppLogger.i("MainViewModel.ingestFromProviders trigger @${System.currentTimeMillis()}")
            messageRepository.ingestFromProviders(getApplication<Application>().contentResolver)
        }
    }
}
