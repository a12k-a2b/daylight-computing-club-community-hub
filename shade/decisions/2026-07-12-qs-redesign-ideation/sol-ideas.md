# Sol’s quick-settings redesign ideas

These are ordered by expected leverage for the work they cost. The common direction is to make the shade read like a small page about the device’s present condition, not a tray of Android switches. Each idea can stand alone.

## 1. Give the light area one plain-language verdict

Under the two light dials, show one short sentence derived from their current values. Examples are “The room is lighting the page,” “A little warm light is helping the page,” and “The backlight is now doing most of the work.” It changes while either thumb moves. At pure reflective, add the quiet line “Best in daylight. Uses no backlight.” At the far bright end, use “Useful when the room cannot help.” Do not show percentages.

This teaches that brightness and warmth are parts of one reading light, that ambient light is preferable, and that a high backlight is a fallback rather than the normal setting.

Difficulty is small, about an afternoon. The shade app can build it today in `PanelView.java`, with a pure function beside `Brightness.java` and `Warmth.java`. The design mock must change at the same time. It does not need the blessing, although the sentence should acknowledge that warmth is unavailable in preview mode.

Maintenance through AOSP 16/17 is very low. It reads values the app already reads and adds no system contract.

The main collision is copy combinatorics at zone boundaries, especially when a user changes the two brightness boundaries. The verdict must use those same boundaries rather than inventing another model. Live updates should reuse the existing slider callback and never create a timer. If warmth cannot be written, the verdict must not imply that it changed.

## 2. Replace switch labels with outcomes

Let every control state what will be true, not merely name an Android subsystem. A connected tile could read “Online” with “Home Wi-Fi” below it; when off it could read “Wi-Fi off.” Bluetooth becomes “Headphones” when audio is routed, “Bluetooth on” when merely available, and “Bluetooth off” otherwise. Quiet becomes “Calls can ring” or “Only chosen people can interrupt.” Rotation becomes “Page stays upright” or “Page can turn.” Airplane becomes “Radios resting” or “Radios available.” The familiar noun can remain in the smaller second line where needed.

This teaches the consequence of each setting and removes the need to understand Android vocabulary before acting. It also distinguishes “a radio is on” from “something useful is connected.”

Difficulty is small to medium, roughly one or two afternoons. The shade app can do most of it today by extending `refreshTiles()` and `TileButton`. The exact Quiet sentence may remain conservative unless the app can reliably inspect the active interruption policy; fuller Focus-style wording belongs post-blessing if policy access is pre-granted.

Maintenance is low for Wi-Fi, Bluetooth, rotation, and airplane because their state is already wrapped in control classes. It is medium for Quiet because notification-policy semantics and access can shift across AOSP releases.

Long device names can break the three-column layout, so they need a calm truncation rule and should appear only on the Bluetooth tile. Outcome copy can lie if a broadcast has not arrived yet; keep the existing short delayed refresh and never add polling. A failed direct action must retain the current hand-off behavior. No new hidden calls should escape `SysApi.java`.

## 3. Make the six controls read as three human pairs

Keep six controls, but give the grid three quiet captions or spatial groupings: “connection” over Wi-Fi and Bluetooth, “attention” over Quiet and Airplane, and “page” over Light/Dark appearance and Rotation. In portrait, use three two-control rows instead of two rows of three. In landscape, the same pairs can sit across one row with hairline gaps between pairs. The control names can then be shorter because the group supplies context.

This teaches a durable mental map: connection affects how the tablet reaches things, attention affects what reaches the person, and page affects how reading looks. A user no longer has to memorize six unrelated Android tiles.

Difficulty is small, about an afternoon for the shade app plus the required mock update. It works today. If choose-your-own controls arrives later, the editor should preserve these three slots as roles rather than present an unstructured bag of six.

Maintenance is very low across AOSP versions because this is layout and copy only.

