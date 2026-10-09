# Finance backups

`FinanceBackupService` exports the eight Room v1 finance tables, including the append-only reservation change history. Format v1 uses a `format`, `formatVersion`, `databaseVersion`, and `tables` envelope. All signed 64-bit values are decimal **strings**, avoiding precision loss in JSON consumers. Nulls and IDs are preserved.

## Safety contract

- Backups are **NOT ENCRYPTED**. They contain sensitive financial amounts and notes. Store them only in a private location; do not attach them to bug reports or commit them. The service does not log, upload, or schedule exports.
- Call `previewJson` to validate and display table counts before asking the user for confirmation. Call `restoreJson(json, confirmed = true)` only after explicit confirmation.
- Restore is supported only into an entirely empty database. Any existing record rejects the operation; no table is cleared and no record is overwritten. Merge and destructive replacement are deliberately unsupported.
- Validation rejects unsupported versions, unknown/missing fields, invalid references, duplicate IDs, incompatible currencies, invalid dates, invalid money, and checked-arithmetic overflow. Existing reservations may legitimately be underfunded after spending and are preserved.
- All inserts and the empty-target check use one Room transaction. Any failure rolls back every table. Export reads all eight tables in one transaction.
- The current limit is 10 Mi characters. A file adapter must also bound input while reading, before allocating a complete string.
- The service is not a file-picker UI. Integration must use Android Storage Access Framework, explain the plaintext warning, handle cancellation and I/O errors, and request a user-selected destination/source. Do not expose raw JSON on screen.

## Verification

`./gradlew.bat :app:connectedDebugAndroidTest` runs the in-memory Room round-trip, exact large integer/null preservation, explicit-confirmation/nonempty-target rejection, invalid input rejection, and mid-restore failure rollback tests. These tests require an Android device or emulator. Compilation alone is not evidence that device tests passed.
