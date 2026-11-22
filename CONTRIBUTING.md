# BEACON COMPUTING INITIATIVE
**Developer Seal:** `CJB-DEV-2026`

# Contributing to SATELLITE1

SATELLITE1 is a research-stage Kotlin Android app with an optional Python receiver. Submissions should be small, testable and scoped to authorized device and network telemetry. A public-source license has not yet been selected; discuss redistribution terms with the maintainer before contributing reusable third-party code or assets.

1. Explain the user-visible behavior and the precise permissions needed. Never add covert collectors, collection of IMSI/IMEI/SMS/calls, broad subnet scanning, or plaintext telemetry transfer.
2. Preserve the BCI header, `CJB-DEV-2026` developer seal, provenance in generated JSONL, and explicit **IF AND ONLY IF** security gates for execution paths.
3. Add regression tests alongside code changes. Run `sha256sum -c SHA256SUMS` against an untouched checkout, then run source tests. If changing tracked files, regenerate the source hash manifest in the same reviewed commit (excluding `SHA256SUMS` itself).
4. For Android changes, build with JDK 17, SDK 35, Gradle 8.10.2 and run on a consented Samsung/emulator. State clearly which checks were actually executed.
5. For intake changes, test bounded payloads, duplicate and invalid records, authentication rejection, rollback, idempotency and TLS constraints. Never include real device telemetry in CI fixtures.
6. Include security/privacy impacts and a short entry in `docs/RELEASE-NOTES.md`. Never publish signing or enrollment credentials.

**Acceptance:** Merge IF AND ONLY IF source integrity, relevant tests, privacy review and reviewer authorization pass. Neither a passing CI run nor a code review establishes rogue-tower detection accuracy.