The panel becomes taller in portrait, which may push media or notifications below the fold. A compact pair caption and 64dp controls may recover most of the height without shrinking targets below 48dp. Custom controls create an emergent taxonomy problem: flashlight does not naturally belong to “page,” and screenshot is not a persistent state. This idea is strongest if Daylight chooses a small, opinionated set rather than promising arbitrary tiles.

## 4. Show the consequence before handing someone to Android

When the shade cannot perform an action directly, keep the person in the shade for one calm sentence before opening Settings. Examples are “Wi-Fi needs the system page on this version. Continue,” and “Airplane mode pauses Wi-Fi and Bluetooth. Continue.” This is not a modal dialog: the tile expands in place into a two-line row with “continue” and “stay here.” On blessed builds, the extra step disappears for ordinary reversible actions. Long-press can still go straight to the deeper surface.

This teaches what an unfamiliar action does and makes a sudden Material Settings page feel like an intentional continuation rather than a failure or trap.

Difficulty is medium, about a weekend, for the shade app today. It needs a reusable inline confirmation state in `PanelView` or `TileButton`. No platform work is required.

Maintenance is low. Intent actions are already isolated in the control layer, though each AOSP jump should verify that the destination still exists and that the explanatory sentence remains true.

An extra tap would be irritating on every use, so it should appear only for hand-offs and consequential actions, not ordinary blessed toggles. Expanded rows can disturb the sheet height during a drag; expansion should happen only after the panel is fully open. If the destination activity is missing, the row should return to its prior state. This must not weaken the crash-max story or introduce a background component.

## 5. Turn the blank notification state into a reflective-use lesson

When there are no notifications, do not leave only “all quiet.” Use the empty space for one stable, situational line: “All quiet. Close the shade and return to the page.” If the backlight is screen-like, use “All quiet. The room may be bright enough to lower the light.” If Quiet is on, use “Quiet is keeping the page still.” Show only one line, chosen when the panel opens; do not rotate tips.

This teaches that the shade is a brief interruption, reinforces reflective lighting at the moment there is room to say it, and makes Quiet feel connected to reading rather than to Android notification machinery.

Difficulty is small, less than an afternoon, in the shade app today. It reuses current notification count, brightness, and Quiet state.

Maintenance is very low because it adds no APIs.

The lesson can become nagging if it changes while the panel is open or appears every time with a scolding tone. Choose it once per panel construction and keep the language observational. Notification access may be absent; in that case the existing grant row must win, because the app cannot honestly claim all is quiet. No timer, random rotation, or stored tip campaign is needed.

## 6. Give every action a brief receipt

After a successful tap, replace the tile’s small state line for about the length of the existing visual refresh with a concrete receipt such as “Wi-Fi off,” “Quiet on,” “Page can turn,” or “Connected to AirPods.” Prefer a visual pressed state and immediate final copy; where the system settles asynchronously, show “turning on…” until the existing receiver reports the state. If a direct action fails, show “Opening Wi-Fi settings” before the current hand-off.

This teaches cause and effect. It also removes the Android habit of making users infer success from a tiny icon color change.

Difficulty is medium. The shade app can implement receipts today for synchronous controls in an afternoon. Wi-Fi and Bluetooth should use their existing open-panel broadcasts rather than guesses; finishing all six well is a weekend. No platform change is needed.

Maintenance is low to medium. The UI state machine is portable, but radio transition broadcasts and Bluetooth restrictions need rechecking on AOSP 16/17.

A literal timed receipt would violate the spirit of the no-timer promise if it outlived the interaction. Use view-posted, panel-lifetime callbacks only for synchronous controls, and receiver-driven final states for radios; nothing survives panel close. Rapid repeated taps can reorder receipts, so each tile needs one pending action token. The final state must always override the optimistic one. Crashes still remove the overlay and return stock shade.

## 7. Make network pages answer “am I connected?” before listing machinery

The Wi-Fi page should begin with a sentence such as “Connected to Home. The internet is reachable,” “Connected to Home. The internet has not answered yet,” or “Not connected.” Below that, divide choices into “known networks” and “new networks,” avoiding “saved,” “SSID,” and security acronyms. The Bluetooth page should begin “Sound is playing through AirPods,” “AirPods are connected,” or “No sound device connected,” then separate “my devices” from “new nearby devices.” Signal bars remain useful but never carry the whole meaning.

