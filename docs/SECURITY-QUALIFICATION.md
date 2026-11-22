# BEACON COMPUTING INITIATIVE
**Developer Seal:** `CJB-DEV-2026`

# Research Build: Security, Privacy, and Release Gates

**NOT RELEASE READY.** Only source structure and local static assertions are presently tested.

- Collection consent: foreground service starts by user action; location and cell switches independently controlled; unknown Wi-Fi fails closed; optional active probes need a separate explicit authorization dialog; Stop is always available in notification and dashboard.
- Data minimization: no IMEI, IMSI, phone numbers, SMS, microphone, camera, media or cross-app content. Geolocation and cell identifiers are sensitive and should be retained only for an approved purpose.
- Storage: app-private SQLite ciphertext and AndroidKeyStore AES-GCM; app backup off. Deletion/retention, secure wiping and legal holds need explicit specification before deployment. SQLite metadata including event timestamps and type remain unencrypted and should be treated as sensitive.
- Transport: HTTPS only with normal TLS verification; user-configured ORBIT11 endpoint and credential; no trust-all certificate handlers. The API contract is proposed, not verified.
- Active network checks: on a reviewed SSID+BSSID and only the current gateway, TCP ports 22/80/443, at most 30-min intervals; no host discovery sweeps or exploit testing. No root privileges, packet sniffing or VPN takeover.
- Android permissions: fine/coarse location for SSID/cell fixes; optional phone state for cell; foreground service location; notification; network state and Wi-Fi state. New platform permission rules require target-SDK review.
- Fail-closed requirements: revoke authorization on network change, changed source hash, denied permission, auth loss, unknown local network, low battery, or invalid receipt. Avoid silently resuming collection after a reboot or process kill.
- Tests needed: actual Android Studio Gradle compile; emulator and physical device permission matrix; 2G/3G/4G/5G multi-SIM; screen-off/OEM battery management; Wi-Fi roaming and BSSID spoofing; TLS pin/enrollment rotation; export validation; queue replay and crash recovery; stress/resource and battery cost; Android 17 local-network permissions.
- ORBIT11 admission and training remain **HOLD** until verified source ownership, custody, labels, masks, independent partitions and evaluated performance.
