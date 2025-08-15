package net.melisma.relay.data

import android.content.ContentResolver
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.melisma.relay.MessageKind
import net.melisma.relay.AppLogger
import net.melisma.relay.MessageScanner
import net.melisma.relay.db.MessageDao
import net.melisma.relay.db.MessageEntity
import net.melisma.relay.db.MmsPartEntity
import net.melisma.relay.db.MmsAddrEntity
import net.melisma.relay.db.MessageWithParts
import android.net.Uri
import java.security.MessageDigest

class MessageRepository(private val dao: MessageDao) {
    fun observeMessages() = dao.observeMessages()
    fun observeMessagesWithParts(): Flow<List<MessageWithParts>> = dao.observeMessagesWithParts()

    suspend fun ingestFromProviders(cr: ContentResolver) = withContext(Dispatchers.IO) {
        AppLogger.i("MessageRepository.ingestFromProviders start")
        val lastSms = dao.getMaxTimestampForKind(MessageKind.SMS.name) ?: 0L
        val lastMms = dao.getMaxTimestampForKind(MessageKind.MMS.name) ?: 0L
        val lastRcs = dao.getMaxTimestampForKind(MessageKind.RCS.name) ?: 0L

        // Initial full sync: when DB is empty per kind (max == 0), do not filter by timestamp
        val smsAll = MessageScanner.scanSms(cr)
        val mmsAll = MessageScanner.scanMms(cr)
        val rcsAll = MessageScanner.scanRcsHeuristics(cr)
        val sms = if (lastSms == 0L) smsAll else smsAll.filter { it.timestamp > lastSms }
        val mms = if (lastMms == 0L) mmsAll else mmsAll.filter { it.timestamp > lastMms }
        val rcs = MessageScanner.scanRcsHeuristics(cr).filter { it.timestamp > lastRcs }
        AppLogger.d("Ingest new counts: sms=${sms.size} mms=${mms.size} rcs=${rcs.size}")
        // Merge and sort by timestamp desc for unified ordering
        val all = (sms + mms + rcs).sortedByDescending { it.timestamp }
        val messageEntities = mutableListOf<MessageEntity>()
        val partEntitiesAll = mutableListOf<MmsPartEntity>()
        val addrEntitiesAll = mutableListOf<MmsAddrEntity>()
        // For MMS, fetch detailed metadata once to avoid repeated queries per item
        val detailedList = MessageScanner.scanMmsDetailed(cr, limit = 100)
        val detailedById = detailedList.associateBy { it.mmsId }
        // Some MMS may be looked up by timestamp as a fallback
        val detailedByTs = detailedList.groupBy { it.timestampMs }

        for (item in all) {
            val id = hashFor(item.kind.name, item.sender, item.body, item.timestamp)
            val entity = MessageEntity(
                id = id,
                kind = item.kind.name,
                providerId = item.providerId,
                msgBox = item.msgBox,
                threadId = item.threadId,
                address = item.sender,
                body = materializeBody(item, cr),
                timestamp = item.timestamp,
                dateSent = item.dateSent,
                read = item.read,
                status = item.status,
                serviceCenter = item.serviceCenter,
                protocol = item.protocol,
                seen = item.seen,
                locked = item.locked,
                errorCode = item.errorCode,
                subject = item.subject,
                mmsContentType = item.mmsContentType,
                synced = 0,
                smsJson = if (item.kind == MessageKind.SMS) toRawJson(item) else null,
                mmsJson = if (item.kind != MessageKind.SMS) toRawJson(item) else null,
                convJson = null
            )
            messageEntities.add(entity)
            if (item.kind == MessageKind.MMS) {
                val detailed = detailedById[item.providerId ?: -1L]
                    ?: detailedByTs[item.timestamp]?.firstOrNull()
                val partEntities = detailed?.partMeta?.mapNotNull { meta ->
                    val isImage = meta.ct?.startsWith("image/") == true
                    val blob = if (isImage) readPartBytes(cr, meta.partId) else null
                    val textValue = if (!isImage && meta.ct?.startsWith("text/") == true) meta.text else null
                    MmsPartEntity(
                        partId = meta.partId.toString(),
                        messageId = id,
                        seq = meta.seq,
                        ct = meta.ct,
                        text = textValue,
                        data = blob,
                        dataPath = meta.dataPath,
                        name = meta.name,
                        chset = meta.chset,
                        cid = meta.cid,
                        cl = meta.cl,
                        cttS = meta.cttS,
                        cttT = meta.cttT,
                        cd = meta.cd,
                        fn = meta.fn,
                        isImage = isImage
                    )
                } ?: emptyList()
                partEntitiesAll.addAll(partEntities)

                val mmsIdForAddr = detailed?.mmsId ?: item.providerId
                if (mmsIdForAddr != null && mmsIdForAddr > 0) {
                    val addrs = MessageScanner.scanMmsAddrs(cr, mmsIdForAddr)
                    addrs.forEach { ar ->
                        addrEntitiesAll.add(
                            MmsAddrEntity(
                                messageId = id,
                                address = ar.address,
                                type = ar.type,
                                charset = ar.charset
                            )
                        )
                    }
                }
            }
        }
        dao.insertBatch(messageEntities, partEntitiesAll, addrEntitiesAll)
        AppLogger.i("MessageRepository.ingestFromProviders done inserted messages=${messageEntities.size} parts=${partEntitiesAll.size} addrs=${addrEntitiesAll.size}")
    }

