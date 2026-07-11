package com.daylightcomputer.shade;

import android.content.Intent;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

/** A "Daylight panel" tile inside the STOCK quick settings — the preview-
 *  mode stepping stone until full mode owns the swipe. */
public class ShadeTileService extends TileService {

    @Override public void onStartListening() {
        Tile t = getQsTile();
        if (t != null) { t.setState(Tile.STATE_ACTIVE); t.updateTile(); }
    }

    @SuppressWarnings("deprecation")
    @Override public void onClick() {
        Intent i = new Intent(this, PanelTrampolineActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivityAndCollapse(i);
        } catch (Throwable t) {
            startActivity(i);
        }
    }
}
