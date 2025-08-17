package net.melisma.relay

import android.content.ContentResolver
import android.net.Uri
import android.provider.Telephony

object MessageScanner {
    private fun <T> queryProvider(
        contentResolver: ContentResolver,
        uri: Uri,
        projection: Array<String>,
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String,
        mapper: (android.database.Cursor) -> T
    ): List<T> {
        val results = mutableListOf<T>()
        try {
            contentResolver.query(uri, projection, selection, selectionArgs, sortOrder)?.use { cursor ->
                while (cursor.moveToNext()) {
                    results.add(mapper(cursor))
                }
            }
        } catch (e: Exception) {
            AppLogger.e("Failed to query provider for uri: $uri", e)
        }
        return results
    }
    fun scanSms(
        contentResolver: ContentResolver,
        minProviderIdExclusive: Long? = null,
        limit: Int? = null
    ): List<SmsItem> {
        AppLogger.d("MessageScanner.scanSms start")
        val projection = arrayOf(
            Telephony.Sms._ID, Telephony.Sms.THREAD_ID, Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY, Telephony.Sms.DATE, Telephony.Sms.DATE_SENT,
            Telephony.Sms.READ, Telephony.Sms.TYPE, Telephony.Sms.STATUS,
            Telephony.Sms.SERVICE_CENTER, Telephony.Sms.PROTOCOL, Telephony.Sms.SEEN,
            Telephony.Sms.LOCKED, Telephony.Sms.ERROR_CODE
        )
        val selection = if (minProviderIdExclusive != null) "${Telephony.Sms._ID} > ?" else null
        val selectionArgs = if (minProviderIdExclusive != null) arrayOf(minProviderIdExclusive.toString()) else null
        val sort = if (minProviderIdExclusive != null) "${Telephony.Sms._ID} ASC" else "${Telephony.Sms.DATE} DESC"
        var uri = Telephony.Sms.CONTENT_URI
        if (limit != null) {
            uri = uri.buildUpon().appendQueryParameter("limit", limit.toString()).build()
        }

        val smsList = queryProvider(contentResolver, uri, projection, selection, selectionArgs, sort) { c ->
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

            SmsItem(
                sender = c.getString(addrCol) ?: "<sms>",
                body = c.getString(bodyCol) ?: "",
                timestamp = c.getLong(dateCol),
                kind = MessageKind.SMS,
                providerId = c.getLong(idCol),
                threadId = c.getLong(threadCol),
                read = c.getInt(readCol),
                dateSent = c.getLong(dateSentCol),
                msgBox = c.getInt(typeCol),
                smsType = c.getInt(typeCol),
                status = c.getInt(statusCol),
                serviceCenter = c.getString(scCol),
                protocol = c.getInt(protoCol),
                seen = c.getInt(seenCol),
                locked = c.getInt(lockedCol),
                errorCode = c.getInt(errCol)
            )
        }
        AppLogger.i("MessageScanner.scanSms done count=${smsList.size}")
        return smsList
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
                val parts = resolveMmsParts(contentResolver, id)
                val addresses = scanMmsAddrs(contentResolver, id).mapNotNull { row ->
                    val human = when (row.type) {
                        137 -> "From"
                        151 -> "To"
                        130 -> "Cc"
                        129 -> "Bcc"
                        else -> null
                    }
                    row.address?.let { addr -> human?.let { MessageAddress(addr, it) } }
                }
                val smil = parts.firstOrNull { it.contentType?.equals("application/smil", ignoreCase = true) == true && !it.text.isNullOrBlank() }?.text
                val smilLayout = smil?.let { buildSmilLayoutFromText(it, parts) }

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
                        mmsContentType = ctT,
                        parts = parts,
                        addresses = addresses,
                        smilLayout = smilLayout
                    )
                )
                count++
                if (limit != null && count >= limit) break
            }
        }
        AppLogger.i("MessageScanner.scanMms done count=${results.size}")
        return results
    }
    /**
     * Builds MessagePart list for a given MMS id from content://mms/part rows.
     * This does not copy data locally; it provides enough info for repository to persist attachments.
     */
    private fun resolveMmsParts(contentResolver: ContentResolver, mmsId: Long): List<MessagePart> {
        val partUri = Uri.parse("content://mms/part")
        val selection = "mid=?"
        val args = arrayOf(mmsId.toString())
        val parts = mutableListOf<MessagePart>()
        try {
            contentResolver.query(
                partUri,
                arrayOf("_id", "ct", "name", "fn", "text", "_data", "cid", "cl", "ctt_s"),
                selection,
                args,
                null
            )?.use { pc ->
                val idCol = pc.getColumnIndex("_id")
                val ctCol = pc.getColumnIndex("ct")
                val nameCol = pc.getColumnIndex("name")
                val fnCol = pc.getColumnIndex("fn")
                val textCol = pc.getColumnIndex("text")
                val dataCol = pc.getColumnIndex("_data")
                val cidCol = pc.getColumnIndex("cid")
                val clCol = pc.getColumnIndex("cl")
                val sizeCol = pc.getColumnIndex("ctt_s")
                while (pc.moveToNext()) {
                    val pid = if (idCol >= 0) pc.getLong(idCol) else -1L
                    val ct = if (ctCol >= 0) pc.getString(ctCol) else null
                    val name = if (nameCol >= 0) pc.getString(nameCol) else null
                    val fn = if (fnCol >= 0) pc.getString(fnCol) else null
                    val dataRef = if (dataCol >= 0) pc.getString(dataCol) else null
                    val text = if (textCol >= 0) pc.getString(textCol) else null
                    val isAttachment = ct != null && !ct.startsWith("text/")
                    val contentId = if (cidCol >= 0) pc.getString(cidCol) else null
                    val contentLoc = if (clCol >= 0) pc.getString(clCol) else null
                    val size = if (sizeCol >= 0) pc.getLong(sizeCol) else null
                    val localPath = if (!dataRef.isNullOrBlank() && isAttachment) {
                        copyPartToLocalStorage(contentResolver, pid, inferFileName(name, fn, ct))
                    } else null
                    val type = when {
                        ct == null -> MessagePartType.OTHER
                        ct.startsWith("image/") -> MessagePartType.IMAGE
                        ct.startsWith("video/") -> MessagePartType.VIDEO
                        ct.startsWith("audio/") -> MessagePartType.AUDIO
                        ct.equals("text/vcard", ignoreCase = true) || ct.equals("text/x-vcard", ignoreCase = true) -> MessagePartType.VCARD
                        ct.startsWith("text/") -> MessagePartType.TEXT
                        else -> MessagePartType.OTHER
                    }
                    parts.add(
                        MessagePart(
                            partId = pid,
                            messageId = mmsId,
                            contentType = ct,
                            localUriPath = localPath,
                            filename = name ?: fn,
                            text = if (!isAttachment) text else null,
                            isAttachment = isAttachment,
                            type = type,
                            size = size,
                            contentId = contentId,
                            contentLocation = contentLoc
                        )
                    )
                }
            }
        } catch (t: Throwable) {
            AppLogger.w("resolveMmsParts failed: ${t.message}")
        }
        return parts
    }

    private fun inferFileName(name: String?, fn: String?, ct: String?): String {
        val base = name ?: fn ?: "part"
        val safe = base.replace('/', '_')
        val ext = when {
            ct == null -> ""
            ct.startsWith("image/") -> ".jpg"
            ct.startsWith("video/") -> ".mp4"
            ct.startsWith("audio/") -> ".amr"
            else -> ""
        }
        return if (safe.contains('.')) safe else safe + ext
    }

    private fun copyPartToLocalStorage(cr: ContentResolver, partId: Long, fileName: String): String? {
        return try {
            val uri = Uri.parse("content://mms/part/$partId")
            val ctx = (cr as? android.content.ContextWrapper)?.baseContext ?: return null
            val dir = java.io.File(ctx.filesDir, "mms_attachments")
            if (!dir.exists()) dir.mkdirs()
            val outFile = java.io.File(dir, fileName)
            cr.openInputStream(uri)?.use { input ->
                outFile.outputStream().use { output -> input.copyTo(output) }
            }
            outFile.absolutePath
        } catch (t: Throwable) {
            AppLogger.w("copyPartToLocalStorage failed: ${t.message}")
            null
        }
    }

    fun parseSmil(smilXml: String): SmilPresentation {
        val slides = mutableListOf<SmilSlide>()
        try {
            val factory = org.xmlpull.v1.XmlPullParserFactory.newInstance()
            val parser = factory.newPullParser()
            parser.setInput(smilXml.reader())
            var event = parser.eventType
            var currentItems = mutableListOf<SmilItem>()
            while (event != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                if (event == org.xmlpull.v1.XmlPullParser.START_TAG) {
                    when (parser.name.lowercase()) {
                        "par" -> {
                            currentItems = mutableListOf()
                        }
                        "img", "image" -> {
                            currentItems.add(
                                SmilItem(type = "image", src = parser.getAttributeValue(null, "src"), region = parser.getAttributeValue(null, "region"))
                            )
                        }
                        "video" -> {
                            currentItems.add(
                                SmilItem(type = "video", src = parser.getAttributeValue(null, "src"), region = parser.getAttributeValue(null, "region"))
                            )
                        }
                        "audio" -> {
                            currentItems.add(
                                SmilItem(type = "audio", src = parser.getAttributeValue(null, "src"), region = parser.getAttributeValue(null, "region"))
                            )
                        }
                        "text" -> {
                            currentItems.add(
                                SmilItem(type = "text", src = parser.getAttributeValue(null, "src"), region = parser.getAttributeValue(null, "region"))
                            )
                        }
                    }
                } else if (event == org.xmlpull.v1.XmlPullParser.END_TAG) {
                    if (parser.name.equals("par", ignoreCase = true)) {
                        slides.add(SmilSlide(items = currentItems.toList()))
                        currentItems.clear()
                    }
                }
                event = parser.next()
            }
        } catch (t: Throwable) {
            AppLogger.w("parseSmil failed: ${t.message}")
        }
        return SmilPresentation(slides)
    }

    private fun buildSmilLayoutFromText(smilXml: String, parts: List<MessagePart>): SmilLayout? {
        return try {
            val pres = parseSmil(smilXml)
            val byCidOrCl = parts.associateBy { it.contentId ?: it.contentLocation }
            val order = mutableListOf<Long>()
            pres.slides.forEach { slide ->
                slide.items.forEach { item ->
                    val key = item.src
                    val matched = if (key != null) byCidOrCl[key] else null
                    if (matched != null) order.add(matched.partId)
                }
            }
            if (order.isEmpty()) null else SmilLayout(order)
        } catch (_: Throwable) {
            null
        }
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


