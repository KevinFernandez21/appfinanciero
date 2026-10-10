# Finance backups

`FinanceBackupService` exports the nine Room v2 finance tables, including append-only reservation changes and transaction corrections. Format v2 uses a `format`, `formatVersion`, `databaseVersion`, and `tables` envelope. All signed 64-bit values are decimal **strings**, avoiding precision loss in JSON consumers. Nulls and IDs are preserved.

The importer accepts format/database version pairs `1/1` and `2/2`. Legacy v1 backups contain the original eight tables and restore with an empty corrections table; re-export produces v2. V2 includes `transaction_corrections` with `id`, `originalTransactionId`, nullable `replacementTransactionId`, `reason`, and decimal-string `dateEpochDay`. Voided transactions remain in the raw `transactions` table: corrections exclude their original rows only from effective calculations, never from the backup. Unsupported versions or mismatched version pairs are rejected.

## Safety contract

- Backups are **NOT ENCRYPTED**. They contain sensitive financial amounts and notes. Store them only in a private location; do not attach them to bug reports or commit them. The service does not log, upload, or schedule exports.
- Call `previewJson` to validate and display table counts before asking the user for confirmation. Call `restoreJson(json, confirmed = true)` only after explicit confirmation.
- Restore is supported only into an entirely empty database. Any existing record rejects the operation; no table is cleared and no record is overwritten. Merge and destructive replacement are deliberately unsupported.
- Validation rejects unsupported versions, unknown/missing fields, invalid references, duplicate IDs, incompatible currencies, invalid dates, invalid money, and checked-arithmetic overflow. Correction originals must be unique; unknown references, self-replacements, correction chains/cycles, and blank reasons are rejected. Existing reservations may legitimately be underfunded after spending and are preserved; released zero totals and zero-valued audit entries are retained.
- All inserts and the empty-target check use one Room transaction. Any failure rolls back every table, including corrections. Export reads all nine tables in one transaction.
- The current limit is 10 Mi characters. A file adapter must also bound input while reading, before allocating a complete string.
- The service is not a file-picker UI. Integration must use Android Storage Access Framework, explain the plaintext warning, handle cancellation and I/O errors, and request a user-selected destination/source. Do not expose raw JSON on screen.

## Verification

`./gradlew.bat :app:connectedDebugAndroidTest` runs the in-memory Room round-trip, exact large integer/null preservation, v1 import/v2 export compatibility, released reservation and voided transaction history preservation, explicit-confirmation/nonempty-target rejection, invalid input rejection, and mid-restore failure rollback tests. These tests require an Android device or emulator. Compilation alone is not evidence that device tests passed.
