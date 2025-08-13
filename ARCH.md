# 📐 Architecture Document: Android SMS Sync App

## Overview

This document describes the technical architecture of the Android SMS Sync app, which listens for incoming SMS messages on Android devices and uploads them to a cloud backend. The app **does not send or modify SMS messages**. It is designed for privacy, stability, and minimal battery impact.

---

## 🧱 High-Level Architecture Summary

- SMS will be received via a manifest-declared `BroadcastReceiver` (Phase 2+)
- SMS will be received via a manifest-declared `BroadcastReceiver` (Phase 2+). Implemented as `SmsReceiver` pushing into `SmsInMemoryStore` for now.
- MMS notifications will be received via WAP push (`MmsReceiver`) and surfaced minimally; a manual provider scan reads MMS text parts (`content://mms/part`) and sender (`content://mms/<id>/addr`). RCS is heuristic via MMS DB content types and optional `content://im/chat` where available.
- Messages will be stored in a local Room database (Phase 4+)
- A background WorkManager job will upload unsynced messages (Phase 5+)
- The UI will observe the local DB and reflect sync status (Phase 4+)
- Messages will be deduplicated using a content-based hash (Phase 4+)

---

## 📦 Modules / Layers

### 1. SMS Receiving Layer

- Uses a manifest-declared `BroadcastReceiver` (`SmsReceiver`)
- Listens for `android.provider.Telephony.SMS_RECEIVED`
- Parses sender, body, and timestamp from received messages
- Triggers WorkManager to upload messages
- Persists message in Room DB

**Why:** Manifest-declared receivers are the only reliable way to receive SMS in the background across all supported Android versions.

---

### 2. Local Persistence Layer

- Uses Jetpack Room ORM to store SMS data
- Messages are stored in a `SmsMessageEntity` table
- A SHA-256 hash of `(sender + body + timestamp)` is used as the primary key
- `LiveData` or `StateFlow` is used to observe changes from the UI

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

- Built using MVVM: ViewModel + LiveData or StateFlow. For Phase 2, Compose directly observes a `StateFlow` from `SmsInMemoryStore` using `collectAsState()`. A manual "Scan SMS/MMS/RCS" button calls `MessageScanner` to query providers (SMS inbox, MMS + parts/addr, and heuristic RCS).
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

- Messages are uniquely identified by `SHA-256(sender + timestamp + body)`
- Hash is used as the primary key in Room
- Hash is sent with cloud uploads to prevent re-processing

**Why:** SMS IDs vary between Android versions and devices — content-based hashes are more reliable.

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