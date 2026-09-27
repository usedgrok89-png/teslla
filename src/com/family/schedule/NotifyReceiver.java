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

    @Override
    public void onReceive(Context c, Intent in) {
        String id = in == null ? null : in.getStringExtra("id");
        Data d = Data.get(c);
        Data.Appt a = d.find(id);
        if (a == null || a.done) return;

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

        String body = who + Fmt.time(a.when) + "  (" + Fmt.left(a.when) + ")";
        if (a.note.length() > 0) body = body + "\n" + a.note;

        Intent open = new Intent(c, EditActivity.class).putExtra("id", a.id);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(c, Reminders.rc(a.id), open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

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
                .setContentText(who + Fmt.time(a.when))
                .setStyle(new Notification.BigTextStyle().bigText(body))
                .setWhen(a.when)
                .setShowWhen(true)
                .setAutoCancel(true)
                .setContentIntent(pi);

        if (kid != null) b.setColor(kid.color);

        nm.notify(a.id.hashCode(), b.build());
    }
}
