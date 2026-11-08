# CallGuard

Version 1.4.4

CallGuard is a small, offline Android call-screening utility. It can either let saved contacts ring while sending other callers straight to voicemail (no ring), or send all incoming calls straight to voicemail.

Originally built and tested on a **Samsung Galaxy S10 running Android 12**. CallGuard is designed for **Android 10 (API 29) and newer**, currently compiles and targets **Android 16 (API 36)**, and should work on Android versions where the standard Android Call Screening role and `CallScreeningService` are available. Device diallers, manufacturer background policies, and carrier voicemail behaviour can vary, so testing on additional devices is recommended.

<img width="240" alt="CallGuard" src="https://github.com/user-attachments/assets/d124b743-1910-40ae-afcf-0260e427a625" />

## What it does

- Uses Android's official `CallScreeningService` and Call Screening role.
- **Caller handling** is independent of timing:
  - **Allow contacts** — saved contacts ring while Guard is active; other callers go to voicemail.
  - **Favorites only** — only contacts marked Favorite/Starred ring while Guard is active; everyone else goes to voicemail. This option requires Contacts permission.
  - **Send all calls to voicemail** — saved contacts and non-contacts go to voicemail while Guard is active. This option requires Contacts permission.
- Four **Guard modes**:
  - **Off** — no call filtering and no persistent status notification.
  - **Always on** — filtering stays active continuously.
  - **Schedule** — filtering follows the configured schedule rules.
  - **For a set time** — filtering stays active continuously for a chosen duration, then returns to **Off**, **Always on**, or **Schedule**.
- New installations default to **Off** and **Allow contacts**, so CallGuard never begins screening until the user deliberately selects an active Guard mode.
- Timed mode defaults its **Return to** choice to the mode that was selected immediately before the timer, but the user can change it.
- Schedule rules are only shown while **Schedule** mode is selected.
- Supports multiple schedule rules, overnight periods, and all-day rules.
- Default schedule rules are Mon–Fri 17:00 → 08:00 plus Sat–Sun all day.
- Optional **Call sent to voicemail** notifications, enabled by default.
- Persistent low-priority status notification while an active/scheduled/timed filtering mode is selected; **Off** shows no persistent notification.
- Normal status notifications do not create an app-drawer badge; screened-call notifications may create a badge.
- Direct Boot / reboot restoration without a continuously running foreground service.
- Automatic Light/Dark theme following the Android system theme.
- No Internet permission, ads, analytics, trackers, storage permission, call-log permission, microphone, or location permission. Contacts permission is optional and requested only if **Favorites only** or **Send all calls to voicemail** is selected.

## Required Call Screening role

CallGuard must hold Android's **Call Screening** role. Without it, Android will not deliver incoming calls to CallGuard for screening.

On first run, use **Enable Android call screening role** and approve the Android system prompt. Once granted, the warning and setup button are hidden. If the role is later removed, they appear again.

## Important Android limitation

Android does **not** deliver calls whose caller-ID presentation is restricted/private, unknown, unavailable, or payphone to third-party `CallScreeningService` apps. Those calls cannot be screened by CallGuard. If required, use the Phone app's built-in private/unknown-number controls alongside CallGuard.

## Caller handling

### Allow contacts

Saved contacts are allowed to ring while Guard is active. Calls from numbers not saved in Contacts are sent to voicemail. This is the default caller-handling policy.

### Favorites only

Only saved contacts marked as Favorite/Starred are allowed to ring while Guard is active. Other saved contacts and non-contact callers are sent to voicemail. This uses Android's Contacts `STARRED` flag and requires `READ_CONTACTS` so saved-contact calls are delivered to CallGuard for screening.

### Send all calls to voicemail

All incoming calls delivered to CallGuard are sent to voicemail while Guard is active, including saved contacts. Android requires CallGuard to hold `READ_CONTACTS` so the Telecom framework will also deliver saved-contact calls to the screening service. CallGuard requests Contacts permission only when **Favorites only** or this option is selected. If permission is denied or later revoked, CallGuard falls back to **Allow contacts**.

## Guard modes

### Off

Guard is disabled. Calls behave normally and CallGuard shows no persistent status notification. This is the default mode after a new installation.

### Always on

Guard remains active continuously until another mode is selected.

### Schedule

Guard follows the configured schedule rules. Schedule controls remain hidden when another mode is selected.

For overnight schedules, selected days describe the active portions of those calendar days. For example, Mon–Fri `17:00 → 08:00` means filtering is active `00:00–08:00` and `17:00–24:00` on each selected weekday.

If a rule's Start and End times are identical, that rule is inactive. Use **All day** for a full-day rule.

### For a set time

Selecting **For a set time** opens a duration picker using hour/minute wheels. Guard remains continuously active for that duration.

The same dialog includes **When the timer ends, return to:**

- **Off**
- **Always on**
- **Schedule**

The initial selection defaults to whichever mode was selected before the timer. While the timer is running, its return mode can still be changed. The app and status notification show the timer end time and return mode.

When the timer expires, CallGuard automatically restores the selected return mode.

## Notifications

