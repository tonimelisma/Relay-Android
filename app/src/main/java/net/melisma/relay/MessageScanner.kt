package net.melisma.relay

import android.content.ContentResolver
import android.net.Uri
import android.provider.Telephony

object MessageScanner {
    fun scanSms(
        contentResolver: ContentResolver,
        minProviderIdExclusive: Long? = null,
        limit: Int? = null
    ): List<SmsItem> {
        val results = mutableListOf<SmsItem>()
        AppLogger.d("MessageScanner.scanSms start")
        val projection = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.THREAD_ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
            Telephony.Sms.DATE_SENT,
            Telephony.Sms.READ,
            Telephony.Sms.TYPE,
            Telephony.Sms.STATUS,
            Telephony.Sms.SERVICE_CENTER,
            Telephony.Sms.PROTOCOL,
            Telephony.Sms.SEEN,
            Telephony.Sms.LOCKED,
            Telephony.Sms.ERROR_CODE
        )
        val selection = if (minProviderIdExclusive != null) "${Telephony.Sms._ID} > ?" else null
        val selectionArgs = if (minProviderIdExclusive != null) arrayOf(minProviderIdExclusive.toString()) else null
        val sort = if (minProviderIdExclusive != null) "${Telephony.Sms._ID} ASC" else "${Telephony.Sms.DATE} DESC"
        var uri = Telephony.Sms.CONTENT_URI
        if (limit != null) {
            uri = uri.buildUpon().appendQueryParameter("limit", limit.toString()).build()
        }
        val cursor = contentResolver.query(
            uri,
            projection,
            selection,
            selectionArgs,
            sort
        )
        cursor?.use { c ->
            val idCol = c.getColumnIndex(Telephony.Sms._ID)
            val threadCol = c.getColumnIndex(Telephony.Sms.THREAD_ID)
            val addrCol = c.getColumnIndex(Telephony.Sms.ADDRESS)
            val bodyCol = c.getColumnIndex(Telephony.Sms.BODY)
            val dateCol = c.getColumnIndex(Telephony.Sms.DATE)
            val dateSentCol = c.getColumnIndex(Telephony.Sms.DATE_SENT)
            val readCol = c.getColumnIndex(Telephony.Sms.READ)
            val typeCol = c.getColumnIndex(Telephony.Sms.TYPE)
            val statusCol = c.getColumnIndex(Telephony.Sms.STATUS)
            val scCol = c.getColumnIndex(Telephony.Sms.SERVICE_CENTER)
            val protoCol = c.getColumnIndex(Telephony.Sms.PROTOCOL)
            val seenCol = c.getColumnIndex(Telephony.Sms.SEEN)
            val lockedCol = c.getColumnIndex(Telephony.Sms.LOCKED)
            val errCol = c.getColumnIndex(Telephony.Sms.ERROR_CODE)
            var count = 0
            while (c.moveToNext()) {
                val rowId = if (idCol >= 0) c.getLong(idCol) else null
                val threadId = if (threadCol >= 0) c.getLong(threadCol) else null
                val sender = if (addrCol >= 0) c.getString(addrCol) else null
                val body = if (bodyCol >= 0) c.getString(bodyCol) else null
                val ts = if (dateCol >= 0) c.getLong(dateCol) else System.currentTimeMillis()
                val dateSent = if (dateSentCol >= 0) c.getLong(dateSentCol) else null
                val read = if (readCol >= 0) c.getInt(readCol) else null
                results.add(
                    SmsItem(
                        sender = sender ?: "<sms>",
                        body = body ?: "",
                        timestamp = ts,
                        kind = MessageKind.SMS,
                        providerId = rowId,
                        threadId = threadId,
                        read = read,
                        dateSent = dateSent,
                        msgBox = if (typeCol >= 0) c.getInt(typeCol) else null,
                        smsType = if (typeCol >= 0) c.getInt(typeCol) else null,
                        status = if (statusCol >= 0) c.getInt(statusCol) else null,
                        serviceCenter = if (scCol >= 0) c.getString(scCol) else null,
                        protocol = if (protoCol >= 0) c.getInt(protoCol) else null,
                        seen = if (seenCol >= 0) c.getInt(seenCol) else null,
                        locked = if (lockedCol >= 0) c.getInt(lockedCol) else null,
                        errorCode = if (errCol >= 0) c.getInt(errCol) else null
                    )
                )
                count++
                if (limit != null && count >= limit) break
            }
        }
        AppLogger.i("MessageScanner.scanSms done count=${results.size}")
        return results
    }
    fun scanMms(
        contentResolver: ContentResolver,
        minProviderIdExclusive: Long? = null,
        limit: Int? = null
    ): List<SmsItem> {
        val results = mutableListOf<SmsItem>()
        val projection = arrayOf("_id", "date", "sub", "ct_t", "thread_id", "date_sent", "read", "m_type")
        val sort = if (minProviderIdExclusive != null) "_id ASC" else "date DESC"
        AppLogger.d("MessageScanner.scanMms start")
        val selection = if (minProviderIdExclusive != null) "_id > ?" else null
        val selectionArgs = if (minProviderIdExclusive != null) arrayOf(minProviderIdExclusive.toString()) else null
        var uri = Telephony.Mms.CONTENT_URI
        if (limit != null) {
            uri = uri.buildUpon().appendQueryParameter("limit", limit.toString()).build()
        }
        val cursor = contentResolver.query(uri, projection, selection, selectionArgs, sort)
        cursor?.use { c ->
            val idCol = c.getColumnIndex("_id")
            val dateCol = c.getColumnIndex("date")
            val subCol = c.getColumnIndex("sub")
            val ctCol = c.getColumnIndex("ct_t")
            val threadCol = c.getColumnIndex("thread_id")
            val dateSentCol = c.getColumnIndex("date_sent")
            val readCol = c.getColumnIndex("read")
            val boxCol = c.getColumnIndex("m_type")
            var count = 0
            while (c.moveToNext()) {
                val id = if (idCol >= 0) c.getLong(idCol) else -1L
                // MMS date column is in seconds; convert to ms
                val tsRaw = if (dateCol >= 0) c.getLong(dateCol) else System.currentTimeMillis()
                val ts = if (tsRaw < 10_000_000_000L) tsRaw * 1000 else tsRaw
                val sub = if (subCol >= 0) c.getString(subCol) else null
                val ctT = if (ctCol >= 0) c.getString(ctCol) else null
                val threadId = if (threadCol >= 0) c.getLong(threadCol) else null
                val dateSentRaw = if (dateSentCol >= 0) c.getLong(dateSentCol) else null
                val dateSent = dateSentRaw?.let { if (it < 10_000_000_000L) it * 1000 else it }
                val read = if (readCol >= 0) c.getInt(readCol) else null

                val sender = resolveMmsSender(contentResolver, id)
                val textBody = resolveMmsTextParts(contentResolver, id)

                val finalBody = when {
                    !textBody.isNullOrBlank() -> textBody
                    !sub.isNullOrBlank() -> "sub: $sub"
                    else -> "MMS"
                }

                results.add(
                    SmsItem(
                        sender = sender ?: "<mms:$id>",
                        body = finalBody,
                        timestamp = ts,
                        kind = MessageKind.MMS,
                        providerId = id,
                        msgBox = if (boxCol >= 0) c.getInt(boxCol) else null,
                        threadId = threadId,
                        read = read,
                        dateSent = dateSent,
                        subject = sub,
                        mmsContentType = ctT
                    )
                )
                count++
                if (limit != null && count >= limit) break
            }
        }
        AppLogger.i("MessageScanner.scanMms done count=${results.size}")
        return results
    }

    data class MmsDetailed(
        val mmsId: Long,
        val timestampMs: Long,
        val sender: String?,
        val subject: String?,
        val textParts: List<String>,
        val partMeta: List<MmsPartMeta>
    )

    data class MmsPartMeta(
        val partId: Long,
        val seq: Int?,
        val ct: String?,
        val hasData: Boolean,
        val dataPath: String?,
        val name: String?,
        val chset: String?,
        val cd: String?,
        val fn: String?,
        val cid: String?,
        val cl: String?,
        val cttS: String?,
        val cttT: String?,
        val text: String?
    )

    fun scanMmsDetailed(contentResolver: ContentResolver, limit: Int = 25): List<MmsDetailed> {
        val results = mutableListOf<MmsDetailed>()
        val projection = arrayOf("_id", "date", "sub")
        val sort = "date DESC"
        val cursor = contentResolver.query(Telephony.Mms.CONTENT_URI, projection, null, null, sort)
        cursor?.use { c ->
            var count = 0
            val idCol = c.getColumnIndex("_id")
            val dateCol = c.getColumnIndex("date")
            val subCol = c.getColumnIndex("sub")
            while (c.moveToNext() && count < limit) {
                val id = if (idCol >= 0) c.getLong(idCol) else -1L
                val tsRaw = if (dateCol >= 0) c.getLong(dateCol) else System.currentTimeMillis()
                val ts = if (tsRaw < 10_000_000_000L) tsRaw * 1000 else tsRaw
                val sub = if (subCol >= 0) c.getString(subCol) else null
                val sender = resolveMmsSender(contentResolver, id)
                val texts = resolveMmsTextPartsList(contentResolver, id)
                val parts = resolveMmsPartsMeta(contentResolver, id)
                results.add(MmsDetailed(id, ts, sender, sub, texts, parts))
                count++
            }
        }
        return results
    }

    private fun resolveMmsTextParts(contentResolver: ContentResolver, mmsId: Long): String? {
        // Parts table: content://mms/part, where mid = mmsId
        // Columns typically: _id, ct (MIME), text, _data
        val partUri = Uri.parse("content://mms/part")
        val selection = "mid=?"
        val args = arrayOf(mmsId.toString())
        val texts = mutableListOf<String>()
        try {
            contentResolver.query(partUri, arrayOf("_id", "ct", "text"), selection, args, null)?.use { pc ->
                val ctCol = pc.getColumnIndex("ct")
                val textCol = pc.getColumnIndex("text")
                while (pc.moveToNext()) {
                    val ct = if (ctCol >= 0) pc.getString(ctCol) else null
                    if (ct != null && ct.startsWith("text/")) {
                        val t = if (textCol >= 0) pc.getString(textCol) else null
                        if (!t.isNullOrBlank()) texts.add(t)
                    }
                }
            }
        } catch (t: Throwable) {
            AppLogger.w("resolveMmsTextParts failed: ${t.message}")
        }
        return if (texts.isEmpty()) null else texts.joinToString(separator = "\n")
    }

    private fun resolveMmsTextPartsList(contentResolver: ContentResolver, mmsId: Long): List<String> {
        val partUri = Uri.parse("content://mms/part")
        val selection = "mid=?"
        val args = arrayOf(mmsId.toString())
        val texts = mutableListOf<String>()
        try {
            contentResolver.query(partUri, arrayOf("_id", "ct", "text"), selection, args, null)?.use { pc ->
                val ctCol = pc.getColumnIndex("ct")
                val textCol = pc.getColumnIndex("text")
                while (pc.moveToNext()) {
                    val ct = if (ctCol >= 0) pc.getString(ctCol) else null
                    if (ct != null && ct.startsWith("text/")) {
                        val t = if (textCol >= 0) pc.getString(textCol) else null
                        if (!t.isNullOrBlank()) texts.add(t)
                    }
                }
            }
        } catch (t: Throwable) {
            AppLogger.w("resolveMmsTextPartsList failed: ${t.message}")
        }
        return texts
    }

    private fun resolveMmsPartsMeta(contentResolver: ContentResolver, mmsId: Long): List<MmsPartMeta> {
        val partUri = Uri.parse("content://mms/part")
        val selection = "mid=?"
        val args = arrayOf(mmsId.toString())
        val metas = mutableListOf<MmsPartMeta>()
        try {
            contentResolver.query(
                partUri,
                arrayOf("_id", "ct", "seq", "_data", "name", "chset", "cd", "fn", "cid", "cl", "ctt_s", "ctt_t", "text"),
                selection,
                args,
                null
            )?.use { pc ->
                val idCol = pc.getColumnIndex("_id")
                val ctCol = pc.getColumnIndex("ct")
                val seqCol = pc.getColumnIndex("seq")
                val dataCol = pc.getColumnIndex("_data")
                val nameCol = pc.getColumnIndex("name")
                val chsetCol = pc.getColumnIndex("chset")
                val cdCol = pc.getColumnIndex("cd")
                val fnCol = pc.getColumnIndex("fn")
                val cidCol = pc.getColumnIndex("cid")
                val clCol = pc.getColumnIndex("cl")
                val cttSCol = pc.getColumnIndex("ctt_s")
                val cttTCol = pc.getColumnIndex("ctt_t")
                val textCol = pc.getColumnIndex("text")
                while (pc.moveToNext()) {
                    val pid = if (idCol >= 0) pc.getLong(idCol) else -1L
                    val ct = if (ctCol >= 0) pc.getString(ctCol) else null
                    val seq = if (seqCol >= 0) pc.getInt(seqCol) else null
                    val dataPath = if (dataCol >= 0) pc.getString(dataCol) else null
                    val hasData = !dataPath.isNullOrBlank()
                    val name = if (nameCol >= 0) pc.getString(nameCol) else null
                    val chset = if (chsetCol >= 0) pc.getString(chsetCol) else null
                    val cd = if (cdCol >= 0) pc.getString(cdCol) else null
                    val fn = if (fnCol >= 0) pc.getString(fnCol) else null
                    val cid = if (cidCol >= 0) pc.getString(cidCol) else null
                    val cl = if (clCol >= 0) pc.getString(clCol) else null
                    val cttS = if (cttSCol >= 0) pc.getString(cttSCol) else null
                    val cttT = if (cttTCol >= 0) pc.getString(cttTCol) else null
                    val text = if (textCol >= 0) pc.getString(textCol) else null
                    metas.add(
                        MmsPartMeta(
                            pid, seq, ct, hasData, dataPath, name, chset, cd, fn, cid, cl, cttS, cttT, text
                        )
                    )
                }
            }
        } catch (t: Throwable) {
            AppLogger.w("resolveMmsPartsMeta failed: ${t.message}")
        }
        return metas
    }

    // Public single-message variant to avoid expensive bulk prefetching
    fun scanMmsPartsMetaFor(contentResolver: ContentResolver, mmsId: Long): List<MmsPartMeta> {
        val t0 = System.currentTimeMillis()
        val metas = resolveMmsPartsMeta(contentResolver, mmsId)
        AppLogger.d("scanMmsPartsMetaFor mmsId=$mmsId parts=${metas.size} took=${System.currentTimeMillis() - t0}ms")
        return metas
    }

    private fun resolveMmsSender(contentResolver: ContentResolver, mmsId: Long): String? {
        // Addr table per message: content://mms/<id>/addr, columns include address, type
        // Heuristic: type=137 is 'from' per common references
        val addrUri = Uri.parse("content://mms/$mmsId/addr")
        try {
            contentResolver.query(addrUri, arrayOf("address", "type"), null, null, null)?.use { ac ->
                val addrCol = ac.getColumnIndex("address")
                val typeCol = ac.getColumnIndex("type")
                while (ac.moveToNext()) {
                    val type = if (typeCol >= 0) ac.getInt(typeCol) else -1
                    val address = if (addrCol >= 0) ac.getString(addrCol) else null
                    if (type == 137 && !address.isNullOrBlank()) {
                        return address
                    }
                }
            }
        } catch (t: Throwable) {
            AppLogger.w("resolveMmsSender failed: ${t.message}")
        }
        return null
    }

    fun scanRcsHeuristics(contentResolver: ContentResolver, limit: Int = 25): List<SmsItem> {
        val results = mutableListOf<SmsItem>()
        AppLogger.d("MessageScanner.scanRcsHeuristics start")
        // Heuristic pass via MMS table for likely RCS entries
        val projection = arrayOf("_id", "date", "sub", "ct_t")
        val sort = "date DESC"
        val cursor = contentResolver.query(Telephony.Mms.CONTENT_URI, projection, null, null, sort)
        cursor?.use { c ->
            var count = 0
            val idCol = c.getColumnIndex("_id")
            val dateCol = c.getColumnIndex("date")
            val subCol = c.getColumnIndex("sub")
            val ctCol = c.getColumnIndex("ct_t")
            while (c.moveToNext() && count < limit) {
                val ct = if (ctCol >= 0) c.getString(ctCol) else null
                if (ct != null && (ct.contains("application/vnd.gsma.rcs", ignoreCase = true) || ct.contains("rcs", ignoreCase = true))) {
                    val id = if (idCol >= 0) c.getLong(idCol) else -1L
                    val tsRaw = if (dateCol >= 0) c.getLong(dateCol) else System.currentTimeMillis()
                    val ts = if (tsRaw < 10_000_000_000L) tsRaw * 1000 else tsRaw
                    val sub = if (subCol >= 0) c.getString(subCol) else null
                    results.add(
                        SmsItem(
                            sender = "<rcs:$id>",
                            body = sub ?: "RCS candidate ($ct)",
                            timestamp = ts,
                            kind = MessageKind.RCS,
                            providerId = id
                        )
                    )
                    count++
                }
            }
        }
        // Attempt Samsung proprietary provider if gate allows
        if (ImProviderGate.shouldUseImOrCachedFalse()) {
            try {
                val uri: Uri = Uri.parse("content://im/chat")
                contentResolver.query(uri, null, null, null, "date DESC")?.use { c ->
                    val bodyCol = c.getColumnIndex("body")
                    val addrCol = c.getColumnIndex("address")
                    val dateCol = c.getColumnIndex("date")
                    val idCol = c.getColumnIndex("_id")
                    var count = 0
                    while (c.moveToNext() && count < limit) {
                        val body = if (bodyCol >= 0) c.getString(bodyCol) else null
                        val addr = if (addrCol >= 0) c.getString(addrCol) else null
                        val tsRaw = if (dateCol >= 0) c.getLong(dateCol) else System.currentTimeMillis()
                        val ts = if (tsRaw < 10_000_000_000L) tsRaw * 1000 else tsRaw
                        val pid = if (idCol >= 0) c.getLong(idCol) else null
                        if (!body.isNullOrBlank()) {
                            results.add(
                                SmsItem(
                                    sender = addr ?: "<rcs>",
                                    body = body,
                                    timestamp = ts,
                                    kind = MessageKind.RCS,
                                    providerId = pid
                                )
                            )
                            count++
                        }
                    }
                }
            } catch (t: Throwable) {
                AppLogger.w("MessageScanner.scanRcsHeuristics samsung provider not accessible: ${t.message}")
            }
        }
        AppLogger.i("MessageScanner.scanRcsHeuristics done count=${results.size}")
        return results
    }

    data class MmsAddrRow(
        val address: String?,
        val type: Int?,
        val charset: String?
    )

    fun scanMmsAddrs(contentResolver: ContentResolver, mmsId: Long): List<MmsAddrRow> {
        val rows = mutableListOf<MmsAddrRow>()
        val addrUri = Uri.parse("content://mms/$mmsId/addr")
        try {
            contentResolver.query(addrUri, arrayOf("address", "type", "charset"), null, null, null)?.use { ac ->
                val addrCol = ac.getColumnIndex("address")
                val typeCol = ac.getColumnIndex("type")
                val csCol = ac.getColumnIndex("charset")
                while (ac.moveToNext()) {
                    val address = if (addrCol >= 0) ac.getString(addrCol) else null
                    val type = if (typeCol >= 0) ac.getInt(typeCol) else null
                    val charset = if (csCol >= 0) ac.getString(csCol) else null
                    rows.add(MmsAddrRow(address, type, charset))
                }
            }
        } catch (t: Throwable) {
            AppLogger.w("scanMmsAddrs failed: ${t.message}")
        }
        return rows
    }
}


