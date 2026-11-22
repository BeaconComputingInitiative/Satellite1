# BEACON COMPUTING INITIATIVE
**Developer Seal:** `CJB-DEV-2026`

# Proposed HTTPS Endpoint Contract — NOT YET VERIFIED AGAINST ORBIT11

This is a client-side candidate, not a claim that ORBIT11 already exposes this API. An authorized operator must configure an HTTPS endpoint in the UI and supply a per-device enrollment token. Tailscale or another protected network can carry the TLS connection, but no cleartext HTTP fallback or disabled certificate verification is provided.

## Request

- `POST <operator-configured HTTPS URL>`
- `Authorization: Bearer <device enrollment token>`
- `Content-Type: application/x-ndjson; charset=utf-8`
- `X-BCI-Developer-Seal: CJB-DEV-2026`
- `X-BCI-Batch-SHA256: <SHA256 of exact request bytes>`
- Body: 1–100 encrypted-queue entries serialized as UTF-8 JSONL (plaintext **only** within TLS-protected request).

## Required response before deleting/acknowledging from pending queue

HTTP 2xx with JSON:

```json
{"batch_sha256":"matching-full-request-hash","accepted_count":100,"receipt_id":"server-generated-opaque-receipt"}
```

Response must be produced by the authenticated ORBIT11 endpoint, validate record schemas, be durable in a quarantined intake store, and carry server-side deduplication by `event_id` or batch digest. Verify receipt authenticity using authenticated TLS and server-side logging; a signed receipt is recommended before production. The client marks entries uploaded only after exact hash+count+receipt checks.

Missing credential, missing endpoint, non-HTTPS URL, unexpected ACK or any TLS failure: **HOLD**. Unknown schema or server errors: queue stays local. No automatic conversion into training records; ORBIT11 intake/review/evaluation remains separate.

## Server work still required

- Resolve the real ORBIT11 ingestion endpoint and its canonical schema before connection.
- Establish server-side authentication, rate limits, payload bounds, replay protection, ack schema, durable hash-verified storage, revocation and audit policy.
- Test one opt-in batch end-to-end, confirm receipt, and perform a privacy review before continuous delivery.
