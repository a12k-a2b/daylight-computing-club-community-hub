package com.daylightcomputer.shade;

import android.app.Application;

import com.daylightcomputer.shade.control.SysApi;

/** The failure story starts here: if this process ever dies unexpectedly,
 *  hand the pull-down back to the stock shade first (the OS also does this
 *  on its own — SystemUI drops a dead process's disable flags — this is
 *  belt and braces), note the crash for the crash-loop breaker, then die
 *  normally so the system can restart the service. */
public class ShadeApp extends Application {
    @Override public void onCreate() {
        super.onCreate();
        final Thread.UncaughtExceptionHandler previous =
                Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, e) -> {
            try { SysApi.setStockShadeBlocked(this, false); } catch (Throwable ignored) {}
            try { Prefs.noteCrash(this); } catch (Throwable ignored) {}
            if (previous != null) previous.uncaughtException(thread, e);
            else android.os.Process.killProcess(android.os.Process.myPid());
        });
    }
}
