#!/bin/sh
# Plain-tools build: aapt2 + javac + d8, no Gradle.
#   ANDROID_SDK  — SDK root (default /opt/android-sdk)
#   DCC_KEYSTORE — club keystore (default ../../signing/dcc.keystore)
set -e
cd "$(dirname "$0")"

SDK="${ANDROID_SDK:-/opt/android-sdk}"
BT="$SDK/build-tools/35.0.0"
PLAT="$SDK/platforms/android-33/android.jar"
KS="${DCC_KEYSTORE:-../../signing/dcc.keystore}"

rm -rf build
mkdir -p build/gen build/obj

"$BT/aapt2" compile --dir res -o build/res.zip
"$BT/aapt2" link -o build/app.unsigned.apk -I "$PLAT" \
    --manifest AndroidManifest.xml --java build/gen build/res.zip

javac --release 11 -classpath "$PLAT" -d build/obj \
    $(find src build/gen -name '*.java')

jar cf build/classes.jar -C build/obj .
"$BT/d8" --release --lib "$PLAT" --min-api 30 --output build build/classes.jar

cd build && zip -q -j app.unsigned.apk classes.dex && cd ..

"$BT/zipalign" -f 4 build/app.unsigned.apk build/app.aligned.apk
"$BT/apksigner" sign --ks "$KS" --ks-pass pass:android --key-pass pass:android \
    --out build/tracing-paper.apk build/app.aligned.apk
"$BT/apksigner" verify build/tracing-paper.apk

echo "built: build/tracing-paper.apk"
