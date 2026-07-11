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
            if (all.isEmpty()) return null;
            for (MediaController mc : all) {
                PlaybackState s = mc.getPlaybackState();
                if (s != null && s.getState() == PlaybackState.STATE_PLAYING) return mc;
            }
            return all.get(0);
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
        return t == null ? "" : t.toString();
    }

    public static String artist(MediaController mc) {
        MediaMetadata m = mc == null ? null : mc.getMetadata();
        CharSequence t = m == null ? null : m.getText(MediaMetadata.METADATA_KEY_ARTIST);
        if (t == null && m != null) t = m.getText(MediaMetadata.METADATA_KEY_ALBUM_ARTIST);
        return t == null ? "" : t.toString();
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

    /** deltaSec may be negative. Falls back to fast-forward/rewind when the
     *  session doesn't report a position. */
    public static void seekBy(MediaController mc, int deltaSec) {
        if (mc == null) return;
        PlaybackState s = mc.getPlaybackState();
        if (s != null && s.getPosition() != PlaybackState.PLAYBACK_POSITION_UNKNOWN) {
            long pos = Math.max(0, s.getPosition() + deltaSec * 1000L);
            mc.getTransportControls().seekTo(pos);
        } else if (deltaSec > 0) {
            mc.getTransportControls().fastForward();
        } else {
            mc.getTransportControls().rewind();
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
