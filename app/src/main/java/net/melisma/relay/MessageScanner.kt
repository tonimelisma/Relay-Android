package net.melisma.relay

import android.content.ContentResolver
import android.database.Cursor
import android.net.Uri
import android.provider.Telephony

object MessageScanner {
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
            val ctCol = c.getColumnIndex("ct_t")
            while (c.moveToNext() && count < limit) {
                val id = if (idCol >= 0) c.getLong(idCol) else -1L
                val ts = if (dateCol >= 0) c.getLong(dateCol) else System.currentTimeMillis()
                val sub = if (subCol >= 0) c.getString(subCol) else null
                val ct = if (ctCol >= 0) c.getString(ctCol) else null
                val body = buildString {
                    if (!sub.isNullOrBlank()) append("sub: ").append(sub)
                }
                results.add(
                    SmsItem(
                        sender = "<mms:$id>",
                        body = if (body.isBlank()) "MMS" else body,
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


