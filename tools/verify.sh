#!/usr/bin/env bash
# BEACON COMPUTING INITIATIVE
# Developer Seal: CJB-DEV-2026
# Validate IF AND ONLY IF SHA256SUMS match prior to any local build.
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."
sha256sum -c SHA256SUMS
python3 -m unittest discover -s tools -p 'test_*.py' -v
if command -v gradle >/dev/null 2>&1; then
  gradle --no-daemon :app:assembleDebug
else
  echo 'ANDROID_BUILD: NOT_RUN (Gradle unavailable; import into Android Studio)'
fi
