/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 CallGuard contributors
 */
package org.callguard.app;

import android.Manifest;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;
import android.telecom.Call;
import android.telecom.CallScreeningService;

import java.time.LocalDateTime;

public class ScreeningService extends CallScreeningService {
    @Override
    public void onScreenCall(Call.Details details) {
        if (details.getCallDirection() != Call.Details.DIRECTION_INCOMING) return;

        String number = null;
        if (details.getHandle() != null) number = details.getHandle().getSchemeSpecificPart();

        boolean guardActive = Prefs.filteringActive(this, LocalDateTime.now());
        boolean block = false;

        if (guardActive) {
            if (Prefs.sendAllCallsToVoicemail(this)) {
                block = true;
            } else if (Prefs.favoritesOnly(this)) {
                ContactMatch match = lookupContact(number);
                // In Favorites-only mode, only a successfully matched starred contact rings.
                // If the provider itself fails, fail open rather than risk blocking a caller
                // because Android's Contacts storage is temporarily unavailable.
                block = match.lookupSucceeded && !match.starred;
            } else {
                // Without READ_CONTACTS Android normally invokes CallScreeningService only for
                // non-contact calls. If permission was previously granted, explicitly allow
                // saved contacts through while continuing to block non-contacts.
                ContactMatch match = lookupContact(number);
                block = match.lookupSucceeded && !match.saved;
            }
        }

        CallResponse.Builder response = new CallResponse.Builder();
        if (block) {
            response.setDisallowCall(true)
                    .setRejectCall(false)
                    .setSilenceCall(false)
                    .setSkipCallLog(false)
                    .setSkipNotification(true);
        } else {
            response.setDisallowCall(false)
                    .setRejectCall(false)
                    .setSilenceCall(false)
                    .setSkipCallLog(false)
                    .setSkipNotification(false);
        }
        respondToCall(details, response.build());

        if (block && Prefs.notifyScreenedCalls(this)) {
            NotificationHelper.notifyScreenedCall(this, number);
        }
        NotificationHelper.refresh(this);
    }

    private ContactMatch lookupContact(String number) {
        if (number == null || number.trim().isEmpty()) return new ContactMatch(true, false, false);
        if (checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            // Without Contacts permission, calls delivered here are normally non-contacts.
            return new ContactMatch(true, false, false);
        }

        Uri lookup = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number));
        try (Cursor cursor = getContentResolver().query(
                lookup,
                new String[]{ContactsContract.PhoneLookup._ID, ContactsContract.PhoneLookup.STARRED},
                null, null, null)) {
            if (cursor == null) return new ContactMatch(false, false, false);
            if (!cursor.moveToFirst()) return new ContactMatch(true, false, false);
            int starredIndex = cursor.getColumnIndex(ContactsContract.PhoneLookup.STARRED);
            boolean starred = starredIndex >= 0 && cursor.getInt(starredIndex) != 0;
            return new ContactMatch(true, true, starred);
        } catch (RuntimeException ignored) {
            return new ContactMatch(false, false, false);
        }
    }

    private static final class ContactMatch {
        final boolean lookupSucceeded;
        final boolean saved;
        final boolean starred;

        ContactMatch(boolean lookupSucceeded, boolean saved, boolean starred) {
            this.lookupSucceeded = lookupSucceeded;
            this.saved = saved;
            this.starred = starred;
        }
    }

}
