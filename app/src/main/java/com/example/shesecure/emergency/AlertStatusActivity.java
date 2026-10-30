package com.example.shesecure.emergency;

import android.Manifest;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.telephony.SmsManager;
import android.telephony.TelephonyManager;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.R;
import com.example.shesecure.data.local.LocalContactCache;
import com.example.shesecure.models.EmergencyContact;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class AlertStatusActivity extends AppCompatActivity {

    private static final String ACTION_SMS_SENT = "com.example.shesecure.SMS_SENT";
    private static final String ACTION_SMS_DELIVERED = "com.example.shesecure.SMS_DELIVERED";
    private static final String EXTRA_ATTEMPT_ID = "EXTRA_ATTEMPT_ID";
    private static final String EXTRA_CONTACT_ID = "EXTRA_CONTACT_ID";
    private static final String EXTRA_CONTACT_NAME = "EXTRA_CONTACT_NAME";
    private static final String EXTRA_CONTACT_PHONE = "EXTRA_CONTACT_PHONE";
    private static final String EXTRA_PART_INDEX = "EXTRA_PART_INDEX";
    private static final String EXTRA_TOTAL_PARTS = "EXTRA_TOTAL_PARTS";

    private static final AtomicInteger sRequestCodeSeq = new AtomicInteger(1000);
    private static final long DISPATCH_TIMEOUT_MS = 25000L;

    private TextView tvAlertTitle;
    private TextView tvTimestamp;
    private TextView tvLocationDetails;
    private TextView tvMessagePreview;
    private TextView tvContactsOutcomeLog;
    private MaterialCardView cardTestModeIndicator;
    private MaterialButton btnSmsFallback;
    private MaterialButton btnReturnDashboard;

    private LocalContactCache cache;
    private String emergencyMessage = "";
    private String contactNumbersString = "";
    private final Map<String, String> contactStatusMap = new LinkedHashMap<>();
    private final Map<String, RecipientDispatchTracker> dispatchTrackers = new HashMap<>();

    private BroadcastReceiver sentReceiver;
    private BroadcastReceiver deliveryReceiver;
    private boolean receiversRegistered = false;

    // Normal SOS is unconditionally REAL; test mode only runs if explicitly launched from a dedicated test feature
    private long attemptId = 0L;
    private boolean isTestMode = false;
    private boolean hasDispatchedAlert = false;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable timeoutRunnable;

    private static class RecipientDispatchTracker {
        final String contactId;
        final String contactName;
        final String phoneNumber;
        final int totalParts;
        int sentPartsSuccess = 0;
        int deliveryPartsSuccess = 0;
        boolean isFailed = false;
        String failureReason = null;
        boolean isSentComplete = false;
        boolean isDeliveredComplete = false;

        RecipientDispatchTracker(String contactId, String contactName, String phoneNumber, int totalParts) {
            this.contactId = contactId;
            this.contactName = contactName;
            this.phoneNumber = phoneNumber;
            this.totalParts = totalParts;
        }
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_alert_status);

        cache = LocalContactCache.getInstance(this);

        // Explicit check: only true if passed directly from an isolated test intent
        isTestMode = getIntent().getBooleanExtra(SosCountdownActivity.EXTRA_IS_TEST_MODE, false);
        attemptId = System.currentTimeMillis();

        tvAlertTitle = findViewById(R.id.tv_alert_title);
        tvTimestamp = findViewById(R.id.tv_alert_timestamp);
        tvLocationDetails = findViewById(R.id.tv_location_details);
        tvMessagePreview = findViewById(R.id.tv_message_preview);
        tvContactsOutcomeLog = findViewById(R.id.tv_contacts_outcome_log);
        cardTestModeIndicator = findViewById(R.id.card_test_mode_indicator);
        btnSmsFallback = findViewById(R.id.btn_sms_fallback);
        btnReturnDashboard = findViewById(R.id.btn_return_dashboard);

        btnReturnDashboard.setOnClickListener(v -> finish());
        btnSmsFallback.setOnClickListener(v -> launchSystemSmsComposer());

        if (isTestMode) {
            if (cardTestModeIndicator != null) {
                cardTestModeIndicator.setVisibility(View.VISIBLE);
            }
            tvAlertTitle.setText("Test SOS Simulation Completed");
        } else {
            if (cardTestModeIndicator != null) {
                cardTestModeIndicator.setVisibility(View.GONE);
            }
            tvAlertTitle.setText(R.string.alert_status_completed);
        }

        registerSmsOutcomeReceivers();
        processAlert();
    }

    private void registerSmsOutcomeReceivers() {
        if (isTestMode || receiversRegistered) return;

        sentReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent == null) return;
                long receivedAttemptId = intent.getLongExtra(EXTRA_ATTEMPT_ID, -1L);
                if (receivedAttemptId != attemptId) {
                    // Stale or previous attempt broadcast; ignore to prevent cross-attempt interference
                    return;
                }

                String contactName = intent.getStringExtra(EXTRA_CONTACT_NAME);
                String contactPhone = intent.getStringExtra(EXTRA_CONTACT_PHONE);
                int partIndex = intent.getIntExtra(EXTRA_PART_INDEX, 0);
                int totalParts = intent.getIntExtra(EXTRA_TOTAL_PARTS, 1);

                if (contactPhone == null) return;
                RecipientDispatchTracker tracker = dispatchTrackers.get(contactPhone);
                if (tracker == null) return;

                int resultCode = getResultCode();
                if (resultCode == Activity.RESULT_OK) {
                    tracker.sentPartsSuccess++;
                    if (tracker.sentPartsSuccess >= tracker.totalParts) {
                        tracker.isSentComplete = true;
                        if (tracker.isDeliveredComplete) {
                            contactStatusMap.put(contactPhone, "• " + contactName + " (" + contactPhone + "): Delivered to recipient handset ✓");
                        } else {
                            contactStatusMap.put(contactPhone, "• " + contactName + " (" + contactPhone + "): Sent to carrier network ✓ (Awaiting handset delivery confirmation…)");
                        }
                    } else {
                        contactStatusMap.put(contactPhone, "• " + contactName + " (" + contactPhone + "): Sending… (" + tracker.sentPartsSuccess + " of " + tracker.totalParts + " parts accepted by carrier)");
                    }
                } else {
                    tracker.isFailed = true;
                    String statusMsg;
                    switch (resultCode) {
                        case SmsManager.RESULT_ERROR_NO_SERVICE:
                            statusMsg = "Failed: No cellular service or network coverage";
                            break;
                        case SmsManager.RESULT_ERROR_RADIO_OFF:
                            statusMsg = "Failed: Airplane mode active or cellular radio disabled";
                            break;
                        case SmsManager.RESULT_ERROR_NULL_PDU:
                            statusMsg = "Failed: Invalid PDU format";
                            break;
                        case 5: // SmsManager.RESULT_ERROR_LIMIT_EXCEEDED
                            statusMsg = "Failed: Device SMS sending rate limit exceeded";
                            break;
                        case SmsManager.RESULT_ERROR_GENERIC_FAILURE:
                        default:
                            statusMsg = "Failed: Carrier network error (check SIM, cellular signal, or balance)";
                            break;
                    }
                    if (tracker.totalParts > 1) {
                        statusMsg += " [Part " + (partIndex + 1) + " of " + tracker.totalParts + "]";
                    }
                    tracker.failureReason = statusMsg;
                    contactStatusMap.put(contactPhone, "• " + contactName + " (" + contactPhone + "): " + statusMsg + " — Tap fallback below");
                    btnSmsFallback.setVisibility(View.VISIBLE);
                }

                checkAllDispatchedState();
                renderOutcomeLog();
            }
        };

        deliveryReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent == null) return;
                long receivedAttemptId = intent.getLongExtra(EXTRA_ATTEMPT_ID, -1L);
                if (receivedAttemptId != attemptId) {
                    return;
                }

                String contactName = intent.getStringExtra(EXTRA_CONTACT_NAME);
                String contactPhone = intent.getStringExtra(EXTRA_CONTACT_PHONE);

                if (contactPhone == null) return;
                RecipientDispatchTracker tracker = dispatchTrackers.get(contactPhone);
                if (tracker == null) return;

                if (getResultCode() == Activity.RESULT_OK) {
                    tracker.deliveryPartsSuccess++;
                    if (tracker.deliveryPartsSuccess >= tracker.totalParts) {
                        tracker.isDeliveredComplete = true;
                        contactStatusMap.put(contactPhone, "• " + contactName + " (" + contactPhone + "): Delivered to recipient handset ✓ (Confirmed by carrier delivery report)");
                        renderOutcomeLog();
                    }
                } else {
                    if (tracker.isSentComplete && !tracker.isDeliveredComplete) {
                        contactStatusMap.put(contactPhone, "• " + contactName + " (" + contactPhone + "): Sent to carrier network ✓ (Delivery report unconfirmed/unsupported by recipient network)");
                        renderOutcomeLog();
                    }
                }
            }
        };

        ContextCompat.registerReceiver(this, sentReceiver, new IntentFilter(ACTION_SMS_SENT), ContextCompat.RECEIVER_EXPORTED);
        ContextCompat.registerReceiver(this, deliveryReceiver, new IntentFilter(ACTION_SMS_DELIVERED), ContextCompat.RECEIVER_EXPORTED);
        receiversRegistered = true;
    }

    private void checkAllDispatchedState() {
        boolean allFinished = true;
        boolean hasFailure = false;
        for (RecipientDispatchTracker tracker : dispatchTrackers.values()) {
            if (!tracker.isSentComplete && !tracker.isFailed) {
                allFinished = false;
                break;
            }
            if (tracker.isFailed) {
                hasFailure = true;
            }
        }
        if (allFinished) {
            if (timeoutRunnable != null) {
                handler.removeCallbacks(timeoutRunnable);
            }
            if (hasFailure) {
                btnSmsFallback.setVisibility(View.VISIBLE);
                btnSmsFallback.setText(R.string.btn_open_sms_fallback);
            } else {
                btnSmsFallback.setVisibility(View.VISIBLE);
                btnSmsFallback.setText("Re-send via System Messaging App (Optional)");
            }
        }
    }

    private void processAlert() {
        if (hasDispatchedAlert) return;
        hasDispatchedAlert = true;

        String timeStr = new SimpleDateFormat("h:mm:ss a, MMM d", Locale.getDefault()).format(new Date());
        tvTimestamp.setText((isTestMode ? "Test simulation run: " : "Alert triggered: ") + timeStr);

        // 1. Obtain real device location using Android location APIs (never fabricate coordinates)
        Location bestLocation = getBestAvailableLocation();
        String mapsUrl = null;
        if (bestLocation != null && isValidCoordinate(bestLocation.getLatitude(), bestLocation.getLongitude())) {
            mapsUrl = String.format(Locale.US, "https://www.google.com/maps?q=%.6f,%.6f",
                    bestLocation.getLatitude(), bestLocation.getLongitude());
            tvLocationDetails.setText(String.format(Locale.US,
                    "Live GPS Coordinates: %.6f, %.6f (Accuracy: ±%.1fm)\nMap URL: %s",
                    bestLocation.getLatitude(), bestLocation.getLongitude(), bestLocation.getAccuracy(), mapsUrl));
        } else {
            tvLocationDetails.setText("GPS Status: Real location unavailable (GPS signal unavailable, indoors, or location permission revoked). Emergency SMS dispatched without coordinates.");
        }

        // 2. Build message text
        String userName = cache.getUserName();
        String userPhone = cache.getUserPhone();
        String userIdentifier = (userName != null && !userName.trim().isEmpty() && !userName.equals("User"))
                ? userName.trim()
                : "SheSecure User";
        if (userPhone != null && !userPhone.trim().isEmpty()) {
            userIdentifier += " (" + userPhone.trim() + ")";
        }

        if (mapsUrl != null) {
            emergencyMessage = String.format(Locale.US,
                    "EMERGENCY ALERT: %s needs help. My current location: %s",
                    userIdentifier, mapsUrl);
        } else {
            emergencyMessage = String.format(
                    "EMERGENCY ALERT: %s needs help. GPS coordinates unavailable. Please call immediately or send help.",
                    userIdentifier);
        }

        if (isTestMode) {
            emergencyMessage = "[TEST ONLY] " + emergencyMessage;
        }
        tvMessagePreview.setText(emergencyMessage);

        // 3. Dispatch to SAVED EMERGENCY CONTACTS (never to user's own number)
        List<EmergencyContact> allContacts = cache.getContacts();
        List<EmergencyContact> recipients = new ArrayList<>();
        String normalizedUserPhone = normalizePhoneNumber(userPhone);

        for (EmergencyContact c : allContacts) {
            String contactPhone = c.getPhoneNumber();
            String contactNormalized = normalizePhoneNumber(contactPhone);

            // Validate phone number format
            if (!isValidPhoneNumber(contactNormalized)) {
                contactStatusMap.put(contactPhone != null ? contactPhone : c.getId(),
                        "• " + c.getContactName() + ": Invalid phone number (" + contactPhone + ") - Skipped");
                continue;
            }

            // Guard: Never send the SOS to the user's own registered phone number
            if (!normalizedUserPhone.isEmpty() && normalizedUserPhone.equals(contactNormalized)) {
                continue;
            }

            recipients.add(c);
        }

        if (recipients.isEmpty()) {
            tvContactsOutcomeLog.setText("No valid emergency contacts available! Please add trusted contacts in SheSecure.");
            btnSmsFallback.setEnabled(false);
            return;
        }

        StringBuilder numbersBuilder = new StringBuilder();
        for (EmergencyContact contact : recipients) {
            if (numbersBuilder.length() > 0) {
                numbersBuilder.append(";");
            }
            numbersBuilder.append(contact.getPhoneNumber());
        }
        contactNumbersString = numbersBuilder.toString();

        // 4. Test Simulation Mode (Only executed if explicitly requested via test button)
        if (isTestMode) {
            simulateTestModeAlert(recipients);
            return;
        }

        // 5. REAL Direct Automatic SMS Dispatch using SmsManager
        boolean hasSmsPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED;
        boolean hasTelephony = getPackageManager().hasSystemFeature(PackageManager.FEATURE_TELEPHONY);

        TelephonyManager tm = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        boolean simReady = tm != null && tm.getSimState() == TelephonyManager.SIM_STATE_READY;

        // Obtain appropriate SmsManager instance
        SmsManager smsManager;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            smsManager = getSystemService(SmsManager.class);
            if (smsManager == null) {
                smsManager = SmsManager.getDefault();
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            int defaultSubId = SmsManager.getDefaultSmsSubscriptionId();
            if (defaultSubId >= 0) {
                smsManager = SmsManager.getSmsManagerForSubscriptionId(defaultSubId);
            } else {
                smsManager = SmsManager.getDefault();
            }
        } else {
            smsManager = SmsManager.getDefault();
        }

        int piFlags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                ? (PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE)
                : PendingIntent.FLAG_UPDATE_CURRENT;

        // Hide fallback button initially while active automatic dispatch is pending
        btnSmsFallback.setVisibility(View.GONE);

        for (int i = 0; i < recipients.size(); i++) {
            EmergencyContact contact = recipients.get(i);
            String phone = contact.getPhoneNumber();

            if (!hasSmsPermission) {
                contactStatusMap.put(phone, "• " + contact.getContactName() + " (" + phone + "): SEND_SMS permission missing — tap Fallback button below");
                btnSmsFallback.setVisibility(View.VISIBLE);
            } else if (!hasTelephony || !simReady) {
                contactStatusMap.put(phone, "• " + contact.getContactName() + " (" + phone + "): No active cellular SIM card detected — tap Fallback button below");
                btnSmsFallback.setVisibility(View.VISIBLE);
            } else {
                try {
                    // Always divide the message using SmsManager to correctly handle GSM 7-bit (160 char) vs UCS-2 16-bit (70 char) boundaries
                    ArrayList<String> parts = smsManager.divideMessage(emergencyMessage);
                    int partsCount = (parts != null && !parts.isEmpty()) ? parts.size() : 1;

                    RecipientDispatchTracker tracker = new RecipientDispatchTracker(contact.getId(), contact.getContactName(), phone, partsCount);
                    dispatchTrackers.put(phone, tracker);

                    // Show 'Sending' while pending — NEVER show 'Sent' immediately
                    contactStatusMap.put(phone, "• " + contact.getContactName() + " (" + phone + "): Sending emergency SMS (queued to carrier radio, awaiting acknowledgment…)");

                    ArrayList<PendingIntent> sentIntents = new ArrayList<>(partsCount);
                    ArrayList<PendingIntent> deliveryIntents = new ArrayList<>(partsCount);

                    for (int p = 0; p < partsCount; p++) {
                        Intent sentIntent = new Intent(ACTION_SMS_SENT);
                        sentIntent.setPackage(getPackageName());
                        sentIntent.putExtra(EXTRA_ATTEMPT_ID, attemptId);
                        sentIntent.putExtra(EXTRA_CONTACT_ID, contact.getId());
                        sentIntent.putExtra(EXTRA_CONTACT_NAME, contact.getContactName());
                        sentIntent.putExtra(EXTRA_CONTACT_PHONE, phone);
                        sentIntent.putExtra(EXTRA_PART_INDEX, p);
                        sentIntent.putExtra(EXTRA_TOTAL_PARTS, partsCount);

                        int sentReqCode = sRequestCodeSeq.incrementAndGet();
                        PendingIntent sentPI = PendingIntent.getBroadcast(this, sentReqCode, sentIntent, piFlags);
                        sentIntents.add(sentPI);

                        Intent deliveryIntent = new Intent(ACTION_SMS_DELIVERED);
                        deliveryIntent.setPackage(getPackageName());
                        deliveryIntent.putExtra(EXTRA_ATTEMPT_ID, attemptId);
                        deliveryIntent.putExtra(EXTRA_CONTACT_ID, contact.getId());
                        deliveryIntent.putExtra(EXTRA_CONTACT_NAME, contact.getContactName());
                        deliveryIntent.putExtra(EXTRA_CONTACT_PHONE, phone);
                        deliveryIntent.putExtra(EXTRA_PART_INDEX, p);
                        deliveryIntent.putExtra(EXTRA_TOTAL_PARTS, partsCount);

                        int delReqCode = sRequestCodeSeq.incrementAndGet();
                        PendingIntent deliveryPI = PendingIntent.getBroadcast(this, delReqCode, deliveryIntent, piFlags);
                        deliveryIntents.add(deliveryPI);
                    }

                    if (partsCount > 1) {
                        smsManager.sendMultipartTextMessage(phone, null, parts, sentIntents, deliveryIntents);
                    } else {
                        String singlePart = (parts != null && !parts.isEmpty()) ? parts.get(0) : emergencyMessage;
                        smsManager.sendTextMessage(phone, null, singlePart, sentIntents.get(0), deliveryIntents.get(0));
                    }
                } catch (Exception e) {
                    contactStatusMap.put(phone, "• " + contact.getContactName() + " (" + phone + "): Send error: " + e.getMessage());
                    btnSmsFallback.setVisibility(View.VISIBLE);
                }
            }
        }

        renderOutcomeLog();

        if (!hasSmsPermission || !simReady) {
            Toast.makeText(this, "Automatic SMS requires SEND_SMS permission and an active SIM. Tap fallback button below.", Toast.LENGTH_LONG).show();
            btnSmsFallback.setVisibility(View.VISIBLE);
        } else {
            // Schedule dispatch timeout guard
            timeoutRunnable = () -> {
                if (isFinishing() || isDestroyed()) return;
                boolean hasAnyTimeout = false;
                for (RecipientDispatchTracker tracker : dispatchTrackers.values()) {
                    if (!tracker.isSentComplete && !tracker.isFailed) {
                        tracker.isFailed = true;
                        tracker.failureReason = "Timed out waiting for carrier acknowledgment";
                        contactStatusMap.put(tracker.phoneNumber,
                                "• " + tracker.contactName + " (" + tracker.phoneNumber + "): Failed: Dispatch timed out (no carrier acknowledgment after 25s) — Tap fallback below");
                        hasAnyTimeout = true;
                    }
                }
                if (hasAnyTimeout) {
                    btnSmsFallback.setVisibility(View.VISIBLE);
                    renderOutcomeLog();
                }
            };
            handler.postDelayed(timeoutRunnable, DISPATCH_TIMEOUT_MS);
        }
    }

    private void simulateTestModeAlert(List<EmergencyContact> recipients) {
        for (EmergencyContact contact : recipients) {
            String phone = contact.getPhoneNumber();
            contactStatusMap.put(phone, "• " + contact.getContactName() + " (" + phone + "): [TEST] Prepared with GPS coordinates (simulation)");
        }
        renderOutcomeLog();

        handler.postDelayed(() -> {
            if (isFinishing() || isDestroyed()) return;
            for (EmergencyContact contact : recipients) {
                String phone = contact.getPhoneNumber();
                contactStatusMap.put(phone, "• " + contact.getContactName() + " (" + phone + "): [TEST] Sent to cellular network ✓ (simulated)");
            }
            renderOutcomeLog();
        }, 800);

        handler.postDelayed(() -> {
            if (isFinishing() || isDestroyed()) return;
            for (EmergencyContact contact : recipients) {
                String phone = contact.getPhoneNumber();
                contactStatusMap.put(phone, "• " + contact.getContactName() + " (" + phone + "): [TEST] Delivered to contact's handset ✓ (simulated)");
            }
            renderOutcomeLog();
            Toast.makeText(this, "Test SOS completed successfully. No real SMS was sent.", Toast.LENGTH_SHORT).show();
        }, 2000);

        btnSmsFallback.setVisibility(View.GONE);
    }

    private void renderOutcomeLog() {
        StringBuilder sb = new StringBuilder();
        for (String status : contactStatusMap.values()) {
            if (sb.length() > 0) sb.append("\n");
            sb.append(status);
        }
        tvContactsOutcomeLog.setText(sb.toString().trim());
    }

    private Location getBestAvailableLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return null;
        }

        try {
            LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
            if (lm == null) return null;

            Location bestLocation = null;
            List<String> providers = lm.getAllProviders();
            if (providers != null) {
                for (String provider : providers) {
                    try {
                        Location l = lm.getLastKnownLocation(provider);
                        if (l != null) {
                            if (bestLocation == null) {
                                bestLocation = l;
                            } else {
                                long timeDiff = l.getTime() - bestLocation.getTime();
                                if (timeDiff > 60000) {
                                    bestLocation = l; // More recent
                                } else if (timeDiff > -60000 && l.hasAccuracy() && bestLocation.hasAccuracy()) {
                                    if (l.getAccuracy() < bestLocation.getAccuracy()) {
                                        bestLocation = l;
                                    }
                                }
                            }
                        }
                    } catch (SecurityException ignored) {}
                }
            }
            return bestLocation;
        } catch (Exception ignored) {
        }
        return null;
    }

    private boolean isValidCoordinate(double lat, double lon) {
        return lat >= -90.0 && lat <= 90.0 && lon >= -180.0 && lon <= 180.0 && !(lat == 0.0 && lon == 0.0);
    }

    private boolean isValidPhoneNumber(String normalizedPhone) {
        if (normalizedPhone == null || normalizedPhone.trim().isEmpty()) return false;
        String digitsOnly = normalizedPhone.replaceAll("[^0-9]", "");
        return digitsOnly.length() >= 7 && digitsOnly.length() <= 16;
    }

    private String normalizePhoneNumber(String raw) {
        if (raw == null) return "";
        return raw.replaceAll("[^0-9+]", "");
    }

    private void launchSystemSmsComposer() {
        if (contactNumbersString.isEmpty() || emergencyMessage.isEmpty()) {
            Toast.makeText(this, "No recipients or message to compose.", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            Intent intent = new Intent(Intent.ACTION_SENDTO);
            intent.setData(Uri.parse("smsto:" + contactNumbersString));
            intent.putExtra("sms_body", emergencyMessage);
            startActivity(intent);
            Toast.makeText(this, "System SMS app opened. Tap 'Send' in your messaging app to dispatch.", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "Could not open system SMS composer: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (timeoutRunnable != null) {
            handler.removeCallbacks(timeoutRunnable);
        }
        handler.removeCallbacksAndMessages(null);
        if (receiversRegistered) {
            try {
                if (sentReceiver != null) unregisterReceiver(sentReceiver);
            } catch (Exception ignored) {}
            try {
                if (deliveryReceiver != null) unregisterReceiver(deliveryReceiver);
            } catch (Exception ignored) {}
            receiversRegistered = false;
        }
    }
}
