package com.family.schedule;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
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

    private EditText fTitle, fNote;
    private Button fDate, fTime;
    private Spinner fChild, fLead, fSound;
    private Calendar when;

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

        String id = getIntent() == null ? null : getIntent().getStringExtra("id");
        a = d.find(id);
        isNew = a == null;

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

    private static int indexOf(int lead) {
        for (int i = 0; i < LEADS.length; i++) if (LEADS[i] == lead) return i;
        return -1;
    }
}
