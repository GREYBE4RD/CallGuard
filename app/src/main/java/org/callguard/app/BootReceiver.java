/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 CallGuard contributors
 */
package org.callguard.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent != null ? intent.getAction() : null;

        // Migration requires credential-protected storage, so it can only complete after
        // unlock. On later reboots the settings are already in device-protected storage
        // and LOCKED_BOOT_COMPLETED can restore CallGuard immediately.
        if (Intent.ACTION_BOOT_COMPLETED.equals(action)
                || Intent.ACTION_USER_UNLOCKED.equals(action)
                || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {
            Prefs.ensureDeviceProtectedStorage(context);
        }

        NotificationHelper.refresh(context);
    }
}