The **Notify about calls sent to voicemail** toggle is enabled by default. Turning it off does not change screening behaviour; it only suppresses the per-call notification and its launcher badge.

Actual call screening evaluates the current caller-handling policy, Guard mode, and schedule when each call arrives. Android may delay low-priority background refreshes of the persistent status notification, so its displayed state can occasionally lag behind the real filtering state. Waking or unlocking the device, opening CallGuard, receiving a screened call, or another refresh event normally brings the displayed status up to date.

## Reboot behaviour

CallGuard uses Android Direct Boot support and device-protected preferences so the selected Guard configuration can recover automatically after a reboot without requiring the app to be manually opened. It does not use a continuously running foreground service.

Some Android versions/manufacturers may delay boot/background work if the user manually places the app into a restricted or deep-sleeping state.

## Building

The project currently uses:

- `compileSdk 36`
- `targetSdk 36`
- `minSdk 29`

Open it in a current Android Studio installation with Android SDK 36 installed and use **Build → Generate Bundle(s) / APK(s) → Generate APK(s)**.

The debug APK is normally written to:

`app/build/outputs/apk/debug/app-debug.apk`

It can also be built from the command line with Gradle when a Gradle wrapper and Android SDK are available.

## License

CallGuard is licensed under the **GNU General Public License version 3 or later (GPL-3.0-or-later)**. See `LICENSE` for the full licence text.

Distributed modified versions and derivative works covered by the GPL must remain available under compatible GPL terms, including corresponding source code as required by the licence.

## Version history

### v1.4.4

- Added **Favorites only** as a third Caller handling policy.
- Favorites-only mode allows contacts marked Favorite/Starred to ring and sends other callers to voicemail while Guard is active.
- Reuses the optional Contacts permission already required by the all-calls policy.
- Permission denial/revocation safely falls back to **Allow contacts**.
- Updated Guard status and persistent notifications to identify Favorites-only handling.

### v1.4.3

- Added a separate **Caller handling** selector with **Allow contacts** and **Send all calls to voicemail**.
- Added optional `READ_CONTACTS` permission, requested only when **Send all calls to voicemail** is selected.
- When Contacts permission has previously been granted, **Allow contacts** explicitly checks the incoming number against Contacts so saved contacts continue to ring normally.
- Renamed the UI's **Filtering mode** terminology to **Guard mode**.
- Guard status and persistent notifications now reflect whether contacts are allowed or all callers are being sent to voicemail.
- Fresh installs still default to **Off** with no persistent status notification.

### v1.4.2

- Removed the separate master **Enable filtering** switch and made the filtering mode the single source of truth.
- Added **Off** as the first filtering mode.
- Fresh installations now default to **Off**.
- **Off** performs no screening and shows no persistent CallGuard status notification.
- Timed mode can now return to **Off**, **Always on**, or **Schedule**.
- The timer's **Return to** choice still defaults to whichever mode was selected immediately before the timer.

### v1.4.1

- Removed the v1.4.0 all-calls override and removed its `READ_CONTACTS` permission.
- Reworked the filtering modes as **Always on**, **Schedule**, and **For a set time**.
- New installations now default to **Always on**.
- Schedule rules are hidden unless **Schedule** is selected.
- Added a timer duration picker using hour/minute wheels.
- Timed mode defaults its return mode to whichever normal mode was active before the timer.
- Added an explicit **Return to: Always on / Schedule** choice when starting or editing the timer.
- Timed mode automatically restores the selected return mode when it expires.
- App and persistent-notification status now describe timed mode, its end time, and return behaviour.

### v1.4.0

- Renamed the app from Call Filter to **CallGuard** and changed the package to `org.callguard.app`.
- Experimental all-calls override added; superseded and removed in v1.4.1.

### v1.3.2

- Added compatibility wording documenting the original Galaxy S10 / Android 12 test platform and expected Android 10–16 support.
- Generalised device/manufacturer-specific guidance where applicable.

### v1.3.1

- Moved Schedule behaviour to the bottom of Details.
- Clarified that filtering follows the real schedule even when the status notification is temporarily stale.

### v1.3.0

- Added a collapsible Details section.
- Added the **Notify about calls sent to voicemail** toggle, enabled by default.

### v1.2.9

- Added best-effort status-notification refreshes when the screen turns on and when the device is unlocked.

### v1.2.8

- Fixed Direct Boot preference migration compilation.
- Continued API 36 compile/target support.

### v1.2.6

- Added Direct Boot support and automatic filtering-state restoration after reboot.

### v1.2.5

- Disabled launcher badging for normal status notifications while retaining it for screened-call notifications.

### v1.2.4

- Improved status labels and moved status/role information to the top of the UI.

### v1.2.0

- Added GPL-3.0-or-later licensing and prepared the project for public open-source distribution.

### v1.0.0

- Initial call-screening implementation with scheduling, persistent status notification, boot restoration, and offline-only design.

## Notes

- Built with the assistance of LLMs.
- CallGuard does not alter carrier call-forwarding settings; it relies on Android Telecom's screening path and the device/account's existing voicemail service.
