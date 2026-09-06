package com.daylightcomputer.shade.control;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.media.Rating;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.util.Log;

import com.daylightcomputer.shade.ShadeNLService;

import java.util.List;

/** Wraps the foreground media session (Spotify, Audible, …). Access rides on
 *  the notification-listener grant — the same one the notification list
 *  needs — so this works today, no OS change required. */
public final class Media {
    private static final String TAG = "ShadeMedia";
    private Media() {}

    public static MediaController current(Context c) {
        if (!Caps.notifListener(c)) return null;
        try {
            MediaSessionManager msm = c.getSystemService(MediaSessionManager.class);
            List<MediaController> all = msm.getActiveSessions(
                    new ComponentName(c, ShadeNLService.class));
            // playing first; else the first live (paused/buffering) session.
            // Dormant controllers (STATE_NONE/stopped/error — e.g. Audible
            // hours after listening) are not "something playing": skip them.
            MediaController alive = null;
            for (MediaController mc : all) {
                PlaybackState s = mc.getPlaybackState();
                int st = s == null ? PlaybackState.STATE_NONE : s.getState();
                if (st == PlaybackState.STATE_PLAYING) return mc;
                if (alive == null && st != PlaybackState.STATE_NONE
                        && st != PlaybackState.STATE_STOPPED
                        && st != PlaybackState.STATE_ERROR) alive = mc;
            }
            return alive;
        } catch (Throwable t) {
            Log.w(TAG, "getActiveSessions: " + t);
            return null;
        }
    }

    public static boolean isPlaying(MediaController mc) {
        PlaybackState s = mc == null ? null : mc.getPlaybackState();
        return s != null && s.getState() == PlaybackState.STATE_PLAYING;
    }

    public static String title(MediaController mc) {
        MediaMetadata m = mc == null ? null : mc.getMetadata();
        CharSequence t = m == null ? null : m.getText(MediaMetadata.METADATA_KEY_TITLE);
        if ((t == null || t.toString().trim().isEmpty()) && m != null)
            t = m.getText(MediaMetadata.METADATA_KEY_DISPLAY_TITLE);
        return t == null ? "" : t.toString().trim();
    }

    public static String artist(MediaController mc) {
        MediaMetadata m = mc == null ? null : mc.getMetadata();
        CharSequence t = m == null ? null : m.getText(MediaMetadata.METADATA_KEY_ARTIST);
        if (t == null && m != null) t = m.getText(MediaMetadata.METADATA_KEY_ALBUM_ARTIST);
        return t == null ? "" : t.toString().trim();
    }

    public static String appLabel(Context c, MediaController mc) {
        if (mc == null) return "";
        try {
            PackageManager pm = c.getPackageManager();
            ApplicationInfo ai = pm.getApplicationInfo(mc.getPackageName(), 0);
            return pm.getApplicationLabel(ai).toString();
        } catch (Throwable t) {
            return mc.getPackageName();
        }
    }

    public static void playPause(MediaController mc) {
        if (mc == null) return;
        if (isPlaying(mc)) mc.getTransportControls().pause();
        else mc.getTransportControls().play();
    }

    public static void next(MediaController mc) {
        if (mc != null) mc.getTransportControls().skipToNext();
    }

    public static void prev(MediaController mc) {
        if (mc != null) mc.getTransportControls().skipToPrevious();
    }

    /** deltaSec may be negative. Prefers a real seek; falls back to the
     *  app's own jump custom action (Audible's ±30s), then fast-forward/
     *  rewind. */
    public static void seekBy(MediaController mc, int deltaSec) {
        if (mc == null) return;
        PlaybackState s = mc.getPlaybackState();
        if (s != null && s.getPosition() != PlaybackState.PLAYBACK_POSITION_UNKNOWN
                && (s.getActions() & PlaybackState.ACTION_SEEK_TO) != 0) {
            long pos = Math.max(0, s.getPosition() + deltaSec * 1000L);
            mc.getTransportControls().seekTo(pos);
            return;
        }
        for (PlaybackState.CustomAction a : customActions(mc)) {
            if (isJump(a, deltaSec > 0)) { sendCustom(mc, a); return; }
        }
        if (deltaSec > 0) mc.getTransportControls().fastForward();
        else mc.getTransportControls().rewind();
    }

