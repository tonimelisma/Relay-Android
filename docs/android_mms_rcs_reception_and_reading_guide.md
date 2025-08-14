# 📩 MMS & RCS Message Access on Android

This document explains:

1. **How to receive broadcasts for new MMS messages**
2. **How to read stored MMS messages**
3. **How to read stored RCS messages (where possible)**

---

## 1. Receiving New MMS Messages

### 📡 MMS Broadcast Actions

Android delivers MMS notifications to apps via **WAP Push** broadcasts:

| Action                                                                                | Who Receives It                                                                     | Notes                                       |
| ------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------- | ------------------------------------------- |
| `android.provider.Telephony.WAP_PUSH_RECEIVED` with `application/vnd.wap.mms-message` | **All apps** with matching `<intent-filter>` and `RECEIVE_MMS` permission           | Public broadcast for new MMS notifications. |
| `android.provider.Telephony.WAP_PUSH_DELIVER` with `application/vnd.wap.mms-message`  | **Default SMS app only** (requires signature-level `BROADCAST_WAP_PUSH` permission) | Private delivery broadcast.                 |

If your app is **not the default SMS app**, you can still receive the public `WAP_PUSH_RECEIVED` broadcast for MMS. This lets you know a new MMS **notification** has arrived (usually containing headers like sender, subject, and a transaction ID). The actual message body/media is downloaded by the default SMS app.

---

### 📜 Manifest Example

```xml
<uses-permission android:name="android.permission.RECEIVE_MMS" />

<receiver
    android:name=".MmsReceiver"
    android:exported="true">
    <intent-filter>
        <action android:name="android.provider.Telephony.WAP_PUSH_RECEIVED" />
        <data android:mimeType="application/vnd.wap.mms-message" />
    </intent-filter>
</receiver>
```

---

### 📂 Receiver Example (Kotlin)

```kotlin
class MmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Telephony.Sms.Intents.WAP_PUSH_RECEIVED_ACTION) {
            val type = intent.type
            if (type == "application/vnd.wap.mms-message") {
                // You have a new MMS notification
                Log.d("MmsReceiver", "New MMS notification received")
                // Actual download is handled by default SMS app
            }
        }
    }
}
```

---

## 2. Reading Stored MMS Messages

### 📍 Content Provider: `content://mms`

Once the default SMS app downloads an MMS, it is stored in the system’s **MMS database**. You can read it using the Telephony content provider.

**Permissions required:**

```xml
<uses-permission android:name="android.permission.READ_SMS" />
```

> This app does not use `READ_MMS`. On modern Android, MMS DB access falls under the SMS permission group.

**Query example:**

```kotlin
val projection = arrayOf("_id", "thread_id", "date", "msg_box", "sub", "ct_t")
val cursor = contentResolver.query(
    Telephony.Mms.CONTENT_URI,
    projection,
    null,
    null,
    "date DESC"
)

cursor?.use {
    val idCol = it.getColumnIndex("_id")
    val subCol = it.getColumnIndex("sub")
    val dateCol = it.getColumnIndex("date")
    while (it.moveToNext()) {
        val mmsId = it.getLong(idCol)
        val subject = it.getString(subCol)
        val timestamp = it.getLong(dateCol)
        Log.d("MMS", "MMS #$mmsId subject=$subject date=$timestamp")
    }
}
```

**To read MMS parts (text, images, etc.):**

- Use `content://mms/part/<partId>` for attachments.
- The `part` table contains columns like `ct` (MIME type), `_data` (file path), and `text` (text content for text parts).

---

## 3. Reading Stored RCS Messages

### ❗ No Public API

Android does **not** expose a public API for RCS (Rich Communication Services) messages. However, on many devices:

- **Google Messages RCS:** RCS chats are stored in the **MMS database** (`content://mms`) alongside regular MMS. You can read them just like MMS; they may appear with special flags or content types.
- **Samsung Messages RCS:** RCS and SMS/MMS may be stored in a **proprietary provider**, often `content://im/chat`. This URI is undocumented, but querying it can return text and metadata for RCS messages. Attachments may not be accessible.

---

### 📍 Example: Reading Google Messages RCS via MMS Table

```kotlin
val cursor = contentResolver.query(
    Telephony.Mms.CONTENT_URI,
    arrayOf("_id", "date", "sub", "ct_t"),
    null,
    null,
    "date DESC"
)

cursor?.use {
    while (it.moveToNext()) {
        val contentType = it.getString(it.getColumnIndex("ct_t"))
        if (contentType == "application/vnd.gsma.rcs-ft-http+xml" ||
            contentType == "application/vnd.gsma.rcs-ft") {
            Log.d("RCS", "Found likely RCS message in MMS DB")
        }
    }
}
```

**Notes:**

- RCS messages may use MMS-like records but with RCS-specific MIME types or metadata.
- Fields and MIME values vary by implementation; don’t hard-code on MIME checks alone.

---

### 📍 Example: Reading Samsung Messages RCS via `im/chat`

```kotlin
val rcsUri = Uri.parse("content://im/chat")
val cursor = contentResolver.query(
    rcsUri,
    null, // all columns (inspect at runtime)
    null,
    null,
    "date DESC"
)

cursor?.use {
    val bodyCol = it.getColumnIndex("body")
    val addressCol = it.getColumnIndex("address")
    while (it.moveToNext()) {
        val body = it.getString(bodyCol)
        val sender = it.getString(addressCol)
        Log.d("SamsungRCS", "From $sender: $body")
    }
}
```

**Limitations:**

- Media attachments may not be readable without internal permissions.
- Not all Samsung devices expose this URI to third-party apps.

---

## 4. Best Practices

- Request and handle **runtime permissions** (`READ_SMS`, `RECEIVE_MMS`).
- Use **content observers** on `content://mms` and, where present, `content://im/chat` to detect new DB entries for near real-time updates.
- Gracefully handle **OEM differences**: query standard MMS DB first, then OEM-specific URIs.
- Expect **partial access** to RCS content (text is usually available; rich media often is not).
- Avoid hidden/unsupported APIs for Play Store builds; stick to documented providers and observed-safe URIs.

---

## 5. Summary Matrix

| Message Type      | New Message Broadcast                                                                                        | Stored Message Access                                         |
| ----------------- | ------------------------------------------------------------------------------------------------------------ | ------------------------------------------------------------- |
| **SMS**           | `SMS_RECEIVED` (all apps)                                                                                    | `content://sms`                                               |
| **MMS**           | `WAP_PUSH_RECEIVED` with `application/vnd.wap.mms-message` (all apps); `WAP_PUSH_DELIVER` (default app only) | `content://mms` + `content://mms/part`                        |
| **RCS (Google)**  | ❌ None                                                                                                       | Appears in `content://mms` on many devices (MMS-like records) |
| **RCS (Samsung)** | ❌ None                                                                                                       | `content://im/chat` (proprietary; limited access)             |

