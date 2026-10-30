# Implementation Plan: Automatic Emergency SOS SMS Dispatch

Analyze and enhance the automatic SOS SMS workflow in SheSecure so that upon countdown completion, an emergency SMS containing the live GPS coordinates (`https://www.google.com/maps?q=LATITUDE,LONGITUDE`) is automatically dispatched directly via Android `SmsManager` to all saved emergency contacts without manual user interaction, alongside upfront permission onboarding and a safe Test SOS mode.

---

## 1. Problem Analysis & Root Cause Review
1. **SMS Sending Automation**: 
   - Previous flow required user interaction or encountered permission blocks at the moment of emergency if `SEND_SMS` wasn't granted in advance.
   - For long messages (>160 characters) with URLs, `sendMultipartTextMessage` requires aligned `ArrayList<PendingIntent>` for sent and delivery reports across all parts.
   - On Android 12+ (API 31+), `SmsManager.getDefault()` is deprecated in favor of `context.getSystemService(SmsManager.class)` or subscription-aware instances (`SmsManager.getSmsManagerForSubscriptionId()`).
2. **Permission Timing**:
   - In emergencies, asking for runtime permissions mid-crisis creates life-safety risks. Permissions (`SEND_SMS`, `ACCESS_FINE_LOCATION`) must be requested proactively during initial onboarding / Dashboard entry with prominent status indicators.
3. **Emergency Contact vs Own Profile Separation**:
   - The user's phone number belongs in `public.profiles` / session cache, while emergency alerts must be routed strictly to recipient numbers in `LocalContactCache` / `public.emergency_contacts`.
4. **Testing Safety**:
   - Users need a "Test SOS Mode" to verify the countdown, haptics, location acquisition, and receiver callbacks locally without exhausting cellular credits or alarming contacts.

---

## 2. Proposed Architecture & Component Updates

### A. Upfront Permission Onboarding & Verification (`DashboardActivity.java` & `PermissionGuidanceActivity.java`)
- Check `Manifest.permission.SEND_SMS` alongside `ACCESS_FINE_LOCATION` when `DashboardActivity` is opened.
- If permissions are missing, show an immediate banner/card offering a 1-tap grant dialog before any emergency occurs.
- Update safety status badge ("Protected" only when contacts >= 1, SMS permission granted, and Location enabled).

### B. High-Reliability Location Acquisition (`LocationHelper` / `AlertStatusActivity.java`)
- Retrieve the most recent and accurate fix from `LocationManager` (`GPS_PROVIDER`, `NETWORK_PROVIDER`).
- Check GPS provider availability; format URL strictly as:
  `https://www.google.com/maps?q=LATITUDE,LONGITUDE`
- If location fix is unavailable or timeout expires, immediately proceed with last-known coordinates or clear emergency text without blocking SMS transmission.

### C. Automatic SMS Dispatch Engine (`AlertStatusActivity.java`)
- Use `context.getSystemService(SmsManager.class)` for API 31+, falling back to `SmsManager.getDefault()`.
- Detect multi-SIM devices via `SubscriptionManager` if active subscriptions are available.
- Check telephony capability and `SIM_STATE_READY`.
- Handle multi-part SMS messages safely using `divideMessage(message)` with matched `ArrayList<PendingIntent>` for sent and delivered callbacks.
- Register `BroadcastReceiver` dynamically for `ACTION_SMS_SENT` and `ACTION_SMS_DELIVERED` with individual tracking per contact ID.
- Prevent duplicate dispatch using a single-shot execution guard flag (`hasDispatchedAlert`).

### D. Safe Test SOS Mode (`SettingsActivity.java`, `DashboardActivity.java`, `AlertStatusActivity.java`)
- Add a dedicated "Test SOS Mode" toggle in Settings and a Quick Test button on the Dashboard.
- When Test Mode is active:
  - Full countdown, audio/haptic pulse, and real GPS acquisition execute identically to the real flow.
  - At finish, the app logs "Simulated Test SMS" with mock carrier acks and handset delivery receipts in the outcome log, without sending real network SMS.
  - Displays a prominent amber banner: `TEST SOS SIMULATION (No real SMS dispatched)`.

### E. Manual Fallback Composer
- Retain the fallback button (`btn_sms_fallback`) linked to `ACTION_SENDTO` with `smsto:NUMBERS` populated if direct SMS is unavailable due to lack of SIM, airplane mode, or denied SMS permission.

---

## 3. Files to Modify

| File | Changes |
| :--- | :--- |
| `app/src/main/java/com/example/shesecure/dashboard/DashboardActivity.java` | Add upfront `SEND_SMS` check & prompt; update safety status badge; add Quick Test SOS button. |
| `app/src/main/java/com/example/shesecure/emergency/AlertStatusActivity.java` | Refine `SmsManager` multi-part dispatch, subscription management, execution guard against duplicate sends, sent/delivery broadcast receiver tracking, and Test Mode simulation branch. |
| `app/src/main/java/com/example/shesecure/emergency/SosCountdownActivity.java` | Forward `EXTRA_IS_TEST_MODE` to `AlertStatusActivity` when initiated from Test SOS. |
| `app/src/main/java/com/example/shesecure/settings/SettingsActivity.java` | Add Test SOS toggle and direct access to permissions. |
| `app/src/main/java/com/example/shesecure/data/local/LocalContactCache.java` | Add getter/setter for `isTestModeEnabled()`. |
| `app/src/main/res/layout/activity_dashboard.xml` | Add Test Mode banner/button and SMS permission warning card if ungranted. |
| `app/src/main/res/layout/activity_alert_status.xml` | Add Test Mode indicator banner and enhanced real-time status chip. |

---

## 4. Verification Plan

1. **Compilation Check**:
   - Run `compile_applet` to confirm clean Gradle build, zero resource missing IDs, and no syntax errors.
2. **Permission Flow**:
   - Verify that launching `DashboardActivity` prompts for `SEND_SMS` if ungranted, and displays the exact permission status.
3. **Emergency Dispatch Flow**:
   - Initiate SOS countdown:
     - Verify countdown UI ticks 5s with progress bar and haptic pulse.
     - Verify cancel button halts the timer and terminates the activity immediately.
     - Verify countdown completion passes real device GPS coordinates (`https://www.google.com/maps?q=LATITUDE,LONGITUDE`) into the prepared message.
     - Verify `SmsManager` transmits to all contacts in `LocalContactCache` without opening external apps.
     - Verify sent and delivery `BroadcastReceivers` update the UI log per contact.
4. **Test SOS Mode**:
   - Enable Test SOS Mode and trigger countdown:
     - Verify simulation banner appears.
     - Verify no carrier SMS is dispatched while test logs confirm end-to-end trigger.
5. **Fallback Flow**:
   - Verify "Open SMS App" button functions smoothly with pre-filled numbers and text if SMS permissions are denied.
