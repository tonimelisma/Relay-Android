# Changelog

## 0.1.0 - Phase 1

## 0.2.0 - Phase 2

- Add `SmsReceiver` manifest-declared `BroadcastReceiver` to capture `SMS_RECEIVED`
- Implement `SmsInMemoryStore` with a `StateFlow` of messages
- Update `MainActivity` UI to display live messages list
- Add unit test for `SmsInMemoryStore`

- Add SMS and Internet permissions in `AndroidManifest.xml`
- Implement Compose UI in `MainActivity` to request `READ_SMS` and `RECEIVE_SMS` and display status
- Add basic instrumented UI test for permission screen
- Update `PRD.md` and `ARCH.md` to reflect Phase 1 deliverable


