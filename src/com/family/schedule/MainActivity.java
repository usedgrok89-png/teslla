package com.family.schedule;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class MainActivity extends Activity {

    public static final int[] PALETTE = {
            0xFF00897B, 0xFF1E88E5, 0xFF8E24AA, 0xFFF4511E,
            0xFF43A047, 0xFFC2185B, 0xFF6D4C41, 0xFF546E7A
    };

    private Data d;
    private ListView list;
    private TextView empty, sub;
    private LinearLayout chips;
    private final ArrayList<Object> rows = new ArrayList<>();
    private String filter = null;
    private final android.os.Handler tick = new android.os.Handler();
    private final Runnable tickRun = new Runnable() {
        public void run() {
            rebuild();
            tick.postDelayed(this, 30000L);
        }
    };

    /** one rendered line: an appointment pinned to a single occurrence */
    private static class Row {
        final Data.Appt a;
        final long occ;

        Row(Data.Appt a, long occ) {
            this.a = a;
            this.occ = occ;
        }
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_main);
        d = Data.get(this);

        list = findViewById(R.id.list);
        empty = findViewById(R.id.empty);
        sub = findViewById(R.id.hSub);
        chips = findViewById(R.id.chips);

        list.setAdapter(new RowAdapter());

        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> p, View v, int pos, long id) {
                Object o = rows.get(pos);
                if (o instanceof Row) {
                    Row r = (Row) o;
                    open(r.a, r.occ);
                }
            }
        });

        list.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            public boolean onItemLongClick(AdapterView<?> p, View v, int pos, long id) {
                Object o = rows.get(pos);
                if (o instanceof Row) {
                    menu((Row) o);
                    return true;
                }
                return false;
            }
        });

        findViewById(R.id.add).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                open(null, 0L);
            }
        });

        findViewById(R.id.kids).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                kidDialog(null);
            }
        });

        Reminders.ensureAll(this);
        askNotify();
    }

    private void askNotify() {
        if (Build.VERSION.SDK_INT < 33) return;
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return;
        try {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 7);
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void onRequestPermissionsResult(int rq, String[] perms, int[] res) {
        super.onRequestPermissionsResult(rq, perms, res);
        if (rq == 7) Reminders.scheduleAll(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Reminders.scheduleAll(this);
        buildChips();
        rebuild();
        tick.removeCallbacks(tickRun);
        tick.postDelayed(tickRun, 30000L);
    }

    @Override
    protected void onPause() {
        tick.removeCallbacks(tickRun);
        super.onPause();
    }

    // ---------- list ----------

    private void rebuild() {
        rows.clear();
        long today = Fmt.startOfToday();
        long now = System.currentTimeMillis();
        List<Row> fut = new ArrayList<>();
        List<Row> past = new ArrayList<>();
        for (int i = 0; i < d.list.size(); i++) {
            Data.Appt a = d.list.get(i);
            if (filter != null && !filter.equals(a.childId)) continue;
            Row r = new Row(a, Recur.representative(a, now));
            if (r.occ < today) past.add(r);
            else fut.add(r);
        }
        Collections.sort(fut, byOcc);
        Collections.sort(past, byOccDesc);
        group(fut, false);
        group(past, true);

        int n = fut.size();
        String lbl;
        if (n == 0) lbl = "مفيش مواعيد";
        else if (n == 1) lbl = "موعد واحد باقي";
        else if (n == 2) lbl = "موعدين باقي";
        else if (n <= 10) lbl = n + " مواعيد";
        else lbl = n + " موعد";
        sub.setText(Fmt.headerSub() + "   •   " + lbl);

        boolean none = rows.isEmpty();
        empty.setVisibility(none ? View.VISIBLE : View.GONE);
        list.setVisibility(none ? View.GONE : View.VISIBLE);
        ((RowAdapter) list.getAdapter()).notifyDataSetChanged();
    }

    private void group(List<Row> src, boolean past) {
        long last = -1;
        for (int i = 0; i < src.size(); i++) {
            Row r = src.get(i);
            long day = Fmt.dayNo(r.occ);
            if (day != last) {
                last = day;
                if (past && i == 0) rows.add("مواعيد فاتت");
                else rows.add(Fmt.dayTitle(r.occ));
            }
            rows.add(r);
        }
    }

    private static final Comparator<Row> byOcc = new Comparator<Row>() {
        public int compare(Row x, Row y) {
            return x.occ < y.occ ? -1 : (x.occ > y.occ ? 1 : 0);
        }
    };

    private static final Comparator<Row> byOccDesc = new Comparator<Row>() {
        public int compare(Row x, Row y) {
            return x.occ > y.occ ? -1 : (x.occ < y.occ ? 1 : 0);
        }
    };

    private void open(Data.Appt a, long occ) {
        Intent i = new Intent(this, EditActivity.class);
        if (a != null) {
            i.putExtra("id", a.id);
            i.putExtra("occ", occ);
        } else if (filter != null) {
            i.putExtra("child", filter);
        }
        startActivity(i);
    }

    // ---------- row menu ----------

    private void menu(final Row row) {
        final Data.Appt a = row.a;
        int st = Recur.status(a, row.occ);
        String[] items = {
                getString(R.string.edit),
                st != Recur.DONE ? getString(R.string.mark_done) : getString(R.string.mark_not),
                st != Recur.MISSED ? getString(R.string.mark_missed) : getString(R.string.mark_pending),
                getString(R.string.log),
                getString(R.string.postpone),
                getString(R.string.del)
        };
        new AlertDialog.Builder(this)
                .setTitle(a.title + "\n" + Fmt.dateBtn(row.occ) + "  " + Fmt.time(row.occ))
                .setItems(items, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        if (which == 0) {
                            open(a, row.occ);
                        } else if (which == 1) {
                            setStatus(row, st == Recur.DONE ? Recur.PENDING : Recur.DONE);
                        } else if (which == 2) {
                            setStatus(row, st == Recur.MISSED ? Recur.PENDING : Recur.MISSED);
                        } else if (which == 3) {
                            showLog(a);
                        } else if (which == 4) {
                            a.when = Fmt.nextDay(row.occ);
                            Recur.setStatus(a, row.occ, Recur.PENDING);
                            if (!Recur.repeats(a)) a.done = false;
                            d.put(a);
                            Reminders.scheduleAll(MainActivity.this);
                            rebuild();
                        } else {
                            confirmDelete(a);
                        }
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void showLog(Data.Appt a) {
        ArrayList<String> lines = RecurLog.lines(this, a, 12);
        CharSequence[] body = lines.isEmpty()
                ? new CharSequence[]{getString(R.string.log_empty)}
                : lines.toArray(new CharSequence[0]);
        new AlertDialog.Builder(this)
                .setTitle(a.title + " \u2014 " + getString(R.string.log))
                .setItems(body, null)
                .setPositiveButton(R.string.cancel, null)
                .show();
    }

    private void setStatus(Row row, int st) {
        Recur.setStatus(row.a, row.occ, st);
        if (!Recur.repeats(row.a)) row.a.done = st == Recur.DONE;
        d.put(row.a);
        Reminders.scheduleAll(this);
        rebuild();
    }

    private void confirmDelete(final Data.Appt a) {
        new AlertDialog.Builder(this)
                .setTitle(a.title)
                .setMessage("متأكد إنك عايز تمسح الموعد ده؟")
                .setPositiveButton(R.string.del, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d2, int w) {
                        Reminders.cancel(MainActivity.this, a.id);
                        d.del(a.id);
                        Reminders.scheduleAll(MainActivity.this);
                        rebuild();
                        Toast.makeText(MainActivity.this, R.string.deleted, Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    // ---------- children chips ----------

    private TextView mkChip(final String label, final String key, final boolean plus) {
        TextView t = new TextView(this);
        t.setText(label);
        t.setTextSize(14f);
        t.setPadding(22, 10, 22, 10);
        t.setBackgroundResource(R.drawable.bg_chip);
        t.setTextColor(getResources().getColor(R.color.text));

        t.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (plus) {
                    kidDialog(null);
                    return;
                }
                filter = key;
                buildChips();
                rebuild();
            }
        });

        t.setOnLongClickListener(new View.OnLongClickListener() {
            public boolean onLongClick(View v) {
                if (plus || key == null) return false;
                final Data.Child c = d.kid(key);
                if (c == null) return false;
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle(c.name)
                        .setItems(new String[]{getString(R.string.edit), getString(R.string.del)},
                                new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dd, int w) {
                                        if (w == 0) {
                                            kidDialog(c);
                                        } else {
                                            for (int i = 0; i < d.list.size(); i++) {
                                                Reminders.cancel(MainActivity.this, d.list.get(i).id);
                                            }
                                            d.delKid(c);
                                            if (key.equals(filter)) filter = null;
                                            Reminders.scheduleAll(MainActivity.this);
                                            buildChips();
                                            rebuild();
                                        }
                                    }
                                })
                        .setNegativeButton(R.string.cancel, null)
                        .show();
                return true;
            }
        });
        return t;
    }

    private void buildChips() {
        chips.removeAllViews();
        chips.addView(style(mkChip(getString(R.string.all), null, false), filter == null));
        for (int i = 0; i < d.kids.size(); i++) {
            Data.Child c = d.kids.get(i);
            chips.addView(style(mkChip(c.name, c.id, false), c.id.equals(filter)));
        }
        chips.addView(style(mkChip("＋", "x", true), false));
    }

    private View style(TextView t, boolean on) {
        if (on) {
            t.setBackgroundResource(R.drawable.bg_chip_on);
            t.setTextColor(Color.WHITE);
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMarginEnd((int) (6 * getResources().getDisplayMetrics().density));
        t.setLayoutParams(lp);
        return t;
    }

    private void kidDialog(final Data.Child edit) {
        View v = LayoutInflater.from(this).inflate(R.layout.dialog_child, null);
        final EditText name = v.findViewById(R.id.cName);
        final LinearLayout colors = v.findViewById(R.id.cColors);
        final int[] picked = {edit == null ? PALETTE[d.kids.size() % PALETTE.length] : edit.color};

        if (edit != null) name.setText(edit.name);

        final View[] dots = new View[PALETTE.length];
        for (int i = 0; i < PALETTE.length; i++) {
            final int col = PALETTE[i];
            View dot = new View(this);
            GradientDrawable g = new GradientDrawable();
            g.setShape(GradientDrawable.OVAL);
            g.setColor(col);
            g.setStroke(col == picked[0] ? 4 : 0, 0xFF1B1B1F);
            dot.setBackgroundDrawable(g);

            int sz = (int) (36 * getResources().getDisplayMetrics().density);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, sz, 1f);
            lp.setMarginEnd((int) (6 * getResources().getDisplayMetrics().density));
            dot.setLayoutParams(lp);

            dot.setOnClickListener(new View.OnClickListener() {
                public void onClick(View x) {
                    picked[0] = col;
                    for (int k = 0; k < dots.length; k++) {
                        GradientDrawable gg = (GradientDrawable) dots[k].getBackground();
                        gg.setStroke(PALETTE[k] == col ? 4 : 0, 0xFF1B1B1F);
                    }
                }
            });
            dots[i] = dot;
            colors.addView(dot);
        }

        final boolean isNew = edit == null;
        new AlertDialog.Builder(this)
                .setTitle(isNew ? R.string.add_child : R.string.edit)
                .setView(v)
                .setPositiveButton(R.string.save, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dd, int w) {
                        String n = name.getText().toString().trim();
                        if (n.length() == 0) {
                            Toast.makeText(MainActivity.this, R.string.ch_name, Toast.LENGTH_SHORT).show();
                            return;
                        }
                        if (isNew) {
                            filter = d.addKid(n, picked[0]).id;
                        } else {
                            edit.name = n;
                            edit.color = picked[0];
                            d.saveKids();
                        }
                        buildChips();
                        rebuild();
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    // ---------- adapter ----------

    private class RowAdapter extends BaseAdapter {
        public int getCount() {
            return rows.size();
        }

        public Object getItem(int i) {
            return rows.get(i);
        }

        public long getItemId(int i) {
            return i;
        }

        public int getViewTypeCount() {
            return 2;
        }

        public int getItemViewType(int i) {
            return rows.get(i) instanceof String ? 0 : 1;
        }

        public View getView(int i, View cv, ViewGroup parent) {
            Object o = rows.get(i);
            LayoutInflater inf = LayoutInflater.from(MainActivity.this);

            if (o instanceof String) {
                TextView t;
                if (cv instanceof TextView) {
                    t = (TextView) cv;
                } else {
                    t = (TextView) inf.inflate(R.layout.item_day, parent, false);
                }
                t.setText((String) o);
                return t;
            }

            final Row row = (Row) o;
            final Data.Appt a = row.a;
            final long occ = row.occ;
            View v = cv;
            if (v == null || cv instanceof TextView) v = inf.inflate(R.layout.item_appt, parent, false);

            View bar = v.findViewById(R.id.bar);
            TextView time = v.findViewById(R.id.time);
            TextView state = v.findViewById(R.id.state);
            TextView title = v.findViewById(R.id.title);
            TextView meta = v.findViewById(R.id.meta);
            TextView next = v.findViewById(R.id.next);
            ImageView check = v.findViewById(R.id.check);

            Data.Child c = d.kid(a.childId);
            int col = c == null ? 0xFF546E7A : c.color;
            GradientDrawable g = new GradientDrawable();
            g.setShape(GradientDrawable.RECTANGLE);
            g.setColor(col);
            g.setCornerRadius(6 * getResources().getDisplayMetrics().density);
            bar.setBackgroundDrawable(g);

            time.setText(Fmt.time(occ));
            time.setTextColor(col);
            title.setText(a.title);

            StringBuilder m = new StringBuilder();
            m.append(c == null ? "بدون طفل" : c.name);
            if (Recur.repeats(a)) m.append("  •  🔁 ").append(Recur.daysLabel(a));
            if (a.note.length() > 0) m.append("  •  ").append(a.note);
            meta.setText(m.toString());

            int st = Recur.status(a, occ);
            if (st == Recur.DONE) {
                state.setText(R.string.done_lbl);
                state.setTextColor(0xFF43A047);
            } else if (st == Recur.MISSED) {
                state.setText(R.string.missed_lbl);
                state.setTextColor(0xFFC62828);
            } else if (occ < System.currentTimeMillis()) {
                state.setText(R.string.late);
                state.setTextColor(0xFFC62828);
            } else {
                state.setText(Fmt.left(occ));
                state.setTextColor(getResources().getColor(R.color.text_dim));
            }

            long nx = Recur.repeats(a) ? Recur.nextAfter(a, occ) : -1L;
            if (nx > 0) {
                next.setVisibility(View.VISIBLE);
                next.setText(getString(R.string.next_occ, Fmt.dayTitle(nx), Fmt.left(nx)));
            } else {
                next.setVisibility(View.GONE);
            }

            title.setAlpha(st == Recur.PENDING ? 1f : 0.45f);
            meta.setAlpha(st == Recur.PENDING ? 1f : 0.45f);
            v.setAlpha(st == Recur.PENDING ? 1f : 0.7f);

            check.setAlpha(st == Recur.DONE ? 1f : 0.28f);
            check.setOnClickListener(new View.OnClickListener() {
                public void onClick(View x) {
                    setStatus(row, Recur.status(a, occ) == Recur.DONE ? Recur.PENDING : Recur.DONE);
                }
            });
            return v;
        }
    }
}
