package com.family.schedule;

import android.content.Context;

import java.util.ArrayList;

/** Renders the execution log of an appointment as Arabic lines. */
public class RecurLog {

    /** one line per resolved occurrence, newest first, e.g.
     *  "تم التنفيذ — السبت 5 أكتوبر 9:00 ص  (سُجّل 5 أكتوبر 9:12 م)" */
    public static ArrayList<String> lines(Context c, Data.Appt a, int limit) {
        long[][] all = Recur.log(a);
        int n = Math.min(limit, all.length);
        ArrayList<String> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            long occ = all[i][0];
            String head = (all[i][1] == Recur.DONE ? c.getString(R.string.log_done) : c.getString(R.string.log_missed))
                    + " \u2014 " + Fmt.dayTitle(occ) + " " + Fmt.time(occ);
            String tail = all[i][2] == 0L ? ""
                    : "  (" + c.getString(R.string.log_at, Fmt.dayTitle(all[i][2]), Fmt.time(all[i][2])) + ")";
            out.add(head + tail);
        }
        return out;
    }
}
