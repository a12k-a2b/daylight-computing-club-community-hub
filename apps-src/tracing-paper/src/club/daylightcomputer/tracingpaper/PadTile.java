package club.daylightcomputer.tracingpaper;

import android.content.Intent;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

/** Quick-settings tile: one tap opens or closes the pad. */
public class PadTile extends TileService {

    @Override
    public void onStartListening() {
        Tile t = getQsTile();
        if (t == null) return;
        boolean svc = PadService.instance != null;
        t.setState(svc ? (PadService.isShown() ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE)
                : Tile.STATE_UNAVAILABLE);
        t.setLabel(getString(R.string.app_name));
        t.updateTile();
    }

    @Override
    public void onClick() {
        if (!PadService.togglePad(this)) {
            Intent i = new Intent(this, MainActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivityAndCollapse(i);
        } else {
            onStartListening();
        }
    }
}
