# BEACON COMPUTING INITIATIVE
**Developer Seal:** `CJB-DEV-2026`  
**Project:** SATELLITE1 · Native Android Telemetry Agent · Version `0.1.5-orbital-boot`  
**Repository:** [BeaconComputingInitiative/Satellite1](https://github.com/BeaconComputingInitiative/Satellite1)

# SATELLITE1

**An opt-in Android telemetry and network-observation agent, with explainable cellular anomaly screening, evidence-preserving JSONL export, and a candidate authenticated ORBIT11 quarantine intake service.**

> **Research / pre-release.** The v0.1.3 predecessor was installed and exercised on a Samsung device. The v0.1.4 classifier source has local rule, scaffold, and intake unit tests, plus a correction for a Kotlin compile-scope failure. **A successful full v0.1.5 Android build, signed release, in-place Samsung upgrade, end-to-end ORBIT11 API delivery, independent classification validation, and production qualification have not been established in this repository.** This software **cannot verify that a cell tower is fake** or identify who operates one.

## At a glance

| Area | Implemented behavior | Boundary / qualification |
| --- | --- | --- |
| Animated BCI mission-control UI | Native Compose orbital satellite boot animation, skippable intro and mission dashboard with live local counters | Accessibility respects Android disabled-animation setting; no remote assets or hidden sensor startup |
| Continuous Android monitoring | User-started visible foreground service with live status, stop control, service/cellular callback diagnostics | Subject to Android background limits; not stealth, not boot-persistent |
| Wi-Fi telemetry | Network context, RSSI, frequency, gateway, DNS and connection changes | Only approved SSID+BSSID are eligible for scoped observations; approval must reflect actual network-owner authority |
| Active network checks | Optional TCP checks on the current approved Wi-Fi gateway, ports 22/80/443, no more than once per 30 minutes | Additional explicit authorization; no host/subnet sweeps, intrusive probes or packet interception |
| Geolocation | Opt-in location fixes, timestamps, provider and accuracy | Requires Android permissions; coordinates are sensitive |
| Cellular measurements | Serving and neighboring cell observations where Android exposes them; LTE/NR and other reported radio types | Values can be unavailable, delayed, or cached; not verified physical tower coordinates |
| Cell-change history | Per-subscription baselines; registered set changes separated from single-cell replacements | Detected time is not necessarily true radio handoff time |
| Rogue-cell screening | `INSUFFICIENT_EVIDENCE`, `NO_ANOMALY_OBSERVED`, `CELLULAR_ANOMALY`, `SUSPECTED_ROGUE_CELL` | **Unvalidated heuristic**, never `CONFIRMED_ROGUE_CELL` |
| Evidence | App-private SQLite with Android Keystore AES-GCM-encrypted payloads; user-initiated UTF-8 JSONL export and hashes | Hashes are integrity fingerprints, not trusted device signatures |
| ORBIT11 delivery | Configurable HTTPS endpoint, encrypted local token, 30-minute WorkManager retries and exact receipt checks | Defaults disabled; candidate receiver is independent of production ORBIT11 intake |

## Project layout

```text
app/
  build.gradle.kts               Kotlin/Compose Android app (min SDK 29, target/compile 35)
  src/main/
    AndroidManifest.xml          Manifest, permissions, foreground service
    java/org/beaconcomputing/satellite1/
      MainActivity.kt            BCI-themed Compose dashboard and controls
      BootSequence.kt            Native orbital splash animation and status hero
      MonitorService.kt          Visible monitoring lifecycle and collectors
      WifiTelemetry.kt           Wi-Fi context (approved networks only)
      GeoTelemetry.kt            Device geolocation observations
      CellTelemetry.kt           Android telephony observations
      CellChangeTracker.kt       Per-SIM registration/cell-change tracking
      RogueCellRules.kt          Pure classification rules
      RogueCellClassifier.kt     Device-side rule evaluation and evidence
      EvidenceStore.kt           Encrypted event database and JSONL export
      CryptoBox.kt               Keystore-backed event encryption
      Policy.kt                  Local consent and network policy
      CredentialVault.kt         Encrypted API enrollment token
      UploadWorker.kt            HTTPS batch uploader and receipt verification
      GatewayProbe.kt            Optional bounded gateway connectivity checks
    res/                         Branded adaptive / legacy launcher assets
orbit11-intake/
  orbit11_receiver.py            Optional authenticated HTTPS quarantine service
  tests/                         Ingestion tests
  requirements.txt               Server-only Python dependency
  README.md                      Deployment and security notes
docs/                            Implementation, schema, security and release notes
tools/                           Local build, release-sign, diagnostic and source tests
SHA256SUMS                       Published-source SHA-256 manifest
```

## Animated Android boot experience

On an ordinary cold app launch, SATELLITE1 uses Android's native **dark splash** before entering a **moving vector satellite orbiting a stylized Earth**, scanning radar arc, pulse beacon, constellation background, privacy/status labels, BCI developer seal and an **ENTER MISSION CONTROL** skip control. The intro automatically proceeds to the instrument dashboard after approximately 2.6 seconds, or immediately when Android system animation duration is disabled. This transition uses Compose state, coroutines and motion primitives rather than remote resources or a video. It does not trigger sensor permissions, stealth monitoring, connectivity or service startup; collection still requires its own explicit consent. The mission dashboard includes live service state, local evidence count and cell-change count. Original Compose Canvas artwork is authored in `BootSequence.kt`; Android Studio provides two `@Preview` views (boot and mission header). The app uses native Compose transitions, gradients, an animated status indicator and animated counters without requiring downloaded artwork or fonts. See [UI design system](docs/UI-DESIGN.md). Exact BCI Oxanium/Rajdhani/JetBrains Mono fonts are future optional user-installed assets; current code uses Android system fallbacks.

For detailed screen behavior, color tokens, accessibility and Compose design decisions, see [docs/UI-DESIGN.md](docs/UI-DESIGN.md).

## Getting started on Kali Linux / Kubuntu

**Prerequisites:** JDK 17, Android SDK Platform 35 and matching build-tools, Gradle 8.10.2 for Android Gradle Plugin 8.8.2, an authorized Android device for USB deployment, `adb`, Python 3, and an existing release signing identity for in-place upgrades. Kotlin Android Plugin is 2.0.21 and Compose uses the BOM declared in `app/build.gradle.kts`.

The project deliberately does **not** bundle a Gradle wrapper binary or a release keystore. Set `ANDROID_HOME` to your installed SDK. The repository's `tools/build-and-deploy.sh` expects the verified Gradle 8.10.2 binary under `~/.local/opt/bci/gradle-8.10.2/bin/gradle` by default, or via `GRADLE_BIN`.

```bash
# BEACON COMPUTING INITIATIVE
# Developer Seal: CJB-DEV-2026
# Verify and build IF AND ONLY IF source integrity, SDK and JDK checks pass.
(
  set -e
  export ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"
  sha256sum -c SHA256SUMS
  PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s tools -p 'test_*.py' -v
  java -version
  bash tools/build-and-deploy.sh --build-only </dev/tty
)
```

**Release signing:** On a device that already has SATELLITE1 installed, **reuse the original** local PKCS#12 identity, usually `~/.local/share/bci/keys/satellite1-release.p12`; never upload it, enter its password in CI, or regenerate it as a shortcut. Existing keys are required by the upgrade script. To build and install once local tests pass:

```bash
# BEACON COMPUTING INITIATIVE
# Developer Seal: CJB-DEV-2026
# Upgrade IF AND ONLY IF build, APK signature, SHA-256 and ADB gates pass.
(
  set -e
  bash tools/build-and-deploy.sh --install </dev/tty
)
```

The install script uses `adb install -r`; it does **not** intentionally remove the existing installation or erase the app's private evidence. If certificates differ, Android may reject the upgrade. **Do not uninstall without backing up your telemetry.** Debug builds and release builds signed by different identities cannot update one another in place.

The Android build and Samsung upgrade must be executed on a trusted workstation; a source-level test PASS is **not** proof the APK compiles or the hardware collectors work.

## Running SATELLITE1 on Android

1. Install the locally verified and signed APK. Open **SATELLITE1** and review Android's Location, Phone and Notifications permissions. Enable precise location where needed for cell or Wi-Fi identification. Android may return redacted or stale radio data even when permissions are granted.
2. Explicitly enable the collectors you want: Wi-Fi context, GPS/network location, and serving/neighboring cellular measurements. For active probes, separately approve a Wi-Fi network you administer or have authorization to assess, and separately approve bounded gateway checks.
3. Tap **START MONITORING**. A visible, ongoing Android notification and the **LIVE SERVICE: ACTIVE** indicator should appear. **STOP MONITORING** ends voluntary collection. There is no stealth startup or guaranteed automatic restart after Android terminates the foreground service.
4. In **CELLULAR INTELLIGENCE // LIVE**, inspect per-SIM serving cells and event history. Refresh explicitly when needed; the monitoring service may collect while the dashboard is closed.
5. In **EVIDENCE VAULT // JSONL**, export through Android's document picker to a destination you select, such as local Downloads. The UI displays the complete-file SHA-256 digest. Exporting to a cloud document provider may disclose the exported coordinates/cell data to that provider.
6. Keep **automatic ORBIT11 upload disabled** until the separately provisioned server and privacy policy are verified. Use the one-batch test first.

Android scheduling is best effort. The configured periodic WorkManager task runs **approximately** every 30 minutes (not a guaranteed exact timer). Battery-low and connectivity restrictions can defer operation.

## Cellular metrics and change classification

Examples of potentially available phone-reported attributes: MCC/MNC (carrier), LTE CI/TAC/PCI/EARFCN, 5G NCI/TAC/NRARFCN, registered versus neighboring cells, RSSI/dBm, RSRP/RSRQ and measurement age. Support is radio-, modem-, OS-, SIM- and permission-dependent. SATELLITE1 does **not** collect IMSI, IMEI, SMS content, calls, microphones or camera recordings.

Each subscription maintains an independent baseline. Changes in a **registered cell set** are not automatically called a physical handoff. The app emits the following evidence kinds where applicable:

| Kind | Meaning |
| --- | --- |
| `CELL_RADIO_OBSERVATION` | Available radio measurements at observation time |
| `CELL_BASELINE_ESTABLISHED` | Initial usable serving-cell identity for a subscription |
| `CELL_REGISTERED_SET_CHANGED` | Registered-cell membership changed without proving a single-cell replacement |
| `CELL_SERVING_CHANGED` | Single-cell identity replacement observed for a subscription |
| `CELL_SUBSCRIPTION_CHANGED` | Default mobile-data subscription changed |
| `CELL_RISK_ASSESSMENT` | Derived, explicitly unconfirmed rogue-cell screening assessment |
| `GEOLOCATION` | Location fix collected under the app's permission and consent gates |

A real serving-cell identity change can arise from normal mobility, inter-frequency reselection, carrier aggregation, dual SIM behavior or sector changes **on the same tower**. Android's timestamp is the observation/detection time, not a cryptographically verified exact transition time.

### False-tower / rogue-cell screening (research heuristic)

The classifier evaluates bounded, ordered, fresh comparisons only: a 120-second maximum age per radio measurement and 10-minute maximum gap. For an *unambiguous single-cell replacement*, it checks:

- LTE/NR-to-GSM downgrade;
- serving PLMN disagreement with home PLMN when Android reports non-roaming;
- a signal increase of at least 25 dBm while the phone is plausibly stationary;
- a drop from three or more visible neighbors to none under the same stationary condition.

`SUSPECTED_ROGUE_CELL` is emitted **if and only if all four indicators coincide** under the eligibility gates. Partial patterns yield `CELLULAR_ANOMALY`; missing/ambiguous/stale inputs yield `INSUFFICIENT_EVIDENCE`. This is **not a calibrated probability or a confirmed fake-cell-tower finding**. Normal carriers and Android reporting behavior can produce similar observations. `NO_ANOMALY_OBSERVED` does not mean a network is certified safe. See [classifier limitations](docs/ROGUE-CELL-CLASSIFICATION.md).

## Evidence and JSONL integrity

The private event database encrypts event payloads with Android Keystore AES-GCM. Structured JSONL exports contain one UTF-8 event per line, including an initiative header, developer seal, schema candidate identifier, UUID, UTC timestamp, `kind`, `facts`, and event-level `record_sha256`. The final line is an `EXPORT_MANIFEST` with the digest of preceding event lines. The UI reports a separate SHA-256 covering the *entire* file, manifest included.

The app's hashes detect accidental or untrusted byte changes under the chosen serialization scheme; **SHA-256 does not prove authorship, independent custody, a trustworthy device clock, or radio authenticity**. Real exported records may reveal precise geolocation and movement. Keep raw exports private; never commit them to this public repo. The schema is specified in [docs/JSONL-CONTRACT.md](docs/JSONL-CONTRACT.md).

To inspect a copy privately (no event values printed):

```bash
# BEACON COMPUTING INITIATIVE
# Developer Seal: CJB-DEV-2026
# Audit IF AND ONLY IF the supplied JSONL file exists and contains valid JSON objects.
(
  set -e
  file="$HOME/Downloads/SATELLITE1-YYYY-MM-DD.jsonl"  # change to your actual filename
  test -s "$file"
  python3 - "$file" <<'PYJSON'
# BEACON COMPUTING INITIATIVE
# Developer Seal: CJB-DEV-2026
import collections, hashlib, json, pathlib, sys
p=pathlib.Path(sys.argv[1]); counts=collections.Counter(); n=0
with p.open(encoding='utf-8') as f:
    for line in f:
        if not line.strip(): continue
        item=json.loads(line)
        if not isinstance(item,dict): raise ValueError('non-object JSONL record')
        counts[item.get('kind','UNKNOWN')]+=1; n+=1
print('RECORDS',n)
for k,v in sorted(counts.items()): print(k,v)
print('SHA256',hashlib.sha256(p.read_bytes()).hexdigest())
PYJSON
)
```

## ORBIT11 optional authenticated intake

The `orbit11-intake/` directory contains a **candidate** standalone HTTPS receiver, **not** a verified interface in an existing ORBIT11 production deployment. It accepts enrolled JSONL batches at `POST /v1/telemetry/batches`, checks a bearer token and batch/event SHA-256, enforces payload and record limits, deduplicates event IDs, and returns durable receipts after committing encrypted records to a local SQLite quarantine database. `cryptography` is the only third-party Python requirement for that server; use a reviewed installation source.

On a trusted ORBIT11 host, follow [orbit11-intake/README.md](orbit11-intake/README.md). Provision server-side state privately (including `fernet.key` and token hash), configure a valid HTTPS certificate and a private reachable DNS endpoint (for example via an authorized tailnet), then enter the endpoint and enrollment token in the SATELLITE1 UI. **Never commit the token, TLS key, Fernet key or precise telemetry.** Do not expose the listener publicly without an explicit operational security review.

The uploader marks queued records acknowledged **if and only if** the authenticated HTTPS response matches `batch_sha256`, `accepted_count`, and a nonempty `receipt_id`. Failed/missing acknowledgments retain the local queue. The quarantine receiver does **not** grant model-training admission, verified chain of custody, anomaly ground truth, or permission to perform containment.

**ORBIT11 intake status: candidate code tested locally; live Samsung-to-ORBIT11 acceptance, certificate rotation, device revocation, retention and independent evidence review are pending.** See [API contract](docs/ORBIT11-API-CONTRACT.md).

## Tests and release qualification

| Gate | Status in this source publication |
| --- | --- |
| SHA-256 source manifest | Included; run `sha256sum -c SHA256SUMS` |
| Python source/scaffold regression tests | Included; run `python3 -m unittest discover -s tools -p 'test_*.py' -v` |
| Kotlin pure-rule smoke tests | Included for cell changes and rogue-cell screening; require a local Kotlin JVM compiler |
| ORBIT11 intake unit tests | Included; require Python `cryptography` |
| Android v0.1.5 `assembleRelease` | **Not verified for corrected source in this environment** |
| Existing BCI key signing and in-place Samsung upgrade | **Not verified for v0.1.5** |
| Classifier false-positive / true-positive performance against independent labeled RF data | **Not established** |
| Live HTTPS API integration, enrollment and receipt verification | **Not established** |
| Production deployment or autonomous remediation | **HOLD** |

For project-level QA and release checklist see [docs/SECURITY-QUALIFICATION.md](docs/SECURITY-QUALIFICATION.md) and [docs/RELEASE-NOTES.md](docs/RELEASE-NOTES.md). GitHub Actions runs *source and receiver tests only*; a green badge must **never** be interpreted as a signed Android release, validated cellular-security claim or trained ORBIT11 model.

## BCI identity, appearance and principles

SATELLITE1 is part of **Beacon Computing Initiative**, with the developer seal `CJB-DEV-2026`. The Android dashboard and adaptive launcher icon use the BCI palette: Obsidian `#05080D`, Maroon `#650D28`, Graphite `#111820`, Cyan `#00E5FF`, White `#F4F8FB`. Intended type roles are **Oxanium** (headings), **Rajdhani** (UI), and **JetBrains Mono** (logs); the open source tree uses system fallbacks rather than bundling third-party font binaries.

BCI engineering principles: **Observe accurately · Preserve faithfully · Analyze transparently · Communicate securely · Act deliberately.**

## Responsible use, privacy, contribution and license

This software is for devices you own or administer and networks you are explicitly authorized to assess. Do not use it for covert monitoring, unauthorized network scanning or to claim that an identified person, carrier, or physical cell site is malicious. Use notification-visible controls and meaningful consent. Retain and delete local data under an approved data-retention policy; application backup is disabled.

See [SECURITY.md](SECURITY.md) for disclosure and privacy handling and [CONTRIBUTING.md](CONTRIBUTING.md) for contributions. **No software license has yet been selected**; public source availability alone does not authorize unrestricted reuse, redistribution or commercial deployment. Repository maintainers should choose and commit an appropriate LICENSE before inviting broad redistribution.

**Published source integrity:** Verify the tracked files against [`SHA256SUMS`](SHA256SUMS). The manifest excludes itself to avoid a recursive digest and does not serve as a developer code signature. A separate signed release manifest is required before production distribution.
