package com.family.schedule;

import java.util.Calendar;

/** Weekly recurrence: an appointment is either one-off (days == 0) or repeats on the
 *  weekday bits set in `days` (bit i == Calendar.SUNDAY + i) at the anchor's clock time. */
public class Recur {

    public static final int PENDING = 0;
    public static final int DONE = 1;
    public static final int MISSED = 2;

    /** Arabic short weekday labels, Sunday first. */
    public static final String[] DAY_SHORT = {"ح", "ن", "ث", "ر", "خ", "ج", "س"};

    public static boolean repeats(Data.Appt a) {
        return a.days != 0;
    }

    public static long[] next(Data.Appt a, long from, int count) {
        long[] out = new long[count];
        int n = 0;

        if (!repeats(a)) {
            if (a.when >= from) out[n++] = a.when;
        } else {
            Calendar anchor = Calendar.getInstance();
            anchor.setTimeInMillis(a.when);
            int hh = anchor.get(Calendar.HOUR_OF_DAY);
            int mm = anchor.get(Calendar.MINUTE);

            Calendar c = Calendar.getInstance();
            c.setTimeInMillis(from);
            c.set(Calendar.HOUR_OF_DAY, 0);
            c.set(Calendar.MINUTE, 0);
            c.set(Calendar.SECOND, 0);
            c.set(Calendar.MILLISECOND, 0);

            for (int i = 0; i < 400 && n < count; i++) {
                int dow = c.get(Calendar.DAY_OF_WEEK) - 1;
                if (((a.days >> dow) & 1) == 1) {
                    c.set(Calendar.HOUR_OF_DAY, hh);
                    c.set(Calendar.MINUTE, mm);
                    long t = c.getTimeInMillis();
                    if (t >= from) out[n++] = t;
                }
                c.add(Calendar.DAY_OF_YEAR, 1);
                c.set(Calendar.HOUR_OF_DAY, 0);
                c.set(Calendar.MINUTE, 0);
            }
        }

        long[] r = new long[n];
        System.arraycopy(out, 0, r, 0, n);
        return r;
    }

    /** The occurrence shown in the list: today's if the appointment still runs today
     *  (so it can be marked تم / لم يتم even after its time passed), else the next one. */
    public static long representative(Data.Appt a, long from) {
        if (!repeats(a)) return a.when;

        long start = Fmt.startOfToday();
        long[] today = next(a, start, 1);
        if (today.length > 0 && Fmt.dayNo(today[0]) == Fmt.dayNo(from)) return today[0];

        long[] fwd = next(a, from, 1);
        return fwd.length > 0 ? fwd[0] : today.length > 0 ? today[0] : a.when;
    }

    public static int status(Data.Appt a, long occ) {
        Integer v = a.st.get(occ);
        return v == null ? PENDING : v;
    }

    public static void setStatus(Data.Appt a, long occ, int s) {
        if (s == PENDING) {
            a.st.remove(occ);
            a.stAt.remove(occ);
        } else {
            a.st.put(occ, s);
            a.stAt.put(occ, System.currentTimeMillis());
        }
    }

    /** first occurrence strictly after `after`, or -1 when the series ended */
    public static long nextAfter(Data.Appt a, long after) {
        long[] f = next(a, after + 1, 1);
        return f.length > 0 ? f[0] : -1L;
    }

    /** execution log, newest first. Each row is {occurrence, status, recordedAt}. */
    public static long[][] log(Data.Appt a) {
        java.util.ArrayList<Long> keys = new java.util.ArrayList<Long>(a.st.keySet());
        java.util.Collections.sort(keys);
        long[][] out = new long[keys.size()][];
        for (int i = 0; i < keys.size(); i++) {
            long occ = keys.get(i);
            Long at = a.stAt.get(occ);
            out[keys.size() - 1 - i] = new long[]{occ, status(a, occ), at == null ? 0L : at};
        }
        return out;
    }

    /** Human readable list of the selected weekdays, e.g. "السبت - الأحد". */
    public static String daysLabel(Data.Appt a) {
        if (!repeats(a)) return "مرة واحدة";
        StringBuilder sb = new StringBuilder();
        String[] full = {"الأحد", "الاثنين", "الثلاثاء", "الأربعاء", "الخميس", "الجمعة", "السبت"};
        for (int i = 0; i < 7; i++) {
            if (((a.days >> i) & 1) == 0) continue;
            if (sb.length() > 0) sb.append(" - ");
            sb.append(full[i]);
        }
        return sb.toString();
    }
}
