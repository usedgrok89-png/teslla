package com.family.schedule;

import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;

public class Reminders {

    /** Channel ids are versioned (v3): Android channels are immutable once created, so any
     *  change to the sound needs a new id or already-installed apps keep the broken one. */
    private static final String[] CH_ID = {"appt_v3_a", "appt_v3_b", "appt_v3_c"};
    private static final String[] CH_NAME = {"تنبيه بصوت مميز", "تنبيه بصوت هادي", "تنبيه بدون صوت"};
    /** raw resource *names* — the name form of android.resource:// is resolved reliably by the
     *  notification sound player, the numeric-id form is not on every Android version. */
    private static final String[] CH_SOUND = {"raw/ding", "raw/soft", null};

    public static final int SND_COUNT = 3;
    /** how many future occurrences of a repeating appointment we keep alarms for */
    public static final int HORIZON = 7;

    private static final String ACTION = "com.family.schedule.FIRE";

    static int clampSnd(int s) {
        return s < 0 || s >= SND_COUNT ? 0 : s;
    }

    static String channelId(int snd) {
        return CH_ID[clampSnd(snd)];
    }

    private static AudioAttributes attrs() {
        return new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
    }

    public static Uri soundUri(Context c, int snd) {
        if (CH_SOUND[clampSnd(snd)] == null) return null;
        return new Uri.Builder()
                .scheme("android.resource")
                .authority(c.getPackageName())
                .path(CH_SOUND[clampSnd(snd)])
                .build();
    }

    public static void ensureChannel(Context c, int snd) {
        snd = clampSnd(snd);
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        NotificationChannel ch = new NotificationChannel(CH_ID[snd], CH_NAME[snd], NotificationManager.IMPORTANCE_HIGH);
        ch.setSound(soundUri(c, snd), attrs());
        ch.enableVibration(true);
        ch.setShowBadge(true);
        ch.setLockscreenVisibility(android.app.Notification.VISIBILITY_PUBLIC);
        nm.createNotificationChannel(ch);
    }

    public static void ensureAll(Context c) {
        for (int i = 0; i < SND_COUNT; i++) ensureChannel(c, i);
    }

    /** Plays the sound in-app so the user can tell whether the audio itself works. */
    public static void preview(final Context c, int snd) {
        Uri u = soundUri(c, snd);
        if (u == null) return;
        try {
            MediaPlayer mp = MediaPlayer.create(c, u);
            if (mp == null) return;
            mp.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                public void onCompletion(MediaPlayer p) {
                    p.release();
                }
            });
            mp.start();
        } catch (Throwable ignored) {
        }
    }

    static int rc(String id) {
        int h = 7;
        for (int i = 0; i < id.length(); i++) h = h * 31 + id.charAt(i);
        return h & 0x7fffffff;
    }

    private static PendingIntent pi(Context c, String id, int k, long occ, boolean forCancel) {
        Intent i = new Intent(c, NotifyReceiver.class)
                .setAction(ACTION)
                .putExtra("id", id)
                .putExtra("occ", occ);
        int f = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        if (forCancel) f = PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE;
        return PendingIntent.getBroadcast(c, rc(id + "#" + k), i, f);
    }

    public static void cancel(Context c, String id) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        for (int k = 0; k < HORIZON; k++) {
            PendingIntent old = pi(c, id, k, 0L, true);
            if (old == null) continue;
            if (am != null) am.cancel(old);
            old.cancel();
        }
    }

    public static void scheduleAll(Context c) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        boolean exact = true;
        if (Build.VERSION.SDK_INT >= 31) {
            try {
                exact = am.canScheduleExactAlarms();
            } catch (Throwable t) {
                exact = false;
            }
        }
        long now = System.currentTimeMillis();
        Data d = Data.get(c);
        for (int i = 0; i < d.list.size(); i++) {
            Data.Appt a = d.list.get(i);
            cancel(c, a.id);
            if (a.lead <= 0) continue;

            long[] occ = Recur.next(a, now, HORIZON);
            for (int k = 0; k < occ.length; k++) {
                long at = occ[k] - a.lead * 60000L;
                if (at <= now) continue;
                // skip occurrences the user already resolved
                if (Recur.status(a, occ[k]) != Recur.PENDING) continue;
                PendingIntent p = pi(c, a.id, k, occ[k], false);
                if (p == null) continue;
                try {
                    if (exact && Build.VERSION.SDK_INT >= 23) {
                        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, p);
                    } else if (Build.VERSION.SDK_INT >= 23) {
                        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, p);
                    } else {
                        am.set(AlarmManager.RTC_WAKEUP, at, p);
                    }
                } catch (SecurityException se) {
                    am.set(AlarmManager.RTC_WAKEUP, at, p);
                }
            }
        }
    }
}
