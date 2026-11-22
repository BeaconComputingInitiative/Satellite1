#!/usr/bin/env bash
# BEACON COMPUTING INITIATIVE
# Developer Seal: CJB-DEV-2026
# SATELLITE1 — read-only runtime status and SHA-256 diagnostic
# Diagnose IF AND ONLY IF one authorized USB-connected Android device exists.
set -euo pipefail
PKG='org.beaconcomputing.satellite1'
REPORT_DIR="${XDG_DATA_HOME:-$HOME/.local/share}/bci/reports/satellite1"
if [[ "$(adb -d get-state 2>/dev/null)" != 'device' ]]; then
  echo 'HOLD: exactly one authorized USB Android device required' >&2
  exit 1
fi
mkdir -p "$REPORT_DIR"
LOG="$REPORT_DIR/monitor-$(date -u +%Y%m%dT%H%M%SZ).log"
{
  printf '%s\n' '# BEACON COMPUTING INITIATIVE' '# Developer Seal: CJB-DEV-2026' '# SATELLITE1 service read-only status'
  printf '\n=== PACKAGE ===\n'
  adb -d shell dumpsys package "$PKG" | grep -E 'versionCode=|versionName=|targetSdk=' | head -n 6 || true
  printf '\n=== PROCESS ===\n'
  adb -d shell pidof "$PKG" || true
  printf '\n=== ACTIVE SERVICE RECORDS ===\n'
  adb -d shell dumpsys activity services | grep -F -C 5 "$PKG" | head -n 75 || true
  printf '\n=== APP DIAGNOSTICS ===\n'
  adb -d logcat -d -v threadtime -s 'SATELLITE1:I' '*:S' | tail -n 100 || true
} | tee "$LOG"
{
  printf '%s\n' '# BEACON COMPUTING INITIATIVE' '# Developer Seal: CJB-DEV-2026'
  sha256sum "$LOG"
} > "$LOG.sha256"
sha256sum -c "$LOG.sha256"
printf 'DIAGNOSTIC_LOG: %s\n' "$LOG"
