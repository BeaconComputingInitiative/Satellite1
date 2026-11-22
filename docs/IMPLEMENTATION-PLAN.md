# BEACON COMPUTING INITIATIVE
**Developer Seal:** `CJB-DEV-2026`

# SATELLITE1 — Kotlin Architecture and Implementation Plan

## User-selected behavior

| Setting | Approved build target |
|---|---|
| Monitoring | Continuous **user-started visible foreground service**, OS-dependent |
| Recon cadence | 30 minutes; in-service scans with scheduled background upload |
| Network scope | Explicitly approved Wi-Fi SSID **and** BSSID |
| Battery | Pause collection below 15% while not charging |
| Upload | Enabled preference; send only after verified HTTPS endpoint + enrollment credential + matching server acknowledgement |
| Location | Opt-in high-accuracy location updates with timestamp and accuracy |
| Cellular | Opt-in serving/visible neighboring cell identifiers and signal, subject to Android disclosure limits |
| Local export | On-demand `.jsonl` through system document picker; SHA-256 receipt |

## Module map

- `MainActivity.kt`: BCI Compose UI; consent dialog and app settings; JSONL document-picker export and receipts.
- `Policy.kt`: network allowlist and opt-in flags; fails closed with redacted SSID/BSSID.
- `MonitorService.kt`: visible foreground lifecycle; Wi-Fi network callbacks; one-minute decision tick; 30-minute approved Wi-Fi inventory; battery gate; start/stop controls.
- `WifiTelemetry.kt`: Android connectivity, gateway, DNS, addresses, radio strength; approved Wi-Fi only.
- `GatewayProbe.kt`: a bounded TCP handshake on the known approved gateway only, with separate opt-in.
- `CellTelemetry.kt`: phone-side 2G/3G/4G/5G cell samples; explicit permission checks; no tower-coordinate database.
- `GeoTelemetry.kt`: location updates, accuracy/age; foreground-service only.
- `EvidenceStore.kt`: AES-GCM encrypted JSONL record storage, stable IDs, delivery acknowledgments, streaming JSONL export with record and payload digests.
- `CryptoBox.kt`, `CredentialVault.kt`: Android Keystore secrets and AES-GCM encryption.
- `UploadWorker.kt`: 30-minute approximate attempts, HTTPS only, consent-aware bounded batches and receipt verification.

## Four implementation/qualification batches

1. **Build and device enrollment:** Android Studio Gradle sync, compile, install on a device (Android 10+), grant fine location, notification, optional phone state permissions; confirm foreground service and Stop action. Confirm exact typography after locally provisioning approved fonts.
2. **Telemetry and scope:** Test approved/unknown/roaming Wi-Fi, screen-off running, captive portals, VPN/Tailscale interactions, gateway checks only with second authorization, low battery stops, GPS/5G/LTE known/unknown identifiers, cellular multi-SIM behavior. Add tests for these behaviors before production.
3. **Evidence and API:** Verify encrypted local DB, tamper-detection, JSONL event hashes and footer, full file SHA-256, round-trip export, intermittent offline queues, authentic TLS identity, request authentication, signed/validated server receipts, replay and deduplication. Adapt to ORBIT11's **actual** API contract.
4. **Operational acceptance:** Measure resource consumption, Android 14–17 behavior, power-management restrictions, permission withdrawal, lifecycle recovery, OS-enforced telemetry gaps, custody, privacy controls, release signing and separate training admission. HOLD until independently verified.

## Android platform notes

- Android 14+ requires declared foreground service type/permission and while-in-use permission for location; a `location` service normally must start while the app is visible. `ACCESS_BACKGROUND_LOCATION` is **not** requested in this scaffold, and there is no receiver to restart a location service from the background. Platform/background and OEM behavior limit persistence.
- WorkManager 30-minute work is **inexact** and may be deferred by Doze/battery constraints.
- Android 17/API 37 introduces `ACCESS_LOCAL_NETWORK` for apps targeting 37+; this project targets SDK 35. Before retargeting, explicitly implement/request newer local-network permission or picker APIs and re-test all active checks.
- `TelephonyManager.getAllCellInfo()` can return cached, restricted or empty results. Sampling it every five minutes does not force new measurements; stale age is recorded when available.
- SSID/BSSID alone does not establish ownership or prevent spoofing. Network-owner approval is an organizational requirement beyond the app allowlist.

## v0.1.2 — Change-logging acceptance gates

- Validate real-device callbacks under LTE-only, 5G NSA and 5G SA, and when data subscription changes.
- Verify initial baseline creates no false handoff; actual registered cell changes produce `CELL_SERVING_CHANGED` once; signal-only updates do not produce false positives.
- Verify encrypted DB migration retains all existing JSONL evidence without loss, including under simulated interruption.
- Verify Android 29–30 compatibility for legacy callback, Android 31+ callback registration/unregistration, and permission revocation during monitoring.
- Confirm low-battery pause stops both location and cell callback collection; verify consent toggles.
- Verify the signed in-place upgrade uses the original release certificate and that JSONL can be exported after the upgrade.
- Keep ORBIT11 delivery disabled/unqualified until device enrollment, TLS endpoint, custody and acknowledgment contract are verified.

## v0.1.3 lifecycle repair qualification

- Press START MONITORING, verify visible notification and LIVE SERVICE ACTIVE.
- Background the activity; confirm service remains running while Android permits.
- Press Stop, verify notification disappears and LIVE SERVICE STOPPED.
- Force-stop or let Android terminate the service; reopen and confirm the UI says STOPPED even if saved user preference was previously enabled. No covert restart.
- Verify encrypted JSONL history survives in-place upgrade; confirm cell baseline only after a usable identity is observed.
- Keep signed API upload on HOLD until an authenticated server receipt is tested.

## v0.1.4 Rogue-cell candidate extension

- Separate per-SIM baselines and no false handoff from registered set expansion.
- Only fresh, scoped, unambiguous transitions feed the explainable classifier.
- Review the documentation at `docs/ROGUE-CELL-CLASSIFICATION.md` before enabling deployment.
- Derived `CELL_RISK_ASSESSMENT` records are tamper-evidenced with SHA-256 and encrypted in the existing database.
- No definitive false-tower label, no automatic containment, and no training admission.
