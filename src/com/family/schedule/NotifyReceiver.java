package com.family.schedule;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;

public class NotifyReceiver extends BroadcastReceiver {

    public static final String ACT_DONE = "com.family.schedule.MARK_DONE";
    public static final String ACT_MISS = "com.family.schedule.MARK_MISSED";
    public static final String ACT_OPEN = "com.family.schedule.OPEN";

    private static PendingIntent act(Context c, String action, String id, long occ) {
        Intent i = new Intent(c, NotifyReceiver.class)
                .setAction(action)
                .putExtra("id", id)
                .putExtra("occ", occ);
        return PendingIntent.getBroadcast(c, Reminders.rc(action + id + occ),
                i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    @Override
    public void onReceive(Context c, Intent in) {
        if (in == null) return;
        String id = in.getStringExtra("id");
        String action = in.getAction();
        Data d = Data.get(c);
        Data.Appt a = d.find(id);
        if (a == null) return;

        long occ = in.getLongExtra("occ", a.when);

        if (ACT_DONE.equals(action) || ACT_MISS.equals(action)) {
            Recur.setStatus(a, occ, ACT_DONE.equals(action) ? Recur.DONE : Recur.MISSED);
            if (!Recur.repeats(a)) a.done = ACT_DONE.equals(action);
            d.save();
            NotificationManager n = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
            if (n != null) n.cancel(id.hashCode());
            Reminders.scheduleAll(c);
            return;
        }

        if (ACT_OPEN.equals(action)) {
            open(c, a, occ);
            return;
        }

        fire(c, d, a, id, occ);
    }

    private void open(Context c, Data.Appt a, long occ) {
        Intent open = new Intent(c, EditActivity.class)
                .putExtra("id", a.id)
                .putExtra("occ", occ)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        c.startActivity(open);
    }

    private void fire(Context c, Data d, Data.Appt a, String id, long occ) {
        if (Recur.status(a, occ) != Recur.PENDING) return;

        int snd = Reminders.clampSnd(a.snd);
        Reminders.ensureChannel(c, snd);

        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        if (Build.VERSION.SDK_INT >= 33
                && c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        Data.Child kid = d.kid(a.childId);
        String who = kid == null ? "" : kid.name + " • ";

        String body = who + Fmt.time(occ) + "  (" + Fmt.left(occ) + ")";
        if (Recur.repeats(a)) body = body + "\n" + Recur.daysLabel(a);
        if (a.note.length() > 0) body = body + "\n" + a.note;

        PendingIntent pi = act(c, ACT_OPEN, a.id, occ);

        Notification.Builder b;
        if (Build.VERSION.SDK_INT >= 26) {
            b = new Notification.Builder(c, Reminders.channelId(snd));
        } else {
            b = new Notification.Builder(c).setPriority(Notification.PRIORITY_HIGH);
            if (snd == 2) {
                b.setSound((Uri) null);
                b.setVibrate((long[]) null);
            } else {
                b.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION));
            }
        }
        b.setSmallIcon(R.drawable.ic_stat)
                .setContentTitle(a.title)
                .setContentText(who + Fmt.time(occ))
                .setStyle(new Notification.BigTextStyle().bigText(body))
                .setWhen(occ)
                .setShowWhen(true)
                .setAutoCancel(true)
                .setContentIntent(pi)
                .addAction(0, "تم التنفيذ", act(c, ACT_DONE, a.id, occ))
                .addAction(0, "لم يتم", act(c, ACT_MISS, a.id, occ));

        if (kid != null) b.setColor(kid.color);

        nm.notify(id.hashCode(), b.build());
    }
}
