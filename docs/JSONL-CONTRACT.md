# BEACON COMPUTING INITIATIVE
**Developer Seal:** `CJB-DEV-2026`

# SATELLITE1 JSONL — Candidate Export Format

Each line is a UTF-8 JSON object with no newlines embedded in the serialized record. Data fields depend on the collector:

```json
{"initiative":"BEACON COMPUTING INITIATIVE","developer_seal":"CJB-DEV-2026","schema":"satellite1.telemetry.v1-candidate","event_id":"uuid-v4-example","timestamp_utc":"2026-11-21T00:00:00Z","kind":"CELL_RADIO_OBSERVATION","facts":{"source":"android.telephony.TelephonyManager.getAllCellInfo","tower_geolocation_verified":false,"cell_count":0,"cells":[]},"record_sha256":"sha256-of-serialized-record-before-record_sha256-field"}
```

`record_sha256` is a basic integrity fingerprint over the Java `JSONObject` serialization before the hash field is appended. It is **not a digital signature, not independently authenticated custody, and not a canonicalized JSON hash**. For full provenance stronger event signing is a future qualification item.

Last JSONL line:

```json
{"initiative":"BEACON COMPUTING INITIATIVE","developer_seal":"CJB-DEV-2026","kind":"EXPORT_MANIFEST","schema":"satellite1.jsonl-export.v1","events":100,"payload_sha256":"sha256-of-every-previous-line-including-newlines"}
```

The `payload_sha256` intentionally excludes the manifest line to avoid a recursive hash. The UI prints the **whole exported file** SHA-256, including the manifest line. Exported records are decrypted only during user-initiated Storage Access Framework streaming. A file saved to cloud storage by the system picker will leave the device under that provider's policies.

Recommended file: `SATELLITE1-YYYY-MM-DD.jsonl`. The user controls its destination.

**No export equals training admission.** Data must still be reviewed for proper authority, consent, custody, duplication, contamination and objective supervision.

## v0.1.2 — Serving cell transition events

Changes are separate JSONL records, automatically included in a user-initiated export. They are not proof of a physical-tower handoff. `CELL_BASELINE_ESTABLISHED` = first reliable registered cell identity; `CELL_SERVING_CHANGED` = changed registered-cell identity set; `CELL_SUBSCRIPTION_CHANGED` = default-data SIM subscription changed. Missing/redacted identity generates **no fabricated transition event**.

Example (illustrative identifiers only):

```json
{"initiative":"BEACON COMPUTING INITIATIVE","developer_seal":"CJB-DEV-2026","schema":"satellite1.telemetry.v1-candidate","event_id":"sample-uuid","timestamp_utc":"2026-11-21T00:00:00Z","kind":"CELL_SERVING_CHANGED","facts":{"change_type":"registered_cell_identity_set_changed","previous":[{"radio":"LTE","registered":true,"mcc":"310","mnc":"260","tac":1,"ci":100}],"current":[{"radio":"LTE","registered":true,"mcc":"310","mnc":"260","tac":1,"ci":101}],"physical_tower_change_verified":false,"exact_handoff_time_verified":false}}
```

Real records contain `record_sha256` and may include a recent phone location fix if explicitly enabled. `timestamp_utc` is **detection/log time**, not guaranteed radio transition time.
