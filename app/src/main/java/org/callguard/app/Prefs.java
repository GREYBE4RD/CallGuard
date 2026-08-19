/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 CallGuard contributors
 */
package org.callguard.app;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.UserManager;
import android.content.pm.PackageManager;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

public final class Prefs {
    public static final String MODE_OFF = "off";
    public static final String MODE_ALWAYS = "always";
    public static final String MODE_SCHEDULE = "schedule";
    public static final String MODE_TIMER = "timer";

    public static final String CALLER_ALLOW_CONTACTS = "allow_contacts";
    public static final String CALLER_FAVORITES_ONLY = "favorites_only";
    public static final String CALLER_ALL_TO_VOICEMAIL = "all_to_voicemail";

    private static final String FILE = "callguard";
    private static final String LEGACY_FILE = "call_filter";
    private static final String KEY_ENABLED = "enabled";
    private static final String KEY_ALWAYS = "always"; // legacy mode key
    private static final String KEY_MODE = "mode";
    private static final String KEY_RULES = "rules";
    private static final String KEY_NOTIFY_SCREENED = "notify_screened";
    private static final String KEY_CALLER_POLICY = "caller_policy";
    private static final String KEY_TIMER_UNTIL = "timer_until";
    private static final String KEY_TIMER_RETURN_MODE = "timer_return_mode";
    private static final String KEY_DEVICE_STORAGE_READY = "device_storage_ready";

    private Prefs() {}

    private static Context deviceContext(Context c) {
        return c.createDeviceProtectedStorageContext();
    }

