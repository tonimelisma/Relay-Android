package net.melisma.relay

import android.content.ContentResolver
import android.net.Uri
import android.provider.Telephony

object MessageScanner {
    fun scanSms(contentResolver: ContentResolver, limit: Int = 50): List<SmsItem> {
        val results = mutableListOf<SmsItem>()
        AppLogger.d("MessageScanner.scanSms start")
        val projection = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
            Telephony.Sms.TYPE
        )
        val selection = "${Telephony.Sms.TYPE} = ?"
        val selectionArgs = arrayOf(Telephony.Sms.MESSAGE_TYPE_INBOX.toString())
        val sort = "${Telephony.Sms.DATE} DESC"
        val cursor = contentResolver.query(
            Telephony.Sms.CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            sort
        )
        cursor?.use { c ->
            var count = 0
            val addrCol = c.getColumnIndex(Telephony.Sms.ADDRESS)
            val bodyCol = c.getColumnIndex(Telephony.Sms.BODY)
            val dateCol = c.getColumnIndex(Telephony.Sms.DATE)
            while (c.moveToNext() && count < limit) {
                val sender = if (addrCol >= 0) c.getString(addrCol) else null
                val body = if (bodyCol >= 0) c.getString(bodyCol) else null
                val ts = if (dateCol >= 0) c.getLong(dateCol) else System.currentTimeMillis()
                results.add(
                    SmsItem(
                        sender = sender ?: "<sms>",
                        body = body ?: "",
                        timestamp = ts,
                        kind = MessageKind.SMS
                    )
                )
                count++
            }
        }
        AppLogger.i("MessageScanner.scanSms done count=${results.size}")
        return results
    }
    fun scanMms(contentResolver: ContentResolver, limit: Int = 25): List<SmsItem> {
        val results = mutableListOf<SmsItem>()
        val projection = arrayOf("_id", "date", "sub", "ct_t")
        val sort = "date DESC"
        AppLogger.d("MessageScanner.scanMms start")
        val cursor = contentResolver.query(Telephony.Mms.CONTENT_URI, projection, null, null, sort)
        cursor?.use { c ->
            var count = 0
            val idCol = c.getColumnIndex("_id")
            val dateCol = c.getColumnIndex("date")
            val subCol = c.getColumnIndex("sub")
            while (c.moveToNext() && count < limit) {
                val id = if (idCol >= 0) c.getLong(idCol) else -1L
                val ts = if (dateCol >= 0) c.getLong(dateCol) else System.currentTimeMillis()
                val sub = if (subCol >= 0) c.getString(subCol) else null

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
                        kind = MessageKind.MMS
                    )
                )
                count++
            }
        }
        AppLogger.i("MessageScanner.scanMms done count=${results.size}")
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
                    val ts = if (dateCol >= 0) c.getLong(dateCol) else System.currentTimeMillis()
                    val sub = if (subCol >= 0) c.getString(subCol) else null
                    results.add(
                        SmsItem(
                            sender = "<rcs:$id>",
                            body = sub ?: "RCS candidate ($ct)",
                            timestamp = ts,
                            kind = MessageKind.RCS
                        )
                    )
                    count++
                }
            }
        }
        // Attempt Samsung proprietary provider
        try {
            val uri: Uri = Uri.parse("content://im/chat")
            contentResolver.query(uri, null, null, null, "date DESC")?.use { c ->
                val bodyCol = c.getColumnIndex("body")
                val addrCol = c.getColumnIndex("address")
                var count = 0
                while (c.moveToNext() && count < limit) {
                    val body = if (bodyCol >= 0) c.getString(bodyCol) else null
                    val addr = if (addrCol >= 0) c.getString(addrCol) else null
                    if (!body.isNullOrBlank()) {
                        results.add(
                            SmsItem(
                                sender = addr ?: "<rcs>",
                                body = body,
                                timestamp = System.currentTimeMillis(),
                                kind = MessageKind.RCS
                            )
                        )
                        count++
                    }
                }
            }
        } catch (t: Throwable) {
            AppLogger.w("MessageScanner.scanRcsHeuristics samsung provider not accessible: ${t.message}")
        }
        AppLogger.i("MessageScanner.scanRcsHeuristics done count=${results.size}")
        return results
    }
}


