# Architecture and business rules

## Boundary diagram

```text
Compose screens -> ViewModels -> application use cases -> repositories -> Room
                                   |                     |
                                   +-> pure Kotlin domain +-> DataStore (preferences only)

voice / local LLM / PC companion -> proposed action -> validation -> confirmation -> use case
```

The Android application runs locally. There is no server for the core app: a second-language backend would add synchronization, credentials, and deployment burden without helping a single-device ledger. A future optional PC companion can be written in Kotlin (for example with Ktor) and expose narrowly scoped capabilities to a trusted home network. It is **not** part of the core accounting path.

## Source of truth

An account has an opening balance and a currency. Immutable income, expense, and transfer records change its computed balance. Money is stored as signed integer minor units and an ISO currency code. Currency mismatches, overflow, invalid references, and malformed transactions are rejected. A transfer debits one account and credits another; it is excluded from income, expenses, and category budgets. There is no implicit foreign-exchange conversion.

Savings reservations earmark existing account funds. They reduce *available* balance, not actual balance, net worth, or cash flow. A goal has a target, deadline, reserved amount, remaining amount, and a monthly contribution estimate. Budgets are limits for an inclusive date period and category; they do not create expenses. Forecasts are derived estimates, never ledger facts.

## Write flow

1. A screen, voice adapter, or model constructs a draft with a stable identifier.
2. A use case validates the draft against current domain invariants and repository state.
3. The user explicitly confirms AI/voice-generated writes.
4. Room commits the write atomically; observers receive a fresh snapshot.
5. UI derives balances, budget progress, and goal progress from the new snapshot.

AI output is untrusted input. It cannot execute SQL, read raw database files, or silently modify money records. A PC/MCP integration, if enabled later, should use explicit tool allowlists, pairing, transport authentication, user confirmation for writes, and redacted responses. MCP is a tool/context protocol, not a direct LLM-to-LLM communication bus; a host or application orchestrates sharing between models.

## Voice and model strategy

The app must remain fully functional without any downloaded model. Speech-to-text and text-to-speech are interfaces with optional local implementations; native Android speech facilities may depend on installed offline language packs, so offline behavior must be tested on the target device. A small quantized local LLM may explain already-computed summaries and draft transactions, but must never calculate authoritative totals. Downloadable model files live outside the APK, require explicit consent, and need storage/RAM/battery limits. The Xiaomi Redmi Note 14 Pro+ target has enough advertised storage and physical RAM to *evaluate* small models, but benchmark on the actual device before selecting one; extended/virtual RAM is not equivalent to physical memory.

## Security and data survival

Private app storage, no telemetry by default, no production logging of notes/amounts/audio/prompts. Require an app lock and encrypted, user-controlled export before claiming real-data readiness. Room schema changes must have tested migrations; destructive fallback is forbidden. Because automatic backup is currently disabled, a verified export/restore path is a release blocker, not a nice-to-have.

## Testing contract

Pure domain rules use strict red-green-refactor unit tests. Room DAO and migration behavior use instrumentation tests. ViewModels and critical screens get state/UI tests. A release requires a clean build, unit tests, emulator/device instrumentation, export/restore round trip, and a manual check on the target phone. Build success alone is not release readiness.
