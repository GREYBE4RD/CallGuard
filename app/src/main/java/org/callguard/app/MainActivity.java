/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 CallGuard contributors
 */
package org.callguard.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.app.role.RoleManager;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

public class MainActivity extends Activity {
    private static final int REQ_ROLE = 42;
    private static final int REQ_NOTIFICATIONS = 43;
    private static final int REQ_CONTACTS = 44;

    private LinearLayout rulesSection;
    private LinearLayout rulesBox;
    private LinearLayout timerOptions;
    private TextView status;
    private TextView timerSummary;
    private TextView roleStatus;
    private Switch notifyScreened;
    private RadioButton allowContacts, favoritesOnly, allCalls;
    private RadioButton off, always, scheduled, timed;
    private RadioButton returnOff, returnAlways, returnSchedule;
    private Button roleButton;
    private final List<RuleEditor> editors = new ArrayList<>();
    private boolean suppressCallerListener;
    private boolean suppressModeListener;
    private boolean suppressReturnListener;
    private String previousNormalMode = Prefs.MODE_OFF;
    private String pendingCallerPolicy = null;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        Prefs.ensureDeviceProtectedStorage(this);
        setContentView(buildUi());
        requestNotificationPermissionIfNeeded();
        NotificationHelper.refresh(this);
    }

    @Override protected void onResume() {
        super.onResume();
        syncModeUi();
        updateStatus();
        NotificationHelper.refresh(this);
    }

    private View buildUi() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = vertical(20);
        scroll.addView(root);

        root.addView(text("CallGuard", 28, true));
        root.addView(text("Control which callers can ring and when CallGuard is active.", 16, false));

        status = text("", 17, true);
        root.addView(status);

        roleStatus = text("Call screening role: NOT enabled", 15, true);
        root.addView(roleStatus);

        roleButton = new Button(this);
        roleButton.setText("Enable Android call screening role");
        roleButton.setOnClickListener(v -> requestRole());
        root.addView(roleButton);


        root.addView(text("Caller handling", 19, true));
        RadioGroup callerHandling = new RadioGroup(this);
        callerHandling.setOrientation(RadioGroup.VERTICAL);
        allowContacts = new RadioButton(this); allowContacts.setId(View.generateViewId()); allowContacts.setText("Allow contacts");
        favoritesOnly = new RadioButton(this); favoritesOnly.setId(View.generateViewId()); favoritesOnly.setText("Favorites only");
        allCalls = new RadioButton(this); allCalls.setId(View.generateViewId()); allCalls.setText("Send all calls to voicemail");
        callerHandling.addView(allowContacts); callerHandling.addView(favoritesOnly); callerHandling.addView(allCalls);
        root.addView(callerHandling);

        callerHandling.setOnCheckedChangeListener((g,id) -> {
            if (suppressCallerListener) return;
            String requested = id == allCalls.getId() ? Prefs.CALLER_ALL_TO_VOICEMAIL
                    : (id == favoritesOnly.getId() ? Prefs.CALLER_FAVORITES_ONLY : Prefs.CALLER_ALLOW_CONTACTS);
            if (Prefs.CALLER_ALLOW_CONTACTS.equals(requested)) {
                pendingCallerPolicy = null;
                Prefs.setCallerPolicy(this, requested);
                changed();
            } else if (checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
                pendingCallerPolicy = null;
                Prefs.setCallerPolicy(this, requested);
                changed();
            } else {
                pendingCallerPolicy = requested;
                requestPermissions(new String[]{Manifest.permission.READ_CONTACTS}, REQ_CONTACTS);
            }
        });

        root.addView(text("Guard mode", 19, true));
        RadioGroup mode = new RadioGroup(this);
        mode.setOrientation(RadioGroup.VERTICAL);
        off = new RadioButton(this); off.setId(View.generateViewId()); off.setText("Off");
        always = new RadioButton(this); always.setId(View.generateViewId()); always.setText("Always on");
        scheduled = new RadioButton(this); scheduled.setId(View.generateViewId()); scheduled.setText("Schedule");
        timed = new RadioButton(this); timed.setId(View.generateViewId()); timed.setText("For a set time");
        mode.addView(off); mode.addView(always); mode.addView(scheduled); mode.addView(timed);
        root.addView(mode);

        timerOptions = vertical(8);
        timerOptions.setPadding(18, 0, 0, 8);
        timerSummary = text("", 15, true);
        timerOptions.addView(timerSummary);
        Button duration = new Button(this);
        duration.setText("Set / change duration");
        duration.setOnClickListener(v -> showTimerDialog(false));
        timerOptions.addView(duration);
        timerOptions.addView(text("Return to", 15, true));
        RadioGroup returnGroup = new RadioGroup(this);
        returnGroup.setOrientation(RadioGroup.VERTICAL);
        returnOff = new RadioButton(this); returnOff.setId(View.generateViewId()); returnOff.setText("Off");
        returnAlways = new RadioButton(this); returnAlways.setId(View.generateViewId()); returnAlways.setText("Always on");
        returnSchedule = new RadioButton(this); returnSchedule.setId(View.generateViewId()); returnSchedule.setText("Schedule");
        returnGroup.addView(returnOff); returnGroup.addView(returnAlways); returnGroup.addView(returnSchedule);
        timerOptions.addView(returnGroup);
        root.addView(timerOptions);

        returnGroup.setOnCheckedChangeListener((g,id) -> {
            if (suppressReturnListener) return;
            Prefs.setTimerReturnMode(this, id == returnSchedule.getId() ? Prefs.MODE_SCHEDULE : (id == returnAlways.getId() ? Prefs.MODE_ALWAYS : Prefs.MODE_OFF));
            updateTimerSummary();
            changed();
        });

        mode.setOnCheckedChangeListener((g,id)->{
            if (suppressModeListener) return;
            if (id == timed.getId()) {
                String current = Prefs.mode(this);
                if (!Prefs.MODE_TIMER.equals(current)) previousNormalMode = Prefs.normalMode(current);
                showTimerDialog(true);
            } else {
                String selected = id == scheduled.getId() ? Prefs.MODE_SCHEDULE
                        : (id == always.getId() ? Prefs.MODE_ALWAYS : Prefs.MODE_OFF);
                previousNormalMode = selected;
                Prefs.cancelTimerTo(this, selected);
                syncModeUi();
                changed();
            }
        });

        notifyScreened = new Switch(this);
        notifyScreened.setText("Notify about calls sent to voicemail");
        notifyScreened.setTextSize(16);
        notifyScreened.setChecked(Prefs.notifyScreenedCalls(this));
        notifyScreened.setOnCheckedChangeListener((b,v) -> Prefs.setNotifyScreenedCalls(this, v));
        root.addView(notifyScreened, lpMatchWrap());

        rulesSection = vertical(0);
        rulesSection.addView(text("Schedule rules", 21, true));
        rulesBox = vertical(0);
        rulesSection.addView(rulesBox);
        for (ScheduleRule r : Prefs.rules(this)) addRuleEditor(r);
        Button add = new Button(this);
        add.setText("+ Add schedule rule");
        add.setOnClickListener(v -> addRuleEditor(new ScheduleRule(EnumSet.of(DayOfWeek.MONDAY), 17*60, 8*60, false)));
        rulesSection.addView(add);
        root.addView(rulesSection);

        Button detailsButton = new Button(this);
        detailsButton.setText("Details ▾");
        root.addView(detailsButton);

        LinearLayout details = vertical(0);
        details.setVisibility(View.GONE);
        details.addView(text("Caller handling", 17, true));
        details.addView(text("Allow contacts lets saved contacts ring while Guard is active. Favorites only lets only contacts marked as Favorite/Starred ring. Send all calls to voicemail allows nobody through. Favorites only and all-calls handling require Contacts permission; CallGuard requests it only when one of those options is selected.", 14, false));
        details.addView(text("Call screening", 17, true));
        details.addView(text("Android's Call Screening role is required. If the role is not enabled, CallGuard shows the role warning and setup button near the top of the app.", 14, false));
        details.addView(text("Private and hidden numbers", 17, true));
        details.addView(text("Android does not pass hidden/private/unavailable caller-ID calls to third-party call-screening apps. Use your phone app's built-in private/unknown-number blocking if you also want those suppressed.", 14, false));
        details.addView(text("Notifications", 17, true));
        details.addView(text("The status notification shows the current Guard mode and caller-handling policy. The optional per-call notification can be disabled without affecting call screening.", 14, false));
        details.addView(text("CallGuard always checks the current Guard mode and schedule when a call arrives, even if the status notification is temporarily out of date. Android may delay background notification refreshes while the phone is idle; waking or unlocking the phone, opening CallGuard, or another refresh event will update the displayed status.", 14, false));
        details.addView(text("Timed mode", 17, true));
        details.addView(text("For a set time keeps Guard active continuously for the chosen duration. When the timer ends, CallGuard returns to Off, Always on, or Schedule. The return mode defaults to whichever mode was selected before Timed mode, but it can be changed while the timer is active.", 14, false));
        details.addView(text("Schedule behaviour", 17, true));
        details.addView(text("Schedule rules are shown only when Schedule mode is selected. Overnight rules are supported. Example: Mon–Fri 17:00 → 08:00 means 00:00–08:00 and 17:00–24:00 on each selected weekday.", 14, false));
        details.addView(text("If Start and End are the same, the rule is inactive. Use All day for a full-day rule.", 14, false));
        root.addView(details);

        detailsButton.setOnClickListener(v -> {
            boolean opening = details.getVisibility() != View.VISIBLE;
            details.setVisibility(opening ? View.VISIBLE : View.GONE);
            detailsButton.setText(opening ? "Details ▴" : "Details ▾");
        });

        syncModeUi();
        return scroll;
    }

    private void showTimerDialog(boolean enteringTimer) {
        LinearLayout box = vertical(8);
        box.addView(text("Choose how long CallGuard should stay continuously active.", 14, false));

        LinearLayout wheels = new LinearLayout(this);
        wheels.setOrientation(LinearLayout.HORIZONTAL);
        NumberPicker hours = new NumberPicker(this);
        hours.setMinValue(0); hours.setMaxValue(72); hours.setValue(1);
        NumberPicker minutes = new NumberPicker(this);
        minutes.setMinValue(0); minutes.setMaxValue(59); minutes.setValue(0);
        wheels.addView(hours, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        wheels.addView(minutes, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        box.addView(wheels);
        LinearLayout labels = new LinearLayout(this); labels.setOrientation(LinearLayout.HORIZONTAL);
        TextView h = text("hours", 13, false); h.setGravity(android.view.Gravity.CENTER);
        TextView m = text("minutes", 13, false); m.setGravity(android.view.Gravity.CENTER);
        labels.addView(h, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT,1));
        labels.addView(m, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT,1));
        box.addView(labels);

        box.addView(text("When the timer ends, return to", 15, true));
        RadioGroup returnChoice = new RadioGroup(this);
        returnChoice.setOrientation(RadioGroup.VERTICAL);
        RadioButton dialogReturnOff = new RadioButton(this);
        dialogReturnOff.setId(View.generateViewId());
        dialogReturnOff.setText("Off");
        RadioButton dialogReturnAlways = new RadioButton(this);
        dialogReturnAlways.setId(View.generateViewId());
        dialogReturnAlways.setText("Always on");
        RadioButton dialogReturnSchedule = new RadioButton(this);
        dialogReturnSchedule.setId(View.generateViewId());
        dialogReturnSchedule.setText("Schedule");
        returnChoice.addView(dialogReturnOff);
        returnChoice.addView(dialogReturnAlways);
        returnChoice.addView(dialogReturnSchedule);
        String defaultReturn = enteringTimer ? previousNormalMode : Prefs.timerReturnMode(this);
        if (Prefs.MODE_SCHEDULE.equals(defaultReturn)) dialogReturnSchedule.setChecked(true);
        else if (Prefs.MODE_ALWAYS.equals(defaultReturn)) dialogReturnAlways.setChecked(true);
        else dialogReturnOff.setChecked(true);
        box.addView(returnChoice);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Set Guard duration")
                .setView(box)
                .setPositiveButton(enteringTimer ? "Start" : "Update", null)
                .setNegativeButton("Cancel", null)
                .create();
        dialog.setOnShowListener(x -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                int totalMinutes = hours.getValue() * 60 + minutes.getValue();
                if (totalMinutes <= 0) {
                    Toast.makeText(this, "Choose at least 1 minute.", Toast.LENGTH_SHORT).show();
                    return;
                }
                long until = System.currentTimeMillis() + totalMinutes * 60_000L;
                String selectedReturn = dialogReturnSchedule.isChecked() ? Prefs.MODE_SCHEDULE : (dialogReturnAlways.isChecked() ? Prefs.MODE_ALWAYS : Prefs.MODE_OFF);
                Prefs.startTimer(this, until, selectedReturn);
                syncModeUi();
                changed();
                dialog.dismiss();
            });
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener(v -> {
                if (enteringTimer) {
                    Prefs.cancelTimerTo(this, previousNormalMode);
                    syncModeUi();
                    changed();
                }
                dialog.dismiss();
            });
        });
        dialog.show();
    }

    private void syncModeUi() {
        if (always == null) return;

        String policy = Prefs.callerPolicy(this);
        if (!Prefs.CALLER_ALLOW_CONTACTS.equals(policy)
                && checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            // Favorites-only and all-calls policies need Contacts permission. Fall back
            // visibly and safely if permission has been revoked.
            Prefs.setCallerPolicy(this, Prefs.CALLER_ALLOW_CONTACTS);
            policy = Prefs.CALLER_ALLOW_CONTACTS;
        }
        suppressCallerListener = true;
        allCalls.setChecked(Prefs.CALLER_ALL_TO_VOICEMAIL.equals(policy));
        favoritesOnly.setChecked(Prefs.CALLER_FAVORITES_ONLY.equals(policy));
        allowContacts.setChecked(Prefs.CALLER_ALLOW_CONTACTS.equals(policy));
        suppressCallerListener = false;

        String mode = Prefs.mode(this);
        suppressModeListener = true;
        off.setChecked(Prefs.MODE_OFF.equals(mode));
        always.setChecked(Prefs.MODE_ALWAYS.equals(mode));
        scheduled.setChecked(Prefs.MODE_SCHEDULE.equals(mode));
        timed.setChecked(Prefs.MODE_TIMER.equals(mode));
        suppressModeListener = false;

        rulesSection.setVisibility(Prefs.MODE_SCHEDULE.equals(mode) ? View.VISIBLE : View.GONE);
        timerOptions.setVisibility(Prefs.MODE_TIMER.equals(mode) ? View.VISIBLE : View.GONE);
        if (Prefs.MODE_TIMER.equals(mode)) {
            suppressReturnListener = true;
            String ret = Prefs.timerReturnMode(this);
            returnSchedule.setChecked(Prefs.MODE_SCHEDULE.equals(ret));
            returnAlways.setChecked(Prefs.MODE_ALWAYS.equals(ret));
            returnOff.setChecked(Prefs.MODE_OFF.equals(ret));
            suppressReturnListener = false;
            updateTimerSummary();
        }
    }

    private void updateTimerSummary() {
        if (timerSummary == null || !Prefs.timerActive(this)) return;
        long until = Prefs.timerUntil(this);
        LocalDateTime end = LocalDateTime.ofInstant(Instant.ofEpochMilli(until), ZoneId.systemDefault());
        timerSummary.setText("Guard active until " + end.format(DateTimeFormatter.ofPattern("EEE HH:mm"))
                + "\nThen returns to: " + modeLabel(Prefs.timerReturnMode(this)));
    }

    private String modeLabel(String mode) {
        if (Prefs.MODE_SCHEDULE.equals(mode)) return "Schedule";
        if (Prefs.MODE_ALWAYS.equals(mode)) return "Always on";
        return "Off";
    }

    private void addRuleEditor(ScheduleRule rule) {
        RuleEditor e = new RuleEditor(rule);
        editors.add(e);
        rulesBox.addView(e.box);
        saveRules();
    }

    private void saveRules() {
        List<ScheduleRule> list = new ArrayList<>();
        for (RuleEditor e : editors) list.add(e.toRule());
        Prefs.setRules(this, list);
        changed();
    }

    private void changed() {
        updateStatus();
        syncModeUi();
        NotificationHelper.refresh(this);
    }

    private void updateStatus() {
        RoleManager rm = getSystemService(RoleManager.class);
        boolean holds = rm != null && rm.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING) && rm.isRoleHeld(RoleManager.ROLE_CALL_SCREENING);
        if (roleButton != null) roleButton.setVisibility(holds ? View.GONE : View.VISIBLE);
        if (roleStatus != null) roleStatus.setVisibility(holds ? View.GONE : View.VISIBLE);

        String statusText;
        String mode = Prefs.mode(this);
        if (Prefs.MODE_OFF.equals(mode)) {
            statusText = "Guard: OFF";
        } else if (!holds) {
            statusText = "Guard: ROLE REQUIRED";
        } else {
            if (Prefs.MODE_TIMER.equals(mode)) {
                LocalDateTime end = LocalDateTime.ofInstant(Instant.ofEpochMilli(Prefs.timerUntil(this)), ZoneId.systemDefault());
                statusText = "Guard: ACTIVE — Timed until " + end.format(DateTimeFormatter.ofPattern("HH:mm"));
            } else if (Prefs.MODE_ALWAYS.equals(mode)) {
                statusText = "Guard: ACTIVE — Always on";
            } else if (Prefs.filteringActive(this, LocalDateTime.now())) {
                statusText = "Guard: ACTIVE — Scheduled";
            } else {
                statusText = "Guard: INACTIVE — Scheduled";
            }
        }
        if (!Prefs.MODE_OFF.equals(mode) && holds) {
            if (Prefs.sendAllCallsToVoicemail(this)) statusText += " — ALL CALLS";
            else if (Prefs.favoritesOnly(this)) statusText += " — Favorites only";
            else statusText += " — Contacts allowed";
        }
        status.setText(statusText);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_CONTACTS) {
            boolean granted = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            String requested = pendingCallerPolicy;
            pendingCallerPolicy = null;
            if (granted && (Prefs.CALLER_FAVORITES_ONLY.equals(requested) || Prefs.CALLER_ALL_TO_VOICEMAIL.equals(requested))) {
                Prefs.setCallerPolicy(this, requested);
            } else {
                Prefs.setCallerPolicy(this, Prefs.CALLER_ALLOW_CONTACTS);
                if (!granted) {
                    Toast.makeText(this, "Contacts permission is required for Favorites only and Send all calls to voicemail.", Toast.LENGTH_LONG).show();
                }
            }
            syncModeUi();
            changed();
        }
    }

    private void requestRole() {
        RoleManager rm = getSystemService(RoleManager.class);
        if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)) {
            startActivityForResult(rm.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING), REQ_ROLE);
        } else {
            Toast.makeText(this, "Call screening role is not available on this device.", Toast.LENGTH_LONG).show();
        }
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIFICATIONS);
        }
    }

    private final class RuleEditor {
        final LinearLayout box = vertical(8);
        final CheckBox[] days = new CheckBox[7];
        final Switch allDay = new Switch(MainActivity.this);
        final Button start = new Button(MainActivity.this), end = new Button(MainActivity.this);
        int startMin, endMin;

        RuleEditor(ScheduleRule r) {
            box.setPadding(12,12,12,12);
            LinearLayout dayRow = new LinearLayout(MainActivity.this); dayRow.setOrientation(LinearLayout.HORIZONTAL);
            String[] names = {"M","T","W","T","F","S","S"};
            DayOfWeek[] vals = DayOfWeek.values();
            for (int i=0;i<7;i++) {
                days[i] = new CheckBox(MainActivity.this); days[i].setText(names[i]); days[i].setChecked(r.days.contains(vals[i]));
                days[i].setOnCheckedChangeListener((b,v)->saveRules()); dayRow.addView(days[i]);
            }
            box.addView(dayRow);
            allDay.setText("All day"); allDay.setChecked(r.allDay); allDay.setOnCheckedChangeListener((b,v)->{ setTimeEnabled(!v); saveRules(); }); box.addView(allDay);
            startMin=r.startMinutes; endMin=r.endMinutes;
            LinearLayout timeRow = new LinearLayout(MainActivity.this); timeRow.setOrientation(LinearLayout.HORIZONTAL);
            start.setText("Start " + ScheduleRule.timeText(startMin)); end.setText("End " + ScheduleRule.timeText(endMin));
            start.setOnClickListener(v->pickTime(true)); end.setOnClickListener(v->pickTime(false));
            timeRow.addView(start, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT,1));
            timeRow.addView(end, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT,1)); box.addView(timeRow);
            setTimeEnabled(!r.allDay);
            Button del = new Button(MainActivity.this); del.setText("Delete rule"); del.setOnClickListener(v->{ editors.remove(this); rulesBox.removeView(box); saveRules(); }); box.addView(del);
        }

        void pickTime(boolean isStart) {
            int m = isStart ? startMin : endMin;
            new TimePickerDialog(MainActivity.this, (v,h,min)->{
                int n=h*60+min; if(isStart) startMin=n; else endMin=n;
                start.setText("Start " + ScheduleRule.timeText(startMin)); end.setText("End " + ScheduleRule.timeText(endMin)); saveRules();
            }, m/60, m%60, true).show();
        }

        void setTimeEnabled(boolean e) { start.setEnabled(e); end.setEnabled(e); }
        ScheduleRule toRule() {
            EnumSet<DayOfWeek> set = EnumSet.noneOf(DayOfWeek.class);
            DayOfWeek[] vals = DayOfWeek.values(); for(int i=0;i<7;i++) if(days[i].isChecked()) set.add(vals[i]);
            return new ScheduleRule(set,startMin,endMin,allDay.isChecked());
        }
    }

    private LinearLayout vertical(int pad) { LinearLayout l=new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); l.setPadding(pad,pad,pad,pad); return l; }
    private TextView text(String s,int sp,boolean bold){ TextView t=new TextView(this); t.setText(s); t.setTextSize(sp); if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD); t.setPadding(0,8,0,8); return t; }
    private LinearLayout.LayoutParams lpMatchWrap(){ return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT); }
}
