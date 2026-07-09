# Daylight Computing Club — instructions for Claude sessions

This repo is a static app-sharing site ("the club") for Daylight DC-1
tablets. The site lives in `site/`, deploys to GitHub Pages on push to
`master`/`main`, and needs no build step. Full context: README.md, ROADMAP.md.

## When asked to "share/shelve/add an app to the club"

All of these phrasings (and anything similar) mean the same request:
"share it with the club", "share it with the DCC", "share it with
Daylight Computing Club" / "daylight computer club", "share it with the
computer club", "shelve it", "add it to the shelf/club/hub".

That means: add the app to the catalog so it appears on the shelf. Steps:

1. **APK apps**: build the APK from its source repo and sign it with the
   club key:
   ```sh
   apksigner sign --ks signing/dcc.keystore --ks-pass pass:android \
       --key-pass pass:android <app>.apk
   ```
   Put it at `site/apps/<app-id>/<app-id>.apk`. (dc1-keys builds with plain
   `aapt2` + `javac` + `d8` — see its repo's build.sh; no Gradle needed.)
2. **Web apps (PWAs)**: no file needed, just the app's URL.
3. Add an entry to `site/apps.json` following the existing ones. Required
   fields: id, name, tagline, author, type ("apk"|"pwa"), version, updated,
   description, source (link to the app's source repo). For APKs also
   `apk: {file, size, package}`; for PWAs also `url`.
4. Write `afterInstall` as one-step-per-line instructions a non-technical
   friend can follow on the tablet (permissions to grant, services to
   enable). No adb commands — on-device steps only.
5. If the owner (Anjan) is asking, commit to the default branch directly.
   If a friend is contributing, open a PR for Anjan to approve.

## When asked to "shelve the app from issue #N"

Friends also submit via the share form (a GitHub issue labeled
`app-submission`). Read the issue, take the attached .zip (extract the
APK; sign it with the club key) or the app URL, then follow the steps
above using the form's answers for the catalog entry. Open a PR for
Anjan to approve, close the issue from the PR (`Closes #N`), and if
anything essential is missing, ask in an issue comment.

## Design rules for the site itself

Built for a monochrome reflective LCD: pure grayscale, high contrast, real
2px borders instead of shadows, no gradients, no animation, ≥48px touch
targets, relative URLs only (the site is served from a subpath).

## Verifying changes

Serve `site/` with any static server and drive it in Chromium (Playwright):
the shelf must render every catalog entry, the install wizard must walk
forward/back through all steps, the APK link must return 200, and the
console must stay error-free.
