# Repository Guidelines

## Project Structure & Module Organization
- `app/`: Android app module (Kotlin). Sources in `app/src/main/java/net/melisma/relay/`; resources in `app/src/main/res/`.
- Tests: unit in `app/src/test/`, instrumented in `app/src/androidTest/`.
- Docs: `PRD.md`, `ARCH.md`, `CLAUDE.md`, `CHANGELOG.md` describe scope and architecture.
- Typical packages: `receiver/` (SMS/MMS), `data/` (Room entities/DAO/repository), `ui/` (Compose), `work/` (WorkManager).

## Build, Test, and Development Commands
- `./gradlew assembleDebug`: Build debug APK.
- `./gradlew installDebug`: Install on connected device/emulator.
- `./gradlew assembleRelease`: Build release APK (signing required).
- `./gradlew testDebugUnitTest`: Run JVM unit tests.
- `./gradlew connectedDebugAndroidTest`: Run instrumented tests (device/emulator).
- `./gradlew lint` / `./gradlew lintDebug`: Android Lint; reports in `app/build/reports/lint/`.

## Coding Style & Naming Conventions
- Language: Kotlin; 4‑space indent, no tabs; idiomatic Kotlin and Compose patterns.
- Names: classes/objects PascalCase; functions/properties camelCase; constants UPPER_SNAKE_CASE; packages lower.case.
- Files: one top‑level class/object per file; match file name to primary type.
- Compose: prefer stateless composables; state via ViewModel; preview names end with `Preview`.
- Lint: fix Android Lint warnings; use IDE Kotlin formatter before submitting.

## Testing Guidelines
- Frameworks: JUnit for unit tests; AndroidX Test and Compose testing for instrumented UI.
- Locations: unit tests in `app/src/test`, instrumented in `app/src/androidTest`.
- Naming: `FeatureNameTest` (unit), `FeatureNameInstrumentedTest` (device).
- Scope: cover receivers (intent parsing), repository/Room (ingest/dedup), and ViewModel flows.
- Run: `testDebugUnitTest` locally; use `connectedDebugAndroidTest` on a device for provider/permission flows.

## Commit & Pull Request Guidelines
- Commits: imperative mood, concise scope prefix when helpful (e.g., `Docs:`, `UI:`, `DB:`, `Build:`). Keep changes focused.
- PRs: include summary, rationale, screenshots/logs for UI or receivers, test plan, and linked issues. Ensure `assembleDebug`, lint, and tests pass.

## Security & Configuration Tips
- Read‑only by design: do not add SMS sending or unnecessary permissions/contacts access.
- Never commit secrets. Backend config (Phase 5+) should use `BuildConfig`/gradle properties.
- Follow `ARCH.md` data flow: Receivers → Repository/Room → ViewModel → Compose; WorkManager for background sync.

