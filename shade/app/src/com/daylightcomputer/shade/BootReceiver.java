package com.daylightcomputer.shade;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent i) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(i.getAction())) return;
        // only wake up if there is something to stand ready for
        if (Prefs.STRIP_OFF.equals(Prefs.stripMode(c)) && !Prefs.takeOver(c)) return;
        c.startForegroundService(new Intent(c, ShadeService.class)
                .setAction(ShadeService.ACTION_APPLY));
    }
}
