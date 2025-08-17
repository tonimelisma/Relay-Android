# Changelog

## 0.1.0 - Phase 1

## 0.2.0 - Phase 2

## 0.2.1 - Debug Logging Enhancements

## 0.3.0 - MVP MMS + RCS Scan
## 0.3.1 - Manual SMS/MMS/RCS Scan

- Add SMS inbox scanner (Telephony.Sms) and wire button to scan SMS+MMS+RCS
- Update button copy and logs

## 0.4.0 - Local persistence (Room) + MMS images

- Add Room DB (`messages`, `mms_parts`, `mms_addr`)
- Store MMS image bytes in DB; render small previews in UI
- Unified, timestamp-sorted list across SMS/MMS/RCS
- Migrated to KSP for annotation processing

## 0.4.1 - MMS address ingestion + metadata mapping

- Persist all MMS address rows (`content://mms/<id>/addr`) into `mms_addr`
- Map additional MMS/SMS fields into `messages`: `threadId`, `read`, `dateSent`, `subject`
- Improve MMS part ingestion to include text parts metadata (and keep image bytes)
- Build and JVM unit tests passing


- Add `RECEIVE_MMS` and `RECEIVE_WAP_PUSH` permissions
- Add `MmsReceiver` to surface MMS notifications via WAP push
- Add `MessageScanner` with manual scan for MMS and heuristic RCS
- Update `MainActivity` to collect `StateFlow` and add "Scan MMS/RCS" button
- Build/tests passing


- Introduce `AppLogger` utility with safe fallbacks for unit tests
- Add detailed Logcat statements in `MainActivity`, `SmsReceiver`, and `SmsInMemoryStore`
- Build and unit tests passing

- Add `SmsReceiver` manifest-declared `BroadcastReceiver` to capture `SMS_RECEIVED`
- Implement `SmsInMemoryStore` with a `StateFlow` of messages
- Update `MainActivity` UI to display live messages list
- Add unit test for `SmsInMemoryStore`

- Add SMS and Internet permissions in `AndroidManifest.xml`
- Implement Compose UI in `MainActivity` to request `READ_SMS` and `RECEIVE_SMS` and display status
- Add basic instrumented UI test for permission screen
- Update `PRD.md` and `ARCH.md` to reflect Phase 1 deliverable


## 0.5.0 - Receivers → Room, ViewModel centralization, MMS ingest optimization

- Removed in-memory store; receivers no longer write to a transient flow
- `SmsReceiver`/`MmsReceiver` now trigger repository ingest and persist directly to Room (off main thread with `goAsync()` + IO coroutine)
- UI centralized on `MainViewModel` in `MainActivity`; auto/manual ingest delegated to ViewModel; UI observes `messages` StateFlow
- Repository now exposes `observeMessagesWithParts()`; MMS detailed scan performed once per ingest pass and reused
- Tests updated: removed in-memory store unit test; aligned UI instrumentation tests; added DB/repository instrumented test
- Docs updated to reflect architecture changes


## 0.6.0 - Full provider mirroring + initial full sync + observers + polling

- Expand Room schema to mirror provider fields for SMS/MMS (message box, provider id, status, service center, protocol, seen, locked, error code, subject, content type)
- Persist extended MMS part metadata (`dataPath`, `cd`, `fn`) and keep bytes for image parts
- Store raw provider snapshots in `smsJson`/`mmsJson`
- Perform initial full scan per kind on first run; incremental ingest afterward
- Register content observers on `content://sms` and `content://mms`; trigger ingest on changes
- Lifecycle-aware periodic polling (every ~10s) while app is foregrounded
- Added logging across ingest start/counts/inserted totals

## 0.7.0 - Ingestion trigger overhaul, background sync, RCS fixes

- Removed manual "Scan" button and removed 10s foreground polling
- Kept broadcast receivers and content observers (SMS/MMS + best-effort RCS)
- Added `MessageSyncWorker` with WorkManager periodic (~15 min) ingest; scheduled on app start and after boot via `BootReceiver`
- Introduced ingestion concurrency guard to avoid overlapping runs
- Switched incremental ingest to providerId per kind; still performs full scan per kind on first run
- Fixed timestamp inconsistencies for MMS/RCS (normalize to ms; use provider timestamps for Samsung RCS)
- Avoid duplicate MMS vs RCS by excluding MMS rows that match RCS provider ids
- Optimized MMS detail ingest: fetch parts/addresses on-demand per inserted MMS (reduced UI latency)
- Kept `synced=0` on insert; destructive migrations allowed pre-release

## 0.7.1 - File logging, AppExitInfo dedup, Last Sync, and full SMS/MMS syncing

- Implemented rotating file logger writing to `files/logs/` while preserving Logcat output (`AppLogger`)
- Added `RelayApp` to initialize logging and log only the most recent `ApplicationExitInfo` once per new exit; stored in `SharedPreferences` as `lastExitLoggedTs`
- `MessageSyncWorker` now stores `lastSyncSuccessTs` in `SharedPreferences` on success
- UI (`MainActivity`) displays "Last synced:" from `lastSyncSuccessTs`
- Removed redundant periodic worker scheduling in `MainActivity.onCreate` to avoid WorkManager churn; rely on `BootReceiver` and an onStart() health check
- Switched to watermark-based, chunked syncing of all SMS and MMS folders using provider `_id` with ascending queries; eliminates 50/25 caps and avoids missed bursts

## 0.7.2 - Persisted IM provider gate (Samsung RCS) and tests

- Added `ImProviderGate` to detect Samsung IM provider availability once per install and persist the result in `SharedPreferences`
- `RelayApp` primes the gate on startup; `MainActivity` registers `content://im/chat` observer only if enabled; `MessageScanner` queries Samsung provider only when gate allows
- On observer registration or query failures, the gate is permanently marked unavailable to prevent future attempts on that install
- Unit tests: broadened suite; receivers tests adjusted to avoid pending-result NPE logs; build/tests passing

## 0.7.3 - Centralized coroutine scope + MessageScanner refactor

- Added application-level `CoroutineScope` in `RelayApp` using `SupervisorJob()+Dispatchers.IO` for background work
- `SmsReceiver` and `MmsReceiver` now use `applicationScope` with `goAsync()` to run DB ingest, avoiding per-receive scope creation
- `MessageScanner` gained a generic `queryProvider` helper to reduce boilerplate and refactored `scanSms` to use it
- Build and tests pass; minor Robolectric stderr from async receivers remains non-fatal