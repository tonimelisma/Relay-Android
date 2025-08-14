package net.melisma.relay

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import net.melisma.relay.data.MessageRepository
import net.melisma.relay.db.AppDatabase
import net.melisma.relay.db.MessageEntity
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DbInstrumentedTest {

	private lateinit var db: AppDatabase

	@Before
	fun setup() {
		val context = InstrumentationRegistry.getInstrumentation().targetContext
		db = androidx.room.Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
	}

	@Test
	fun repository_observeMessagesWithParts_emitsInserted() = runBlocking {
		val dao = db.messageDao()
		val repo = MessageRepository(dao)

		val message = MessageEntity(
			id = "test",
			kind = "SMS",
			threadId = 1,
			address = "1234567890",
			body = "Hello",
			timestamp = System.currentTimeMillis(),
			dateSent = System.currentTimeMillis(),
			read = 1,
			synced = 0,
			smsJson = null,
			mmsJson = null,
			convJson = null
		)
		dao.insertMessage(message)

		val items = repo.observeMessagesWithParts().first()
		assertEquals(1, items.size)
		assertEquals("Hello", items[0].message.body)
	}
}
