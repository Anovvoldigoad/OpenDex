#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

REPO_NAME="${1:-OpenDexLauncher}"
VISIBILITY="${2:-public}"

if ! command -v git >/dev/null 2>&1; then
  echo "git belum ada. Jalankan: pkg install git" >&2
  exit 2
fi
if ! command -v gh >/dev/null 2>&1; then
  echo "GitHub CLI belum ada. Jalankan: pkg install gh" >&2
  exit 2
fi
if ! gh auth status >/dev/null 2>&1; then
  echo "Login GitHub dulu dengan: gh auth login" >&2
  exit 3
fi

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
python3 scripts/preflight.py 2>/dev/null || true

if [ ! -d .git ]; then
  git init
fi
git add .
if ! git diff --cached --quiet; then
  git -c user.name="OpenDex Mobile" -c user.email="opendex-mobile@users.noreply.github.com" commit -m "OpenDex v0.3.2-alpha"
fi
git branch -M main

if gh repo view "$REPO_NAME" >/dev/null 2>&1; then
  echo "Repo $REPO_NAME sudah ada; memastikan remote origin..."
  URL="$(gh repo view "$REPO_NAME" --json url -q .url)"
  git remote remove origin >/dev/null 2>&1 || true
  git remote add origin "${URL}.git"
  git push -u origin main
else
  gh repo create "$REPO_NAME" "--$VISIBILITY" --source=. --remote=origin --push
fi

echo
echo "Selesai. Buka GitHub > $REPO_NAME > Actions > Build OpenDex APK > Run workflow."
