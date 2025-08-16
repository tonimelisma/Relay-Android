# 📐 Architecture Document: Android SMS/MMS/RCS Sync App

## Overview

This document describes the technical architecture of the Android SMS/MMS/RCS Sync app, which listens for incoming SMS/MMS and heuristic RCS, displays them in a unified list, and later uploads them to a cloud backend. The app **does not send or modify messages**. It is designed for privacy, stability, and minimal battery impact.

---

## 🧱 High-Level Architecture Summary

- SMS is received via a manifest-declared `BroadcastReceiver` (`SmsReceiver`).
- MMS notifications are received via WAP push (`MmsReceiver`, `application/vnd.wap.mms-message`). Provider scans read MMS text parts (`content://mms/part`) and sender (`content://mms/<id>/addr`). RCS is heuristic via MMS DB content types and optional `content://im/chat` where available.
- Receivers trigger repository ingest on a background thread and persist directly into Room (no in-memory intermediary).
- Messages are stored in a local Room database mirroring provider fields with MMS parts/addresses.
- Foreground change detection via content observers on `content://sms` and `content://mms` (and best-effort `content://im/chat` for RCS). Manual scan and foreground polling were removed.
- Background periodic ingest via WorkManager (~15 min) catches missed broadcasts and RCS. Scheduled after boot via `BootReceiver`; `MainActivity` onStart() verifies health and reschedules only if missing/cancelled.
- A background WorkManager job will upload unsynced messages (Phase 5+)
- The UI observes the local DB and reflects changes; no manual refresh button. The UI also surfaces a "Last synced" label read from `SharedPreferences` (`lastSyncSuccessTs`) updated by `MessageSyncWorker` on success.
- Messages are deduplicated using a content-based hash.

---

## 📦 Modules / Layers

### 1. SMS Receiving Layer

- Uses a manifest-declared `BroadcastReceiver` (`SmsReceiver`)
- Listens for `android.provider.Telephony.SMS_RECEIVED`
- Parses sender, body, and timestamp from received messages
- Triggers ingest into Room DB via repository (off main thread)
- Future: triggers WorkManager to upload messages (Phase 5+)

**Why:** Manifest-declared receivers are the only reliable way to receive SMS in the background across all supported Android versions.

---

### 2. Local Persistence Layer

- Uses Jetpack Room ORM to store SMS/MMS/RCS data
- Schema (current implementation):
  - `messages` (Room entity `MessageEntity`)
    - `id` TEXT PRIMARY KEY (SHA-256 of kind + sender + body + timestamp)
    - `kind` TEXT (SMS|MMS|RCS)
    - `providerId` INTEGER NULL, `msgBox` INTEGER NULL
    - `threadId` INTEGER NULL, `address` TEXT NULL, `body` TEXT NULL
    - `timestamp` INTEGER (ms), `dateSent` INTEGER NULL (ms), `read` INTEGER NULL, `synced` INTEGER NULL (0/1; default 0)
    - `status` INTEGER NULL, `serviceCenter` TEXT NULL, `protocol` INTEGER NULL
    - `seen` INTEGER NULL, `locked` INTEGER NULL, `errorCode` INTEGER NULL
    - `subject` TEXT NULL, `mmsContentType` TEXT NULL
    - `smsJson` TEXT NULL, `mmsJson` TEXT NULL, `convJson` TEXT NULL
  - `mms_parts` (Room entity `MmsPartEntity`)
    - `partId` TEXT PRIMARY KEY, `messageId` TEXT (FK)
    - `seq` INTEGER NULL, `ct` TEXT NULL, `text` TEXT NULL
    - `data` BLOB NULL (full image bytes for image parts; used directly for small previews)
    - `dataPath` TEXT NULL, `cd` TEXT NULL, `fn` TEXT NULL
    - `name` TEXT NULL, `chset` TEXT NULL, `cid` TEXT NULL, `cl` TEXT NULL, `cttS` TEXT NULL, `cttT` TEXT NULL
    - `isImage` INTEGER NULL (0/1)
  - `mms_addr` (Room entity `MmsAddrEntity`, planned ingestion)
    - `rowId` INTEGER PK, `messageId` TEXT (FK), `address` TEXT NULL, `type` INTEGER NULL, `charset` TEXT NULL
    - All rows from `content://mms/<id>/addr` are persisted for each MMS
- UI observes a transactional relation (`@Transaction` `observeMessagesWithParts()`) via `MessageRepository.observeMessagesWithParts()` and renders small image previews from stored bytes
- MMS detailed metadata scan is performed once per ingest pass and reused for all new MMS rows
- Deduplication via `messages.id` primary key (content-based hash)
- MMS timestamps (seconds) normalized to ms for unified ordering in the repository
- Provider fields mapped into `messages` when available: `threadId`, `read`, `dateSent`, MMS `subject`

### Incremental Ingest & Permissions Gate

- On first run per kind, perform a full scan. Then incremental ingest filters by provider id per kind (new rows have `providerId > lastSeen`) using ascending, chunked `_id` queries to cover all folders (not just inbox) without arbitrary caps. Inserts are batched transactionally to minimize UI emissions and memory usage.
- Ingest runs automatically at app start (when permissions are granted). Content observers on `content://sms`, `content://mms`, and best-effort `content://im/chat` trigger ingest on changes while the app is foregrounded. The previous foreground polling loop (~10s) and manual scan button were removed.
- WorkManager performs periodic ingest in the background to catch missed broadcasts and all RCS.
- Permissions gate: if required permissions (READ_SMS, RECEIVE_SMS, RECEIVE_MMS, RECEIVE_WAP_PUSH) are missing, the UI renders only the explanation + request flow; list is hidden until all are granted.
- A simple concurrency guard ensures only one ingest runs at a time.
- Permissions gate: if required permissions (READ_SMS, RECEIVE_SMS, RECEIVE_MMS, RECEIVE_WAP_PUSH) are missing, the UI renders only the explanation + request flow; list is hidden until all are granted.

