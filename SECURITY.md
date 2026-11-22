# BEACON COMPUTING INITIATIVE
**Developer Seal:** `CJB-DEV-2026`

# SATELLITE1 Security and Privacy

**Security status:** Pre-release research candidate. No independent verification of cell-site authenticity, release-signing on the maintainer's workstation, production threat model, server deployment, secure updates, or operational readiness has been established.

- **Consent and scope.** Monitor only with user permission and a visible foreground-service notification. Limit active gateway checks to explicitly approved Wi-Fi and separately approved probing. An SSID/BSSID match is not proof of ownership.
- **Sensitive observations.** Wi-Fi BSSIDs, serving-cell IDs, subscription context, timestamps and GPS coordinates can reveal location and activity. Do not upload raw device exports, screenshots of identifiers, or credentials to GitHub issues or public test suites.
- **Cryptographic materials.** Never commit PKCS#12 release keystores, their passwords, Android app data, API bearer tokens, TLS keys, intake Fernet keys, or decrypted quarantine databases. A hash of a file is not the private key and is not proof of signer identity.
- **Trusted boundaries.** Android device → encrypted local queue → TLS-authenticated ORBIT11 quarantine. A receipt confirms transport acceptance according to the candidate contract, **not** independent radio evidence or training eligibility.
- **Research classification.** `SUSPECTED_ROGUE_CELL` is an uncalibrated multi-indicator result. No observed cellular change alone proves a false tower, malicious operator or interception. Preserve underlying observations and competing explanations.
- **Reporting.** Report security defects privately to the repository maintainer using a non-public disclosure channel available in the repository/account. Do not include sensitive telemetry in public issues. Maintain a minimal reproduction using synthetic values.
- **Retention.** A fleet-wide deletion, revocation, retention and forensic hold policy is not yet implemented. Operators must review their own responsibilities before continuous deployment.

**Engineering release gate:** Distribute broadly IF AND ONLY IF device testing, privacy review, credential storage, HTTPS validation, independent assessment, rollback and key continuity have been qualified.
