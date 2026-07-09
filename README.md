# Daylight Computer Club

A homebrew-computer-club-style sharing place for apps we make for our
[Daylight DC-1](https://daylightcomputer.com) tablets. Not an app store — a
potluck. Everything is vibecoded, open source, and shared between friends.

**The club lives at:** **https://daylightcomputer.club** (hosted on Railway,
which owns the domain; `https://a12k-a2b.github.io/daylight-computing-club-community-hub/`
is a free mirror via GitHub Pages). Both redeploy automatically on every push
to `master` — Railway watches the repo, and the Pages workflow does the rest.

## What's here

```
site/                     the club itself — a static site, no build step
  index.html              the shelf: every shared app as a card
  install.html            the guided installer — walks a friend through every
                          Android screen, one step at a time
  share.html              how to add your own app (incl. a copy-paste Claude prompt)
  apps.json               the catalog — one entry per app
  apps/<id>/<id>.apk      the app files themselves
  manifest.webmanifest,   the club is itself an installable web app
  sw.js                   (works offline once visited)
.github/workflows/        deploys site/ to GitHub Pages on every push to master
signing/                  the shared club signing key (see signing/README.md)
```

## Turning it on (one-time, ~2 minutes)

1. **Name the repo** `daylight-computing-club-community-hub`: GitHub → this
   repo → Settings → General → Repository name. (GitHub redirects the old
   name automatically, so nothing breaks.)
2. **Make it public**: same Settings page → Danger Zone → Change visibility
   → Public. GitHub Pages on a free account needs this — and everything here
   is meant to be open source anyway; that's the club ethos.
3. Merge to `master`. The **Deploy club to GitHub Pages** action runs and
   switches Pages on by itself.
4. The club is live at `https://a12k-a2b.github.io/daylight-computing-club-community-hub/`.

## Sharing with Melissa (or anyone), today

Text or email her the club link. On her DC-1, in Chrome:

1. She opens the link and taps **Daylight Keys → Install it**.
2. The site walks her through every screen — the download, the one-time
   "allow installs from Chrome" switch, Play Protect if it pops up, and the
   app's own setup (accessibility service, permissions).
3. Bonus: the shelf has a card to pin **the club itself** to her home screen,
   so future apps are one tap away.

No accounts, no TestFlight, no telegramming APKs.

## Adding an app

See [the share page](site/share.html) — or in short: drop an APK in
`site/apps/<id>/`, add an entry to `site/apps.json`, push. For web apps
(PWAs), no file needed — just the URL in the catalog entry. There's a
copy-paste prompt on the share page that makes any Claude Code session do
this for you.

## Where this is going

See [ROADMAP.md](ROADMAP.md) — from "me sharing two apps with Melissa" to a
community exchange for bespoke, personal software.
