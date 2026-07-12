package com.daylightcomputer.shade.control;

import android.content.Context;
import android.content.Intent;
import android.provider.Settings;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** The lantern for the attic: a plain-language index over Android's
 *  Settings, written in the vocabulary of someone arriving from an
 *  iPhone. Ask in your own words ("set up a pin code"), get the screen.
 *
 *  v1 is a bundled, hand-curated catalog — offline, deterministic, no
 *  model, no network (the shade's no-INTERNET promise stands). Post-
 *  blessing, READ_SEARCH_INDEXABLES swaps this for Settings' own live
 *  index (see README, the platform ask); the synonym layer stays either
 *  way. Entries whose screen doesn't exist on this build are dropped at
 *  first use, so the list never offers a dead door.
 *
 *  `key` (optional) names the exact preference row: Settings scrolls to
 *  it and flashes it (the ":settings:fragment_args_key" contract its own
 *  search results use). Keys are per-AOSP-version data — only ship ones
 *  verified on glass; a null key just opens the screen, unhighlighted. */
public final class SettingsIndex {
    private SettingsIndex() {}

    public static final class Entry {
        public final String title;     // in our voice
        public final String place;     // "in Security" — where it lives
        public final String keywords;  // lowercase, space-separated synonyms
        public final String action;    // Settings intent action
        public final String key;       // optional highlight key (verified only)

        Entry(String title, String place, String keywords, String action, String key) {
            this.title = title; this.place = place;
            this.keywords = keywords; this.action = action; this.key = key;
        }

        public Intent intent() {
            Intent i = new Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (key != null) i.putExtra(":settings:fragment_args_key", key);
            return i;
        }
    }

    private static Entry e(String title, String place, String keywords, String action) {
        return new Entry(title, place, keywords, action, null);
    }

    // The catalog. Titles in our voice; keywords in theirs — iPhone words
    // first ("passcode", "airdrop"…), Android words too, typos welcome.
    private static final Entry[] ALL = {
        e("PIN & screen lock", "in Security",
                "pin passcode password code lock unlock screen lock fingerprint touch id secure security",
                Settings.ACTION_SECURITY_SETTINGS),
        e("Wi-Fi networks", "in Network & internet",
                "wifi wi-fi wireless internet network networks join password router",
                Settings.ACTION_WIFI_SETTINGS),
        e("Bluetooth devices", "in Connected devices",
                "bluetooth pair pairing earbuds headphones airpods speaker keyboard connect",
                Settings.ACTION_BLUETOOTH_SETTINGS),
        e("Airplane Mode", "in Network & internet",
                "airplane flight plane travel radios offline",
                Settings.ACTION_AIRPLANE_MODE_SETTINGS),
        e("Screen & brightness", "in Display",
                "display screen brightness dim sleep timeout dark light auto rotate rotation font",
                Settings.ACTION_DISPLAY_SETTINGS),
        e("Sounds & volume", "in Sound",
                "sound volume ringtone mute silent vibrate alarm sounds media loud",
                Settings.ACTION_SOUND_SETTINGS),
        e("Do Not Disturb", "in Sound",
                "do not disturb dnd focus quiet interruptions silence moon",
                "android.settings.ZEN_MODE_SETTINGS"),
        e("Notifications", "in Apps & notifications",
                "notifications alerts banners badges interruptions app notifications",
                "android.settings.NOTIFICATION_SETTINGS"),
        e("Storage", "in Storage",
                "storage space full free gb memory disk room",
                Settings.ACTION_INTERNAL_STORAGE_SETTINGS),
        e("Battery", "in Battery",
                "battery power charge charging percent saver drain life",
                Intent.ACTION_POWER_USAGE_SUMMARY),
        e("Battery saver", "in Battery",
                "battery saver low power mode save power",
                Settings.ACTION_BATTERY_SAVER_SETTINGS),
        e("Language", "in System",
                "language english spanish french locale region translate",
                Settings.ACTION_LOCALE_SETTINGS),
        e("Keyboard", "in System",
                "keyboard typing autocorrect spell input swipe type",
                Settings.ACTION_INPUT_METHOD_SETTINGS),
        e("Date & time", "in System",
                "date time clock timezone zone hour am pm automatic",
                Settings.ACTION_DATE_SETTINGS),
        e("Accessibility", "in Accessibility",
                "accessibility bigger text zoom magnify contrast talkback read aloud vision hearing motor",
                Settings.ACTION_ACCESSIBILITY_SETTINGS),
        e("Apps", "in Apps",
                "apps applications uninstall delete remove app info force stop clear",
                Settings.ACTION_APPLICATION_SETTINGS),
        e("Default apps", "in Apps",
                "default apps browser launcher home app opens with",
                Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS),
        e("Location", "in Location",
                "location gps maps find my whereabouts tracking",
                Settings.ACTION_LOCATION_SOURCE_SETTINGS),
        e("Privacy & permissions", "in Privacy",
                "privacy permissions camera microphone mic contacts allow deny",
                "android.settings.PRIVACY_SETTINGS"),
        e("VPN", "in Network & internet",
                "vpn private network tunnel",
                Settings.ACTION_VPN_SETTINGS),
        e("Hotspot & sharing internet", "in Network & internet",
                "hotspot tether tethering share internet personal hotspot",
                "android.settings.TETHER_SETTINGS"),
        e("Cast the screen", "in Connected devices",
                "cast mirror screen mirroring chromecast airplay project tv",
                Settings.ACTION_CAST_SETTINGS),
        e("NFC & tap to pay", "in Connected devices",
                "nfc tap pay contactless",
                Settings.ACTION_NFC_SETTINGS),
        e("Accounts", "in Passwords & accounts",
                "accounts account google email sync sign in log in",
                Settings.ACTION_SYNC_SETTINGS),
        e("Software update", "in System",
                "update upgrade software version new sol:os os install",
                "android.settings.SYSTEM_UPDATE_SETTINGS"),
        e("About this tablet", "in About",
                "about device info serial model version android sol:os legal",
                Settings.ACTION_DEVICE_INFO_SETTINGS),
        e("Backup", "in System",
                "backup back up restore save data",
                "android.settings.BACKUP_AND_RESET_SETTINGS"),
        e("Print", "in Connected devices",
                "print printer printing",
                Settings.ACTION_PRINT_SETTINGS),
        e("Developer options", "in System",
                "developer options usb debugging adb debug",
                Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS),
        e("Wallpaper", "picker",
                "wallpaper background home screen picture photo",
                Intent.ACTION_SET_WALLPAPER),
        e("Alarms", "in the clock app",
                "alarm alarms wake wake up clock timer",
                android.provider.AlarmClock.ACTION_SHOW_ALARMS),
    };

    private static List<Entry> sLive; // entries whose door exists here

    private static List<Entry> live(Context c) {
        if (sLive == null) {
            List<Entry> out = new ArrayList<>();
            for (Entry en : ALL) {
                try {
                    if (en.intent().resolveActivity(c.getPackageManager()) != null) out.add(en);
                } catch (Throwable ignored) {}
            }
            sLive = out;
        }
        return sLive;
    }

    /** Ask in your own words; up to six doors back, best first. */
    public static List<Entry> search(Context c, String query) {
        List<Entry> out = new ArrayList<>();
        if (query == null) return out;
        String[] tokens = query.toLowerCase(Locale.ROOT).split("[^a-z0-9:+-]+");
        List<Entry> live = live(c);
        int[] scores = new int[live.size()];
        for (int i = 0; i < live.size(); i++) {
            Entry en = live.get(i);
            String title = en.title.toLowerCase(Locale.ROOT);
            int s = 0;
            for (String t : tokens) {
                if (t.length() < 2) continue;
                if (title.contains(t)) s += 3;
                for (String k : en.keywords.split(" ")) {
                    if (k.equals(t)) { s += 3; break; }
                    if (t.length() >= 3 && k.startsWith(t)) { s += 2; break; }
                }
            }
            scores[i] = s;
        }
        for (int n = 0; n < 6; n++) {
            int best = -1;
            for (int i = 0; i < live.size(); i++)
                if (scores[i] > 0 && (best < 0 || scores[i] > scores[best])) best = i;
            if (best < 0) break;
            out.add(live.get(best));
            scores[best] = 0;
        }
        return out;
    }
}
