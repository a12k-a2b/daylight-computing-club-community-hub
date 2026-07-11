package club.daylightcomputer.tracingpaper;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

/**
 * The little home screen: turn the service on, learn the button, set the
 * pen and backlight preferences, export everything. Big type, black on
 * white, 2px borders — made for a monochrome reflective screen.
 */
public class MainActivity extends Activity {

    private TextView status;
    private TextView keyLabel;
    private SharedPreferences prefs;

    private final BroadcastReceiver calibrated = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { refresh(); }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = Prefs.get(this);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        int p = dp(20);
        col.setPadding(p, p, p, p);

        TextView title = text("Tracing Paper", 30, true);
        col.addView(title);
        TextView sub = text("A sheet of glass over whatever you're reading. "
                + "Press the top button, jot the thought, press it again — "
                + "you never lost your place.", 17, false);
        sub.setPadding(0, dp(6), 0, dp(14));
        col.addView(sub);

        status = text("", 18, true);
        status.setPadding(dp(14), dp(14), dp(14), dp(14));
        status.setBackground(border(false));
        col.addView(status, fill());

        col.addView(gap());
        col.addView(bigButton("Turn the pad service on", v ->
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))));
        col.addView(gap());
        col.addView(bigButton("Open the pad now", v -> {
            if (!PadService.togglePad(this))
                Toast.makeText(this, "Turn the pad service on first", Toast.LENGTH_SHORT).show();
        }));

        col.addView(sectionRule("The button"));
        keyLabel = text("", 17, false);
        col.addView(keyLabel);
        col.addView(gap());
        col.addView(bigButton("Re-learn the button", v -> {
            if (PadService.instance == null) {
                Toast.makeText(this, "Turn the pad service on first", Toast.LENGTH_SHORT).show();
                return;
            }
            prefs.edit().putLong(Prefs.K_CALIBRATE_UNTIL,
                    System.currentTimeMillis() + 15000).apply();
            Toast.makeText(this, "Now press the hardware button you want (15s)…",
                    Toast.LENGTH_LONG).show();
        }));

        col.addView(sectionRule("How it writes"));
        Switch penOnly = mkSwitch("Only the pen draws (rest your palm anywhere)",
                prefs.getBoolean(Prefs.K_PEN_ONLY, false));
        penOnly.setOnCheckedChangeListener((v, on) ->
                prefs.edit().putBoolean(Prefs.K_PEN_ONLY, on).apply());
        col.addView(penOnly);

        Switch backlight = mkSwitch("Volume-down flips the backlight while the pad is open",
                prefs.getBoolean(Prefs.K_SIDE_BACKLIGHT, false));
        backlight.setOnCheckedChangeListener((v, on) -> {
            prefs.edit().putBoolean(Prefs.K_SIDE_BACKLIGHT, on).apply();
            if (on && !Settings.System.canWrite(this)) {
                Toast.makeText(this, "Allow “Modify system settings” so the pad can reach the backlight",
                        Toast.LENGTH_LONG).show();
                startActivity(new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS,
                        Uri.parse("package:" + getPackageName())));
            }
        });
        col.addView(backlight);

        col.addView(sectionRule("Your notes"));
        col.addView(bigButton("Export every page to PDF", v -> exportAll()));
        col.addView(gap());
        TextView fine = text("Snapshots land in Pictures/Tracing Paper, PDFs in "
                + "Download/Tracing Paper. Notes live only on this tablet.\n\n"
                + "On the glass: PEN and ERASE to switch nibs (the pen's other end erases too), "
                + "CLEAR wipes a page (hold it to tear the page out), FROST cycles clear glass → "
                + "frosted → paper, PEEK lets you scroll the page underneath without closing your "
                + "notes, SNAP saves a picture of notes-over-page, and holding the top button "
                + "snaps one too.", 15, false);
        fine.setTextColor(0xFF333333);
        col.addView(fine);

        ScrollView sc = new ScrollView(this);
        sc.setBackgroundColor(Color.WHITE);
        sc.addView(col);
        setContentView(sc);
    }

    @Override
    protected void onResume() {
        super.onResume();
        IntentFilter f = new IntentFilter(Prefs.CALIBRATED_ACTION);
        if (Build.VERSION.SDK_INT >= 33)
            registerReceiver(calibrated, f, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(calibrated, f);
        refresh();
    }

    @Override
    protected void onPause() {
        super.onPause();
        try { unregisterReceiver(calibrated); } catch (Exception ignored) {}
    }

    private void refresh() {
        boolean on = PadService.instance != null;
        status.setText(on ? "Pad service: ON — the top button is listening"
                : "Pad service: OFF — turn it on below, under Accessibility");
        int key = prefs.getInt(Prefs.K_TOGGLE, Prefs.DEFAULT_TOGGLE);
        keyLabel.setText("The pad opens with: " + PadService.keyName(key)
                + (key == Prefs.DEFAULT_TOGGLE ? " (the DC-1's orange top button)" : ""));
    }

    private void exportAll() {
        Toast.makeText(this, "Making the PDF…", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                float aspect = (float) prefs.getInt(Prefs.K_CANVAS_W, 1200)
                        / Math.max(1, prefs.getInt(Prefs.K_CANVAS_H, 1600));
                String name = Exporter.exportPdf(this, new NoteStore(this).load(), aspect);
                runOnUiThread(() -> Toast.makeText(this,
                        "Saved " + name + " in Download", Toast.LENGTH_LONG).show());
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this,
                        "PDF export failed", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    // ------------------------------------------------------------ ui helpers

    private TextView text(String s, int size, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(size);
        t.setTextColor(Color.BLACK);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private TextView bigButton(String label, View.OnClickListener tap) {
        TextView t = text(label, 18, true);
        t.setGravity(Gravity.CENTER);
        t.setMinHeight(dp(56));
        t.setPadding(dp(16), dp(14), dp(16), dp(14));
        t.setBackground(border(false));
        t.setOnClickListener(tap);
        return t;
    }

    private Switch mkSwitch(String label, boolean checked) {
        Switch s = new Switch(this);
        s.setText(label);
        s.setChecked(checked);
        s.setTextSize(17);
        s.setTextColor(Color.BLACK);
        s.setMinHeight(dp(56));
        s.setPadding(0, dp(10), 0, dp(10));
        return s;
    }

    private View sectionRule(String label) {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setPadding(0, dp(22), 0, dp(8));
        View rule = new View(this);
        rule.setBackgroundColor(Color.BLACK);
        wrap.addView(rule, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(2)));
        TextView t = text(label, 20, true);
        t.setPadding(0, dp(10), 0, 0);
        wrap.addView(t);
        return wrap;
    }

    private View gap() {
        View v = new View(this);
        v.setMinimumHeight(dp(10));
        return v;
    }

    private LinearLayout.LayoutParams fill() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private GradientDrawable border(boolean filled) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(filled ? Color.BLACK : Color.WHITE);
        g.setStroke(dp(2), Color.BLACK);
        return g;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
