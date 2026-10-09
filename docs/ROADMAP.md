# Delivery plan

This is the implementation order and acceptance contract, not a promise that unfinished features already exist.

## 1. Financial foundation

- Kotlin/Compose build, test infrastructure, exact money type, account/transaction ledger.
- Room schema/repository with observable snapshots and transactional validation.
- Tests for income, expense, transfer, currencies, overflow, invalid references, and persistence round trips.

**Done when:** account balances and cash flow survive app restart, and unit plus instrumentation tests pass.

## 2. Everyday workflow

- First-run currency/account setup; dashboard; transaction add/edit/delete with confirmation and audit-safe semantics; category management.
- Budget creation and progress; savings goals and virtual reservations; clear calculation explanations.
- Accessible Compose screens, empty/error/loading states, localized currency/date formatting, Spanish UI copy if explicitly chosen for the product.

**Done when:** one person can use the ledger, budgets, and goals without developer tools or AI.

## 3. Data safety

- Export/import with versioned schema, validation, duplicate handling, and restore preview.
- App lock, privacy settings, migration tests, recovery documentation.
- Real-device test on the Redmi Note 14 Pro+.

**Done when:** a user can recover all records on a clean install and no migration destroys history.

## 4. Optional intelligence

- Explainable insights based only on computed financial facts.
- Local STT/TTS adapters and hands-free draft entry, with explicit confirmation before writing.
- Benchmark one small quantized local LLM on the device; feature-gate model download and inference.
- Optional paired Kotlin PC companion with scoped MCP tools, authenticated transport, and no direct database exposure.

**Done when:** disabling every AI feature leaves all financial workflows intact, and security/latency/battery tests pass.

## Decision gates

Do not ship a network service just to serve a single-user local app. Add a PC companion only when a concrete cross-device workflow justifies pairing and maintenance. Do not select an STT/TTS/LLM runtime from hardware specifications alone: measure latency, memory, battery use, offline operation, and Spanish quality on the actual phone. Do not call the app production-ready until milestone 3 passes.
