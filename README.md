# AppAhorro (`appfinanciero`)

Private, offline-first Android app for recording money, understanding spending, and planning savings. Built with Kotlin and Jetpack Compose. The financial ledger—not an AI model—is the source of truth.

## Status

Under active development. Export/restore and v1 persistence tests exist, but backups are plaintext and physical-phone/model inference tests are still pending. Do **not** use this build as the only record of real finances.

## What the app is for

- Record accounts, income, expenses, and internal transfers.
- See balances and cash flow without counting transfers as earnings or spending.
- Set category budgets and savings goals; reserve existing funds without creating fake transactions.
- Learn from clear explanations of calculations and optional, explicitly confirmed AI drafts.

## Technology

Single-device Android app: Compose UI, Kotlin domain rules, Room database, DataStore preferences. No remote backend is required for a single-user offline ledger. Optional on-device speech/LLM and an authenticated PC companion are separate adapters; neither owns financial state.

See [architecture](docs/ARCHITECTURE.md), [delivery plan](docs/ROADMAP.md), [backup safety](docs/BACKUP.md), [optional local AI](docs/AI.md), and the [PC/MCP boundary](docs/PC_COMPANION.md).

## Build

Use Android Studio with JDK 21 and Android SDK 36. From the repository root on Windows:

```powershell
./gradlew.bat :app:assembleDebug :app:testDebugUnitTest
```

Instrumentation tests require an Android emulator or device:

```powershell
./gradlew.bat :app:connectedDebugAndroidTest
```

The repository includes `AGENT.md` and project-local Android/Kotlin skills for contributors and coding agents.
