package com.family.schedule;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.View;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Calendar;

public class EditActivity extends Activity {

    private static final int[] LEADS = {0, 5, 10, 15, 30, 60, 120, 1440};
    private static final int[] LEAD_LABELS = {
            R.string.no_remind, R.string.min5, R.string.min10, R.string.min15,
            R.string.min30, R.string.hour1, R.string.hour2, R.string.daybefore
    };

    private Data d;
    private Data.Appt a;
    private boolean isNew;
    private final ArrayList<String> kidIds = new ArrayList<>();

    private static final String[] DAY_FULL = {
            "الأحد", "الاثنين", "الثلاثاء", "الأربعاء", "الخميس", "الجمعة", "السبت"
    };

    private EditText fTitle, fNote;
    private Button fDate, fTime;
    private Spinner fChild, fLead, fSound, fStatus;
    private LinearLayout fDays;
    private TextView fRepHint, fLog;
    private Calendar when;
    private long occIn = 0L;
    private int daysMask = 0;
    private final TextView[] dayBtns = new TextView[7];

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_edit);
        d = Data.get(this);

        fTitle = findViewById(R.id.fTitle);
        fNote = findViewById(R.id.fNote);
        fDate = findViewById(R.id.fDate);
        fTime = findViewById(R.id.fTime);
        fChild = findViewById(R.id.fChild);
        fLead = findViewById(R.id.fLead);
        fSound = findViewById(R.id.fSound);
        fStatus = findViewById(R.id.fStatus);
        fDays = findViewById(R.id.fDays);
        fRepHint = findViewById(R.id.fRepHint);
        fLog = findViewById(R.id.fLog);

        String id = getIntent() == null ? null : getIntent().getStringExtra("id");
        a = d.find(id);
        isNew = a == null;
        if (getIntent() != null) occIn = getIntent().getLongExtra("occ", 0L);

        if (isNew) {
            a = new Data.Appt();
            a.id = Data.uid();
            a.lead = 15;
            when = Calendar.getInstance();
            when.add(Calendar.HOUR_OF_DAY, 1);
            when.set(Calendar.MINUTE, 0);
            when.set(Calendar.SECOND, 0);
            when.set(Calendar.MILLISECOND, 0);
        } else {
            when = Calendar.getInstance();
            when.setTimeInMillis(a.when);
        }

        ((TextView) findViewById(R.id.titleBar)).setText(isNew ? R.string.add_appt : R.string.edit_appt);
        fDate.setText(Fmt.dateBtn(when.getTimeInMillis()));
        fTime.setText(Fmt.time(when.getTimeInMillis()));
        if (!isNew) {
            fTitle.setText(a.title);
            fNote.setText(a.note);
        }

        ArrayList<String> kidNames = new ArrayList<>();
        kidNames.add("— بدون طفل —");
        kidIds.add("");
        for (int i = 0; i < d.kids.size(); i++) {
            kidNames.add(d.kids.get(i).name);
            kidIds.add(d.kids.get(i).id);
        }
        ArrayAdapter<String> ka = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, kidNames);
        ka.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        fChild.setAdapter(ka);
        if (!isNew) {
            int p = kidIds.indexOf(a.childId);
            fChild.setSelection(p < 0 ? 0 : p);
        } else {
            int p = kidIds.indexOf(getIntent().getStringExtra("child"));
            fChild.setSelection(p > 0 ? p : (kidIds.size() > 1 ? 1 : 0));
        }

        String[] leadTxt = new String[LEADS.length];
        for (int i = 0; i < LEADS.length; i++) leadTxt[i] = getString(LEAD_LABELS[i]);
        ArrayAdapter<String> la = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, leadTxt);
        la.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        fLead.setAdapter(la);
        if (!isNew) {
            int p = indexOf(a.lead);
            fLead.setSelection(p < 0 ? indexOf(15) : p);
        } else {
            fLead.setSelection(indexOf(15));
        }

        String[] sndTxt = {getString(R.string.snd_bright), getString(R.string.snd_soft), getString(R.string.snd_none)};
        ArrayAdapter<String> sa = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, sndTxt);
        sa.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        fSound.setAdapter(sa);
        fSound.setSelection(Reminders.clampSnd(isNew ? 0 : a.snd));
        Reminders.ensureAll(this);

        final String[] stTxt = {
                getString(R.string.st_pending), getString(R.string.st_done), getString(R.string.st_missed)
        };
        ArrayAdapter<String> sta = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, stTxt);
        sta.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        fStatus.setAdapter(sta);
        fStatus.setSelection(isNew || occIn == 0L
                ? Recur.PENDING
                : Recur.status(a, occIn));

        buildDayToggles(isNew ? 0 : a.days);
        paintLog();

        findViewById(R.id.fTest).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                int sn = fSound.getSelectedItemPosition();
                Reminders.ensureChannel(EditActivity.this, sn);
                Reminders.preview(EditActivity.this, sn);
                Toast.makeText(EditActivity.this, R.string.test_sound_toast, Toast.LENGTH_LONG).show();
            }
        });

        if (isNew) fTitle.requestFocus();

        fDate.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                DatePickerDialog dlg = new DatePickerDialog(EditActivity.this,
                        new DatePickerDialog.OnDateSetListener() {
                            public void onDateSet(android.widget.DatePicker vv, int y, int m, int dd) {
                                when.set(Calendar.YEAR, y);
                                when.set(Calendar.MONTH, m);
                                when.set(Calendar.DAY_OF_MONTH, dd);
                                fDate.setText(Fmt.dateBtn(when.getTimeInMillis()));
                            }
                        },
                        when.get(Calendar.YEAR), when.get(Calendar.MONTH), when.get(Calendar.DAY_OF_MONTH));
                dlg.getDatePicker().setMinDate(Fmt.startOfToday());
                dlg.show();
            }
        });

        fTime.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                new TimePickerDialog(EditActivity.this, new TimePickerDialog.OnTimeSetListener() {
                    public void onTimeSet(android.widget.TimePicker vv, int hh, int mm) {
                        when.set(Calendar.HOUR_OF_DAY, hh);
                        when.set(Calendar.MINUTE, mm);
                        fTime.setText(Fmt.time(when.getTimeInMillis()));
                    }
                }, when.get(Calendar.HOUR_OF_DAY), when.get(Calendar.MINUTE), true).show();
            }
        });

        findViewById(R.id.back).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                finish();
            }
        });

        Button save = findViewById(R.id.save);
        save.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                String t = fTitle.getText().toString().trim();
                if (t.length() == 0) {
                    Toast.makeText(EditActivity.this, R.string.ch_title, Toast.LENGTH_SHORT).show();
                    fTitle.requestFocus();
                    return;
                }
                a.title = t;
                a.note = fNote.getText().toString().trim();
                a.childId = kidIds.get(fChild.getSelectedItemPosition());
                a.lead = LEADS[fLead.getSelectedItemPosition()];
                a.snd = fSound.getSelectedItemPosition();
                a.when = when.getTimeInMillis();
                a.days = daysMask;

                if (Recur.repeats(a)) {
                    int dow = when.get(Calendar.DAY_OF_WEEK) - 1;
                    if (((a.days >> dow) & 1) == 0) {
                        // anchor landed on a non-repeated day: roll forward to the nearest one
                        for (int i = 0; i < 7; i++) {
                            when.add(Calendar.DAY_OF_YEAR, 1);
                            if (((a.days >> (when.get(Calendar.DAY_OF_WEEK) - 1)) & 1) == 1) break;
                        }
                        a.when = when.getTimeInMillis();
                        fDate.setText(Fmt.dateBtn(a.when));
                    }
                }

                int st = fStatus.getSelectedItemPosition();
                if (occIn != 0L) {
                    Recur.setStatus(a, occIn, st);
                    if (!Recur.repeats(a)) a.done = st == Recur.DONE;
                } else if (!Recur.repeats(a)) {
                    a.done = st == Recur.DONE;
                    Recur.setStatus(a, a.when, st);
                }
                d.put(a);
                Reminders.scheduleAll(EditActivity.this);
                finish();
            }
        });

        Button del = findViewById(R.id.del);
        if (isNew) {
            del.setVisibility(View.GONE);
        } else {
            del.setVisibility(View.VISIBLE);
            del.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    new AlertDialog.Builder(EditActivity.this)
                            .setTitle(a.title)
                            .setMessage("متأكد إنك عايز تمسح الموعد ده؟")
                            .setPositiveButton(R.string.del, new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dd, int w) {
                                    Reminders.cancel(EditActivity.this, a.id);
                                    d.del(a.id);
                                    Reminders.scheduleAll(EditActivity.this);
                                    finish();
                                }
                            })
                            .setNegativeButton(R.string.cancel, null)
                            .show();
                }
            });
        }
    }

    private void paintLog() {
        if (isNew) {
            fLog.setText(R.string.log_empty);
            return;
        }
        java.util.ArrayList<String> lines = RecurLog.lines(this, a, 10);
        if (lines.isEmpty()) {
            fLog.setText(R.string.log_empty);
            return;
        }
        StringBuilder sb = new StringBuilder(getString(R.string.log_count, lines.size()));
        sb.append('\n');
        for (int i = 0; i < lines.size(); i++) {
            sb.append("\u2022 ").append(lines.get(i));
            if (i < lines.size() - 1) sb.append('\n');
        }
        fLog.setText(sb.toString());
    }

    /** seven toggle chips, one per weekday; empty selection means "one-off" */
    private void buildDayToggles(int initial) {
        fDays.removeAllViews();
        daysMask = initial;
        float den = getResources().getDisplayMetrics().density;
        for (int i = 0; i < 7; i++) {
            final int idx = i;
            TextView t = new TextView(this);
            t.setText(Recur.DAY_SHORT[i]);
            t.setTextSize(13f);
            t.setGravity(android.view.Gravity.CENTER);
            t.setPadding(0, (int) (10 * den), 0, (int) (10 * den));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            lp.setMarginEnd((int) (5 * den));
            if (i == 6) lp.setMarginEnd(0);
            t.setLayoutParams(lp);
            t.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    daysMask ^= (1 << idx);
                    paintDays();
                }
            });
            dayBtns[i] = t;
            fDays.addView(t);
        }
        paintDays();
    }

    private void paintDays() {
        for (int i = 0; i < 7; i++) {
            boolean on = ((daysMask >> i) & 1) == 1;
            GradientDrawable g = new GradientDrawable();
            g.setShape(GradientDrawable.RECTANGLE);
            g.setCornerRadius(8 * getResources().getDisplayMetrics().density);
            g.setColor(on ? 0xFF00897B : 0xFFFFFFFF);
            g.setStroke(1, 0xFF00897B);
            dayBtns[i].setBackgroundDrawable(g);
            dayBtns[i].setTextColor(on ? Color.WHITE : 0xFF00897B);
            dayBtns[i].setAlpha(on ? 1f : 0.55f);
        }
        fRepHint.setText(daysMask == 0
                ? getString(R.string.repeat_hint)
                : getString(R.string.repeat_on, Recur.daysLabel(maskAppt())));
    }

    private Data.Appt maskAppt() {
        Data.Appt t = new Data.Appt();
        t.days = daysMask;
        return t;
    }

    private static int indexOf(int lead) {
        for (int i = 0; i < LEADS.length; i++) if (LEADS[i] == lead) return i;
        return -1;
    }
}
