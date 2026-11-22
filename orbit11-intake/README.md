# BEACON COMPUTING INITIATIVE
**Developer Seal:** `CJB-DEV-2026`

## ORBIT11 — SATELLITE1 Quarantine Intake (candidate v0.1.4)

This is a **separate optional ingestion service** to be installed on the ORBIT11 host. It is **not proof that the existing ORBIT11 native intake schema has been qualified** and **does not grant training admission**. It accepts only explicitly enrolled SATELLITE1 telemetry through HTTPS with a per-device bearer token and encrypts stored JSONL rows at rest using Fernet. No telemetry is added to model datasets or production rules automatically.

### Install on the ORBIT11 machine

Create an isolated virtual environment with Python 3.11+ and the installed `cryptography` library (the only third-party server dependency). Prefer your organization's verified Python package mirror and lock dependency versions for production. A SHA256SUMS file is provided for the project; verify it *before* editing configuration or executing.

```bash
# BEACON COMPUTING INITIATIVE
# Developer Seal: CJB-DEV-2026
# Initialize IF AND ONLY IF this is your privately controlled ORBIT11 host.
python3 -m venv .venv
.venv/bin/python -m pip install -r orbit11-intake/requirements.txt
.venv/bin/python orbit11-intake/orbit11_receiver.py \
  --state-dir "$HOME/.local/share/bci/orbit11-satellite1-intake" --init-state
```

The enrollment token prints **once**. Copy it privately to the SATELLITE1 device enrollment UI. Do **not** paste it into this chat or commit it. Future runs preserve the encryption key and token hash. Back up the private `fernet.key` securely; losing it prevents reading the quarantine database.

Use a **trusted certificate whose DNS name resolves to the ORBIT11 host and is valid on Android**, ideally a private Tailscale HTTPS DNS name and trusted certificate, or a vetted reverse proxy. Do **not** disable TLS verification or send precise location and tower history over plaintext HTTP. Example *after* your HTTPS credentials and DNS are configured:

```bash
# BEACON COMPUTING INITIATIVE
# Developer Seal: CJB-DEV-2026
.venv/bin/python orbit11-intake/orbit11_receiver.py \
  --state-dir "$HOME/.local/share/bci/orbit11-satellite1-intake" \
  --serve --bind 127.0.0.1 --port 8765 \
  --tls-cert /path/to/trusted-fullchain.pem --tls-key /path/to/private-key.pem
```

**Note:** Binding to `127.0.0.1` makes the server reachable only locally, even with HTTPS. To reach it from a phone, use an operator-reviewed Tailscale HTTPS forwarding setup, or explicitly bind to your verified private tailnet interface with a certificate valid for the DNS name. Do not bind publicly or use a guessed Tailscale IP.

### SATELLITE1 enrollment

- In the v0.1.4 Android GUI, enable **automatic ORBIT11 upload** explicitly; default is OFF.
- Enter the complete HTTPS URL ending `/v1/telemetry/batches`, plus the private bearer token created on ORBIT11.
- Save, then tap **TEST ONE AUTHENTICATED UPLOAD**.
- Inspect `ORBIT11_RECEIPT_VERIFIED` in filtered Android Logcat and review the `batches` table on the server. The batch is acknowledged locally only after exact `batch_sha256`, `accepted_count`, and a nonblank `receipt_id` are received.
- Only then leave automatic upload enabled, with the 30-minute WorkManager cadence. Android may delay attempts.

### Safety, integrity, privacy

All incoming records require a BCI seal, a valid event schema, UUID, UTC timestamp, unique identity, an exact record-level SHA-256, and an exact request SHA-256. SQLite transaction commits are durable before a successful receipt is returned. Repeated exact batches return an idempotent receipt, and a reused UUID with changed bytes is a hard conflict. Rows are encrypted at rest, the listener must use TLS, and the token is stored server-side as a SHA-256 digest. Local state defaults to 0700/0600 permissions. Plaintext user telemetry is never written to server logs.

This **does not** establish independently witnessed custody, a cryptographic signature from the Android device, a trusted time source, fleet management, retention policy, revocation administration, or operational qualification. It does not claim physical cell tower location, timing or completeness. Retain the source export and its manifest as evidence separately; the API receiver is not an evidentiary signing authority.

### Tests

```bash
# BEACON COMPUTING INITIATIVE | Developer Seal: CJB-DEV-2026
python3 -m unittest discover -s orbit11-intake/tests -v
```

The HTTP integration test uses a local loopback ephemeral **plaintext HTTP test server only** (never exposed or run in production). The actual live `--serve` command requires a TLS key and certificate.
