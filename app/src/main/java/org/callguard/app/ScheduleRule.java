/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 CallGuard contributors
 */
package org.callguard.app;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.EnumSet;

public final class ScheduleRule {
    public final EnumSet<DayOfWeek> days;
    public final int startMinutes;
    public final int endMinutes;
    public final boolean allDay;

    public ScheduleRule(EnumSet<DayOfWeek> days, int startMinutes, int endMinutes, boolean allDay) {
        this.days = days.clone();
        this.startMinutes = startMinutes;
        this.endMinutes = endMinutes;
        this.allDay = allDay;
    }

    public boolean isActive(LocalDateTime now) {
        DayOfWeek today = now.getDayOfWeek();
        int minute = now.getHour() * 60 + now.getMinute();

        if (allDay) return days.contains(today);

        // Equal start/end is deliberately treated as an inactive rule. The UI has an
        // explicit All day switch, so 08:00 -> 08:00 must not unexpectedly mean 24 hours.
        if (startMinutes == endMinutes) return false;

        if (!days.contains(today)) return false;

        if (startMinutes < endMinutes) {
            return minute >= startMinutes && minute < endMinutes;
        }

        // Overnight rules are interpreted by selected calendar day, which is how the UI
        // reads naturally: Mon-Fri 17:00 -> 08:00 means each selected weekday is active
        // from 00:00-08:00 and again from 17:00-24:00.
        return minute >= startMinutes || minute < endMinutes;
    }

    public String encode() {
        int mask = 0;
        for (DayOfWeek d : days) mask |= 1 << (d.getValue() - 1);
        return mask + "," + startMinutes + "," + endMinutes + "," + (allDay ? 1 : 0);
    }

    public static ScheduleRule decode(String s) {
        String[] p = s.split(",");
        int mask = Integer.parseInt(p[0]);
        EnumSet<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);
        for (DayOfWeek d : DayOfWeek.values()) if ((mask & (1 << (d.getValue() - 1))) != 0) days.add(d);
        return new ScheduleRule(days, Integer.parseInt(p[1]), Integer.parseInt(p[2]), "1".equals(p[3]));
    }

    public static String timeText(int minutes) {
        return String.format("%02d:%02d", minutes / 60, minutes % 60);
    }
}