    private fun materializeBody(item: net.melisma.relay.SmsItem, cr: ContentResolver): String? {
        if (item.kind != MessageKind.MMS) return item.body
        // If MMS body is null/empty, check if there are non-text parts to label as [Picture]
        val mmsId = item.sender?.substringAfter("<mms:")?.substringBefore(">")?.toLongOrNull()
        if (!item.body.isNullOrBlank()) return item.body
        if (mmsId != null) {
            val parts = MessageScanner.scanMmsDetailed(cr, limit = 1).firstOrNull { it.mmsId == mmsId }
            val hasImage = parts?.partMeta?.any { it.ct?.startsWith("image/") == true } == true
            if (hasImage) return "[Picture]"
        }
        return "MMS"
    }

    private fun readPartBytes(cr: ContentResolver, partId: Long): ByteArray? {
        return try {
            val uri = Uri.parse("content://mms/part/$partId")
            cr.openInputStream(uri)?.use { it.readBytes() }
        } catch (t: Throwable) {
            null
        }
    }

    private fun toRawJson(item: net.melisma.relay.SmsItem): String {
        // Minimal JSON snapshot of the scanned item
        val parts = listOf(
            "kind" to item.kind.name,
            "providerId" to (item.providerId?.toString() ?: "null"),
            "threadId" to (item.threadId?.toString() ?: "null"),
            "sender" to (item.sender ?: ""),
            "body" to (item.body ?: ""),
            "timestamp" to item.timestamp.toString(),
            "dateSent" to (item.dateSent?.toString() ?: "null"),
            "read" to (item.read?.toString() ?: "null"),
            "subject" to (item.subject ?: ""),
            "ct_t" to (item.mmsContentType ?: ""),
            "msgBox" to (item.msgBox?.toString() ?: "null"),
            "status" to (item.status?.toString() ?: "null")
        )
        return parts.joinToString(prefix = "{", postfix = "}", separator = ",") { (k, v) ->
            "\"$k\":\"" + v.replace("\"", "\\\"") + "\""
        }
    }
    private fun hashFor(kind: String, sender: String?, body: String?, ts: Long): String {
        val md = MessageDigest.getInstance("SHA-256")
        val input = listOf(kind, sender ?: "", body ?: "", ts.toString()).joinToString("|")
        val bytes = md.digest(input.toByteArray())
        return bytes.joinToString(separator = "") { b -> "%02x".format(b) }
    }
}


