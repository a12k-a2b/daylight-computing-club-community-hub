#!/usr/bin/env bash
# Daylight Shade — Gradle-less build, same spirit as dc1-keys:
# plain aapt2 + javac + d8, signed with the club key.
#
# Needs: JDK 11+, zip, and two dirs from the Android SDK:
#   ANDROID_BUILD_TOOLS  → build-tools/33.x (aapt2, d8, zipalign, apksigner)
#   ANDROID_PLATFORM     → platforms/android-33 (android.jar)
# Grab them without Android Studio:
#   curl -O https://dl.google.com/android/repository/build-tools_r33.0.2-linux.zip
#   curl -O https://dl.google.com/android/repository/platform-33_r02.zip
set -euo pipefail
cd "$(dirname "$0")"

BT="${ANDROID_BUILD_TOOLS:?set ANDROID_BUILD_TOOLS to the build-tools dir}"
PLATFORM="${ANDROID_PLATFORM:?set ANDROID_PLATFORM to the dir containing android.jar}"
KS="${SHADE_KEYSTORE:-$(cd .. && pwd)/signing/dcc.keystore}"
JAR="$PLATFORM/android.jar"
# d8 from build-tools 33 crashes on classes emitted by JDK 17+; point D8 at a
# newer build-tools' d8 (r34+) if your JDK is modern. aapt2/zipalign/apksigner
# from r33 are fine either way.
D8="${ANDROID_D8:-$BT/d8}"

rm -rf build dist
mkdir -p build/gen build/classes build/dex dist

echo "— aapt2: compile + link resources"
"$BT/aapt2" compile --dir app/res -o build/res.zip
"$BT/aapt2" link -o build/unsigned.apk \
    -I "$JAR" \
    --manifest app/AndroidManifest.xml \
    --java build/gen \
    --min-sdk-version 33 --target-sdk-version 33 \
    build/res.zip

echo "— javac"
javac --release 11 -Xlint:-options,-deprecation \
    -classpath "$JAR" \
    -d build/classes \
    $(find app/src build/gen -name '*.java')

echo "— d8"
jar cf build/classes.jar -C build/classes .
"$D8" --release --lib "$JAR" --min-api 33 --output build/dex build/classes.jar

echo "— package + align + sign (club key)"
(cd build/dex && zip -qj ../unsigned.apk classes.dex)
"$BT/zipalign" -f 4 build/unsigned.apk build/aligned.apk
"$BT/apksigner" sign \
    --ks "$KS" --ks-pass pass:android --key-pass pass:android \
    --out dist/daylight-shade.apk build/aligned.apk
"$BT/apksigner" verify dist/daylight-shade.apk

echo "OK → $(du -h dist/daylight-shade.apk | cut -f1) dist/daylight-shade.apk"
