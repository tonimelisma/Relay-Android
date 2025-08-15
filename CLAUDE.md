# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is an Android SMS sync app called "Relay" that listens for incoming SMS messages and uploads them to a cloud backend. The app is designed for privacy, stability, and minimal battery impact. It **reads SMS messages only** - it does not send or modify SMS messages.

**Key Details:**
- Package: `net.melisma.relay`
- Min SDK: 23 (Android 6.0)
- Target SDK: 36
- Built with Jetpack Compose and modern Android architecture

## Development Commands

### Build and Run
```bash
# Build debug APK
./gradlew assembleDebug

# Install and run on connected device
./gradlew installDebug

# Build release APK
./gradlew assembleRelease
```

### Testing
```bash
# Run unit tests
./gradlew test

# Run instrumented tests (requires device/emulator)
./gradlew connectedAndroidTest

# Run all tests
./gradlew check
```

### Code Quality
```bash
# Lint check
./gradlew lint

# Generate lint report
./gradlew lintDebug
```

## Architecture Overview

The app follows a layered architecture designed for background SMS processing:

### Core Components
1. **SMS Receiving Layer**: Manifest-declared BroadcastReceiver (`SmsReceiver`) listens for `android.provider.Telephony.SMS_RECEIVED`
2. **Local Persistence**: Room database stores SMS/MMS/RCS with SHA-256 hash-based deduplication and mirrors key provider fields (SMS/MMS), including MMS parts and addresses  
3. **Cloud Sync Layer**: WorkManager handles background uploads with network constraints and retry logic
4. **UI Layer**: MVVM with Jetpack Compose, shows permissions state and message list
5. **Permissions Management**: Runtime permission handling for `RECEIVE_SMS` and `READ_SMS`

### Data Flow
1. SMS/MMS events received via BroadcastReceivers
2. Content observers on `content://sms` and `content://mms` trigger ingest on provider changes
3. Lifecycle-aware polling (~10s) while app is foregrounded
4. Repository performs initial full scan per kind, then incremental ingest; persists to Room (deduplicated by content hash)
5. UI observes DB changes via StateFlow; future: WorkManager handles upload

### Key Design Decisions
- **Hash-based deduplication**: Uses `SHA-256(sender + timestamp + body)` as primary key
- **WorkManager for background sync**: Handles device restarts, Doze mode, and network constraints
- **Manifest-declared receiver**: Only reliable way to receive SMS in background across Android versions
- **No SMS sending/writing**: Read-only access for privacy and Play Store compliance

## Current Implementation Status

The project is currently in **Phase 1** of development (per PRD.md):
- ✅ Basic app structure with Compose UI
- ✅ SMS permissions request flow
- ✅ Permission status display
- ❌ SMS receiving logic not yet implemented
- ❌ Local persistence (Room) not yet implemented  
- ❌ Cloud sync (WorkManager) not yet implemented

## Key Files and Locations

### Main Application
- `app/src/main/java/net/melisma/relay/MainActivity.kt`: Main activity with permissions UI
- `app/src/main/AndroidManifest.xml`: App permissions and component declarations
- `app/build.gradle.kts`: App-level Gradle configuration

### Dependencies
- `gradle/libs.versions.toml`: Centralized dependency management
- Uses Jetpack Compose, AndroidX libraries, JUnit/Espresso for testing

### Documentation
- `PRD.md`: Product requirements with 7-phase development plan
- `ARCH.md`: Detailed technical architecture document
- `docs/SMS.md`: Comprehensive SMS implementation guide

## Development Guidelines

### Testing Strategy
- Unit tests for business logic (hash generation, ViewModel state, etc.)
- Instrumented tests for SMS receiving, permissions, and Room database
- Use WorkManager test APIs for background sync testing
- Follow test coverage priorities: deduplication logic, upload retry, DB queries

### Permissions Considerations
- Always request `READ_SMS` and `RECEIVE_SMS` together (Android 8.0+ requirement)
- Handle permission denial gracefully with user-friendly messaging
- Be aware of Play Store SMS permission policies for distribution

### Background Work Best Practices
- Use WorkManager with network constraints (`NetworkType.CONNECTED`)
- Implement exponential backoff for failed uploads
- Batch operations where possible to reduce battery impact
- Test on recent Android versions (13+) for behavior changes

## Common Tasks

### Adding New Dependencies
Edit `gradle/libs.versions.toml` to add version, then reference in `app/build.gradle.kts`

### Implementing Next Phase Features
Follow the phase-based approach in PRD.md:
1. Phase 2: SMS receiving with BroadcastReceiver
2. Phase 3: Reading existing SMS from content provider
3. Phase 4: Room database for local persistence
4. Phase 5: WorkManager for cloud sync
5. Phase 6: Bulk upload and sync state management
6. Phase 7: Settings UI and polish

### Testing SMS Functionality
- Use Android emulator's extended controls to send test SMS
- Test with device in Doze mode for background behavior
- Verify permissions work across different Android versions

## Security Notes

- Use HTTPS for cloud API communications
- Never log sensitive SMS content in production
- Handle API keys/tokens securely (not hardcoded)
- Follow Android security best practices for data storage