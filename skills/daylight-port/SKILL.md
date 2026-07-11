---
name: daylight-port
description: Take a web app (usually vibe-coded, browser-first) and make it run properly on the Daylight DC-1 tablet — choose the right container (PWA, thin WebView-shell APK, or native), fix every web↔Android collision (touch, selection, keyboard, offline, back button), and package it ready to shelve in the Daylight Computer Club. Use when someone says "make this work on my Daylight / DC-1", "turn my web app into an Android app", "wrap this as an APK", "make this installable on the tablet", or "daylight-ify this" (packaging half).
---

# daylight-port — from "runs in a browser tab" to "belongs on the DC-1"

The path of least resistance for an AI is a web app. The DC-1 is an Android
13 tablet (Sol:OS). This skill closes that gap **without rewriting the app**:
pick the right container, then fix the seams where web and Android collide.

Two sibling skills finish the job: **daylight-design** (redesign for the
grayscale reflective screen, tablet layout, voice) and **daylight-preview**
(simulate + audit the result). Port first, then design, previewing throughout.

## The device, in one table

| Fact | Consequence for porting |
|---|---|
| Android 13 (Sol:OS), Chrome + WebView available | PWAs and WebView shells both work; modern web APIs OK |
| 10.5″ 4:3, 1600×1200, ~190 PPI | CSS viewport ≈ 1280×960 landscape @ DPR 1.25 (measure!) |
| MediaTek Helio G99, 8 GB RAM | midrange: small bundles, no re-render storms |
| Wi-Fi only, no cellular; used outdoors, on walks | **offline-first is a requirement, not a feature** |
| No camera; mic + speakers; Wacom EMR stylus | no QR/camera flows; voice input is first-class |
| Grayscale reflective screen | handled by daylight-design — but ship grayscale-safe icons here |

## Step 0 — understand the dish

Read the app and answer three questions:

1. **Does it need device powers the web can't grant?** (accessibility
   services, overlays, volume keys, background services, broad file access)
2. **Is it self-contained?** (static assets, no server, or server optional)
3. **Is it inseparable from a live server?** (auth, shared state, APIs)

## The container rule — the container follows what the dish needs

This is settled club policy (MECHANICS.md): do NOT convert everything to
APKs, and do NOT leave everything as bookmarks.

- **Device powers needed → native APK.** The app already had to be native;
  Kotlin/Java with the club's no-Gradle toolchain if feasible. Never rewrite
  a working web app into Kotlin just to "make it an app" — that trade is all
  cost, no benefit. Rewrite only when the web platform genuinely can't do
  the job.
- **Self-contained applet (games, tools, most vibe-coded things) → thin
  WebView shell APK** with the assets bundled inside. It works offline
  forever, carries a version, updates through the shelf, and the Club
  Companion can manage it. Full recipe + template:
  [references/webview-shell/](references/webview-shell/README.md)
- **Live server-backed app → PWA.** Keep the browser sandbox (zero collision
  surface with other dishes), instant updates, three-tap pinning. Ship a real
  manifest + service worker so it opens fullscreen and survives a dead Wi-Fi
  moment. Checklist below.

When in between (server-backed but mostly static): prefer PWA. A Trusted Web
Activity is possible but rarely worth it here — it needs a stable HTTPS
origin plus `assetlinks.json`, and buys little over a pinned PWA on this
device.

## Path: PWA checklist

1. `manifest.webmanifest`: `name`, `short_name` (≤12 chars), `start_url`,
   `display: "standalone"` (or `fullscreen` for immersive tools),
   `background_color`/`theme_color` in **grayscale**, icons 192 + 512 +
   `purpose: "maskable"` — and design icons to read in grayscale: bold
   silhouette, no color-coded meaning, no fine gradients.
2. Service worker, cache-first for the app shell, versioned cache name,
   `navigator.storage.persist()` if the app keeps user data (IndexedDB /
   localStorage). Test: airplane-mode the tablet, launch from home screen —
   it must open and show data, even if sync waits.
3. Fix the collisions that apply in Chrome-standalone (viewport, touch,
   overscroll, keyboard, back/history discipline):
   [references/collisions.md](references/collisions.md)
4. Shelve as `type: "pwa"` with the app's URL (see "Shelving" below).

## Path: WebView shell checklist

1. Copy [references/webview-shell/](references/webview-shell/README.md)
   (AndroidManifest + a single MainActivity.java + build.sh — no Gradle, no
   dependencies; builds with `aapt2` + `javac` + `d8` like the club's
   dc1-keys). Bundle the app into `assets/`.
2. The shell already handles the deadly WebView defaults: `textZoom` pinned
   to 100 (else Android's font-size setting silently multiplies your CSS and
   shatters layouts), assets served from a private `https://` origin (never
   `file://` — ES modules and fetch break), external links opened in Chrome,
   back button = history back, file pickers and downloads actually wired up
   (by default they do *nothing* — the classic "button is dead" jank).
3. Voice: the Web Speech API (`SpeechRecognition`) doesn't exist inside
   WebView, so the shell ships a `RecognizerIntent` bridge plus a JS shim
   (`assets-extras/daylight-voice.js`) giving apps **one voice API that
   works in both Chrome and the shell**. Copy the shim into `assets/` for
   any app with voice features; keyboard-mic dictation works everywhere
   regardless.
4. Fix the web↔Android collisions:
   [references/collisions.md](references/collisions.md)
5. Build, then sign with the club key and shelve as `type: "apk"`.

## Verify — don't declare victory from the desk

- Run **daylight-preview** (sibling skill) on every visual pass: grayscale
  simulation + audits for touch targets, effective contrast, tiny text,
  overflow, console errors.
- Run `scripts/collision-tests.mjs <url>` — the checklist below as an
  executable harness (rotation reflow, offline relaunch, back navigation,
  overscroll/touch CSS, input hygiene), honest about what still needs a
  real device.
- Exercise the seams by hand or with Playwright: rotate (state must survive),
  background-and-return, airplane mode relaunch, back button from three
  screens deep (must not exit), a file upload and a download/export if the
  app has them, long-press on text (selection should work in content, not in
  chrome), keyboard opening over the input it belongs to.
- If an emulator is handy: Android 13, 1600×1200 190 dpi profile. Real DC-1
  beats everything.

## Shelving it in the club

Follow the repo's CLAUDE.md ("share/shelve an app"): APKs get signed with the
club key (`signing/dcc.keystore`) and live at `site/apps/<id>/<id>.apk`; every
app gets an `apps.json` entry (id, name, tagline, author, type, version,
updated, description, source; `apk:{file,size,package}` or `url`), plus
`afterInstall` steps a non-technical friend can follow on the tablet.

Two rules of the house:

- **Stable identity**: pick the `applicationId` (suggest
  `club.daylight.<app-id>`) once and never change it — updates require the
  same package name and the same signing key.
- **Honesty / lineage**: a ported dish's card says it was daylight-ified
  from the friend's original ("daylight-ified from Maya's original") and
  links their source. The kitchen is a sous-chef, not a ghostwriter.
