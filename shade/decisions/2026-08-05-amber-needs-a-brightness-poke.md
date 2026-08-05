# The amber key is a passive side channel — writing it does nothing

**Date:** 2026-08-05 · **Device:** DC-1 `JP4R01422` ("rooted 2"), rooted +
bootloader-unlocked, userdebug, SELinux Enforcing ·
**Found by:** desktop Claude session (Fable), root shell reading the LED
driver directly.

## What we believed

`ROADMAP.md` v0.5 said of the warmth slider: *"The code already does this
— it just needs the permissions to exist."* The story was that
`Settings.System screen_brightness_amber_rate` = 256 + amber is the whole
mechanism, and that the only thing standing between Shade and a working
amber slider was write access to that key.

The encoding half of that is right, and is now confirmed three ways
(on-glass key diff 2026-07-11; `dc1-backlight/01-root-cause-analysis.md`
decompile of SystemUI + LightsService; and the quantitative check below).

The "just needs permissions" half is **wrong**, and would have shipped a
slider that looks broken on the day the blessing landed.

## What actually happens

Measured against ground truth — the kernel driver's own nodes,
`/sys/class/leds/lcd-backlight-amber/brightness` and
`/sys/class/leds/lcd-backlight/brightness`, readable with root:

| step | amber setting | hw amber | hw white |
|---|---|---|---|
| baseline | 496 | 27 | 1 |
| write key = 300, wait | 300 | 27 | 1 |
| rewrite brightness to the value it already held (28) | 300 | 27 | 1 |
| nudge brightness 28 → 27 → 28 | 300 | 5 | 23 |
| write key = 496, nudge again | 496 | 27 | 1 |

Three things fall out:

1. **Writing the key alone moves nothing.** LightsService re-reads the
   setting only while servicing a *brightness* write
   (`amber = getInt(...)` inside `setBrightness`) — the decompile said as
   much, and the strings confirm it. `DisplayPowerController`'s
   `mLatestIntBrightnessAmber` also stayed at its stale 496 throughout,
   so it is not a usable progress signal either.
2. **A same-value brightness write does not count** — the settings
   provider dedupes it, no change event, strings unmoved. Stock SystemUI
   "re-pokes the current brightness"; it has to be a real change.
3. **The split is proportional and exact.** amber byte 44 (setting 300) at
   brightness 28 → 5/23; amber byte 240 (setting 496) → 27/1. Both match
   `total × amberByte/255` to the rounding. This is the strongest
   confirmation of the encoding we have.

## Decision

`Warmth.set()` writes the key **and then nudges brightness one step and
back** (`pokeBacklight`). The nudge goes *up* from any raw value ≤ 3,
because `Brightness` records that raw 1 is backlight-off and raw 2 is a
dead rung — a naive `b-1` would flash the panel dark. At raw ≤ 1 we skip
the poke entirely: the backlight is off and LightsService pins amber to
255 anyway.

The poke costs only `WRITE_SETTINGS`, which the brightness slider already
requires. **This half works today on any DC-1, blessed or not** — it is
the key write that stays gated.

## What still gates the amber write (corrected)

Not a permission. The settings provider's system-table rule turns on
whether the *caller is a system app*; no grant reaches it. Consequences:

- `pm grant WRITE_SECURE_SETTINGS` does not help. (Already known —
  `shade-test-report.md`. Restated because root tempts people to retry it.)
- **Root does not help the app either**, which is the surprise. This
  build's `su` is AOSP `/system/xbin/su` (it rejects `su -c`, wanting
  `su 0 <cmd>`), and AOSP's su serves only callers that are already root
  or shell; with SELinux Enforcing, `untrusted_app` cannot transition to
  the `su` domain regardless. An in-app `su` helper is a dead end here.
  Root helps by letting us *place the APK on a system partition*, not by
  letting the app escalate at runtime.
- Therefore the cheapest real unlock is **system-partition residency**.
  Note this is a weaker requirement than the privileged-permission
  allowlist that `priv-install/` sets up: the amber write needs
  `FLAG_SYSTEM`, not any privileged permission. A plain
  `/system_ext/app/` install should unlock amber while sidestepping the
  `ro.control_privapp_permissions=enforce` boot check that makes the
  priv-app route able to bootloop `system_server`. **Untested** — it
  needs Anjan's go before anything touches the system partition on his
  daily tablet.

## Honest status

Device-verified: the encoding, the proportional split, the dead bare
write, the dedupe, and the nudge recipe — all measured at the driver.

Not verified: `pokeBacklight` running *inside Shade* against a live amber
key, because that cannot happen until Shade lives on a system partition.
The code path is exercised only when the key is writable.
