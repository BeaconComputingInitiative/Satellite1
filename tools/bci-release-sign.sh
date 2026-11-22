#!/usr/bin/env bash
# BEACON COMPUTING INITIATIVE
# Developer Seal: CJB-DEV-2026
# SATELLITE1 release APK signing procedure
# Sign IF AND ONLY IF source, toolchain, release build, alignment, and signature verification pass.
set -euo pipefail
umask 077

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)"
KEY_DIR="${XDG_DATA_HOME:-$HOME/.local/share}/bci/keys"
KEY_FILE="$KEY_DIR/satellite1-release.p12"
KEY_ALIAS='satellite1-release'
REPORT_DIR="$ROOT/release-evidence"

hold() { printf 'HOLD: %s\n' "$*" >&2; exit 1; }
[[ -f "$ROOT/SHA256SUMS" ]] || hold 'Source manifest missing'
(cd "$ROOT" && sha256sum --check --strict SHA256SUMS) || hold 'Source SHA-256 verification failed'
command -v keytool >/dev/null 2>&1 || hold 'JDK keytool not found'
command -v java >/dev/null 2>&1 || hold 'Java not found'

if [[ "${1:-}" == '--init-key' ]]; then
  mkdir -p -- "$KEY_DIR"
  chmod 700 -- "$KEY_DIR"
  [[ ! -e "$KEY_FILE" ]] || hold 'Signing key exists: do not replace it; preserve it for future updates'
  printf '%s\n' '=== BEACON COMPUTING INITIATIVE ===' 'Developer Seal: CJB-DEV-2026'
  printf 'Creating private SATELLITE1 release key at %s\n' "$KEY_FILE"
  STAGING="$(mktemp -d "$KEY_DIR/.satellite1-key.XXXXXXXX")"
  trap 'rm -f -- "$STAGING/candidate.p12"; rmdir -- "$STAGING" 2>/dev/null || true' EXIT
  if ! keytool -genkeypair -v -storetype PKCS12 -keystore "$STAGING/candidate.p12" \
    -alias "$KEY_ALIAS" -keyalg RSA -keysize 3072 -sigalg SHA256withRSA \
    -validity 10000 -dname 'CN=SATELLITE1, OU=Beacon Computing Initiative, O=Beacon Computing Initiative, C=US'; then
    hold 'Key creation failed; no release identity installed'
  fi
  [[ -s "$STAGING/candidate.p12" ]] || hold 'Keytool produced no keystore'
  chmod 600 -- "$STAGING/candidate.p12"
  # Hard-link is an atomic no-clobber install on the same filesystem.
  ln -- "$STAGING/candidate.p12" "$KEY_FILE" || hold 'Signing identity already exists; preserving it'
  printf '%s\n' 'KEY_CREATED: YES. Back up this keystore privately, outside the project.'
  exit 0
