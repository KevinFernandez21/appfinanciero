# Optional PC companion and MCP boundary

The Android ledger remains usable without a computer, server, Wi-Fi, or model. This document describes a future integration contract; no network pairing or MCP server is shipped in the current app.

## Topology

```text
Android app (authoritative Room ledger)
  <mutually authenticated, scoped connection>
Kotlin PC companion (optional gateway)
  <MCP client/host orchestration>
User-selected PC LLMs and MCP servers
```

MCP standardizes how a host discovers and invokes tools and shares context. It does not automatically make two LLMs exchange messages. The PC companion or its host must explicitly choose what context to pass between models. The app must never expose its Room file or arbitrary SQL endpoint.

## First useful contract

Read-only capabilities: `getMonthlySummary(currency, month)`, `getBudgetProgress(id)`, and `getGoalProgress(id)`. Responses contain derived totals and user-approved labels only. Raw transaction notes and full history require a separate per-request consent scope. A model may propose `draftTransaction`, but the phone displays an editable draft and requires confirmation; the model cannot call a direct write endpoint.

## Security gates before implementation

1. Pair one named laptop from the phone with a short-lived code and pin the companion identity. No public discovery or open LAN listener by default.
2. Use encrypted transport and rotate/revoke credentials. Bind scopes to the paired device and individual tool; default to read-only.
3. Present a confirmation screen showing fields, destination, and retention before sending private data to the PC. Keep prompts and amounts out of diagnostic logs.
4. Rate-limit requests, expire sessions, reject replayed requests, and show a visible active-connection indicator and revocation control.
5. Test on the user's actual LAN and threat model. Never describe this as secure merely because it is local.

Ktor is a candidate for an all-Kotlin companion if this workflow becomes valuable; it is not a dependency of the Android app today. A second-language backend adds no value for a single-device ledger.
