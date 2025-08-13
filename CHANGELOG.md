# Changelog

## 0.1.0 - Phase 1

## 0.2.0 - Phase 2

## 0.2.1 - Debug Logging Enhancements

## 0.3.0 - MVP MMS + RCS Scan
## 0.3.1 - Manual SMS/MMS/RCS Scan

- Add SMS inbox scanner (Telephony.Sms) and wire button to scan SMS+MMS+RCS
- Update button copy and logs


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