    // ---- custom actions (how most apps expose like/favorite, jumps, …) ----

    public static List<PlaybackState.CustomAction> customActions(MediaController mc) {
        PlaybackState s = mc == null ? null : mc.getPlaybackState();
        List<PlaybackState.CustomAction> a = s == null ? null : s.getCustomActions();
        return a == null ? java.util.Collections.emptyList() : a;
    }

    private static boolean matches(PlaybackState.CustomAction a, String... needles) {
        String hay = (a.getAction() + " " + a.getName())
                .toLowerCase(java.util.Locale.ROOT).replace('-', '_');
        for (String n : needles) if (hay.contains(n)) return true;
        return false;
    }

    /** The app's like/favorite action, if it publishes one (Spotify does —
     *  as a custom action, not via the rating API). */
    public static PlaybackState.CustomAction likeAction(MediaController mc) {
        for (PlaybackState.CustomAction a : customActions(mc)) {
            if (matches(a, "dislike", "thumbs_down", "thumb_down")) continue;
            if (matches(a, "like", "heart", "favorite", "favourite",
                    "thumbs_up", "thumb_up", "add_to_collection", "add_to_library",
                    "save_to")) return a;
        }
        return null;
    }

    /** Heuristic: does the like action's current label say "already liked"?
     *  (The action list updates on every playback-state change, so the
     *  heart re-reads this after each tap.) */
    public static boolean likeShowsLiked(PlaybackState.CustomAction a) {
        return a != null && matches(a, "unlike", "unfavorite", "unfavourite",
                "remove", "liked", "saved", "added");
    }

    private static boolean isJump(PlaybackState.CustomAction a, boolean forward) {
        if (forward) {
            return matches(a, "fast_forward", "jump_forward", "skip_forward",
                    "seek_forward", "forward_10", "forward_15", "forward_30",
                    "jump_ahead", "fastforward");
        }
        return matches(a, "rewind", "jump_back", "skip_back", "seek_back",
                "replay_10", "replay_15", "replay_30", "back_10", "back_15",
                "back_30", "jumpback");
    }

    /** Everything else the app offers (shuffle, repeat, sleep timer…) —
     *  minus the like and jump actions the card already covers. Max 4,
     *  calm over complete. */
    public static List<PlaybackState.CustomAction> extraActions(MediaController mc) {
        List<PlaybackState.CustomAction> out = new java.util.ArrayList<>();
        PlaybackState.CustomAction like = likeAction(mc);
        for (PlaybackState.CustomAction a : customActions(mc)) {
            if (like != null && a.getAction().equals(like.getAction())) continue;
            if (isJump(a, true) || isJump(a, false)) continue;
            out.add(a);
            if (out.size() == 4) break;
        }
        return out;
    }

    public static void sendCustom(MediaController mc, PlaybackState.CustomAction a) {
        if (mc == null || a == null) return;
        try { mc.getTransportControls().sendCustomAction(a, null); }
        catch (Throwable t) { Log.w(TAG, "sendCustomAction: " + t); }
    }

    /** The action's icon, loaded from the media app's own package and
     *  re-inked to match the shade. Null when unloadable (caller skips). */
    public static android.graphics.drawable.Drawable customIcon(
            Context c, MediaController mc, PlaybackState.CustomAction a, int tint) {
        try {
            android.content.res.Resources res = c.getPackageManager()
                    .getResourcesForApplication(mc.getPackageName());
            android.graphics.drawable.Drawable d = res.getDrawable(a.getIcon(), null);
            if (d == null) return null;
            d = d.mutate();
            d.setTint(tint);
            return d;
        } catch (Throwable t) {
            return null;
        }
    }

    public static boolean supportsHeart(MediaController mc) {
        return mc != null && mc.getRatingType() == Rating.RATING_HEART;
    }

    public static boolean hearted(MediaController mc) {
        MediaMetadata m = mc == null ? null : mc.getMetadata();
        Rating r = m == null ? null : m.getRating(MediaMetadata.METADATA_KEY_USER_RATING);
        return r != null && r.hasHeart();
    }

    public static void toggleHeart(MediaController mc) {
        if (mc == null) return;
        mc.getTransportControls().setRating(Rating.newHeartRating(!hearted(mc)));
    }
}
