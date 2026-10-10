# Finance backups

`FinanceBackupService` exports the nine Room v2 finance tables, including append-only reservation changes and transaction corrections. Format v2 uses a `format`, `formatVersion`, `databaseVersion`, and `tables` envelope. All signed 64-bit values are decimal **strings**, avoiding precision loss in JSON consumers. Nulls and IDs are preserved.

The importer accepts format/database version pairs `1/1` and `2/2`. Legacy v1 backups contain the original eight tables and restore with an empty corrections table; re-export produces v2. V2 includes `transaction_corrections` with `id`, `originalTransactionId`, nullable `replacementTransactionId`, `reason`, and decimal-string `dateEpochDay`. Voided transactions remain in the raw `transactions` table: corrections exclude their original rows only from effective calculations, never from the backup. Unsupported versions or mismatched version pairs are rejected.

## Password-encrypted portable files

`EncryptedBackupCodec.encrypt(json: String, password: CharArray): ByteArray` wraps the existing JSON in a portable binary envelope. `decrypt(envelope: ByteArray, password: CharArray): String` authenticates and unwraps it; JSON validation/preview/confirmed restoration remain the responsibility of `FinanceBackupService`. `isEncrypted(bytes)` checks the magic prefix only, not authenticity.

Encryption uses AES-256-GCM with a 128-bit authentication tag, a fresh random 16-byte salt, and a fresh random 12-byte nonce for every export. PBKDF2-HMAC-SHA256 derives the key with 600,000 iterations. These choices follow [Android cryptography guidance](https://developer.android.com/privacy-and-security/cryptography) and [OWASP's PBKDF2 work factor](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html#pbkdf2). PBKDF2-SHA256 is [available on Android API 26+](https://developer.android.com/reference/javax/crypto/SecretKeyFactory); no device-bound Keystore key or external crypto dependency is required.

The version-1 encrypted envelope is separate from the version-2 JSON format. Its 43-byte authenticated header, in order, contains:

| Field | Encoding |
|---|---|
| Magic | Four ASCII bytes `AFBK` |
| Envelope version, KDF ID, cipher ID | One byte each, all `1` |
| Iterations | Big-endian 32-bit integer, exactly `600000` |
| Salt, nonce | 16 bytes, then 12 bytes |
| Plaintext UTF-8 length | Big-endian 32-bit integer |

The body is ciphertext followed by the 16-byte GCM tag. The complete header is authenticated as AAD. Unknown algorithms/versions, altered iteration counts, impossible lengths, truncation, trailing data, wrong passwords, and tampering are rejected. Decryption never returns a plaintext prefix before tag verification and reports only `Invalid encrypted backup or password`, without a provider cause or sensitive details.

Exports require 12–1024 password characters; imports accept 1–1024 for future compatibility. Choose a long, unique passphrase and keep it separately: there is **no password recovery**. The codec does not store/log passwords, does not modify the caller's password array, and clears its temporary key/plaintext buffers on a best-effort basis. The caller must clear its `CharArray` in `finally`; immutable JSON strings and internal platform-provider copies cannot be reliably erased.

Run encryption/decryption off the UI thread. Bounds are 10 Mi JSON characters, 40 MiB UTF-8 plaintext, and 40 MiB + 59 bytes for the entire envelope. File adapters must cap bytes while reading, before allocating a full input. A matching magic prefix must route to decryption rather than silently fall back to plaintext if authentication fails.

This protects exported files, not the live Room database or an unlocked/compromised device. A copied encrypted file permits offline password guessing; the work factor slows guessing but cannot compensate for a weak passphrase. File length and the cryptographic format header are public.

## Safety contract

- `FinanceBackupService` JSON and legacy JSON files are **NOT ENCRYPTED**; wrap exports with `EncryptedBackupCodec` before writing user files. Legacy JSON contains sensitive financial amounts and notes. Store it only in a private location; do not attach it to bug reports or commit it. Neither service logs, uploads, or schedules exports.
- Call `previewJson` to validate and display table counts before asking the user for confirmation. Call `restoreJson(json, confirmed = true)` only after explicit confirmation.
- Restore is supported only into an entirely empty database. Any existing record rejects the operation; no table is cleared and no record is overwritten. Merge and destructive replacement are deliberately unsupported.
- Validation rejects unsupported versions, unknown/missing fields, invalid references, duplicate IDs, incompatible currencies, invalid dates, invalid money, and checked-arithmetic overflow. Correction originals must be unique; unknown references, self-replacements, correction chains/cycles, and blank reasons are rejected. Existing reservations may legitimately be underfunded after spending and are preserved; released zero totals and zero-valued audit entries are retained.
- All inserts and the empty-target check use one Room transaction. Any failure rolls back every table, including corrections. Export reads all nine tables in one transaction.
- The current limit is 10 Mi characters. A file adapter must also bound input while reading, before allocating a complete string.
- The app uses Android Storage Access Framework for user-selected destinations and sources. Exports are encrypted `.afbk` files; imports accept encrypted files and legacy plaintext JSON with a warning. The codecs alone do not provide this UI. Do not expose raw JSON on screen.

## Verification

`./gradlew.bat :app:connectedDebugAndroidTest` runs the in-memory Room round-trip, exact large integer/null preservation, v1 import/v2 export compatibility, released reservation and voided transaction history preservation, explicit-confirmation/nonempty-target rejection, invalid input rejection, and mid-restore failure rollback tests. These tests require an Android device or emulator. Compilation alone is not evidence that device tests passed.

`./gradlew.bat :app:testDebugUnitTest` also verifies encrypted Unicode round-trip, an independently generated Python PBKDF2/AES-GCM reference envelope with a Unicode password, wrong-password rejection, authenticated header/ciphertext/tag tampering, fresh randomness, truncation/trailing-data rejection, strict UTF-8 handling, and input/password bounds. `EncryptedBackupCodecAndroidTest` verifies the device's crypto providers and the same portable reference. A test passing on a newer Android emulator does not prove API-26 execution.
