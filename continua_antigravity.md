# Continue AppAhorro from the verified baseline

**Status on 2026-10-24:** Approximately **70% of the four-phase roadmap** is complete. This is a judgment-based progress estimate, not measured code coverage or a release guarantee. The offline ledger is substantially implemented, but **the app is not ready to be the only record of real finances** until device-level recovery and security checks pass.

## Start here

1. Read `AGENT.md`, `README.md`, and `docs/ROADMAP.md`. Preserve the ledger as the source of truth; AI/voice may propose drafts but may not write without validation and explicit confirmation.
2. Keep all financial rules in exact integer minor units with an explicit currency. Use strict red-green-refactor TDD for changed rules. Never use a destructive Room migration.
3. Execute the tasks below **in order**, checking each box only after its acceptance check passes. Record the command, device/API level, result, and any failure. Do not mark an unrun device test as passed.

## Verified baseline

| Area | Evidence | Status |
|---|---|---|
| Core ledger, budgets, goals, corrections, weekly review | Kotlin domain, Room, Compose flows, and tests exist | Implemented; device suite not run in this audit |
| Backup and lock | Encrypted export/import, Room v1→v2 migration, app-lock gate, platform backup exclusions exist | Implemented; physical recovery/authentication unverified |
| Voice and local AI | Voice draft preview and optional LiteRT-LM adapter exist | Synthetic tests exist; microphone and real-model inference unverified |
| PC/MCP companion | Only `docs/PC_COMPANION.md` contract exists | Not implemented; optional, not a core-app release blocker |

The 2026-10-24 local run of `:app:assembleDebug :app:testDebugUnitTest :app:lintDebug --offline` **passed**. All **77 unit tests** passed. Lint reported **0 errors, 3 warnings, 1 hint**. There are **20 instrumented test classes**, but `adb devices` showed no connected device, so none ran in this audit. One AVD named `Pixel_7_Pro` is available but was not started. The working tree was clean before this document was added.

The estimate is based on the roadmap's four phases, weighted toward financial safety: foundation ~95%, everyday workflow ~85%, data safety ~55%, optional intelligence ~25%. Weighted together this is about 70%. These are planning estimates, **not** a claim that 70% of tests or release criteria passed. Remaining work may take disproportionately long because device validation and recovery failures are high-risk.

## 1. Establish a reproducible device-test baseline (highest priority)

- [x] **1.1 Configure the local environment without committing machine-specific paths.** Set `JAVA_HOME` to a working JDK 21 and `ANDROID_HOME` to an SDK with API 36. On this machine, the working paths were `C:\Program Files\Microsoft\jdk-21.0.11.10-hotspot` and `C:\Users\kdfernan\AppData\Local\Android\Sdk`. Do not commit `local.properties`.
- [x] **1.2 Re-run the local gate.** From the repository root run `./gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug --offline`. Acceptance: `BUILD SUCCESSFUL`, no failed unit tests, lint has no errors. Record the current warning count separately rather than calling lint warning-free. (Passed on 2026-10-24: 77 unit tests passed; lint reported 0 errors, 3 warnings [GradleDependency, MissingApplicationIcon, UseKtx], 1 hint [AutoboxingStateCreation]).
- [x] **1.3 Start the existing `Pixel_7_Pro` AVD or connect a test device.** Run `<Android SDK>/platform-tools/adb devices` and `adb shell getprop ro.build.version.sdk`. Acceptance: exactly the intended device is listed as `device`, and its API level is recorded. If the emulator cannot boot, document the blocker; do not substitute compilation for device execution. (Passed on 2026-10-24: `emulator-5554` connected as `device`, `ro.build.version.sdk=37`, model `sdk_gphone16k_x86_64`).
- [x] **1.4 Run the entire instrumented suite.** Run `./gradlew.bat :app:connectedDebugAndroidTest --offline --console=plain`. Acceptance: Gradle succeeds and the XML reports under `app/build/outputs/androidTest-results/connected/` show zero failures. Record number run and skipped; fix failures one focused test at a time before moving on. (Passed on 2026-10-24 on `Pixel_7_Pro` API 37: 84 tests executed across all 20 test classes, 0 failures, 0 errors, 0 skipped. Duration: 3m 49s).
- [ ] **1.5 Exercise API 26 compatibility.** Create/use an API 26 emulator if available, then rerun the instrumented suite or at minimum the backup-crypto, lock, migration, and first-run tests. Acceptance: record the API 26 results. A newer emulator does not prove `minSdk = 26` compatibility.

## 2. Prove financial data survives a real-device recovery

- [ ] **2.1 Prepare disposable test data on the target phone (Redmi Note 14 Pro+).** Add two accounts, income, expense, transfer, category, budget, savings goal/reservation, weekly review, and one voided transaction. Use fake amounts and notes; photograph or manually note expected record counts and balances without sharing private data.
- [ ] **2.2 Export an encrypted `.afbk` through the app's Settings flow.** Choose a strong test password, save to a user-selected location, close and relaunch the app. Acceptance: the file is nonempty, opens only with the correct password, and plaintext notes are not visible by inspecting the file. Never commit the export.
- [ ] **2.3 Restore on a clean app install or separate clean test device.** Import the file, inspect the preview counts, explicitly confirm, then restart the app. Acceptance: accounts, balances, budgets, reservations, reviews, and correction history match 2.1. Check transfers are not counted as income/expense and reservations do not change account balances.
- [ ] **2.4 Test safe refusal paths.** Try a wrong password, a tampered copy, and import into a nonempty database. Acceptance: each is rejected without partial writes or deleting existing history. Keep the original export untouched.
- [ ] **2.5 Validate a real v1→v2 upgrade.** Install a v1 build with disposable records, upgrade to the current build, and check balances/history and the new corrections table. Acceptance: no records disappear. If no reproducible v1 APK is available, record this as an unresolved release blocker rather than checking the box.
- [ ] **2.6 Update `docs/BACKUP.md` and `README.md` only with observed results.** Record device model, Android/API version, test date, exact recovery steps, and remaining caveats. Never claim physical-device validation based only on Room or emulator tests.

