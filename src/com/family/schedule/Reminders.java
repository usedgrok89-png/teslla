package com.family.schedule;

import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

public class Reminders {

    static final String CH = "appts";
    private static final String ACTION = "com.family.schedule.FIRE";

    public static void ensureChannel(Context c) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        NotificationChannel ch = new NotificationChannel(CH, "تنبيه المواعيد", NotificationManager.IMPORTANCE_HIGH);
        ch.enableVibration(true);
        ch.setShowBadge(true);
        nm.createNotificationChannel(ch);
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
