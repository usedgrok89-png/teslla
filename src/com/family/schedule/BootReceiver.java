package com.family.schedule;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent in) {
        Reminders.ensureAll(c);
        Reminders.scheduleAll(c);
    }
}
