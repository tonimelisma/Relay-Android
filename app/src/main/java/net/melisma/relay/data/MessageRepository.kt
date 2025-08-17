package net.melisma.relay.data

import android.content.ContentResolver
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.melisma.relay.MessageKind
import net.melisma.relay.AppLogger
import net.melisma.relay.MessageScanner
import net.melisma.relay.MessagePart
import net.melisma.relay.db.MessageDao
import net.melisma.relay.db.MessageEntity
import net.melisma.relay.db.MmsPartEntity
import net.melisma.relay.db.MmsAddrEntity
import net.melisma.relay.db.MessageWithParts
import android.net.Uri
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean
import java.util.Base64

class MessageRepository(private val dao: MessageDao) {
    companion object {
        private val ingestInFlight = AtomicBoolean(false)
    }
    fun observeMessages() = dao.observeMessages()
    fun observeMessagesWithParts(): Flow<List<MessageWithParts>> = dao.observeMessagesWithParts()

    suspend fun ingestFromProviders(cr: ContentResolver, kind: MessageKind? = null) = withContext(Dispatchers.IO) {
        if (!ingestInFlight.compareAndSet(false, true)) {
            AppLogger.w("Ingest skipped: already running")
            return@withContext
        }
        AppLogger.i("MessageRepository.ingestFromProviders start kind=${kind?.name ?: "ALL"}")
        try {
            val lastSmsId = dao.getMaxProviderIdForKind(MessageKind.SMS.name) ?: 0L
            val lastMmsId = dao.getMaxProviderIdForKind(MessageKind.MMS.name) ?: 0L
            val lastRcsId = dao.getMaxProviderIdForKind(MessageKind.RCS.name) ?: 0L

            val chunkSize = 500

            // SMS: all folders, watermark by provider _id
            val sms = when (kind) {
                MessageKind.SMS, null -> {
                    val out = mutableListOf<net.melisma.relay.SmsItem>()
                    var cursorFromId: Long? = if (lastSmsId > 0L) lastSmsId else null
                    while (true) {
                        val batch = MessageScanner.scanSms(
                            contentResolver = cr,
                            minProviderIdExclusive = cursorFromId,
                            limit = chunkSize
                        )
                        if (batch.isEmpty()) break
                        out.addAll(batch)
                        val last = batch.last().providerId
                        if (last != null) cursorFromId = last
                        if (batch.size < chunkSize) break
                    }
                    out
                }
                else -> emptyList()
            }

            // MMS: all types, watermark by provider _id
            val mms = when (kind) {
                MessageKind.MMS, null -> {
                    val out = mutableListOf<net.melisma.relay.SmsItem>()
                    var cursorFromId: Long? = if (lastMmsId > 0L) lastMmsId else null
                    while (true) {
                        val batch = MessageScanner.scanMms(
                            contentResolver = cr,
                            minProviderIdExclusive = cursorFromId,
                            limit = chunkSize
                        )
                        if (batch.isEmpty()) break
                        out.addAll(batch)
                        val last = batch.last().providerId
                        if (last != null) cursorFromId = last
                        if (batch.size < chunkSize) break
                    }
                    out
                }
                else -> emptyList()
            }

            // RCS heuristic remains as before; watermark by MMS _id space when available
            val rcs = when (kind) {
                MessageKind.RCS, null -> MessageScanner.scanRcsHeuristics(cr, limit = 25)
                else -> emptyList()
            }

            AppLogger.d("Ingest new counts: sms=${sms.size} mms=${mms.size} rcs=${rcs.size}")
            val all = (sms + mms + rcs).sortedBy { it.timestamp } // ascending to keep mem steady; we batch insert
            val messageEntities = mutableListOf<MessageEntity>()
            val partEntitiesAll = mutableListOf<MmsPartEntity>()
            val addrEntitiesAll = mutableListOf<MmsAddrEntity>()
            // Avoid heavy prefetch; resolve parts/addresses on-demand per MMS
            val tIngestStart = System.currentTimeMillis()
            AppLogger.d("Ingest assembly started allCount=${all.size}")

            for (item in all) {
                val id = hashFor(item.kind.name, item.sender, item.body, item.timestamp)
                val entity = MessageEntity(
                    id = id,
                    kind = item.kind.name,
                    providerId = item.providerId,
                    msgBox = item.msgBox,
                    threadId = item.threadId,
                    address = item.sender,
                    body = item.body,
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
                    val pid = item.providerId ?: -1L
                    val tPartsStart = System.currentTimeMillis()
                    // Prefer SmsItem.parts when provided; fallback to meta scan
                    val partsFromItem = item.parts
                    val partEntities = if (partsFromItem.isNotEmpty()) {
                        partsFromItem.mapNotNull { p ->
                            val isImage = p.contentType?.startsWith("image/") == true
                            val storedPath = if (p.isAttachment) p.localUriPath else null
                            val textValue = if (!p.isAttachment) p.text else null
                            MmsPartEntity(
                                partId = p.partId.toString(),
                                messageId = id,
                                seq = null,
                                ct = p.contentType,
                                text = textValue,
                                data = null, // store as file instead of blob
                                dataPath = storedPath,
                                name = p.filename,
                                chset = null,
                                cid = null,
                                cl = null,
                                cttS = null,
                                cttT = null,
                                cd = null,
                                fn = p.filename,
                                isImage = isImage
                            )
                        }
                    } else {
                        val partsMeta = MessageScanner.scanMmsPartsMetaFor(cr, pid)
                        partsMeta.mapNotNull { meta ->
                            val isImage = meta.ct?.startsWith("image/") == true
                            val storedPath = if (isImage || (meta.ct != null && !meta.ct.startsWith("text/"))) {
                                val contentUri = "content://mms/part/${meta.partId}"
                                saveAttachmentToFiles(cr, contentUri, meta.fn ?: meta.name)
                            } else null
                            val textValue = if (!isImage && meta.ct?.startsWith("text/") == true) meta.text else null
                            MmsPartEntity(
                                partId = meta.partId.toString(),
                                messageId = id,
                                seq = meta.seq,
                                ct = meta.ct,
                                text = textValue,
                                data = null,
                                dataPath = storedPath ?: meta.dataPath,
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
                        }
                    }
                    partEntitiesAll.addAll(partEntities)
                    AppLogger.d("Ingest parts for mmsId=$pid parts=${partEntities.size} took=${System.currentTimeMillis() - tPartsStart}ms")

                    if (pid > 0) {
                        val tAddrStart = System.currentTimeMillis()
                        val addrs = MessageScanner.scanMmsAddrs(cr, pid)
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
                        AppLogger.d("Ingest addrs for mmsId=$pid count=${addrs.size} took=${System.currentTimeMillis() - tAddrStart}ms")
                    }
                }

                // Periodically flush to DB to keep memory footprint small
                if (messageEntities.size >= 500) {
                    val tDbStartPartial = System.currentTimeMillis()
                    dao.insertBatch(messageEntities, partEntitiesAll, addrEntitiesAll)
                    AppLogger.d("Partial insert batch messages=${messageEntities.size} parts=${partEntitiesAll.size} addrs=${addrEntitiesAll.size} dbMs=${System.currentTimeMillis() - tDbStartPartial}")
                    messageEntities.clear()
                    partEntitiesAll.clear()
                    addrEntitiesAll.clear()
                }
            }
            val tDbStart = System.currentTimeMillis()
            if (messageEntities.isNotEmpty() || partEntitiesAll.isNotEmpty() || addrEntitiesAll.isNotEmpty()) {
                dao.insertBatch(messageEntities, partEntitiesAll, addrEntitiesAll)
            }
            val tTotal = System.currentTimeMillis() - tIngestStart
            AppLogger.i("MessageRepository.ingestFromProviders done inserted messages=${sms.size + mms.size + rcs.size} parts=~${0 + 0} addrs=~${0 + 0} dbMs=${System.currentTimeMillis() - tDbStart} totalMs=$tTotal")
        } finally {
            ingestInFlight.set(false)
        }
    }

    private fun readPartBytes(cr: ContentResolver, partId: Long): ByteArray? {
        return try {
            val uri = Uri.parse("content://mms/part/$partId")
            cr.openInputStream(uri)?.use { it.readBytes() }
        } catch (t: Throwable) {
            null
        }
    }

    private fun saveAttachmentToFiles(cr: ContentResolver, contentRef: String, filename: String?): String? {
        return try {
            val uri = Uri.parse(contentRef)
            val dir = java.io.File((cr as? android.content.ContextWrapper)?.baseContext?.filesDir, "mms_attachments")
            if (!dir.exists()) dir.mkdirs()
            val safeName = (filename ?: "part").replace("/", "_")
            val outFile = java.io.File(dir, safeName)
            cr.openInputStream(uri)?.use { input ->
                outFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            outFile.absolutePath
        } catch (t: Throwable) {
            AppLogger.w("saveAttachmentToFiles failed: ${t.message}")
            null
        }
    }

    // Build a transport-friendly DTO for sync (base64 for binary parts)
    fun toSyncDto(entity: net.melisma.relay.db.MessageWithParts): net.melisma.relay.SyncMessageDTO {
        val message = entity.message
        val parts = entity.parts.map { p ->
            val base64 = p.data?.let { Base64.getEncoder().encodeToString(it) }
            net.melisma.relay.SyncPartDTO(
                partId = p.partId,
                contentType = p.ct,
                filename = p.fn ?: p.name,
                text = p.text,
                base64Data = base64
            )
        }
        return net.melisma.relay.SyncMessageDTO(
            id = message.id,
            kind = message.kind,
            providerId = message.providerId,
            threadId = message.threadId,
            address = message.address,
            body = message.body,
            timestamp = message.timestamp,
            dateSent = message.dateSent,
            read = message.read,
            subject = message.subject,
            mmsContentType = message.mmsContentType,
            msgBox = message.msgBox,
            status = message.status,
            serviceCenter = message.serviceCenter,
            protocol = message.protocol,
            seen = message.seen,
            locked = message.locked,
            errorCode = message.errorCode,
            parts = parts
        )
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


