# Handoff prompt — continue the club in a desktop Claude Code thread

Paste everything below into a fresh Claude Code session on the Mac:

---

You're picking up the Daylight Computer Club project on my Mac, continuing
work from a cloud Claude session. Read these first, in order — they carry
the full context:

  1. CLAUDE.md        (how sessions work in this repo)
  2. MECHANICS.md     (how a dish travels; YOUR lane is defined here)
  3. guides/next-moves.png  (my current human to-do list)
  Repo: github.com/a12k-a2b/daylight-computing-club-community-hub
  Live: daylightcomputer.club   (Railway, deploys on push to master)

WHAT THIS IS: "the club" — a Homebrew-Computer-Club-style potluck where
friends share homebrew apps for Daylight DC-1 tablets. Everything speaks
potluck language (dishes, shelf, cooks, keepers, gifts). All of it is live:
shelf, guided install wizard, invites (invite.html?f=melissa), gift
dedications (for:["name"] renders wrapped gifts with a lid-lift animation),
robots (auto-shelving, Claude source review, emulator crash test, weekly
caretaker), trust list (.github/club-members.json), signing key in repo.

THE TWO THREADS: the cloud thread owns the website + robots (don't rework
those — coordinate via MECHANICS.md and commits). THE DESKTOP THREAD (you)
owns: the Clubhouse APK (repo a12k-a2b/dcc-companion — built with plain
aapt2+javac+d8 like dc1-keys, no Gradle), real-device work over USB, and
anything needing my accounts (texting invites, secrets).

NAMING (settled): the Android app is "The Clubhouse" — the club's home ON
the Daylight (its home tab should BE the shelf, a WebView of the site).
The website is the club everywhere else. Butler/inspector/reporter are the
Clubhouse's internal staff roles. A CLEAN RENAME is queued in MECHANICS.md
(package → club.daylightcomputer.clubhouse + label + catalog id, one
coordinated shot) — DO NOT do it until I say "do the clean rename".

DESIGN LANGUAGE: grayscale ink-on-paper, 2px borders, one amber accent,
one small sun as signature; "living paper" motion — brief, physical (lid
lifts, page settles), never looping (screen is 60-120fps). Design koans
live in .claude/skills/dcc-poster-design/SKILL.md.

YOUR IMMEDIATE MISSIONS (in order, ask me before anything destructive):
  1. When I plug the DC-1 in over USB-C: set up adb, take a real
     screenshot of daylightcomputer.club on the tablet, and walk the
     install wizard on real hardware yourself (adb shell input), noting
     every rough edge. Real logcat when anything misbehaves.
  2. Install + test the Clubhouse APK on the real device: notifications,
     one-tap install path, and add the shelf as its home tab (WebView
     carrying the tablet's seat) per the contract in MECHANICS.md.
  3. Help me set the VT_API_KEY repo secret when I hand you the key
     (gh secret set VT_API_KEY --repo a12k-a2b/daylight-computing-club-community-hub).

HOUSE RULES: honest descriptions; sign APKs with the club key
(signing/dcc.keystore, pass "android"); never let robots merge/approve —
humans decide; commit messages in the repo's warm plain-language style;
and don't be a sorcerer's apprentice — when my words and my intent
diverge, serve the intent and say so.
