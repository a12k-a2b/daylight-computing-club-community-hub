# Daylight Computer Club — instructions for Claude sessions

This repo is a static app-sharing site ("the club") for Daylight DC-1
tablets. The site lives in `site/`, needs no build step, and deploys on
every push to `master`: Railway serves https://daylightcomputer.club (the
canonical link, via Dockerfile + Caddyfile) and GitHub Pages serves a free
mirror. Full context: README.md, ROADMAP.md.

Share-form submissions (issues labeled `app-submission`) are shelved
automatically by `.github/workflows/shelve.yml`: authors listed in
`.github/club-members.json` publish straight to master; everyone else's
submission becomes a PR for Anjan to approve. Shelve PRs also get an
advisory Claude source review (`claude-review.yml`, needs the
ANTHROPIC_API_KEY secret) and an emulator crash/memory smoke test
(`inspect-dynamic.yml`); hard gates (signing, VirusTotal) live in
`.github/scripts/shelve.py` + `inspect.py`. Trusted members' dishes
auto-shelve and get a post-hoc audit (inspectors comment on the
submission issue). If an app must be pulled from the shelf, follow
RECALL.md.

## When asked to "share/shelve/add an app to the club"

All of these phrasings (and anything similar) mean the same request:
"share it with the club", "share it with the DCC", "share it with
Daylight Computer Club" / "daylight computer club", "share it with the
computer club", "shelve it", "add it to the shelf/club/hub" —
and the old name "daylight computing club" still means this club too.

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

## When asked to "invite a friend to the club"

An invite has a warm half and a trust half:

1. **Warm half** — add an entry to `site/friends.json` (key = lowercase
   first name; fields: `name`, `from` (who's inviting), optional `note` —
   one warm sentence from the inviter). Their personal link is
   `https://daylightcomputer.club/invite.html?f=<key>` — a page that greets
   them by name and seats them at the potluck.
2. **Trust half** — if the inviter vouches for them ("I'd eat anything they
   bring"), add their GitHub username to `.github/club-members.json`
   `trusted`. Username not known yet? Skip it — add it when their first
   dish arrives and a keeper approves that one PR.
3. The inviter sends the link personally, by text or email. A Claude
   session may only send it after showing the exact message and getting
   explicit approval.

The link is warmth, not authentication — nothing on the page grants
power; trust is enforced only by `club-members.json`. So a guessed or
shared invite URL exposes nothing but a friendly greeting.

## When asked to "send a dish to a friend" (desktop sessions)

Sending = a personal text or email from Anjan's own accounts, never a
robot blast. Compose a short personal message with the app's deep link
(`https://daylightcomputer.club/install.html?app=<id>`), or their invite
link if they're new (see the invite section). Show Anjan the exact
message and recipient, send only on explicit approval.

## When asked to "shelve what <friend> texted/emailed me" (desktop sessions)

Friends without GitHub or Claude can just text or email Anjan their APK
(or web app URL). A desktop session: find the attachment in Messages/Mail,
verify with Anjan it's expected and from that friend, then follow the
shelve steps above — sign with the club key, write the catalog entry and
`afterInstall` from what the friend said it does, open a PR for Anjan to
approve. Mention the friend by first name as `author`.

## When asked to "shelve the app from issue #N"

Friends also submit via the share form (a GitHub issue labeled
`app-submission`). Read the issue, take the attached .zip (extract the
APK; sign it with the club key) or the app URL, then follow the steps
above using the form's answers for the catalog entry. Open a PR for
Anjan to approve, close the issue from the PR (`Closes #N`), and if
anything essential is missing, ask in an issue comment.

## When asked to "daylight-ify" / port / redesign an app for the DC-1

Use the skill pack in `skills/`: `daylight-port` (choose PWA / WebView
shell / native, fix web↔Android jank), `daylight-design` (grayscale
mapping, tablet layout, voice), `daylight-teach` (onboarding), with
`daylight-preview` as the feedback loop after every visual pass. Fold
lessons learned on real dishes back into those skills.

## Design rules for the site itself

Built for a monochrome reflective LCD: pure grayscale, high contrast, real
2px borders instead of shadows, no gradients, motion only as "living
paper" — brief, physical, paper-like movement (a lid lifts, a page
settles; think Harry Potter's newspapers), never looping or
attention-seeking — ≥48px touch
targets, relative URLs only (the site is served from a subpath).

## Verifying changes

Serve `site/` with any static server and drive it in Chromium (Playwright):
the shelf must render every catalog entry, the install wizard must walk
forward/back through all steps, the APK link must return 200, and the
console must stay error-free.
