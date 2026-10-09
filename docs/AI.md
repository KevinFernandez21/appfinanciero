# Optional local AI

The ledger and savings planner work without a model. AI output is untrusted
explanatory text, never an authoritative calculation or a recorded transaction.
The adapter has no repository, network, or executable-tool access.

## Model setup

Use a **CPU-compatible `.litertlm` container**, not GGUF, `.task`, `.tflite`, or
raw weights. A starting candidate is the Gemma 3 1B instruction-tuned, INT4 QAT
LiteRT-LM variant in the [official linked model repository](https://huggingface.co/litert-community/Gemma3-1B-IT).
Accept its license and obtain the file yourself; no model is shipped or downloaded
automatically. Only install models from trusted sources.

Import through the Android document picker. The importer checks extension,
`LITERTLM` signature, and the actual streamed **1 GiB maximum**, then atomically
replaces a fixed app-private file. A failed import preserves the existing model.
These checks are preliminary: native initialization checks actual model/backend
compatibility. A compatible extension does not guarantee a usable model.

`LocalLlmService` uses pinned `litertlm-android:0.10.0`, matching the project's
Kotlin 2.2.21. It builds with the existing JVM 17 target and JDK 21 toolchain.
Newer runtime versions require a separate compatibility review. See the
[current Android API guide](https://developers.google.com/edge/litert-lm/android).
MediaPipe LLM Inference is maintenance-only; this integration uses LiteRT-LM.

## Operating limits

- CPU inference uses four threads, a 1024-token context, a 60-second generation
  timeout, and a 4000-character response cap. Model initialization also runs off
  the UI thread; native initialization cannot be interrupted immediately.
- Cancel stops the coroutine and requests native cancellation. Close serializes
  disposal behind ongoing operations. A new conversation is used per question.
- The imported model lives in `noBackupFilesDir/local-llm`; financial backups do
  not include it. Removing the model restores the deterministic no-model response.
- No prompts or model replies are logged by the adapter; native logs are disabled.
  Prompts remain on-device. Do not include sensitive notes unnecessarily.
- A small 1B INT4 model is a starting point for the Redmi Note 14 Pro+ with 12 GB
  physical RAM, **not a performance guarantee**. RAM expansion is not physical RAM.
  Available memory, native compatibility, heat, and battery must be tested on the
  actual phone. Prefer short foreground requests; no background inference loop.
- A model may hallucinate or ignore instructions. Display generated output as AI
  explanation and keep computed financial facts separate. Do not parse text into
  ledger operations. Any future draft must pass domain validation and explicit
  user confirmation before persistence.

## Verification and future PC boundary

Run `:app:testDebugUnitTest --tests '*ai.LocalAiPolicyTest'` for import and prompt
rules. Instrumented `LocalLlmServiceTest` checks native packaging and no-model
lifecycle only. Neither test establishes real inference quality or phone speed;
model inference still needs physical-device validation with a licensed model.

A PC LLM/MCP bridge is **not implemented**. A future bridge must be opt-in,
authenticated/paired, send only consented/redacted facts, and expose allowlisted
read operations or validated drafts. Never expose raw database files, unrestricted
SQL, automatic ledger writes, or remote execution. Offline calculation remains
the source of truth if the PC is unavailable.
