#!/usr/bin/env bash
# BEACON COMPUTING INITIATIVE
# Developer Seal: CJB-DEV-2026
# SATELLITE1 v0.1.5 — Existing-release-key Android upgrade
# Build/install IF AND ONLY IF source manifest, SDK, keystore, APK signature, and device gates pass.
set -euo pipefail
umask 077

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)"
MODE="${1:---build-only}"
[[ "$MODE" == '--build-only' || "$MODE" == '--install' ]] || { echo 'USAGE: bash tools/build-and-deploy.sh [--build-only|--install]'; exit 2; }
hold(){ printf 'HOLD: %s\n' "$*" >&2; exit 1; }
(cd "$ROOT" && sha256sum --check --strict SHA256SUMS) || hold 'Unverified project source'
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"
[[ -s "$ANDROID_HOME/platforms/android-35/android.jar" ]] || hold 'Android SDK 35 missing'
[[ -d "$ANDROID_HOME/build-tools" ]] || hold 'Android build-tools missing'
KEY="${XDG_DATA_HOME:-$HOME/.local/share}/bci/keys/satellite1-release.p12"
[[ -s "$KEY" && ! -L "$KEY" ]] || hold 'Existing SATELLITE1 release key missing; do not generate a replacement identity'

# Local Android SDK configuration only. Keep this machine-specific file out of SHA256SUMS.
python3 - "$ROOT" "$ANDROID_HOME" <<'PYSDK'
# BEACON COMPUTING INITIATIVE
# Developer Seal: CJB-DEV-2026
from pathlib import Path
import sys, os
root = Path(sys.argv[1]); sdk = Path(sys.argv[2]).resolve(strict=True)
p = root/'local.properties'
if p.is_symlink(): raise SystemExit('HOLD: local.properties is symlink')
lines = p.read_text().splitlines() if p.exists() else []
lines = [line for line in lines if not line.strip().startswith('sdk.dir=')]
lines.append('sdk.dir='+str(sdk))
tmp = root/'local.properties.bci-new'
if tmp.exists() or tmp.is_symlink(): raise SystemExit('HOLD: temporary SDK path occupied')
tmp.write_text('\n'.join(lines)+'\n')
os.chmod(tmp,0o600)
os.replace(tmp,p)
PYSDK

APK="$ROOT/release-evidence/SATELLITE1-v0.1.5-signed.apk"
SDK_BT="$(find "$ANDROID_HOME/build-tools" -mindepth 1 -maxdepth 1 -type d -printf '%f\n' | sort -V | tail -n 1)"
SIGNER="$ANDROID_HOME/build-tools/$SDK_BT/apksigner"
[[ -x "$SIGNER" ]] || hold 'apksigner missing'
if [[ ! -s "$APK" ]]; then
  export GRADLE_BIN="${GRADLE_BIN:-$HOME/.local/opt/bci/gradle-8.10.2/bin/gradle}"
  [[ -x "$GRADLE_BIN" ]] || hold 'Verified Gradle 8.10.2 missing'
  bash "$ROOT/tools/bci-release-sign.sh" || hold 'Unsigned build or signing failed'
else
  echo 'SIGNED_APK: existing artifact detected; verifying before reuse'
fi
[[ -s "$APK" && -s "$APK.sha256" ]] || hold 'Release APK or SHA-256 file missing'
(cd "$(dirname "$APK")" && sha256sum -c "$(basename "$APK").sha256") || hold 'Release APK digest mismatch'
"$SIGNER" verify --verbose --print-certs "$APK" || hold 'APK signature failed'
sha256sum "$APK"
if [[ "$MODE" == '--install' ]]; then
  command -v adb >/dev/null || hold 'ADB missing'
  adb devices -l
  [[ "$(adb -d get-state 2>/dev/null)" == 'device' ]] || hold 'Exactly one authorized USB Android device required'
  adb -d install -r "$APK" || hold 'Upgrade failed; existing application not deliberately removed'
  adb -d shell pm path org.beaconcomputing.satellite1 || hold 'Application not installed'
  echo 'SATELLITE1_UPDATE: PASS'
else
  echo 'SATELLITE1_SIGNED_BUILD: PASS (not installed)'
fi