fi
[[ $# -eq 0 ]] || hold 'Usage: bash tools/bci-release-sign.sh [--init-key]'
[[ -s "$KEY_FILE" ]] || hold 'Release key missing: run bash tools/bci-release-sign.sh --init-key first'
[[ ! -L "$KEY_FILE" ]] || hold 'Signing key cannot be a symlink'

if [[ -n "${ANDROID_HOME:-}" && -d "${ANDROID_HOME}/build-tools" ]]; then
  SDK="$ANDROID_HOME"
elif [[ -d "$HOME/Android/Sdk/build-tools" ]]; then
  SDK="$HOME/Android/Sdk"
else
  hold 'Android SDK Build Tools missing; set ANDROID_HOME'
fi
mapfile -t VERSIONS < <(find "$SDK/build-tools" -mindepth 1 -maxdepth 1 -type d -printf '%f\n' | sort -V -r)
[[ ${#VERSIONS[@]} -gt 0 ]] || hold 'No Android SDK Build Tools installed'
BUILD_TOOLS="$SDK/build-tools/${VERSIONS[0]}"
ZIPALIGN="$BUILD_TOOLS/zipalign"
APKSIGNER="$BUILD_TOOLS/apksigner"
[[ -x "$ZIPALIGN" && -x "$APKSIGNER" ]] || hold 'zipalign/apksigner missing'

if [[ -n "${GRADLE_BIN:-}" ]]; then
  GRADLE="$GRADLE_BIN"
elif [[ -x "$HOME/.local/opt/bci/gradle-8.10.2/bin/gradle" ]]; then
  GRADLE="$HOME/.local/opt/bci/gradle-8.10.2/bin/gradle"
else
  hold 'Verified Gradle 8.10.2 missing (run the prior SATELLITE1 Gradle repair)'
fi
[[ -x "$GRADLE" ]] || hold 'Gradle executable not found'
GRADLE_VERSION="$("$GRADLE" --version | sed -n 's/^Gradle //p' | head -n 1)"
[[ "$GRADLE_VERSION" == '8.10.2' ]] || hold "Expected verified Gradle 8.10.2; got $GRADLE_VERSION"

printf '%s\n' '=== BEACON COMPUTING INITIATIVE ===' 'Developer Seal: CJB-DEV-2026'
printf '\n=== BUILD UNSIGNED RELEASE APK ===\n'
export GRADLE_USER_HOME="${XDG_CACHE_HOME:-$HOME/.cache}/bci/satellite1-build/gradle-user-home"
(cd "$ROOT" && "$GRADLE" --no-daemon --console=plain :app:assembleRelease) || hold 'Gradle release build failed'
UNSIGNED="$ROOT/app/build/outputs/apk/release/app-release-unsigned.apk"
[[ -s "$UNSIGNED" && ! -L "$UNSIGNED" ]] || hold 'Unsigned release APK not generated'

mkdir -p -- "$REPORT_DIR"
ALIGNED="$REPORT_DIR/SATELLITE1-v0.1.5-aligned-unsigned.apk"
SIGNED="$REPORT_DIR/SATELLITE1-v0.1.5-signed.apk"
[[ ! -e "$SIGNED" ]] || hold 'Signed output exists; move/archive it before generating a new release'
"$ZIPALIGN" -f -v 4 "$UNSIGNED" "$ALIGNED"
"$ZIPALIGN" -c -v 4 "$ALIGNED" || hold 'APK alignment verification failed'

printf '\n=== SIGN RELEASE APK (password prompts are local) ===\n'
[[ -r /dev/tty ]] || hold 'Interactive terminal required for private signing password'
"$APKSIGNER" sign --ks "$KEY_FILE" --ks-key-alias "$KEY_ALIAS" --out "$SIGNED" "$ALIGNED" </dev/tty || hold 'Release signing failed'
CERT_FILE="$REPORT_DIR/SATELLITE1-v0.1.5-signature-verification.txt"
{
  printf '%s\n' '# BEACON COMPUTING INITIATIVE' '# Developer Seal: CJB-DEV-2026' '# APK signature verification'
  "$APKSIGNER" verify --verbose --print-certs "$SIGNED"
} | tee "$CERT_FILE" || hold 'APK signature verification failed'
(cd "$REPORT_DIR" && sha256sum "$(basename "$SIGNED")" > "$(basename "$SIGNED").sha256")
(cd "$REPORT_DIR" && sha256sum "$(basename "$CERT_FILE")" > "$(basename "$CERT_FILE").sha256")
(cd "$REPORT_DIR" && sha256sum -c "$(basename "$SIGNED").sha256") || hold 'Signed APK SHA-256 verification failed'
printf '\nSIGNED_APK: %s\n' "$SIGNED"
printf 'SIGNED_APK_SHA256: '; sha256sum "$SIGNED" | awk '{print $1}'
printf '%s\n' 'SIGNATURE: VERIFIED' 'PRODUCTION: HOLD until functional/device/API qualification.'
