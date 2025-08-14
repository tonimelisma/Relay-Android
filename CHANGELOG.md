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