**Why:** Room provides type-safe queries, lifecycle awareness, and easy testing support.

---

### 3. Cloud Sync Layer

- Background sync is implemented using WorkManager
- `SmsUploadWorker` reads unsynced messages from the DB
- Messages are sent as JSON via HTTPS to the backend
- On success, the message is marked as synced
- On failure, WorkManager retries with exponential backoff
- Sync jobs are constrained to run only on available network

**Why:** WorkManager is the most battery- and API-friendly job scheduler that handles device restarts and Doze mode.

---

### 4. UI Layer

- Compose observes `MainViewModel.messages` (backed by Room Flow `observeMessagesWithParts()`) using `collectAsState()`. No manual scan button; updates come from receivers, observers, and background worker.
- UI shows:
  - List of messages
  - Sync status
  - Permission state
- Displays an onboarding flow if permissions are not yet granted

**Why:** ViewModel enables lifecycle-aware state management and clean separation of concerns.

---

### 5. Permissions UX (Phase 1)

- Uses `ActivityResultContracts.RequestMultiplePermissions` to request `RECEIVE_SMS` and `READ_SMS`, and also requests `RECEIVE_MMS` and `RECEIVE_WAP_PUSH` at runtime.
- Shows basic status UI and request button
- Future: rationale UI and settings redirect if denied

**Why:** Respecting runtime permissions is essential for user trust and Play Store compliance.

---

### 6. Settings & Configuration

- Stores settings in `SharedPreferences`
- Configurable options:
  - Enable/disable cloud sync
  - Clear history
  - (Optional) Change API endpoint for staging vs. production

**Why:** SharedPreferences are ideal for simple key-value configuration.

---

### 7. Deduplication Strategy

- Messages are uniquely identified by `SHA-256(kind + sender + body + timestamp)`
- Hash is used as the primary key in Room
- Hash is sent with cloud uploads to prevent re-processing

**Why:** SMS IDs vary between Android versions and devices — content-based hashes are more reliable.

---

## ⚙️ Implementation Notes

- Ingestion concurrency guard via `AtomicBoolean` to prevent overlapping runs.
- MMS parts/addresses are fetched on-demand per new MMS instead of bulk-prefetching to reduce UI update latency.
- RCS timestamps are normalized to ms; Samsung provider timestamps are used rather than `System.currentTimeMillis()`.
- Room destructive migrations allowed during pre-release via `fallbackToDestructiveMigration(true)`.

---

## 🔄 Sync Workflow

1. SMS is received and parsed
2. Stored in Room DB if not already present (hash-based check)
3. WorkManager is triggered with the new message
4. Upload is attempted
5. On success, `synced = true` is saved in DB
6. UI updates via reactive DB observation

---

## 📊 Data Model

### `SmsMessageEntity` (Room)

```kotlin
@Entity(tableName = "sms_messages")
data class SmsMessageEntity(
    @PrimaryKey val id: String, // SHA-256 hash of sender + timestamp + body
    val sender: String,
    val body: String,
    val timestamp: Long, // epoch milliseconds
    val synced: Boolean = false
)

## 🧪 Testing Strategy

The app will be tested across multiple layers to ensure correctness, stability, and reliability. The goal is to isolate logic, minimize manual QA, and automate where possible.

### ✅ Unit Tests

| Component           | What to Test                                  | Tools/Frameworks       |
|---------------------|-----------------------------------------------|------------------------|
| Room DB             | Insert, query, and deduplication logic        | JUnit, Room in-memory DB |
| Hash Generator      | Correct SHA-256 generation from SMS content   | JUnit, Mockito         |
| ViewModel Logic     | State updates, permission flows               | JUnit, LiveData Test Rules / Turbine (for Flow) |
| SmsUploadWorker     | Success/failure handling, retry logic         | WorkManager test APIs  |
| SharedPreferences   | Settings toggles and default values           | JUnit                  |

---

### ✅ Instrumented Tests

| Component         | What to Test                                   | Tools/Frameworks     |
|-------------------|------------------------------------------------|----------------------|
| `SmsReceiver`     | Receipt of `SMS_RECEIVED` broadcast            | Robolectric or Emulator |
| Permissions Flow  | Runtime permission grant/deny flows            | Espresso, UI Automator |
| End-to-End Sync   | SMS receipt → DB insert → cloud sync           | Emulator + test server |
| Settings UI       | Toggle sync, clear history                     | Espresso             |

---

### ✅ Manual Testing (Minimal)

| Scenario                          | Devices / Tools         |
|----------------------------------|--------------------------|
| Full E2E sync on real device     | Android device w/ SIM    |
| Permission edge cases            | Settings toggle tests    |
| Offline → online sync recovery   | Airplane mode toggling   |
| OS Version Compatibility         | Android 6.0–14 devices   |

---

### ✅ CI Recommendations

- Run unit tests on every PR (GitHub Actions or Bitrise)
- Lint + formatting checks (`ktlint`, `detekt`)
- Optionally run instrumented tests on emulator matrix (API 23+)

---

**Test Coverage Priorities:**

1. Deduplication logic (core integrity requirement)
2. Upload retry logic (resilience)
3. DB queries (sync state correctness)
4. Receiver and Worker behaviors
5. ViewModel state flow (user experience)


N.B. for further architectural guidance please also read CLAUDE.md