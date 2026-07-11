#!/usr/bin/env bash
# Build + sign the WebView shell APK with no Gradle — same toolchain as the
# club's dc1-keys. Needs: Java 11+, Android build-tools;33.0.2+ and
# platforms;android-33 (install once with:
#   sdkmanager "build-tools;33.0.2" "platforms;android-33").
set -euo pipefail
cd "$(dirname "$0")"

SDK="${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}"
BT="$SDK/build-tools/$(ls "$SDK/build-tools" | sort -V | tail -1)"
PLATFORM="$SDK/platforms/android-33/android.jar"
# Point at the club keystore (repo: signing/dcc.keystore) or your own.
KEYSTORE="${KEYSTORE:?set KEYSTORE=/path/to/dcc.keystore}"

OUT=build
rm -rf "$OUT"; mkdir -p "$OUT/classes"

# 1. compile resources (the launcher icon)
"$BT/aapt2" compile --dir res -o "$OUT/res.zip"

# 2. link manifest + resources + bundled web app (assets/) into a base APK
"$BT/aapt2" link -o "$OUT/unsigned.apk" -I "$PLATFORM" \
    --manifest AndroidManifest.xml -A assets "$OUT/res.zip"

# 3. compile the shell and dex it
javac --release 11 -classpath "$PLATFORM" -d "$OUT/classes" \
    src/club/daylight/shell/MainActivity.java
"$BT/d8" --release --lib "$PLATFORM" --output "$OUT" \
    "$OUT"/classes/club/daylight/shell/*.class

# 4. package, align, sign
(cd "$OUT" && zip -q unsigned.apk classes.dex)
"$BT/zipalign" -f 4 "$OUT/unsigned.apk" "$OUT/aligned.apk"
"$BT/apksigner" sign --ks "$KEYSTORE" --ks-pass pass:android --key-pass pass:android \
    --out "$OUT/app.apk" "$OUT/aligned.apk"

rm -f "$OUT/unsigned.apk" "$OUT/aligned.apk"
echo "signed APK → $OUT/app.apk"
