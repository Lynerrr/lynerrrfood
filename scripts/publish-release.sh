#!/usr/bin/env bash
# Builds the release APK and publishes it as a GitHub release; installed apps offer it as an update on next start.
#
# Before running: bump versionCode and versionName in app/build.gradle.kts (the tag is "v<versionName>"),
# set updatesRepo=<owner>/<repo> in gradle.properties, and log in once with `gh auth login`.
# Usage: scripts/publish-release.sh ["What's new in this version"]
set -euo pipefail
cd "$(dirname "$0")/.."

repo=$(sed -n 's/^updatesRepo=//p' gradle.properties)
[ -n "$repo" ] || { echo "Set updatesRepo=<owner>/<repo> in gradle.properties first." >&2; exit 1; }
version=$(sed -n 's/^ *versionName = "\(.*\)"$/\1/p' app/build.gradle.kts)
[ -n "$version" ] || { echo "Couldn't read versionName from app/build.gradle.kts." >&2; exit 1; }
if gh release view "v$version" --repo "$repo" >/dev/null 2>&1; then
    echo "Release v$version already exists; bump versionName (and versionCode) first." >&2
    exit 1
fi

./gradlew :app:assembleRelease
apk="LynerrrFood-$version.apk"
cp app/build/outputs/apk/release/app-release.apk "$apk"
gh release create "v$version" "$apk" --repo "$repo" --title "LynerrrFood $version" --notes "${1:-}"
echo "Published v$version to https://github.com/$repo/releases"