## 3. Validate the security and external-activity boundaries

- [ ] **3.1 Check app lock on the physical phone.** With device PIN/biometric configured, test cold launch, successful unlock, cancel, failure, background/reopen, rotation, and biometric lockout/credential fallback. Acceptance: financial UI is never visible before successful authentication; failures remain locked.
- [ ] **3.2 Check document-picker return while locked.** Start export/import or model selection, background the app, return from the picker, then authenticate. Acceptance: no financial content is shown while locked and the selected URI is handled only after unlock. Repeat after rotation; record process-death behavior as a separate limitation if selection must be retried.
- [ ] **3.3 Check Android backup/device-transfer policy on an actual phone pair if available.** Confirm the live Room database is not copied automatically. Acceptance: only user-selected encrypted export is relied on for recovery. If a phone pair is unavailable, leave this unchecked and retain the README warning.
- [ ] **3.4 Review privacy on a release-like build.** Inspect Logcat during backup, voice, lock, and AI use. Acceptance: no amounts, notes, passwords, audio/transcripts, model prompts, or model replies are logged by the app. Fix any leak with a regression test.

## 4. Validate optional voice and model features without making them authoritative

- [ ] **4.1 Test offline Spanish speech on the target phone.** Disable network, confirm an on-device recognizer/language pack is available, dictate valid income/expense commands and ambiguous/unsupported phrases. Acceptance: valid speech only prepares an editable preview; ambiguous speech is rejected; nothing persists until ordinary **Save** is tapped. If the recognizer is absent, typed entry must remain fully usable.
- [ ] **4.2 Test microphone denial and lifecycle interruption.** Deny permission, cancel recognition, rotate/background, and retry. Acceptance: no crash, no persisted movement, no sensitive transcript retained beyond the active screen. Add targeted UI tests for any defect.
- [ ] **4.3 Benchmark one licensed, CPU-compatible `.litertlm` model on the physical phone only if optional local AI remains in scope.** Measure model load time, peak memory, response latency, battery/heat, Spanish quality, cancellation, and no-model fallback. Acceptance: write measured results in `docs/AI.md`; do not infer viability from hardware specifications or synthetic tests.
- [ ] **4.4 Verify AI isolation.** Use a misleading prompt requesting an invented balance or direct transaction write. Acceptance: displayed totals still come from the ledger, model output is labeled as explanation, and no model text is persisted as a financial fact. Add a regression test if a boundary fails.

## 5. Polish and release decision

- [ ] **5.1 Fix the visible lint warnings deliberately.** Lint currently flags a missing application icon, `androidx.fragment:fragment:1.3.0` as older, and a SharedPreferences KTX suggestion; it also hints about `mutableIntStateOf`. Address the icon before release. For dependency changes, verify compatibility and rerun build, unit, device tests, and lint; do not upgrade just to silence a warning.
- [ ] **5.2 Walk through the entire first-run flow with a non-developer.** Observe account/currency setup, recording, voiding, budgeting, goal reservation/release, weekly review, export, and restore. Record confusing steps and accessibility issues (screen reader, larger text, empty/error/loading states); fix them in small, tested work units.
- [ ] **5.3 Reconcile documentation with reality.** Update `README.md`, `docs/USAGE.md`, and `docs/ROADMAP.md` to distinguish implemented features from device-verified ones. Keep the warning against sole reliance on this build until section 2 and critical parts of section 3 pass.
- [ ] **5.4 Make an explicit release call.** Gate any “ready for real records” statement on successful clean build, unit tests, emulator/API 26 tests, real-phone export/restore, migration, and lock/privacy checks. Record known limitations and the tested device/OS versions.
- [ ] **5.5 Treat the PC/MCP companion as a separate decision.** Do not start a LAN server merely to raise the percentage. First document a concrete cross-device user workflow, threat model, pairing/authentication, consent, and rollback plan. If not justified, leave `docs/PC_COMPANION.md` as future scope and ship the offline app without it.

## Rules for each Antigravity work unit

1. Take **one checkbox** or one failing acceptance case at a time. Inspect the relevant source/test and `AGENT.md` before editing.
2. For financial rule changes, write a failing unit test first, implement the minimum fix, refactor, then run the focused test and full unit suite. For Room/UI/platform changes, add focused instrumentation or UI coverage and run it on a device.
3. Keep code, UI strings, and docs in the project's existing English unless the user explicitly requests another artifact language. Keep exact money/currency, correction history, reservations, and backup invariants intact.
4. Report **what changed, the exact commands actually run, pass/fail counts, device/API level, and remaining blockers**. Never mark a task complete from code inspection alone when its acceptance requires a device.
