# Relay (Android SMS/MMS/RCS Sync)

Read‑only Android app that listens for incoming SMS/MMS (and heuristic RCS), displays them in a unified list, and can later upload to a backend. The app never sends or modifies messages.

## Quick Start

```bash
# Build debug APK
./gradlew assembleDebug

# Install on a connected device/emulator
./gradlew installDebug

# Run unit tests / instrumented tests
./gradlew testDebugUnitTest
./gradlew connectedDebugAndroidTest   # requires device/emulator

# Lint
./gradlew lintDebug
```

## Permissions

The app requests only what it needs:
- RECEIVE_SMS, READ_SMS
- RECEIVE_MMS, RECEIVE_WAP_PUSH
- INTERNET

Grant runtime permissions on first launch. Background receipt works via manifest receivers on supported versions.

## Project Layout

- `app/src/main/java/net/melisma/relay/` – Kotlin sources (receivers, Room, ViewModels, Compose UI)
- `app/src/main/res/` – Android resources
- `app/src/test/` – Unit tests (JVM)
- `app/src/androidTest/` – Instrumented tests (device/emulator)

## Features (current focus)

- SMS receiver and MMS WAP push handling
- Manual scan of provider data for SMS/MMS (+ heuristic RCS)
- Room persistence with message/part tables and content‑hash dedupe
- Incremental ingest with content observers and foreground polling
- Background upload worker (planned; see roadmap)

## Documentation

- Contributor guide: [AGENTS.md](AGENTS.md)
- Claude Code guidance: [CLAUDE.md](CLAUDE.md)
- Architecture: [ARCH.md](ARCH.md)
- Product requirements: [PRD.md](PRD.md)
- Changelog: [CHANGELOG.md](CHANGELOG.md)

See CHANGELOG for implementation status details.

