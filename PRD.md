# 📱 Android SMS/MMS/RCS Sync App – Product Requirements Document (PRD)

## Overview

**Goal:**  
Create a lightweight Android app that listens for incoming SMS/MMS/RCS messages, displays them in a unified list, and later uploads them to a cloud backend. The app will **not send or write** messages — only read and (later) upload.

**Platform:** Android  
**Minimum SDK:** 23 (Android 6.0)  
**Permissions Required:**  
- `RECEIVE_SMS`
- `READ_SMS`
- `RECEIVE_MMS`
- `RECEIVE_WAP_PUSH`
- `INTERNET`

---

## 🧭 Phase Roadmap

Each phase builds upon the last. You can build and test after every phase.

---

### 🟢 Phase 1 – Bootstrap & Permission Setup

**Objective:** Basic app with UI that requests SMS permissions.

**Requirements:**
- Set `minSdkVersion = 23`
- Request `RECEIVE_SMS` and `READ_SMS` at runtime
- UI:
  - Button: "Request SMS Permissions"
  - Text: Permission status (`Granted` / `Denied`)

**Deliverable:**  
App requests permissions and displays the result. Implemented in `MainActivity` with Compose UI. Manifest declares `RECEIVE_SMS`, `READ_SMS`, and `INTERNET`.

---

### 🟡 Phase 2 – Log Received SMS/MMS in UI

**Objective:** Show SMS received via `BroadcastReceiver`.

**Requirements:**
- Register a `BroadcastReceiver` for `android.provider.Telephony.SMS_RECEIVED`
- Parse sender + body from each received message
- Append message to an in-app list (RecyclerView or simple list)
- Register a `BroadcastReceiver` for MMS notifications (`WAP_PUSH_RECEIVED` with `application/vnd.wap.mms-message`) and surface a minimal entry
- Add a manual "Scan SMS/MMS/RCS" button to query `content://sms` (inbox), `content://mms` (+ `content://mms/part`, `content://mms/<id>/addr`), and heuristically surface RCS (incl. best-effort `content://im/chat` where accessible)

**Deliverable:**  
SMS and MMS notifications are persisted directly into Room via receivers triggering repository ingest on a background thread. Manual scan shows recent SMS, MMS (text parts + sender), and any heuristic RCS entries. UI observes changes reactively via ViewModel.

---

### 🟠 Phase 3 – Read Existing Inbox SMS

**Objective:** Load SMS history from `content://sms/inbox`.

**Requirements:**
- Button: "Load SMS History"
- Query inbox and display messages (sender, body, timestamp)
- Combine with live received messages
- Sort messages by timestamp (descending)

**Deliverable:**  
App shows historical and live messages together.

---

### 🔵 Phase 4 – Add Local Persistence

**Objective:** Store messages on device for reload after app restart.

**Requirements:**
- Store SMS/MMS/RCS messages in Room using a unified schema with satellite tables (full provider mirroring):
  - `messages`
    - `id` TEXT PRIMARY KEY (SHA-256 of kind + sender + body + timestamp)
    - `kind` TEXT (SMS|MMS|RCS)
    - `providerId` INTEGER NULL (provider `_id`)
    - `msgBox` INTEGER NULL (SMS `type` / MMS `m_type`)
    - `threadId` INTEGER NULL
    - `address` TEXT NULL
    - `body` TEXT NULL
    - `timestamp` INTEGER (ms since epoch)
    - `dateSent` INTEGER NULL (ms since epoch)
    - `read` INTEGER NULL (0/1)
    - `status` INTEGER NULL, `serviceCenter` TEXT NULL, `protocol` INTEGER NULL
    - `seen` INTEGER NULL, `locked` INTEGER NULL, `errorCode` INTEGER NULL
    - `subject` TEXT NULL, `mmsContentType` TEXT NULL
    - `synced` INTEGER NULL (0/1; default 0)
    - `smsJson` TEXT NULL (raw provider snapshot JSON)
    - `mmsJson` TEXT NULL (raw provider snapshot JSON)
    - `convJson` TEXT NULL (raw conversations row JSON if desired)
  - `mms_parts`
    - `partId` TEXT PRIMARY KEY (MMS part id)
    - `messageId` TEXT NOT NULL (FK → messages.id)
    - `seq` INTEGER NULL
    - `ct` TEXT NULL (MIME type, e.g. text/plain, image/jpeg)
    - `text` TEXT NULL (for text parts)
    - `data` BLOB NULL (full bytes for image parts; used directly for small previews)
    - `dataPath` TEXT NULL, `cd` TEXT NULL, `fn` TEXT NULL
    - `name` TEXT NULL, `chset` TEXT NULL, `cid` TEXT NULL, `cl` TEXT NULL, `cttS` TEXT NULL, `cttT` TEXT NULL
    - `isImage` INTEGER NULL (0/1 convenience flag)
  - `mms_addr`
    - `rowId` INTEGER PRIMARY KEY AUTOINCREMENT
    - `messageId` TEXT NOT NULL (FK → messages.id)
    - `address` TEXT NULL
    - `type` INTEGER NULL (137=from, 151=to, 130=cc)
    - `charset` TEXT NULL
    - Ingest: all rows from `content://mms/<id>/addr` are persisted per message
