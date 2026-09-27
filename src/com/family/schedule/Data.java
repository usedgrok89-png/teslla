package com.family.schedule;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class Data {

    private static final String PREFS = "store";
    private static Data I;

    private final SharedPreferences p;

    public final ArrayList<Child> kids = new ArrayList<>();
    public final ArrayList<Appt> list = new ArrayList<>();

    public static class Child {
        public String id = "";
        public String name = "";
        public int color = 0xFF00897B;
    }

    public static class Appt {
        public String id = "";
        public String childId = "";
        public String title = "";
        public String note = "";
        public long when;
        public int lead = 15;
        public int snd = 0;
        /** weekday bitmask, 0 == one-off. bit i == Calendar.SUNDAY + i */
        public int days = 0;
        /** occurrence timestamp -> Recur.PENDING / DONE / MISSED */
        public final HashMap<Long, Integer> st = new HashMap<>();
        /** occurrence timestamp -> when the user recorded that status (log trail) */
        public final HashMap<Long, Long> stAt = new HashMap<>();
        /** legacy mirror of the single-occurrence state, kept for pre-1.2 data */
        public boolean done;
    }

    private Data(Context c) {
        p = c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        load();
    }

    public static synchronized Data get(Context c) {
        if (I == null) I = new Data(c);
        return I;
    }

    public static String uid() {
        return Long.toString(System.currentTimeMillis(), 36) + Integer.toHexString((int) (System.nanoTime() & 0xffffff));
    }

    public Child kid(String id) {
        if (id == null) return null;
        for (int i = 0; i < kids.size(); i++) if (kids.get(i).id.equals(id)) return kids.get(i);
        return null;
    }

    /** single-occurrence helper kept for one-off appointments */
    public static boolean done(Data.Appt a) {
        return Recur.status(a, a.when) == Recur.DONE;
    }

    public static boolean finished(Data.Appt a) {
        return Recur.status(a, a.when) != Recur.PENDING;
    }

    public Appt find(String id) {
        if (id == null) return null;
        for (int i = 0; i < list.size(); i++) if (list.get(i).id.equals(id)) return list.get(i);
        return null;
    }

    public Child addKid(String name, int color) {
        Child c = new Child();
        c.id = uid();
        c.name = name;
        c.color = color;
        kids.add(c);
        save();
        return c;
    }

    public void delKid(Child c) {
        kids.remove(c);
        for (int i = list.size() - 1; i >= 0; i--) {
            if (list.get(i).childId.equals(c.id)) list.remove(i);
        }
        save();
    }

    public void put(Appt a) {
        Appt old = find(a.id);
        if (old == null) list.add(a);
        else list.set(list.indexOf(old), a);
        save();
    }

    public void del(String id) {
        Appt a = find(id);
        if (a != null) list.remove(a);
        save();
    }

    public void saveKids() {
        save();
    }

    public void save() {
        try {
            JSONArray ks = new JSONArray();
            for (int i = 0; i < kids.size(); i++) {
                Child c = kids.get(i);
                JSONObject o = new JSONObject();
                o.put("i", c.id);
                o.put("n", c.name);
                o.put("c", c.color);
                ks.put(o);
            }
            JSONArray as = new JSONArray();
            for (int i = 0; i < list.size(); i++) {
                Appt a = list.get(i);
                JSONObject o = new JSONObject();
                o.put("i", a.id);
                o.put("k", a.childId);
                o.put("t", a.title);
                o.put("n", a.note);
                o.put("w", a.when);
                o.put("l", a.lead);
                o.put("s", a.snd);
                o.put("w2", a.days);
                JSONObject stj = new JSONObject();
                Iterator<Map.Entry<Long, Integer>> it = a.st.entrySet().iterator();
                while (it.hasNext()) {
                    Map.Entry<Long, Integer> e = it.next();
                    stj.put(String.valueOf(e.getKey()), e.getValue());
                }
                o.put("st", stj);
                JSONObject atj = new JSONObject();
                Iterator<Map.Entry<Long, Long>> ita = a.stAt.entrySet().iterator();
                while (ita.hasNext()) {
                    Map.Entry<Long, Long> e = ita.next();
                    atj.put(String.valueOf(e.getKey()), e.getValue());
                }
                o.put("sta", atj);
                o.put("d", done(a) ? 1 : 0);
                as.put(o);
            }
            JSONObject root = new JSONObject();
            root.put("kids", ks);
            root.put("appts", as);
            p.edit().putString("json", root.toString()).commit();
        } catch (Exception e) {
            // in-memory list stays valid
        }
    }

    private void load() {
        String s = p.getString("json", null);
        if (s == null) return;
        try {
            JSONObject root = new JSONObject(s);
            JSONArray ks = root.optJSONArray("kids");
            if (ks != null) {
                for (int i = 0; i < ks.length(); i++) {
                    JSONObject o = ks.getJSONObject(i);
                    Child c = new Child();
                    c.id = o.optString("i");
                    c.name = o.optString("n");
                    c.color = o.optInt("c", 0xFF00897B);
                    kids.add(c);
                }
            }
            JSONArray as = root.optJSONArray("appts");
            if (as != null) {
                for (int i = 0; i < as.length(); i++) {
                    JSONObject o = as.getJSONObject(i);
                    Appt a = new Appt();
                    a.id = o.optString("i");
                    a.childId = o.optString("k");
                    a.title = o.optString("t");
                    a.note = o.optString("n");
                    a.when = o.optLong("w");
                    a.lead = o.optInt("l", 15);
                    a.snd = o.optInt("s", 0);
                    a.days = o.optInt("w2", 0);
                    JSONObject stj = o.optJSONObject("st");
                    if (stj != null) {
                        java.util.Iterator<String> itk = stj.keys();
                        while (itk.hasNext()) {
                            String k = itk.next();
                            try {
                                a.st.put(Long.parseLong(k), stj.getInt(k));
                            } catch (Exception ignored) {
                            }
                        }
                    }
                    JSONObject atj = o.optJSONObject("sta");
                    if (atj != null) {
                        java.util.Iterator<String> ita = atj.keys();
                        while (ita.hasNext()) {
                            String k = ita.next();
                            try {
                                a.stAt.put(Long.parseLong(k), atj.getLong(k));
                            } catch (Exception ignored) {
                            }
                        }
                    }
                    a.done = o.optInt("d", 0) == 1;
                    if (a.done) a.st.put(a.when, Recur.DONE);
                    list.add(a);
                }
            }
        } catch (Exception e) {
            kids.clear();
            list.clear();
        }
    }
}
