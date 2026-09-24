package com.daylightcomputer.shade.ui;

import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.os.Environment;
import android.os.StatFs;
import android.provider.AlarmClock;
import android.provider.Settings;
import android.widget.LinearLayout;

import com.daylightcomputer.shade.control.Caps;
import com.daylightcomputer.shade.control.Toggles;

/** The settings people actually come looking for, in plain words — live
 *  where a public API allows, an honest hand-off where it doesn't, and
 *  never more than eight rows. Stock Settings becomes the attic
 *  ("everything else…" in the footer), not the front door. Every row
 *  must survive an AOSP jump by degrading to a hand-off. */
public class EssentialsView extends PickerPage {

    private final Runnable closePanel;
    private final Runnable openShadeSetup;
    private final LinearLayout list;

    // calm ladders, not free dials
    private static final float[] FONT_STEPS = {0.85f, 1.0f, 1.15f, 1.3f};
    private static final String[] FONT_NAMES = {"small", "standard", "large", "larger"};
    private static final int[] SLEEP_STEPS = {30_000, 120_000, 600_000, 1_800_000};
    private static final String[] SLEEP_NAMES = {"30 seconds", "2 minutes", "10 minutes", "30 minutes"};
    private static final String[] SOUND_NAMES = {"off", "quiet", "medium", "loud"};
    private static final float[] SOUND_LEVELS = {0f, 0.25f, 0.6f, 1f};

    public EssentialsView(Context c, Runnable closePanel, Runnable openShadeSetup) {
        super(c);
        this.closePanel = closePanel;
        this.openShadeSetup = openShadeSetup;
        list = new LinearLayout(c);
        list.setOrientation(VERTICAL);
        addView(list, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
    }

    @Override public String title() { return "essentials"; }
    @Override public void start() { populate(); }
    @Override public void stop() {}

    private void open(String action) {
        try {
            getContext().startActivity(new Intent(action)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Throwable ignored) {}
        closePanel.run();
    }

    private void populate() {
        Context c = getContext();
        list.removeAllViews();

        // text size — a ladder on Settings.System.FONT_SCALE
        float fs = Settings.System.getFloat(c.getContentResolver(),
                Settings.System.FONT_SCALE, 1f);
        int fi = nearest(FONT_STEPS, fs);
        list.addView(row("Text size · " + FONT_NAMES[fi],
                "tap for the next size", () -> {
                    if (!Caps.writeSettings(c)) { open(Settings.ACTION_DISPLAY_SETTINGS); return; }
                    try {
                        Settings.System.putFloat(c.getContentResolver(),
                                Settings.System.FONT_SCALE,
                                FONT_STEPS[(nearest(FONT_STEPS,
                                        Settings.System.getFloat(c.getContentResolver(),
                                                Settings.System.FONT_SCALE, 1f)) + 1)
                                        % FONT_STEPS.length]);
                        postDelayed(this::populate, 150);
                    } catch (Throwable t) { open(Settings.ACTION_DISPLAY_SETTINGS); }
                }), rowLp());

        // screen sleep — SCREEN_OFF_TIMEOUT ladder
        int sl = Settings.System.getInt(c.getContentResolver(),
                Settings.System.SCREEN_OFF_TIMEOUT, 600_000);
        list.addView(row("Screen sleeps after · " + SLEEP_NAMES[nearestInt(SLEEP_STEPS, sl)],
                "tap to change", () -> {
                    if (!Caps.writeSettings(c)) { open(Settings.ACTION_DISPLAY_SETTINGS); return; }
                    try {
                        int cur = Settings.System.getInt(c.getContentResolver(),
                                Settings.System.SCREEN_OFF_TIMEOUT, 600_000);
                        Settings.System.putInt(c.getContentResolver(),
                                Settings.System.SCREEN_OFF_TIMEOUT,
                                SLEEP_STEPS[(nearestInt(SLEEP_STEPS, cur) + 1) % SLEEP_STEPS.length]);
                        populate();
                    } catch (Throwable t) { open(Settings.ACTION_DISPLAY_SETTINGS); }
                }), rowLp());

        // sound — media volume as words
        try {
            AudioManager am = c.getSystemService(AudioManager.class);
            int max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            float cur = max <= 0 ? 0 : am.getStreamVolume(AudioManager.STREAM_MUSIC) / (float) max;
            int si = nearest(SOUND_LEVELS, cur);
            list.addView(row("Sound · " + SOUND_NAMES[si], "tap for the next level", () -> {
                try {
                    AudioManager a2 = c.getSystemService(AudioManager.class);
                    int m2 = a2.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
                    float c2 = m2 <= 0 ? 0 : a2.getStreamVolume(AudioManager.STREAM_MUSIC) / (float) m2;
                    int next = (nearest(SOUND_LEVELS, c2) + 1) % SOUND_LEVELS.length;
                    a2.setStreamVolume(AudioManager.STREAM_MUSIC,
                            Math.round(SOUND_LEVELS[next] * m2), 0);
                    populate();
                } catch (Throwable t) { open(Settings.ACTION_SOUND_SETTINGS); }
            }), rowLp());
        } catch (Throwable ignored) {}

        // alarms — the clock app owns them; we show the next one
        String alarm = Toggles.nextAlarmText(c);
        list.addView(row(alarm.isEmpty() ? "Alarms" : "Alarms · " + alarm.replace("alarm ", ""),
                "set or change in the clock app", () -> {
                    try {
                        getContext().startActivity(new Intent(AlarmClock.ACTION_SHOW_ALARMS)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                    } catch (Throwable t) { /* no clock app — stay put */ }
                    closePanel.run();
                }), rowLp());

        // storage — one honest line, nothing to press
        try {
            StatFs fs2 = new StatFs(Environment.getDataDirectory().getPath());
            long free = Math.round(fs2.getAvailableBytes() / 1e9);
            long total = Math.round(fs2.getTotalBytes() / 1e9);
            list.addView(row("Storage · " + free + " GB free of " + total, null, null), rowLp());
        } catch (Throwable ignored) {}

        // hand-offs, plainly labeled
        list.addView(row("Wallpaper", "choose a new one", () -> {
            try {
                getContext().startActivity(new Intent(Intent.ACTION_SET_WALLPAPER)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (Throwable ignored) {}
            closePanel.run();
        }), rowLp());
        list.addView(row("Date & time", "opens system settings",
                () -> open(Settings.ACTION_DATE_SETTINGS)), rowLp());
        list.addView(row("Software update", "check for a new Sol:OS", () -> {
            try {
                getContext().startActivity(new Intent("android.settings.SYSTEM_UPDATE_SETTINGS")
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                closePanel.run();
            } catch (Throwable t) { open(Settings.ACTION_DEVICE_INFO_SETTINGS); }
        }), rowLp());

        // and the shade's own control room, since it left the footer
        list.addView(row("Shade setup", "how this panel opens and behaves",
                openShadeSetup::run), rowLp());
    }

    private static int nearest(float[] steps, float v) {
        int best = 0;
        for (int i = 1; i < steps.length; i++)
            if (Math.abs(steps[i] - v) < Math.abs(steps[best] - v)) best = i;
        return best;
    }

    private static int nearestInt(int[] steps, int v) {
        int best = 0;
        for (int i = 1; i < steps.length; i++)
            if (Math.abs(steps[i] - v) < Math.abs(steps[best] - v)) best = i;
        return best;
    }
}
