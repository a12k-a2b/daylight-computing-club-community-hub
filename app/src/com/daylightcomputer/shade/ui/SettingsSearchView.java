package com.daylightcomputer.shade.ui;

import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;

import com.daylightcomputer.shade.control.SettingsIndex;

import java.util.List;

/** "looking for a setting?" — ask in your own words, get the screen.
 *  Fully offline: the answers come from the bundled catalog in
 *  SettingsIndex (post-blessing, Settings' own live index). A no-match
 *  is still a door: it hands off to full Settings, plainly labeled. */
public class SettingsSearchView extends PickerPage {

    private final Runnable closePanel;
    private final EditText box;
    private final LinearLayout results;

    public SettingsSearchView(Context c, Runnable closePanel) {
        super(c);
        this.closePanel = closePanel;

        box = new EditText(c);
        box.setTypeface(android.graphics.Typeface.SERIF);
        box.setTextSize(18);
        box.setTextColor(Ui.INK);
        box.setHintTextColor(Ui.FAINT);
        box.setHint("ask in your own words…");
        box.setSingleLine(true);
        box.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        box.setBackground(Ui.box(c, 2, Ui.PAPER, Ui.INK));
        int p = Ui.dp(c, 14);
        box.setPadding(p, p, p, p);
        addView(box, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        results = new LinearLayout(c);
        results.setOrientation(VERTICAL);
        addView(results, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        box.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int n) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int n) {}
            @Override public void afterTextChanged(Editable s) { populate(s.toString()); }
        });
        populate("");
    }

    @Override public String title() { return "find a setting"; }

    @Override public void start() {
        // raise the keyboard into the overlay
        box.requestFocus();
        box.post(() -> {
            try {
                InputMethodManager imm = getContext().getSystemService(InputMethodManager.class);
                if (imm != null) imm.showSoftInput(box, InputMethodManager.SHOW_IMPLICIT);
            } catch (Throwable ignored) {}
        });
    }

    @Override public void stop() {
        try {
            InputMethodManager imm = getContext().getSystemService(InputMethodManager.class);
            if (imm != null) imm.hideSoftInputFromWindow(box.getWindowToken(), 0);
        } catch (Throwable ignored) {}
    }

    private void open(Intent i) {
        try {
            getContext().startActivity(i);
        } catch (Throwable ignored) {}
        closePanel.run();
    }

    private void populate(String query) {
        Context c = getContext();
        results.removeAllViews();

        if (query.trim().isEmpty()) {
            results.addView(row("try “set up a pin code”", "or “make the text bigger”, "
                    + "or “is my storage full” — plain words work", null), rowLp());
            return;
        }

        List<SettingsIndex.Entry> hits = SettingsIndex.search(c, query);
        for (SettingsIndex.Entry en : hits) {
            results.addView(row(en.title, en.place + " — tap to open",
                    () -> open(en.intent())), rowLp());
        }
        if (hits.isEmpty()) {
            results.addView(row("nothing close by that name",
                    "tap for everything else…", () ->
                            open(new Intent(Settings.ACTION_SETTINGS)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))), rowLp());
        }
    }
}
