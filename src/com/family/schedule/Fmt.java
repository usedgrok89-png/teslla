package com.family.schedule;

import java.util.Calendar;

public class Fmt {
    private static final String[] DOW = {
            "الأحد", "الاثنين", "الثلاثاء", "الأربعاء", "الخميس", "الجمعة", "السبت"
    };
    private static final String[] MON = {
            "يناير", "فبراير", "مارس", "أبريل", "مايو", "يونيو",
            "يوليو", "أغسطس", "سبتمبر", "أكتوبر", "نوفمبر", "ديسمبر"
    };

    private static Calendar cal(long t) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(t);
        return c;
    }

    public static long startOfToday() {
        Calendar c = cal(System.currentTimeMillis());
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    /** sortable day stamp, immune to DST / timezone maths */
    public static long dayNo(long t) {
        Calendar c = cal(t);
        return c.get(Calendar.YEAR) * 1000L + c.get(Calendar.DAY_OF_YEAR);
    }

    public static int daysFromToday(long t) {
        return (int) (dayNo(t) - dayNo(System.currentTimeMillis()));
    }

    public static String dayTitle(long t) {
        int d = daysFromToday(t);
        if (d == 0) return "اليوم";
        if (d == 1) return "بكرة";
        if (d == 2) return "بعد بكرة";
        if (d == -1) return "إمبارح";
        Calendar c = cal(t);
        return DOW[c.get(Calendar.DAY_OF_WEEK) - 1] + " " + c.get(Calendar.DAY_OF_MONTH) + " " + MON[c.get(Calendar.MONTH)];
    }

    public static String dateBtn(long t) {
        Calendar c = cal(t);
        return c.get(Calendar.DAY_OF_MONTH) + " " + MON[c.get(Calendar.MONTH)] + " " + c.get(Calendar.YEAR);
    }

    public static String headerSub() {
        Calendar c = cal(System.currentTimeMillis());
        return DOW[c.get(Calendar.DAY_OF_WEEK) - 1] + " " + c.get(Calendar.DAY_OF_MONTH) + " " + MON[c.get(Calendar.MONTH)];
    }

    public static String time(long t) {
        Calendar c = cal(t);
        int h = c.get(Calendar.HOUR_OF_DAY);
        int m = c.get(Calendar.MINUTE);
        int h12 = h % 12;
        if (h12 == 0) h12 = 12;
        StringBuilder sb = new StringBuilder();
        sb.append(h12).append(':');
        if (m < 10) sb.append('0');
        sb.append(m).append(' ').append(h < 12 ? "ص" : "م");
        return sb.toString();
    }

    /** same clock time, next calendar day */
    public static long nextDay(long t) {
        Calendar r = cal(t);
        r.add(Calendar.DAY_OF_YEAR, 1);
        r.set(Calendar.SECOND, 0);
        r.set(Calendar.MILLISECOND, 0);
        return r.getTimeInMillis();
    }

    public static int hourOf(long t) {
        return cal(t).get(Calendar.HOUR_OF_DAY);
    }

    public static int minuteOf(long t) {
        return cal(t).get(Calendar.MINUTE);
    }

    public static String left(long t) {
        long ms = t - System.currentTimeMillis();
        if (ms <= 0) return "دلوقتي";
        long min = ms / 60000L;
        if (min < 60) return "بعد " + min + " د";
        long h = min / 60;
        if (h < 24) return "بعد " + h + " س";
        return "بعد " + (h / 24) + " يوم";
    }
}
