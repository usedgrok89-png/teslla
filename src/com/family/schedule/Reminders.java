package com.family.schedule;

import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

public class Reminders {

    /** Channel ids are versioned: Android channels are immutable once created, so a new
     *  sound must live in a new channel id or already-installed apps keep the old silent one. */
    private static final String[] CH_ID = {"appts_s0", "appts_s1", "appts_s2"};
    private static final String[] CH_NAME = {"تنبيه بصوت مميز", "تنبيه بصوت هادي", "تنبيه بدون صوت"};
    private static final int[] CH_RES = {R.raw.ding, R.raw.soft, 0};

    public static final int SND_COUNT = 3;

    private static final String ACTION = "com.family.schedule.FIRE";

    static int clampSnd(int s) {
        return s < 0 || s >= SND_COUNT ? 0 : s;
    }

    static String channelId(int snd) {
        return CH_ID[clampSnd(snd)];
    }

    public static void ensureChannel(Context c, int snd) {
        snd = clampSnd(snd);
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        NotificationChannel ch = new NotificationChannel(CH_ID[snd], CH_NAME[snd], NotificationManager.IMPORTANCE_HIGH);
        AudioAttributes attrs = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
        if (CH_RES[snd] == 0) {
            ch.setSound(null, null);
        } else {
            Uri u = new Uri.Builder()
                    .scheme("android.resource")
                    .authority(c.getPackageName())
                    .appendPath(String.valueOf(CH_RES[snd]))
                    .build();
            ch.setSound(u, attrs);
        }
        ch.enableVibration(true);
        ch.setShowBadge(true);
        nm.createNotificationChannel(ch);
    }

    public static void ensureAll(Context c) {
        for (int i = 0; i < SND_COUNT; i++) ensureChannel(c, i);
    }

    static int rc(String id) {
        int h = 7;
        for (int i = 0; i < id.length(); i++) h = h * 31 + id.charAt(i);
        return h & 0x7fffffff;
    }

    private static PendingIntent pi(Context c, String id, boolean forCancel) {
        Intent i = new Intent(c, NotifyReceiver.class).setAction(ACTION).putExtra("id", id);
        int f = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        if (forCancel) f = PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE;
        return PendingIntent.getBroadcast(c, rc(id), i, f);
    }

    public static void cancel(Context c, String id) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        PendingIntent old = pi(c, id, true);
        if (old == null) return;
        if (am != null) am.cancel(old);
        old.cancel();
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
        Data d = Data.get(c);
        for (int i = 0; i < d.list.size(); i++) {
            Data.Appt a = d.list.get(i);
            cancel(c, a.id);
            if (a.done || a.lead <= 0) continue;
            long at = a.when - a.lead * 60000L;
            if (at <= System.currentTimeMillis()) continue;
            PendingIntent p = pi(c, a.id, false);
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
