# Thin WebView shell — bundle a web app as a real DC-1 APK, no Gradle

A complete, dependency-free wrapper: one manifest, one Java file, one build
script. Builds with `aapt2` + `javac` + `d8` + `apksigner` — the same
toolchain the club's dc1-keys uses. The result: your web app installed like
a native app, working offline forever, versioned and updatable through the
club shelf.

```
webview-shell/
  AndroidManifest.xml               ← rename the package + label
  src/club/daylight/shell/MainActivity.java
  build.sh                          ← point at your SDK + the club keystore
  res/drawable/icon.png             ← you supply: 192×192+, bold, grayscale-legible
  assets/                           ← your entire web app; index.html at the root
  assets-extras/daylight-voice.js   ← copy into assets/ if the app uses voice
```

## Use it

1. Copy this directory into the app's repo (e.g. as `android/`).
2. `AndroidManifest.xml`: set `package="club.daylight.<app-id>"` and
   `android:label`. **The package name and signing key can never change
   after first shelving** — updates depend on both matching.
3. Drop the built/static web app into `assets/` (everything relative,
   `index.html` at the root). Then check it's honestly offline:
   `grep -rl "https://" assets/` — bundle or inline every CDN font, script,
   and stylesheet you find.
4. Add `res/drawable/icon.png` — bold silhouette that reads in grayscale.
5. `./build.sh` → `build/app.apk`, already signed if you pointed `KEYSTORE`
   at the club's `signing/dcc.keystore`.
6. Each release: bump `versionCode` (integer, always +1) and `versionName`
   (human string) in the manifest.

Requirements: Java 11+, Android command-line tools with `build-tools;33.0.2`
(or newer) and `platforms;android-33` installed
(`sdkmanager "build-tools;33.0.2" "platforms;android-33"`).

## What the shell already handles (the jank you'd otherwise ship)

- **Assets over a private `https://` origin** (`https://dish.local/`),
  intercepted in-process — never `file://`, where ES modules, `fetch`, and
  cookies quietly break. Secure context, so localStorage/IndexedDB work.
- **`textZoom` pinned to 100** — otherwise Android's system font-size
  setting multiplies your CSS font sizes and shatters the layout.
- **Back button** = history back, exit only from the root.
- **Rotation** without losing state (`configChanges` + WebView state save).
- **External links** open in Chrome instead of trapping the user.
- **`<input type=file>`** opens the system picker (a bare WebView ignores it).
- **Downloads/exports**: `data:` URLs and `http(s)` downloads land as real
  files in Downloads (a bare WebView drops them on the floor).
- No zoom controls, no autoplay gesture requirement, white background
  (no black flash on launch).

## Voice

`SpeechRecognition` does not exist in WebView, so the shell ships a bridge:
a `DaylightVoice` JavaScript interface that opens the system speech dialog
(`RecognizerIntent` — it owns the mic permission, so the APK needs none).
Copy `assets-extras/daylight-voice.js` into your `assets/`, include it, and
call one API that works in both Chrome and the shell:

```js
if (daylightVoice.available()) {
  const text = await daylightVoice.listen({ lang: 'en-US' });
}
```

Keyboard-mic dictation into text fields works everywhere with zero code.

## Known limits (design around or extend)
- **`blob:` downloads** need a bridge: in JS, fetch the blob, base64 it, and
  call a `@JavascriptInterface` that writes via MediaStore — mirror the
  `data:` branch in `MainActivity.saveDataUrl`. Only needed if your app
  generates files with `URL.createObjectURL`.
- No service worker, no Background Sync, no push — bundled assets make the
  first irrelevant; design sync around `online` events.
- If the dish needs zero network, leave the `INTERNET` permission commented
  out in the manifest — then it *provably* can't phone home, which is worth
  bragging about in the app's club card.

## Before shelving

Run the sibling **daylight-preview** skill on the app, and smoke-test the
APK on an Android 13 emulator (1600×1200, 190 dpi) or a real DC-1: launch,
rotate, back-button from deep, airplane-mode relaunch, one file in, one file
out. This template is young — if you hit a build or runtime wart, fix it and
send the fix back to the club repo so every future dish benefits.
