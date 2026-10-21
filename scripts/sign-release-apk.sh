#!/usr/bin/env bash
# Signs the unsigned release APK with the release key plus the proof that the old debug key handed over to it
# (APK Signature Scheme v3 key rotation, signing/lineage.bin). Android 9+ then sees the release key and still
# accepts the APK as an update of builds signed with the debug key; older Android keeps seeing the debug key.
#
# Usage: scripts/sign-release-apk.sh <output.apk>   (after ./gradlew :app:assembleRelease)
# Keys: the old one at ~/.android/debug.keystore; the release one from lynerrrfoodReleaseKeystore and
# lynerrrfoodReleasePassword in ~/.gradle/gradle.properties or ORG_GRADLE_PROJECT_* variables (as Gradle reads them).
set -euo pipefail
cd "$(dirname "$0")/.."
out=${1:?usage: $0 <output.apk>}

prop() {
    local env="ORG_GRADLE_PROJECT_$1"
    if [ -n "${!env:-}" ]; then echo "${!env}"; else sed -n "s/^$1=//p" ~/.gradle/gradle.properties 2>/dev/null; fi
}
keystore=$(prop lynerrrfoodReleaseKeystore)
RELEASE_PASSWORD=$(prop lynerrrfoodReleasePassword)
export RELEASE_PASSWORD
[ -n "$keystore" ] && [ -n "$RELEASE_PASSWORD" ] || { echo "Release key not configured (see app/build.gradle.kts)." >&2; exit 1; }
[ -f ~/.android/debug.keystore ] || { echo "Old signing key ~/.android/debug.keystore is missing." >&2; exit 1; }

sdk=${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}}
tools="$(ls -d "$sdk"/build-tools/*/ | sort -V | tail -1)"
"$tools/zipalign" -f -p 4 app/build/outputs/apk/release/app-release-unsigned.apk "$out"
# --rotation-min-sdk-version 28: rotate for every Android version that supports it (the default is 33).
"$tools/apksigner" sign \
    --ks ~/.android/debug.keystore --ks-key-alias androiddebugkey --ks-pass pass:android --key-pass pass:android \
    --next-signer --ks "$keystore" --ks-key-alias lynerrrfood --ks-pass env:RELEASE_PASSWORD \
    --lineage signing/lineage.bin --rotation-min-sdk-version 28 \
    "$out"
"$tools/apksigner" lineage --in "$out" --print-certs 2>/dev/null | grep "DN:"
echo "Signed $out"
