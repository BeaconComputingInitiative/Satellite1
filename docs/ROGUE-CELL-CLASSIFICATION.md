# BEACON COMPUTING INITIATIVE
**Developer Seal:** `CJB-DEV-2026`

# SATELLITE1 v0.1.4 — Rogue-Cell Anomaly Screening Specification

**Classification status: research candidate, NOT validated against independent ground truth.**

This feature attempts to identify **anomalies sometimes associated with rogue cell stations** (cell-site simulators, often called *false towers*). A non-root Android application's public telephony APIs do **not** expose sufficient control-plane evidence to authenticate the serving base station or determine an operator's identity. The classifier must **never** emit `CONFIRMED_ROGUE_CELL`, and an alert must not be interpreted as proof of surveillance, interception, a physical tower's location, or an adversary.

## Inputs and gate

- Monitor only while a user-started visible foreground service is active, and only after explicit cellular and precise-location permissions.
- Use separate per-subscription records; never confuse two simultaneous registered cells with a handoff.
- Discard cell observations more than 120 seconds old for transition classification, and reject unordered observations or those separated by more than 10 minutes.
- Consider single-serving-cell identity replacements only. Multiple simultaneously registered cells are `INSUFFICIENT_EVIDENCE` for this heuristic (normal dual-connectivity and carrier aggregation occur).
- If the phone's location is available for both observations, consider it *stationary* when the two fixes have accuracy at most 75 m and separation at most 60 m. Otherwise stationary is unknown; do not infer it from tower IDs.
- Do not record IMSI/IMEI, SMS/call content, or cell-site operator identity.

## Explainable indicators

| Reason code | Threshold | Caveat |
|---|---|---|
| `LTE_NR_TO_2G_DOWNGRADE` | One serving cell changes from LTE/NR to GSM | Coverage loss or ordinary fallback can cause this |
| `SIM_HOME_PLMN_DISAGREEMENT` | Cell PLMN differs from SIM home PLMN while Android reports not roaming | MVNOs and network sharing can produce mismatches |
| `STATIONARY_SIGNAL_SURGE` | Same phone location, new cell at least 25 dBm stronger | Multipath and normal power changes can produce this |
| `NEIGHBOR_VISIBILITY_DROP` | At least three fresh neighbors, then none, while phone location is stationary | Modem filtering and scan throttling can cause this |

An isolated indicator receives `CELLULAR_ANOMALY`, not a claim of a fake base station. `SUSPECTED_ROGUE_CELL` requires **all four** indicators concurrently across a fresh, unambiguous single-cell replacement. This threshold is a deliberate candidate rule to reduce false positives; it has **not been calibrated** and is **not a confidence percentage**.

## Classification outcomes

- `INSUFFICIENT_EVIDENCE` — unusable/stale/unordered or ambiguous comparison.
- `NO_ANOMALY_OBSERVED` — no listed anomaly in the current comparison; does **not** verify that the cell is legitimate.
- `CELLULAR_ANOMALY` — one or more ambiguous indicators recorded for investigation.
- `SUSPECTED_ROGUE_CELL` — all four candidate indicators concurrently; **unconfirmed**.

Every unambiguous single-cell transition generates an encrypted `CELL_RISK_ASSESSMENT` JSONL record with `classification`, `risk_score` (unvalidated rule points), `reason_codes`, `detector_version`, and explicit fields `rogue_cell_confirmed=false`, `independent_rf_confirmation=false` and `physical_tower_identity_verified=false`.

## Provenance and ORBIT11 admission

`CELL_RISK_ASSESSMENT` is derived from phone-side telemetry and must remain linked to the original `CELL_SERVING_CHANGED` and periodic measurements. ORBIT11 ingestion validates JSONL and stores encrypted rows in quarantine; it must **not** treat a classifier label as independently verified ground truth or automatically train, contain, or report confirmed malicious activity.

A higher-confidence investigation would require compatible modem diagnostics, independent passive radio observations (for example, authorized SDR measurements), carrier-provided network data and independent interpretation. Even such sources may be inconclusive. The app does not provide these sources. Android's own Mobile Network Security warning/timeline features can provide additional on-device information on supported hardware, but third-party apps should not assume privileged access to those notifications or modem signaling.

## Retrospective data limitations

SATELLITE1 v0.1.3 exports lacked the per-subscription home-PLMN and fresh-neighbor context required by these rules. **Do not retroactively label the user's historical cell changes as fake or suspicious** based on v0.1.3 measurements alone.

## Sources consulted

- Android TelephonyManager / TelephonyCallback CellInfoListener documentation: https://developer.android.com/reference/android/telephony/TelephonyManager and https://developer.android.com/reference/android/telephony/TelephonyCallback.CellInfoListener
- EFF, Cell-Site Simulators: https://sls.eff.org/technologies/cell-site-simulators-imsi-catchers
- Android Mobile Network Security: https://source.android.com/docs/security/features/cellular-security/mobile-network-security

## Execution invariant

**Screen as `SUSPECTED_ROGUE_CELL` IF AND ONLY IF** the bounded, fresh single-cell transition meets the four documented indicators. This is a heuristic alert, never proof of rogue infrastructure.
