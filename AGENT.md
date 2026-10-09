# AppFinanciero development contract

## Purpose

Build a private, offline-first Android application that helps one person understand spending and plan savings. The financial ledger is the source of truth; AI features may explain or propose actions but must not invent balances or bypass validation.

## Architecture

- Kotlin Android app with Jetpack Compose UI, screen ViewModels, repositories, Room persistence, and DataStore for small preferences.
- Keep financial calculations in Android-independent Kotlin domain code.
- Use exact monetary units with an explicit currency; never use binary floating point for money.
- Internal account transfers are neither income nor expense. Savings reservations are virtual earmarks and do not move account balances.
- Local STT, TTS, and LLM are replaceable adapters. The app must remain usable without any model installed.
- A future PC companion may expose narrowly scoped MCP tools; no raw database, unrestricted SQL, or silent write access.

## Delivery rules

- Follow strict TDD for financial rules: failing test, minimal implementation, refactor.
- Include focused persistence, UI, and migration tests when those boundaries change.
- Never silently destroy financial history. Verify backup and restore before relying on the app for real records.
- Do not log financial notes, amounts, audio, or model prompts in production.
- Require explicit user confirmation before a voice or LLM-generated draft changes the ledger.
- Document and verify build/test commands. Do not claim an emulator or device test that did not run.
- Keep code, identifiers, comments, and documentation in English unless the user requests otherwise for an artifact.

## Skills

- `.agents/skills/testing-setup/SKILL.md` for Android test infrastructure.
- `.agents/skills/android-kotlin-finance/SKILL.md` for project-specific Kotlin financial invariants.