This teaches the difference between a radio, a connection, and usable service. It also teaches that pairing is a one-time introduction and connection is the everyday action.

Difficulty is medium. Copy and grouping are a shade-app weekend after blessing because the useful Wi-Fi list is blessing-gated. Basic Bluetooth wording works today. A trustworthy “internet is reachable” statement should use existing `NetworkCapabilities` validation, not perform a network request; the app keeps no INTERNET permission. Audio-route wording may need post-blessing profile access already planned.

Maintenance is medium. Public connectivity capabilities are fairly stable, but Wi-Fi configuration APIs are deprecated and system-app behavior must be checked during the AOSP jump. Bluetooth profile behavior also deserves an upgrade test.

“Validated” does not always mean the user’s intended internet works, so say “has answered” rather than promise perfect connectivity. Captive portals complicate the three-state copy. Scans remain receiver-driven only while the picker is visible. Password entry and pairing confirmation should stay with the system unless Daylight can own their security and error cases completely. Any unavoidable hidden profile action stays in `SysApi.java`.

## 8. Offer one temporary “reading pause,” not a new Focus system

Long-pressing Quiet opens a small in-shade page with two choices: “Let people reach me” and “Keep this reading session quiet.” The second applies the existing priority interruption filter and labels the tile “Reading quietly.” It lasts only until the user turns Quiet off; there is no duration picker, schedule, timer, automation, or named-mode editor. The page explains in one line, “Alarms and people you have allowed may still come through.”

This teaches the practical meaning of Do Not Disturb without importing Android’s policy language. It frames Quiet around the activity the device is for, while remaining honest about exceptions.

Difficulty is small to medium for the shade app today, about an afternoon if current DND access is granted. Post-blessing, pre-granted policy access makes it seamless. The platform team is not otherwise needed.

Maintenance is medium because notification-policy access, priority categories, and user configuration can change across AOSP versions. The app should set only the interruption filter and never become the owner of the user’s allow-list.

The phrase “reading session” may suggest an automatic end that does not exist. The page must say that tapping Quiet again ends it. Do not add a timer or wakeup to make the label literal. Other apps or system schedules can change DND behind the shade; refresh from actual state on open and via the current open-panel receiver path. Avoid promising silence when alarms or allowed contacts may interrupt.

## 9. Add a one-time, hands-on first pull

On the first successful opening only, place three small annotations inside the live panel: “Start with the room” beside the far-left brightness area, “Tap changes it; hold opens it” above the control pairs, and “Swipe up or tap outside to return” at the bottom. Each annotation disappears after the person performs the related action, and all remaining annotations disappear when the panel closes. A small “show the first-pull guide again” row lives in Shade Setup.

This teaches the reflective-first model, the Control Center interaction grammar, and how to leave, without a tour, carousel, or setup manual.

Difficulty is medium, about a weekend in the shade app. It needs a few preference bits and hooks in existing touch paths. It can ship today and requires no platform work.

Maintenance is very low at the API level, but medium at the design level because annotations must move whenever the 1:1 panel layout changes.

Overlays can clutter the exact calm surface they are meant to explain. Keep them as ordinary text in reserved layout space, never coach marks floating over targets. Accessibility and accidental dismissal need testing. Preference writes happen only on direct user actions, with no service or timer. If guide code fails, the normal panel must still build; it should be an optional layer rather than a new opening state machine.

## 10. Let the page say when the room can take over

If the DC-1 exposes a trustworthy ambient-light reading through a stable sensor, add a tiny mark under the brightness dial when the panel opens: “Room light is strong” or “Room light is low.” When strong room light coincides with screen-like backlight, add the non-blocking suggestion “Try lowering the light; the page may stay clear.” Never move the slider automatically. Read the sensor only while the panel is visible and stop immediately on close.

