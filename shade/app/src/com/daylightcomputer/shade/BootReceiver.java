package com.daylightcomputer.shade;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent i) {
        // boot, and also app updates — otherwise the strip stays down after
        // every new APK until a reboot or a manual visit to shade setup
        if (!Intent.ACTION_BOOT_COMPLETED.equals(i.getAction())
                && !Intent.ACTION_MY_PACKAGE_REPLACED.equals(i.getAction())) return;
        // only wake up if there is something to stand ready for
        if (Prefs.STRIP_OFF.equals(Prefs.stripMode(c)) && !Prefs.takeOver(c)) return;
        c.startForegroundService(new Intent(c, ShadeService.class)
                .setAction(ShadeService.ACTION_APPLY));
    }
}
