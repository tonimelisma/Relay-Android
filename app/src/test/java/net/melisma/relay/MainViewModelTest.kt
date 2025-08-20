package net.melisma.relay

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import net.melisma.relay.data.MessageRepository
import net.melisma.relay.db.AppDatabase
import net.melisma.relay.db.MessageDao
import net.melisma.relay.db.MessageEntity
import net.melisma.relay.db.MessageWithParts
import net.melisma.relay.db.MmsAddrEntity
import net.melisma.relay.db.MmsPartEntity
import org.junit.Assert.assertEquals
import org.junit.Test

private class FakeDao : MessageDao {
    private val state = MutableStateFlow<List<MessageWithParts>>(emptyList())
    private val stateWithAddrs = MutableStateFlow<List<net.melisma.relay.db.MessageWithPartsAndAddrs>>(emptyList())
    override suspend fun insertMessage(message: MessageEntity) {}
    override suspend fun insertMessages(messages: List<MessageEntity>) {}
    override suspend fun insertParts(parts: List<MmsPartEntity>) {}
    override suspend fun insertAddrs(addrs: List<MmsAddrEntity>) {}
    override fun observeMessages(): Flow<List<MessageEntity>> = MutableStateFlow(emptyList())
    override fun observeMessagesWithParts(): Flow<List<MessageWithParts>> = state
    override fun observeMessagesWithPartsAndAddrs(): Flow<List<net.melisma.relay.db.MessageWithPartsAndAddrs>> = stateWithAddrs
    override suspend fun clearAll() {}
    override suspend fun getMaxTimestampForKind(kind: String): Long? = null
    override suspend fun getMaxProviderIdForKind(kind: String): Long? = null
    override suspend fun insertBatch(messages: List<MessageEntity>, parts: List<MmsPartEntity>, addrs: List<MmsAddrEntity>) {}
}

@OptIn(ExperimentalCoroutinesApi::class)
@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [34])
class MainViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @org.junit.Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @org.junit.After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun stateFlowInitialIsEmpty_andIngestDoesNotCrash() = runTest {
        val app = ApplicationProvider.getApplicationContext<Application>()
        // Injecting default AppDatabase is heavy; instead ensure VM constructs and exposes flow
        val vm = MainViewModel(app)
        // Initially empty list
        assertEquals(0, vm.messages.value.size)
        // Call ingest to ensure it launches and does not crash without permissions
        vm.ingestFromProviders()
    }
}


