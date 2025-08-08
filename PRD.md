# 📱 Android SMS Sync App – Product Requirements Document (PRD)

## Overview

**Goal:**  
Create a lightweight Android app that listens for incoming SMS messages and uploads them to a cloud backend. The app will **not send or write** SMS messages — only read and transmit.

**Platform:** Android  
**Minimum SDK:** 23 (Android 6.0)  
**Permissions Required:**  
- `RECEIVE_SMS`
- `READ_SMS`
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
App that requests permissions and displays the result.

---

### 🟡 Phase 2 – Log Received SMS in UI

**Objective:** Show SMS received via `BroadcastReceiver`.

**Requirements:**
- Register a `BroadcastReceiver` for `android.provider.Telephony.SMS_RECEIVED`
- Parse sender + body from each received message
- Append message to an in-app list (RecyclerView or simple list)

**Deliverable:**  
SMS appear in UI as they arrive (even in background).

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

### 🔵 Phase 4 – Add Local Persistence (Optional)

**Objective:** Store messages on device for reload after app restart.

**Requirements:**
- Store SMS messages in local DB (Room, SQLite, or flat file)
- Load stored messages on startup
- Prevent duplicates (based on hash of `sender+body+timestamp`)
- Optional: "Clear History" button

**Deliverable:**  
SMS list persists across app restarts.

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


