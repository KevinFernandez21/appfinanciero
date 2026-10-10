# Prepare a transaction draft by voice

On-device speech recognition can prepare an **editable** income or expense draft. It never saves a movement. You must review the fields and tap **Save** yourself.

## Quick path

1. Open **Activity → Record movement** and select the account whose currency applies.
2. Tap **Dictate movement draft (on-device)** and allow microphone access if asked. Say a numeric command such as `gasto 12,50 en café` or `ingreso 12,50 por venta`.
3. Check the recognized words and interpreted amount. Tap **Apply voice draft**, review all transaction fields, then tap **Save** only if they are correct. **Discard transcript** changes nothing.

## Safety boundaries

| Area | Behavior |
|------|----------|
| Availability | The button appears only when Android reports an on-device recognizer. No network-recognizer fallback is used. An offline Spanish language pack may be required. |
| Grammar | Only `gasto` or `ingreso`, a numeric amount with at most two decimal digits, and an optional `en` / `por` note are interpreted. Spoken number words, transfers, grouping separators, and ambiguous commands are rejected. |
| Exact money | The amount must be positive and representable in the selected account's ISO currency. Unsupported precision is rejected rather than rounded. |
| Field changes | Applying a preview changes type, amount, and a nonblank note only. Account, category, and date remain as selected. Recognition alone changes no fields. |
| Persistence | Only the ordinary **Save** action can write, using the existing finance validation. Transcripts are kept in screen memory, not the database or saved-state bundle. |

You can always type the movement instead. This limited grammar is intentional: uncertain recognition must not silently invent a financial fact. The emulator tests exercise synthetic transcripts and the preview UI; microphone accuracy and offline Spanish recognition still need testing on the physical phone.

## Verification

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:connectedDebugAndroidTest
```

See [the delivery plan](ROADMAP.md) for the remaining device-validation work.
Android's [`SpeechRecognizer` reference](https://developer.android.com/reference/android/speech/SpeechRecognizer) documents the on-device service check and factory used here.
