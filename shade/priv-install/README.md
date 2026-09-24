# Full mode on rooted 2 — priv-app install kit (2026-08-05)

The bootloader unlock on `JP4R01422` makes Shade's full mode buildable
NOW on that one unit: club-signed APK into `/system_ext/priv-app/` plus a
generated permission allowlist. No platform signature needed for most of
it — verified against the live device:

**Arrives (privileged, allowlistable):** real amber-key writes (the
warmth slider drives the actual backlight — the headline), silence the
stock shade (STATUS_BAR), clean dark flips (MODIFY_DAY_NIGHT_MODE),
paired-device audio routing (BLUETOOTH_PRIVILEGED; may also need the
hidden-API toggle — watch logcat), live settings-search index
(READ_SEARCH_INDEXABLES).

**Still waits for true platform signing (signature-only on this build):**
the status-bar-surface swipe (INTERNAL_SYSTEM_WINDOW), the in-shade Wi-Fi
network list (NETWORK_SETTINGS), and — correcting our README's tier
guess — airplane direct flip (NETWORK_AIRPLANE_MODE is plain `signature`
here, not privileged).

## Why the ceremony

`ro.control_privapp_permissions=enforce` on this build: a priv-app
requesting a privileged permission that the allowlist misses **crashes
system_server at boot**. So `install-on-rooted2.sh` generates the
allowlist mechanically — aapt-dumped requests ∩ the device's actual
privileged set, the same intersection the boot check enforces — validates
it, and refuses to proceed on any surprise. Recovery from the worst case:
root adbd answers during a crash loop on this userdebug build, so
`rollback-on-rooted2.sh` (remount, rm, reboot) stays reachable. We hold
no stock images on this Mac — that's the honest backstop gap.

## Rules of engagement

This is Anjan's **daily tablet with real ink** (not the empty bench it
used to be): explicit owner go required, dc1-glass claimed as
`shade priv-install`, serial pinned in every command, stroke-count check
before/after any UI walking (DC1-DEVICES.md "Never automate over real
ink"), and the `/data` sideload already on the device gets uninstalled
first so it can't shadow the system copy.