    private static SharedPreferences devicePrefs(Context c) {
        return deviceContext(c).getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    private static boolean userUnlocked(Context c) {
        UserManager um = c.getSystemService(UserManager.class);
        return um == null || um.isUserUnlocked();
    }

    public static void ensureDeviceProtectedStorage(Context c) {
        SharedPreferences dp = devicePrefs(c);
        if (dp.getBoolean(KEY_DEVICE_STORAGE_READY, false)) return;
        if (!userUnlocked(c)) return;

        deviceContext(c).moveSharedPreferencesFrom(c, FILE);
        deviceContext(c).moveSharedPreferencesFrom(c, LEGACY_FILE);

        SharedPreferences legacy = deviceContext(c).getSharedPreferences(LEGACY_FILE, Context.MODE_PRIVATE);
        SharedPreferences current = devicePrefs(c);
        SharedPreferences.Editor e = current.edit();
        if (!current.contains(KEY_ENABLED) && legacy.contains(KEY_ENABLED)) e.putBoolean(KEY_ENABLED, legacy.getBoolean(KEY_ENABLED, true));
        if (!current.contains(KEY_RULES) && legacy.contains(KEY_RULES)) e.putString(KEY_RULES, legacy.getString(KEY_RULES, null));
        if (!current.contains(KEY_NOTIFY_SCREENED) && legacy.contains(KEY_NOTIFY_SCREENED)) e.putBoolean(KEY_NOTIFY_SCREENED, legacy.getBoolean(KEY_NOTIFY_SCREENED, true));
        e.putBoolean(KEY_DEVICE_STORAGE_READY, true).commit();
    }

    private static SharedPreferences sp(Context c) {
        ensureDeviceProtectedStorage(c);
        return devicePrefs(c);
    }

    public static boolean enabled(Context c) {
        return !MODE_OFF.equals(mode(c));
    }

    /** Legacy compatibility helper. New UI uses filtering modes directly. */
    public static void setEnabled(Context c, boolean v) {
        setMode(c, v ? MODE_ALWAYS : MODE_OFF);
    }

    /**
     * Returns the current mode. Fresh installs default to Off. If a timer has expired,
     * this method restores its configured return mode before returning.
     */
    public static String mode(Context c) {
        SharedPreferences p = sp(c);
        if (!p.getBoolean(KEY_DEVICE_STORAGE_READY, false) && !userUnlocked(c)) return MODE_OFF;

        String mode;
        if (p.contains(KEY_MODE)) {
            mode = validMode(p.getString(KEY_MODE, MODE_OFF));
        } else if (p.contains(KEY_ENABLED)) {
            // Migrate the pre-mode master switch without unexpectedly changing an existing install.
            if (!p.getBoolean(KEY_ENABLED, true)) {
                mode = MODE_OFF;
            } else if (p.contains(KEY_ALWAYS)) {
                mode = p.getBoolean(KEY_ALWAYS, false) ? MODE_ALWAYS : MODE_SCHEDULE;
            } else {
                mode = MODE_ALWAYS;
            }
            p.edit().putString(KEY_MODE, mode).apply();
        } else if (p.contains(KEY_ALWAYS)) {
            mode = p.getBoolean(KEY_ALWAYS, false) ? MODE_ALWAYS : MODE_SCHEDULE;
            p.edit().putString(KEY_MODE, mode).apply();
        } else {
            // Safe default for a fresh installation: no filtering until the user chooses a mode.
            mode = MODE_OFF;
        }

        if (MODE_TIMER.equals(mode)) {
            long until = p.getLong(KEY_TIMER_UNTIL, 0L);
            if (until <= 0L || System.currentTimeMillis() >= until) {
                String returnMode = normalMode(p.getString(KEY_TIMER_RETURN_MODE, MODE_OFF));
                p.edit().putString(KEY_MODE, returnMode).putLong(KEY_TIMER_UNTIL, 0L).apply();
                return returnMode;
            }
        }
        return mode;
    }

    public static void setMode(Context c, String mode) {
        String safe = MODE_TIMER.equals(mode) ? MODE_TIMER : normalMode(mode);
        sp(c).edit().putBoolean(KEY_DEVICE_STORAGE_READY, true).putString(KEY_MODE, safe).apply();
    }

    public static String normalMode(String mode) {
        if (MODE_SCHEDULE.equals(mode)) return MODE_SCHEDULE;
        if (MODE_ALWAYS.equals(mode)) return MODE_ALWAYS;
        return MODE_OFF;
    }

    private static String validMode(String mode) {
        if (MODE_TIMER.equals(mode)) return MODE_TIMER;
        return normalMode(mode);
    }

    public static boolean timerActive(Context c) {
        return MODE_TIMER.equals(mode(c));
    }

    public static long timerUntil(Context c) {
        return sp(c).getLong(KEY_TIMER_UNTIL, 0L);
    }

    public static String timerReturnMode(Context c) {
        return normalMode(sp(c).getString(KEY_TIMER_RETURN_MODE, MODE_OFF));
    }

    public static void startTimer(Context c, long untilEpochMillis, String returnMode) {
        sp(c).edit()
                .putBoolean(KEY_DEVICE_STORAGE_READY, true)
                .putString(KEY_TIMER_RETURN_MODE, normalMode(returnMode))
                .putLong(KEY_TIMER_UNTIL, Math.max(System.currentTimeMillis() + 60_000L, untilEpochMillis))
                .putString(KEY_MODE, MODE_TIMER)
                .apply();
    }

    public static void setTimerReturnMode(Context c, String returnMode) {
        sp(c).edit().putString(KEY_TIMER_RETURN_MODE, normalMode(returnMode)).apply();
    }

    public static void cancelTimerTo(Context c, String mode) {
        sp(c).edit()
                .putString(KEY_MODE, normalMode(mode))
                .putLong(KEY_TIMER_UNTIL, 0L)
                .apply();
    }

    /**
     * Chooses who is allowed to ring while a Guard mode is active. Fresh installs default
     * to allowing saved contacts. Favorites-only and all-calls policies require READ_CONTACTS
     * so Android will also deliver saved-contact calls to the screening service.
     */
    public static String callerPolicy(Context c) {
        String value = sp(c).getString(KEY_CALLER_POLICY, CALLER_ALLOW_CONTACTS);
        if (CALLER_ALL_TO_VOICEMAIL.equals(value)) return CALLER_ALL_TO_VOICEMAIL;
        if (CALLER_FAVORITES_ONLY.equals(value)) return CALLER_FAVORITES_ONLY;
        return CALLER_ALLOW_CONTACTS;
    }

    public static void setCallerPolicy(Context c, String policy) {
        String safe;
        if (CALLER_ALL_TO_VOICEMAIL.equals(policy)) safe = CALLER_ALL_TO_VOICEMAIL;
        else if (CALLER_FAVORITES_ONLY.equals(policy)) safe = CALLER_FAVORITES_ONLY;
        else safe = CALLER_ALLOW_CONTACTS;
        sp(c).edit().putBoolean(KEY_DEVICE_STORAGE_READY, true).putString(KEY_CALLER_POLICY, safe).apply();
    }

    public static boolean contactsPermissionGranted(Context c) {
        return c.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED;
    }

    public static boolean sendAllCallsToVoicemail(Context c) {
        return CALLER_ALL_TO_VOICEMAIL.equals(callerPolicy(c)) && contactsPermissionGranted(c);
    }

    public static boolean favoritesOnly(Context c) {
        return CALLER_FAVORITES_ONLY.equals(callerPolicy(c)) && contactsPermissionGranted(c);
    }

    public static boolean notifyScreenedCalls(Context c) {
        SharedPreferences p = sp(c);
        if (!p.getBoolean(KEY_DEVICE_STORAGE_READY, false) && !userUnlocked(c)) return true;
        return p.getBoolean(KEY_NOTIFY_SCREENED, true);
    }

    public static void setNotifyScreenedCalls(Context c, boolean v) {
        sp(c).edit().putBoolean(KEY_DEVICE_STORAGE_READY, true).putBoolean(KEY_NOTIFY_SCREENED, v).apply();
    }

    public static List<ScheduleRule> rules(Context c) {
        SharedPreferences p = sp(c);
        if (!p.getBoolean(KEY_DEVICE_STORAGE_READY, false) && !userUnlocked(c)) return new ArrayList<>();
        String raw = p.getString(KEY_RULES, null);
        if (raw == null) return defaults();
        List<ScheduleRule> out = new ArrayList<>();
        if (raw.isEmpty()) return out;
        for (String s : raw.split(";")) {
            try { if (!s.isEmpty()) out.add(ScheduleRule.decode(s)); }
            catch (Exception ignored) { }
        }
        return out;
    }

    public static void setRules(Context c, List<ScheduleRule> rules) {
        StringBuilder b = new StringBuilder();
        for (ScheduleRule r : rules) {
            if (b.length() > 0) b.append(';');
            b.append(r.encode());
        }
        sp(c).edit().putBoolean(KEY_DEVICE_STORAGE_READY, true).putString(KEY_RULES, b.toString()).apply();
    }

    public static boolean filteringActive(Context c, LocalDateTime now) {
        String mode = mode(c);
        if (MODE_OFF.equals(mode)) return false;
        if (MODE_ALWAYS.equals(mode) || MODE_TIMER.equals(mode)) return true;
        for (ScheduleRule r : rules(c)) if (r.isActive(now)) return true;
        return false;
    }

    public static List<ScheduleRule> defaults() {
        List<ScheduleRule> list = new ArrayList<>();
        list.add(new ScheduleRule(EnumSet.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY), 17*60, 8*60, false));
        list.add(new ScheduleRule(EnumSet.of(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY), 0, 0, true));
        return list;
    }
}
