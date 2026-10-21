#!/usr/bin/env bash
# Publishes a new version: tags the current commit "v<versionName>" and pushes it. GitHub Actions
# (.github/workflows/release.yml) then builds the signed APK and creates the release that installed apps update from.
#
# Before running: bump versionCode and versionName in app/build.gradle.kts and commit everything.
# Usage: scripts/publish-release.sh "What's new in this version"
set -euo pipefail
cd "$(dirname "$0")/.."

version=$(sed -n 's/^ *versionName = "\(.*\)"$/\1/p' app/build.gradle.kts)
[ -n "$version" ] || { echo "Couldn't read versionName from app/build.gradle.kts." >&2; exit 1; }
[ -z "$(git status --porcelain)" ] || { echo "Commit your changes first." >&2; exit 1; }
if git rev-parse -q --verify "refs/tags/v$version" >/dev/null || [ -n "$(git ls-remote --tags origin "v$version")" ]; then
    echo "Tag v$version already exists; bump versionName (and versionCode) first." >&2
    exit 1
fi

git push origin HEAD
git tag -a "v$version" -m "${1:-LynerrrFood $version}"
git push origin "v$version"
echo "Tagged v$version. Follow the build with: gh run watch"
