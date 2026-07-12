package com.daylightcomputer.shade.ui;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.provider.Settings;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.MotionEvent;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.daylightcomputer.shade.control.SettingsIndex;

import java.util.ArrayList;
import java.util.List;

/** "looking for a setting?" — ask in your own words, get the screen.
 *  Fully offline: the answers come from the bundled catalog in
 *  SettingsIndex (post-blessing, Settings' own live index). A no-match
 *  is still a door: it hands off to full Settings, plainly labeled.
 *  Hold the mic to speak instead of typing — the audio goes to the
 *  device's own speech service, never to us or the network. */
public class SettingsSearchView extends PickerPage {

    private final Runnable closePanel;
    private final EditText box;
    private final LinearLayout results;
    private final TextView status;
    private IconButton mic;
    private SpeechRecognizer rec;

    public SettingsSearchView(Context c, Runnable closePanel) {
        super(c);
        this.closePanel = closePanel;

        LinearLayout askRow = new LinearLayout(c);
        askRow.setOrientation(HORIZONTAL);

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
        askRow.addView(box, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        if (SpeechRecognizer.isRecognitionAvailable(c)) {
            mic = new IconButton(c, IconButton.Glyph.MIC, () -> {});
            mic.setOnTouchListener((v, e) -> {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN: startListening(); return true;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL: stopListening(); return true;
                }
                return false;
            });
            LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(
                    Ui.dp(c, 56), Ui.dp(c, 56));
            mlp.leftMargin = Ui.dp(c, 8);
            askRow.addView(mic, mlp);
        }
        addView(askRow, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        status = statusLine();
        addView(status);
        status.setVisibility(GONE);

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

    // ---- hold-to-speak ----

    private void say(String s) {
        status.setText(s);
        status.setVisibility(s == null || s.isEmpty() ? GONE : VISIBLE);
    }

    private void startListening() {
        Context c = getContext();
        if (c.checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            // permission lives with an activity — hand off to shade setup,
            // which asks contextually and plainly
            try {
                c.startActivity(new Intent(c, com.daylightcomputer.shade.MainActivity.class)
                        .putExtra("ask_mic", true)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (Throwable ignored) {}
            closePanel.run();
            return;
        }
        try {
            if (rec == null) {
                rec = SpeechRecognizer.createSpeechRecognizer(c);
                rec.setRecognitionListener(new RecognitionListener() {
                    @Override public void onReadyForSpeech(Bundle b) { say("listening…"); }
                    @Override public void onBeginningOfSpeech() {}
                    @Override public void onRmsChanged(float v) {}
                    @Override public void onBufferReceived(byte[] b) {}
                    @Override public void onEndOfSpeech() { say("thinking…"); }
                    @Override public void onError(int code) {
                        mic.setActive(false);
                        say(code == SpeechRecognizer.ERROR_NO_MATCH
                                || code == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
                                ? "didn't catch that — try again, or type"
                                : "the speech service isn't answering — typing still works");
                    }
                    @Override public void onResults(Bundle b) {
                        mic.setActive(false);
                        ArrayList<String> r = b == null ? null
                                : b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                        if (r != null && !r.isEmpty()) {
                            say("");
                            box.setText(r.get(0));
                            box.setSelection(box.getText().length());
                        } else {
                            say("didn't catch that — try again, or type");
                        }
                    }
                    @Override public void onPartialResults(Bundle b) {
                        ArrayList<String> r = b == null ? null
                                : b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                        if (r != null && !r.isEmpty() && !r.get(0).isEmpty()) {
                            box.setText(r.get(0));
                            box.setSelection(box.getText().length());
                        }
                    }
                    @Override public void onEvent(int t, Bundle b) {}
                });
            }
            Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            i.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
            i.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, c.getPackageName());
            mic.setActive(true);
            say("listening…");
            rec.startListening(i);
        } catch (Throwable t) {
            mic.setActive(false);
            say("the speech service isn't answering — typing still works");
        }
    }

    private void stopListening() {
        try { if (rec != null) rec.stopListening(); } catch (Throwable ignored) {}
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
        try { if (rec != null) { rec.destroy(); rec = null; } } catch (Throwable ignored) {}
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
