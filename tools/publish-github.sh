#!/usr/bin/env bash
# BEACON COMPUTING INITIATIVE
# Developer Seal: CJB-DEV-2026
# Publish public source IF AND ONLY IF manifest, allowlist, remote and user auth gates pass.
# Does not publish signing keys, logs, JSONL exports, private device measurements or APKs.
set -euo pipefail
umask 077
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)"
REPO='https://github.com/BeaconComputingInitiative/Satellite1.git'
MODE="${1:---check}"
[[ "$MODE" == '--check' || "$MODE" == '--push' ]] || { echo 'USAGE: bash tools/publish-github.sh [--check|--push]'; exit 2; }
hold(){ printf 'HOLD: %s\n' "$*" >&2; exit 1; }
command -v git >/dev/null || hold 'git is required'
command -v python3 >/dev/null || hold 'python3 is required'
(cd "$ROOT" && sha256sum -c SHA256SUMS >/dev/null) || hold 'Project manifest invalid'
python3 - "$ROOT" <<'PY'
# BEACON COMPUTING INITIATIVE
# Developer Seal: CJB-DEV-2026
# Allow publishing IF AND ONLY IF the list is strictly reviewed and contains no private paths.
from pathlib import Path
import sys
root=Path(sys.argv[1]); files=[]
fixed={'README.md','SECURITY.md','CONTRIBUTING.md','SHA256SUMS','.gitignore',
       'build.gradle.kts','settings.gradle.kts','gradle.properties','app/build.gradle.kts','app/src/main/AndroidManifest.xml'}
roots={'app/src/main/java/org/beaconcomputing/satellite1':{'.kt'},
       'app/src/main/res':{'.xml','.png'},'docs':{'.md'},
       'orbit11-intake':{'.py','.md','.txt'},'tools':{'.py','.sh','.kt'},
       'assets':{'.txt'},'.github/workflows':{'.yml','.yaml'}}
for p in sorted(root.rglob('*')):
    if not p.is_file() or p.is_symlink(): continue
    rel=p.relative_to(root).as_posix()
    allowed=rel in fixed or any((rel.startswith(prefix+'/') and p.suffix in ext)
                                   for prefix,ext in roots.items())
    if not allowed: raise SystemExit('HOLD: Unexpected source path (not publishable): '+rel)
    if p.suffix.lower() in {'.p12','.jks','.keystore','.apk','.aab','.jsonl','.db','.sqlite','.sqlite3','.pem','.key','.log'}:
        raise SystemExit('HOLD: Forbidden public artifact: '+rel)
    files.append(rel)
if len(files)<50: raise SystemExit('HOLD: Source file count unexpectedly low')
print('PUBLICATION_PREFLIGHT: PASS')
print('FILES_REVIEWED:',len(files))
PY
if [[ "$MODE" == '--check' ]]; then
  echo 'PUBLICATION: DRY RUN ONLY'
  echo "REMOTE: $REPO"
  echo 'To publish after reviewing README and docs: bash tools/publish-github.sh --push'
  exit 0
fi
command -v git >/dev/null || hold 'git missing'
WORKBASE="${XDG_STATE_HOME:-$HOME/.local/state}/bci/satellite1-publish"
mkdir -p "$WORKBASE" && chmod 700 "$WORKBASE"
WORK="$(mktemp -d "$WORKBASE/commit.XXXXXXXX")"
echo "=== CLONE EXISTING PUBLIC REPOSITORY ==="
git clone --depth 1 --branch main -- "$REPO" "$WORK/repo" || hold 'Public repo clone failed'
[[ "$(git -C "$WORK/repo" remote get-url origin)" == "$REPO" ]] || hold 'Unexpected git remote'
# Copy only source allowlist; never recursively copy a user's home or local build folders.
python3 - "$ROOT" "$WORK/repo" <<'PY'
# BEACON COMPUTING INITIATIVE
# Developer Seal: CJB-DEV-2026
# Stage files IF AND ONLY IF paths remain beneath the reviewed source root.
from pathlib import Path
import shutil,sys
src=Path(sys.argv[1]).resolve(); out=Path(sys.argv[2]).resolve()
fixed={'README.md','SECURITY.md','CONTRIBUTING.md','SHA256SUMS','.gitignore',
       'build.gradle.kts','settings.gradle.kts','gradle.properties','app/build.gradle.kts','app/src/main/AndroidManifest.xml'}
roots={'app/src/main/java/org/beaconcomputing/satellite1':{'.kt'},
       'app/src/main/res':{'.xml','.png'},'docs':{'.md'},
       'orbit11-intake':{'.py','.md','.txt'},'tools':{'.py','.sh','.kt'},
       'assets':{'.txt'},'.github/workflows':{'.yml','.yaml'}}
paths=[]
for p in sorted(src.rglob('*')):
    if not p.is_file() or p.is_symlink(): continue
    rel=p.relative_to(src).as_posix()
    if rel in fixed or any(rel.startswith(prefix+'/') and p.suffix in extensions
                           for prefix,extensions in roots.items()):
        dest=out/rel; dest.parent.mkdir(parents=True,exist_ok=True)
        shutil.copyfile(p,dest); paths.append(rel)
(out/'BCI-PUBLIC-FILES.txt').write_text(
 '# BEACON COMPUTING INITIATIVE\n# Developer Seal: CJB-DEV-2026\n'+
 '\n'.join(paths)+'\n')
print('SOURCE_STAGED:',len(paths))
PY
cd "$WORK/repo"
git add -A -- README.md SECURITY.md CONTRIBUTING.md SHA256SUMS .gitignore \
  build.gradle.kts settings.gradle.kts gradle.properties app docs tools \
  orbit11-intake assets .github/workflows BCI-PUBLIC-FILES.txt
[[ -n "$(git status --porcelain)" ]] || { echo 'No changes to publish.'; exit 0; }
[[ -n "$(git config user.name || true)" && -n "$(git config user.email || true)" ]] || hold 'Configure git user.name and user.email locally first'
echo '=== REVIEW GIT CHANGES ==='
git diff --cached --stat
# Explicitly refuse force or missing credentials; git handles interactive auth securely.
git commit -m 'feat: SATELLITE1 v0.1.5 Orbital Mission Control, cellular analysis, ORBIT11 candidate intake'
git push origin main || hold 'Remote push failed; staged commit retained in private workdir'
sha="$(git rev-parse HEAD)"
printf '%s\n' '=== BEACON COMPUTING INITIATIVE ===' 'Developer Seal: CJB-DEV-2026'
printf 'GITHUB_COMMIT: %s\n' "$sha"
printf '%s\n' "REPOSITORY: https://github.com/BeaconComputingInitiative/Satellite1"
printf 'PUBLICATION: PASS IF AND ONLY IF GITHUB_COMMIT=%s appears on origin/main\n' "$sha"