- Load stored messages via Room Flow on startup; `MainViewModel` exposes `messages` (backed by the `@Transaction` relation messages + parts) for a unified list
- Prevent duplicates via primary-key hash
- MMS date (seconds) normalized to ms for unified ordering
- Map available provider fields to `messages`: `threadId`, `read`, `dateSent`, `subject`
- Optional: "Clear History" button

### 🔶 Phase 4.1 – Initial Full Sync, Incremental Updates & Permissions Gate (MVP)

**Objective:** Smart incremental ingest from providers gated by permissions.

**Requirements:**
- Implement ingest that performs an initial full scan per kind (when no prior items exist), then incrementally queries new items with `timestamp > MAX(timestamp) WHERE kind = ?`
- Run ingest automatically on app start (if permissions are granted) and via a button; both centered in `MainViewModel`
- Receivers trigger ingest on new events so UI updates promptly from Room
- Add content observers on `content://sms` and `content://mms` to react to provider changes
- Add lifecycle-aware periodic polling (every ~10s while app is in foreground)
- Gate UI: if required permissions (READ_SMS, RECEIVE_SMS, RECEIVE_MMS, RECEIVE_WAP_PUSH) are not granted, show only an explanation + permission request button. If granted, show the Scan button and unified message list

**Deliverable:**  
Unified SMS/MMS/RCS list persists across app restarts and updates incrementally.

---

### 🟣 Phase 5 – Background Upload Worker (Cloud Sync)

**Objective:** Send new messages to backend in background.

**Requirements:**
- Cloud endpoint: `POST /messages`
- Use WorkManager to enqueue upload job
- Send payload: `{ sender, body, timestamp }`
- Use network constraint: `NetworkType.CONNECTED`
- Retry failed uploads (exponential backoff)
- Optional: indicate upload success/failure in UI

**Deliverable:**  
Incoming SMS are uploaded to the cloud, even when app is backgrounded.

---

### 🟤 Phase 6 – Bulk Upload & Sync State

**Objective:** Support delayed/batched sync and deduplication.

**Requirements:**
- Store last uploaded timestamp or hash
- On startup, query new messages and upload in batch
- Prevent duplicate uploads (backend or client-side)
- Sync jobs run on schedule or manual trigger

**Deliverable:**  
Only new messages are uploaded. Missed uploads sync automatically.

---

### ⚪ Phase 7 – UI Polish & Config

**Objective:** Add user-facing controls and polish.

**Requirements:**
- Settings screen:
  - "Enable Cloud Sync" toggle
  - "Clear History" button
- Optional:
  - Message sync status
  - Total synced count

**Deliverable:**  
Basic but clean UX with minimal configuration options.

---

## 📐 Non-Functional Requirements

- Must work while app is backgrounded or inactive
- Efficient battery and network usage (via WorkManager)
- Gracefully handle:
  - Permission denial
  - No network connection
- No unnecessary access (e.g., contacts or SMS sending)

---

## 🧪 Suggested Phase Timeline

| Phase | Feature                          | Est. Build Time |
|-------|----------------------------------|-----------------|
| 1     | Permission handling              | 0.5–1 day       |
| 2     | Receive & display SMS            | 1 day           |
| 3     | Load SMS inbox history           | 0.5 day         |
| 4     | Local persistence                | 1 day           |
| 5     | Upload to cloud backend          | 1–2 days        |
| 6     | Sync state & bulk upload         | 1 day           |
| 7     | Settings & UI polish             | 1 day           |

---

## ✅ Summary

This PRD breaks the app into self-contained phases with increasing functionality. You can ship early, test often, and refactor later without overcommitting to architecture from day one.



N.B. for architecture, please read ARCH.md and CLAUDE.md