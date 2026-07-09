# Club signing key

`dcc.keystore` is the shared key that club APKs are signed with.

- **alias:** `debugkey`
- **store & key password:** `android`

## Why it's committed to the repo

Android only installs an *update* to an app if the update is signed with the
same key as the installed copy. If every rebuild used a throwaway key, every
update would force friends to uninstall/reinstall (losing settings). Keeping
one shared key in the repo means anyone in the club — or any Claude Code
session — can rebuild an app and it updates cleanly on everyone's tablet.

This is a deliberate trade-off, sized for a club of friends:

- It is **not a secret** — treat it exactly like Android's well-known debug
  key. It proves "signed by someone with access to this repo," nothing more.
- Trust in the club comes from the repo's pull-request history and from
  knowing each other, not from this key.
- If the club outgrows this (strangers joining, apps with sensitive
  permissions), move to a real release key held as a GitHub Actions secret
  and sign in CI — see ROADMAP.md.

## Signing an APK with it

```sh
apksigner sign --ks signing/dcc.keystore --ks-pass pass:android \
    --key-pass pass:android your-app.apk
```

One-time note for existing installs: an app already installed with a
*different* key (e.g. a personal debug build) must be uninstalled once before
the club-signed copy will install.
