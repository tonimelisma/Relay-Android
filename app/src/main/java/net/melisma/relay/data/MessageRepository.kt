package net.melisma.relay.data

import android.content.ContentResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.melisma.relay.MessageKind
import net.melisma.relay.MessageScanner
import net.melisma.relay.db.AppDatabase
import net.melisma.relay.db.MessageDao
import net.melisma.relay.db.MessageEntity
import net.melisma.relay.db.MmsAddrEntity
import net.melisma.relay.db.MmsPartEntity
import java.security.MessageDigest

class MessageRepository(private val dao: MessageDao) {
    fun observeMessages() = dao.observeMessages()

    suspend fun ingestFromProviders(cr: ContentResolver) = withContext(Dispatchers.IO) {
        val sms = MessageScanner.scanSms(cr)
        val mms = MessageScanner.scanMms(cr)
        val rcs = MessageScanner.scanRcsHeuristics(cr)
        val all = sms + mms + rcs
        for (item in all) {
            val id = hashFor(item.kind.name, item.sender, item.body, item.timestamp)
            val entity = MessageEntity(
                id = id,
                kind = item.kind.name,
                threadId = null,
                address = item.sender,
                body = item.body,
                timestamp = item.timestamp,
                dateSent = null,
                read = null,
                smsJson = if (item.kind == MessageKind.SMS) "{}" else null,
                mmsJson = if (item.kind != MessageKind.SMS) "{}" else null,
                convJson = null
            )
            dao.insertMessage(entity)
            // For MVP, we are not persisting parts/addr from scanner here; a follow-up can add it
        }
    }

    private fun hashFor(kind: String, sender: String?, body: String?, ts: Long): String {
        val md = MessageDigest.getInstance("SHA-256")
        val input = listOf(kind, sender ?: "", body ?: "", ts.toString()).joinToString("|")
        val bytes = md.digest(input.toByteArray())
        return bytes.joinToString(separator = "") { b -> "%02x".format(b) }
    }
}


