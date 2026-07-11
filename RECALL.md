# The recall — pulling a dish from the shelf

If an app on the shelf turns out to be harmful, broken, or not what it
claims (a 🔴 from an inspector, a report from a friend, a bad feeling),
here is the whole procedure. It's four steps and none of them are scary.
Written down now so nobody has to think under stress later.

## 1. Pull it from the shelf (one commit)

- Remove the app's entry from `site/apps.json`.
- Delete its folder `site/apps/<app-id>/` (if it's an APK).
- Add an entry to `site/recalled.json` — `{id, name, reason, date}` —
  so the shelf shows a removal note to anyone whose tablet took it
  (and the Club Companion can notify).
- Commit straight to `master`: `Recall <app name>: <one-line reason>`.

The shelf updates within a couple of minutes on both hosts. Pages are
served network-first, so even pinned clubs see the app disappear on
their next visit. Nobody new can install it from that moment.

## 2. Say what happened, warmly

Comment on the app's submission issue (and its guestbook, if it has
notes) with what was found and what people should do. Plain language,
no blame theater — the club runs on people feeling safe to bring dishes.
A recall should feel like "we found a bug in the casserole," not a trial.

## 3. Tell the friends who installed it

The site cannot reach onto anyone's tablet — an installed app stays
installed until its owner removes it. So message the people who you know
installed it (the guestbook shows who left notes): on the tablet,
**Settings → Apps → \<app name\> → Uninstall**. That's the whole fix on
their end.

## 4. If it gets fixed: reshelve over the top

Because every club app is signed with the same club key, a corrected
version with a higher version number **installs cleanly over the bad
one** — no uninstall needed for updates. Build, sign with the club key,
bump `version` and `updated` in the catalog entry, shelve as usual.

## If it was bad faith (hopefully never)

Remove the author from `.github/club-members.json` (`trusted`) in the
same recall commit — their future submissions then wait for keeper
approval again. The trust ratchet turns both ways; it just should almost
never have to.

---

*Upstream of all this: the hard gates (signing, VirusTotal) block known-bad
files before shelving, and the inspectors (Claude recipe read + emulator
crash test) post 🔴/🟡/🟢 verdicts on every submission — including
post-hoc on trusted members' auto-shelved dishes. A recall is the last
resort, not the first line.*
