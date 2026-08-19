/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 CallGuard contributors
 */
package org.callguard.app;

import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;

/**
 * Registers lightweight, best-effort notification refresh triggers for events that cannot
 * reliably be handled by manifest receivers on modern Android. No foreground service or
 * continuously running work is used; these callbacks are available only while Android keeps
 * the CallGuard process resident.
 */
public class CallGuardApplication extends Application {
    private BroadcastReceiver interactiveReceiver;

    @Override
    public void onCreate() {
        super.onCreate();

        interactiveReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent != null ? intent.getAction() : null;
                if (Intent.ACTION_SCREEN_ON.equals(action)
                        || Intent.ACTION_USER_PRESENT.equals(action)) {
                    NotificationHelper.refresh(context);
                }
            }
        };

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_USER_PRESENT);

        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(interactiveReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(interactiveReceiver, filter);
        }
    }
}
