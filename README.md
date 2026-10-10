# AppAhorro (`appfinanciero`)

Private, offline-first Android app for recording money, understanding spending, and planning savings. Built with Kotlin and Jetpack Compose. The financial ledger—not an AI model—is the source of truth.

## Status

Under active development. User-selected exports are password-encrypted by default; legacy plaintext JSON can still be imported with a warning. A device-authenticated app lock is on by default, but it is a UI gate, not database encryption. Export/restore and v1→v2 migration tests exist; physical-phone authentication and local-model inference tests are still pending. Do **not** use this build as the only record of real finances.

## What the app is for

- Record accounts, income, expenses, and internal transfers.
- See balances and cash flow without counting transfers as earnings or spending.
- Set category budgets and savings goals; reserve existing funds without creating fake transactions.
- Understand recorded income and spending with an optional, non-AI weekly review.
- Learn from clear explanations of calculations and optional, explicitly confirmed AI drafts.

## Technology

Single-device Android app: Compose UI, Kotlin domain rules, Room database, DataStore preferences. No remote backend is required for a single-user offline ledger. Optional on-device speech/LLM and an authenticated PC companion are separate adapters; neither owns financial state.

See [architecture](docs/ARCHITECTURE.md), [delivery plan](docs/ROADMAP.md), [first-run usage](docs/USAGE.md), [weekly review](docs/REVIEW.md), [backup safety](docs/BACKUP.md), [app lock](docs/LOCK.md), [optional local AI](docs/AI.md), and the [PC/MCP boundary](docs/PC_COMPANION.md).

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
