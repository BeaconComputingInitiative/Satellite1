# BEACON COMPUTING INITIATIVE
**Developer Seal:** `CJB-DEV-2026`

# SATELLITE1 Developer's Log and Release Notes

These entries record engineering evolution and distinguish verified outcomes from proposed qualifications. **They are not claims of production readiness.**

## v0.1.0 — Native Android scaffold
- Added Compose BCI dashboard, explicit collection controls, Wi-Fi scope policy, Android telemetry collectors, local Keystore-encrypted event storage and JSONL export.
- Introduced candidate HTTPS uploader with queue acknowledgment checks and 30-minute WorkManager cadence.
- Status: source-level validation only in authoring environment.

## v0.1.1 — Branded launcher and local signing
- Integrated BCI launcher artwork into adaptive and legacy mipmap resources.
- Added a separate private release-keystore flow, alignment, signature verification and APK SHA-256 manifest output.
- Key continuity requirement: protect and reuse existing local release identity for subsequent updates.

## v0.1.2 — Cellular identity transitions
- Added telephony callbacks, periodic fallback, registration-set changes, first baseline and subscription-change records.
- Added cellular history panel and additive local SQLite migration.
- Device report: local collection permission checks and prior exports were successful; they do not validate physical-tower identity.

## v0.1.3 — Monitoring lifecycle recovery
- Corrected dashboard's live-service indication and explicit listener/service event logging.
- Samsung ADB evidence established `MONITOR_ACTIVE`, `CELL_LISTENER_ACTIVE` and `isForeground=true` with a visible notification.
- A subsequent user export was inspected for cellular observations, cell transitions, location and JSONL integrity. That private export is deliberately **not** in this public repository.

## v0.1.4 — Per-SIM classification, rogue-cell screening and ORBIT11 intake (candidate)
- Separated per-subscription baselines, multi-registration changes and actual single-cell identity replacements.
- Added freshness/ambiguity gates, explainable `CELL_RISK_ASSESSMENT`, and strict **unconfirmed** rogue-cell labels.
- Added opt-in ORBIT11 HTTPS quarantine receiver with bearer enrollment, encrypted SQLite, batch SHA-256, idempotent acceptance and receipts.
- Resolved a Kotlin scope error in `CellChangeTracker.kt` (`snapshot` → `current` in `details()`) found by an actual `:app:compileReleaseKotlin` attempt; added a regression test.
- The corrected v0.1.4 code still requires an end-to-end compile, signed Samsung upgrade, representative field evaluation, live intake validation, retention review and security qualification.

## v0.1.5 — Orbital Mission Control boot and UI (candidate)
- Added native Compose orbital animation: depth-layered moving satellite, pulsing beacon, radar sweep, stylized Earth, starfield, mission-progress meter and manual skip.
- Designed a cyan/maroon glass Mission Control hero with live service state, animated evidence and cell-change counts, plus native dark Android startup splash; original opt-in telemetry controls remain unchanged.
- Respects disabled Android animations and uses no remote artwork, telemetry, access permissions, or private data while boot screen is displayed.
- Version code 6; pure Compose Canvas artwork, no bundled fonts. Full Android compilation and device UX/accessibility validation remain pending.

## Publication checklist

- Source manifest rebuilt and verified after documentation changes.
- Exclude private `.p12`/certificate secrets, exported JSONL and SQLite, API tokens, environment files, APKs and local SDK settings.
- Source-level CI can validate tests without claiming successful device builds or cell-site authenticity.
- Commit a license only after the maintainer's explicit selection.

**Release gate:** Public source may be shared, but production qualification remains `HOLD` until independently verified.
