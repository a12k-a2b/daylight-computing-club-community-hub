package com.daylightcomputer.shade;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

/** Invisible hop so the stock-QS tile can collapse the stock shade and
 *  open ours in one tap. */
public class PanelTrampolineActivity extends Activity {
    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        startForegroundService(new Intent(this, ShadeService.class)
                .setAction(ShadeService.ACTION_SHOW));
        finish();
    }
}
