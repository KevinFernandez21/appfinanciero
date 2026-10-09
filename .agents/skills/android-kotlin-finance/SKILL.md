---
name: android-kotlin-finance
description: Use when implementing or reviewing AppFinanciero Kotlin Android money calculations, local persistence, savings goals, or AI-assisted financial actions.
---

# Kotlin finance development

Read `AGENT.md` and the relevant domain tests before editing. Keep finance behavior deterministic and Android-independent where possible.

- Represent each monetary value in exact minor units plus a currency code. Reject currency mismatches and arithmetic overflow.
- Separate recorded ledger facts, virtual goal reservations, budget limits, and forecasts. Never count an internal transfer or goal reservation as income or expense.
- Keep AI and voice adapters outside the authoritative calculation path. They may create drafts; only validated domain operations after user confirmation may persist them.
- For any changed financial rule, first add a failing test covering the happy path and at least one boundary case; then implement and refactor.
- Verify Room migrations preserve history. Do not use destructive fallback migrations for real financial data.
- Avoid speculative libraries and premature modules. Choose dependencies only after checking current official Android/Kotlin documentation and the existing build.

Useful primary references:

- Android architecture: https://developer.android.com/topic/architecture/recommendations
- Room: https://developer.android.com/training/data-storage/room
- Kotlin coding conventions: https://kotlinlang.org/docs/coding-conventions.html
