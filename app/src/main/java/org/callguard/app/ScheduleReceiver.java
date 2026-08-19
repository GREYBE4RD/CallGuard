/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 CallGuard contributors
 */
package org.callguard.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class ScheduleReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) { NotificationHelper.refresh(context); }
}
