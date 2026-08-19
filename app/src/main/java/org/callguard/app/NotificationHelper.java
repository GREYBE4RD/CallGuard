/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 CallGuard contributors
 */
package org.callguard.app;

import android.Manifest;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.role.RoleManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public final class NotificationHelper {
    private static final String CHANNEL = "status_v4";
    private static final String[] LEGACY_STATUS_CHANNELS = {"status_v3", "status_v2", "status"};
    private static final String SCREENED_CHANNEL = "screened_calls";
    private static final String SCREENED_GROUP = "screened_calls_group";
    private static final int NOTIFICATION_ID = 1001;
    private static final int ALARM_ID = 1002;

    private NotificationHelper() {}

    public static void refresh(Context c) {
        String mode = Prefs.mode(c); // also restores an expired timer to its return mode
        if (Prefs.MODE_OFF.equals(mode)) {
            c.getSystemService(NotificationManager.class).cancel(NOTIFICATION_ID);
            cancelScheduledRefresh(c);
            return;
        }

        createChannel(c);
        if (Prefs.MODE_TIMER.equals(mode)) {
            long until = Prefs.timerUntil(c);
            if (until > System.currentTimeMillis()) scheduleRefreshAt(c, until);
            else cancelScheduledRefresh(c);
        } else if (Prefs.MODE_ALWAYS.equals(mode)) {
            cancelScheduledRefresh(c);
        } else {
            scheduleNextRefresh(c);
        }

        if (Build.VERSION.SDK_INT >= 33 && c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return;

        boolean roleHeld = hasScreeningRole(c);
        boolean active = roleHeld && Prefs.filteringActive(c, LocalDateTime.now());
        String guardedCallers;
        if (Prefs.sendAllCallsToVoicemail(c)) guardedCallers = "All callers → voicemail";
        else if (Prefs.favoritesOnly(c)) guardedCallers = "Only favorites ring";
        else guardedCallers = "Non-contact callers → voicemail";
        String title;
        String text;
        int smallIcon;

        if (!roleHeld) {
            title = "CallGuard — ROLE REQUIRED";
            text = "Enable the Android call screening role";
            smallIcon = R.drawable.ic_stat_call_filter_inactive;
        } else if (Prefs.MODE_TIMER.equals(mode)) {
            LocalDateTime end = LocalDateTime.ofInstant(Instant.ofEpochMilli(Prefs.timerUntil(c)), ZoneId.systemDefault());
            String returnMode = Prefs.timerReturnMode(c);
            String returns = Prefs.MODE_SCHEDULE.equals(returnMode) ? "Schedule" : (Prefs.MODE_ALWAYS.equals(returnMode) ? "Always on" : "Off");
            title = "CallGuard — ACTIVE — Timed";
            text = guardedCallers + " until " + end.format(DateTimeFormatter.ofPattern("HH:mm")) + "; then " + returns;
            smallIcon = R.drawable.ic_stat_call_filter;
        } else if (Prefs.MODE_ALWAYS.equals(mode)) {
            title = "CallGuard — ACTIVE";
            text = "Always on — " + guardedCallers;
            smallIcon = R.drawable.ic_stat_call_filter;
        } else if (active) {
            title = "CallGuard — ACTIVE";
            text = "Scheduled — " + guardedCallers;
            smallIcon = R.drawable.ic_stat_call_filter;
        } else {
            title = "CallGuard — Scheduled";
            text = "Currently inactive; schedule enabled — " + guardedCallers.replace(" → voicemail", " when active");
            smallIcon = R.drawable.ic_stat_call_filter_inactive;
        }

        Intent open = new Intent(c, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(c, 0, open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification n = new Notification.Builder(c, CHANNEL)
                .setSmallIcon(smallIcon)
                .setContentTitle(title)
                .setContentText(text)
                .setContentIntent(pi)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .build();
        c.getSystemService(NotificationManager.class).notify(NOTIFICATION_ID, n);
    }

    public static void notifyScreenedCall(Context c, String number) {
        createScreenedChannel(c);
        if (Build.VERSION.SDK_INT >= 33 && c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return;

        String displayNumber = (number == null || number.trim().isEmpty()) ? "Unknown number" : number;
        Intent open = new Intent(c, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(c, 1, open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification n = new Notification.Builder(c, SCREENED_CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_voicemail)
                .setContentTitle("Call sent to voicemail")
                .setContentText(displayNumber)
                .setContentIntent(pi)
                .setAutoCancel(true)
                .setGroup(SCREENED_GROUP)
                .setWhen(System.currentTimeMillis())
                .setShowWhen(true)
                .build();
        int id = (int) (System.currentTimeMillis() & 0x7fffffff);
        c.getSystemService(NotificationManager.class).notify(id, n);
    }

    private static void createChannel(Context c) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        NotificationChannel ch = new NotificationChannel(CHANNEL, "CallGuard status", NotificationManager.IMPORTANCE_LOW);
        ch.setDescription("Shows the current CallGuard Guard mode and status");
        ch.setSound(null, null);
        ch.setShowBadge(false);
        nm.createNotificationChannel(ch);
        for (String legacy : LEGACY_STATUS_CHANNELS) {
            if (nm.getNotificationChannel(legacy) != null) nm.deleteNotificationChannel(legacy);
        }
    }

    private static void createScreenedChannel(Context c) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        NotificationChannel ch = new NotificationChannel(SCREENED_CHANNEL, "Screened calls", NotificationManager.IMPORTANCE_DEFAULT);
        ch.setDescription("Notifies when CallGuard sends an incoming call to voicemail");
        ch.setShowBadge(true);
        nm.createNotificationChannel(ch);
    }

    private static void scheduleNextRefresh(Context c) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime next = null;
        for (int d = 0; d <= 8; d++) {
            LocalDateTime day = now.toLocalDate().plusDays(d).atStartOfDay();
            DayOfWeek dow = day.getDayOfWeek();
            for (ScheduleRule r : Prefs.rules(c)) {
                if (!r.days.contains(dow)) continue;
                if (r.allDay) {
                    next = earlierAfter(now, next, day);
                    next = earlierAfter(now, next, day.plusDays(1));
                } else if (r.startMinutes != r.endMinutes) {
                    next = earlierAfter(now, next, day.plusMinutes(r.endMinutes));
                    next = earlierAfter(now, next, day.plusMinutes(r.startMinutes));
                }
            }
        }
        if (next == null) { cancelScheduledRefresh(c); return; }
        scheduleRefreshAt(c, next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
    }

    private static void scheduleRefreshAt(Context c, long when) {
        Intent i = new Intent(c, ScheduleReceiver.class);
        PendingIntent pi = PendingIntent.getBroadcast(c, ALARM_ID, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        AlarmManager am = c.getSystemService(AlarmManager.class);
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, pi);
    }

    private static boolean hasScreeningRole(Context c) {
        RoleManager rm = c.getSystemService(RoleManager.class);
        return rm != null && rm.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING) && rm.isRoleHeld(RoleManager.ROLE_CALL_SCREENING);
    }

    private static void cancelScheduledRefresh(Context c) {
        Intent i = new Intent(c, ScheduleReceiver.class);
        PendingIntent pi = PendingIntent.getBroadcast(c, ALARM_ID, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_NO_CREATE);
        if (pi != null) {
            c.getSystemService(AlarmManager.class).cancel(pi);
            pi.cancel();
        }
    }

    private static LocalDateTime earlierAfter(LocalDateTime now, LocalDateTime current, LocalDateTime candidate) {
        if (!candidate.isAfter(now.plusSeconds(2))) return current;
        return current == null || candidate.isBefore(current) ? candidate : current;
    }
}
