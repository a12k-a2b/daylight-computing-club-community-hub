package com.daylightcomputer.shade.ui;

import android.content.Context;
import android.widget.LinearLayout;

/** A second page inside the shade (Wi-Fi networks, Bluetooth devices).
 *  Lifecycle is explicit: start() when shown (register receivers, kick off
 *  scans), stop() when hidden — a picker must cost nothing once closed. */
public abstract class PickerPage extends LinearLayout {
    public PickerPage(Context c) {
        super(c);
        setOrientation(VERTICAL);
        int p = Ui.dp(c, 20);
        setPadding(p, Ui.dp(c, 6), p, Ui.dp(c, 14));
    }

    public abstract String title();
    public abstract void start();
    public abstract void stop();

    /** A tappable row: title + state line, faint border. */
    protected LinearLayout row(String main, String sub, Runnable onTap) {
        Context c = getContext();
        LinearLayout r = new LinearLayout(c);
        r.setOrientation(VERTICAL);
        r.setPadding(Ui.dp(c, 14), Ui.dp(c, 10), Ui.dp(c, 14), Ui.dp(c, 10));
        r.setBackground(Ui.box(c, 2, Ui.PAPER, Ui.INK));
        r.setMinimumHeight(Ui.dp(c, 56));
        android.widget.TextView t = Ui.text(c, 16, Ui.INK, true, true);
        t.setText(main);
        t.setSingleLine();
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        r.addView(t);
        if (sub != null && !sub.isEmpty()) {
            android.widget.TextView s = Ui.text(c, 13.5f, Ui.MID, true, false);
            s.setText(sub);
            s.setSingleLine();
            s.setEllipsize(android.text.TextUtils.TruncateAt.END);
            r.addView(s);
        }
        if (onTap != null) {
            r.setClickable(true);
            r.setOnClickListener(v -> onTap.run());
        }
        return r;
    }

    protected LinearLayout.LayoutParams rowLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(getContext(), 8);
        return lp;
    }

    protected android.widget.TextView statusLine() {
        android.widget.TextView s = Ui.text(getContext(), 14, Ui.MID, true, false);
        s.setPadding(Ui.dp(getContext(), 2), Ui.dp(getContext(), 10), 0, Ui.dp(getContext(), 2));
        return s;
    }
}
