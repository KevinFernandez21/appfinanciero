# Understand and save a weekly review

The dashboard explains the last seven days of recorded income and expenses without an AI model. Each currency has its own review; currencies are never converted or added together.

## Quick path

1. Open **Dashboard** and scroll to **Weekly review · no AI**.
2. Check the explicit inclusive dates and totals. Select **Review last 7 days** to add an optional reflection.
3. Choose **Save review** to record the totals and note on this device. **Cancel** discards the draft without writing anything.

## What the numbers mean

| Item | Calculation |
|------|-------------|
| Period | Today minus six days through today, inclusive; not a calendar week. |
| Recorded income / expenses | Active ledger movements in that period and currency. Corrected originals are excluded. |
| Net | Recorded income minus recorded expenses; not an account balance or spending recommendation. |
| Exclusions | Opening balances, internal transfers, and virtual goal reservations are not income or expenses. |
| Saved history | **Recorded at review time** shows totals captured when saving. Later entries or corrections do not recompute earlier reviews. |

Missing movements are not estimated. The month-to-date and full-calendar-month dashboard cards have their own explicit periods and are not weekly totals.

## Privacy and recovery

Draft dates and notes survive screen state restoration, but restoration never saves automatically. Saved reviews are included in [backup and restore](BACKUP.md). Reflection notes are financial data: exported backups include them. No model or backend receives ledger facts through this feature.

## Verification

Run from the repository root with JDK 21 and Android SDK 36:

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:connectedDebugAndroidTest --console=plain
```

Device tests require a running emulator or device. Projection tests cover seven-day boundaries across a year, currency separation, transfers, opening balances, corrections, and virtual reservations. Device tests cover explicit save, cancel, restored drafts, persistence, and historical totals after corrections and reopening.
