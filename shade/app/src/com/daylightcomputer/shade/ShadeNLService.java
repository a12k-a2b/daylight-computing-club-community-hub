package com.daylightcomputer.shade;

import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import java.util.ArrayList;
import java.util.List;

/** One grant, two gifts: this listener feeds both the media card (session
 *  access rides on notification access) and the panel's own notification
 *  list — which is how the panel replaces the stock shade without any
 *  passthrough. Runs in the same process as the panel, so a static bridge
 *  is enough. */
public class ShadeNLService extends NotificationListenerService {

    public interface Watcher { void onNotificationsChanged(); }

    private static ShadeNLService live;
    private static Watcher watcher;

    public static void setWatcher(Watcher w) { watcher = w; }

    public static boolean connected() { return live != null; }

    /** Current notifications, newest first, ongoing/foreground ones last. */
    public static List<StatusBarNotification> current() {
        List<StatusBarNotification> out = new ArrayList<>();
        ShadeNLService s = live;
        if (s == null) return out;
        try {
            StatusBarNotification[] active = s.getActiveNotifications();
            if (active == null) return out;
            List<StatusBarNotification> ongoing = new ArrayList<>();
            for (StatusBarNotification sbn : active) {
                if (sbn.isOngoing()) ongoing.add(sbn); else out.add(sbn);
            }
            out.sort((a, b) -> Long.compare(b.getPostTime(), a.getPostTime()));
            ongoing.sort((a, b) -> Long.compare(b.getPostTime(), a.getPostTime()));
            out.addAll(ongoing);
        } catch (Throwable ignored) {}
        return out;
    }

    public static void dismiss(String key) {
        ShadeNLService s = live;
        if (s != null) try { s.cancelNotification(key); } catch (Throwable ignored) {}
    }

    public static void dismissAll() {
        ShadeNLService s = live;
        if (s != null) try { s.cancelAllNotifications(); } catch (Throwable ignored) {}
    }

    private void poke() {
        Watcher w = watcher;
        if (w != null) w.onNotificationsChanged();
    }

    @Override public void onListenerConnected() { live = this; poke(); }
    @Override public void onListenerDisconnected() { if (live == this) live = null; poke(); }
    @Override public void onNotificationPosted(StatusBarNotification sbn) { poke(); }
    @Override public void onNotificationRemoved(StatusBarNotification sbn) { poke(); }
}