This teaches the central Live Paper skill at the exact moment it matters: first use the room, then add light only as needed. It respects personal comfort by suggesting rather than correcting.

Difficulty is medium for a prototype and a project for a trustworthy product feature. The shade app can use the public sensor API if the hardware sensor is present and calibrated. The Sol:OS platform team must own calibration, sensor availability, and a stable interpretation across hardware revisions. This should wait for measured DC-1 data rather than infer lux thresholds from generic phones.

Maintenance is low in app code but medium for the platform and hardware contract. AOSP sensor APIs are stable; calibration and vendor behavior across the 13 to 16/17 base are the real cost.

Bad thresholds would teach the wrong lesson, and a hand shadow or dark sleeve can make readings jump. Sample only on open or through a short panel-lifetime sensor listener, settle the copy conservatively, and register nothing in the dormant service. Do not add adaptive brightness, background learning, wakeups, or hidden sensor APIs. If the sensor is absent or unreliable, omit the feature entirely.

## 11. Put “return to paper” at the end of Android hand-offs

For the few journeys that must enter Settings, the Sol:OS platform team could add a small, consistent “Back to Daylight Shade” affordance to the grayscale Settings theme or preserve a clean task return path so the system Back gesture returns directly to the open context. The shade’s inline hand-off sentence would say “You’ll return here when finished.” This is not a forked replacement for Settings; it is a breadcrumb on Daylight-owned builds.

This teaches that Settings is a deeper room reached for an exceptional task, while the shade remains the everyday home for device conditions. It reduces the fear that a user has fallen into Android.

Difficulty is high relative to the app-only ideas. The shade app can improve intents and task flags post-blessing, but the reliable visual breadcrumb belongs to the Sol:OS platform team and should be considered alongside the Settings RRO. A framework or Settings patch may be required if an overlay cannot add behavior.

Maintenance is high if it patches Settings, because Settings navigation and resources change substantially between AOSP bases. It is medium if the result can be achieved with stable task behavior and an RRO-only visual treatment.

Deep links can land in activities with their own task rules, and vendor pages may ignore the breadcrumb. A misleading promise to return is worse than no promise. This also creates coupling between Shade and Settings, so it should not become required for core actions or crash recovery. The stock shade must still return if Shade dies, and any hidden status-bar operations remain confined to `SysApi.java` rather than spreading into navigation code.

## Things I would deliberately not do

I would not make reading presets the primary surface. Named presets are attractive, but they replace learning with four unexplained recipes and hide the relationship between room light, white backlight, and amber light. They may be useful shortcuts later, after the two dials have taught the model.

I would not add automatic sunset warmth, temporary Quiet durations, rotating tips, or any feature that needs a clock, alarm, wakeup, or background learner. Apart from conflicting with the dormant-service promise, automation makes it harder to learn why the page changed.

I would not imitate iOS Control Center with translucent cards, icon-only circles, nested press-and-hold panels, or a customizable control gallery. Familiar interaction grammar is useful; copying its visual density and hidden meanings would discard Daylight’s paper character and create a permanent compatibility surface.

## Likely shared blind spots

Both brains are likely to overvalue elegant copy because we have no evidence that people read it during a quick pull. The real test is whether a first-time owner can predict what a tap will do, set comfortable light in three rooms, and recover from a system hand-off without explanation.

We are also likely to treat “iPhone-native” as one coherent skill level. Some owners will recognize long-press immediately; others will never discover it. Age, vision, motor control, and language may matter more than phone allegiance, especially on a 10.5-inch reading device.

We may assume the desired reflective behavior before measuring it. “Lower is better” can become moralizing, and ambient light, glare, contrast, eye condition, battery cost, and warmth preference are not interchangeable. The vocabulary needs tests in sun, office light, dim rooms, and darkness with real people.

Finally, both brains may focus on the shade while the worst Android intimidation happens after it: permission grants, pairing confirmation, password entry, captive portals, and full Settings. A calm front door does not solve a frightening hallway. The app should be judged by the whole round trip, including every honest fallback and the AOSP 16/17 migration.
