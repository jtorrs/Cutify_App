package com.example.online_alot;

import android.content.Context;
import android.os.Bundle;

import com.google.firebase.analytics.FirebaseAnalytics;
import com.mixpanel.android.mpmetrics.MixpanelAPI;

import org.json.JSONObject;

/**
 * Product analytics: {@link FirebaseAnalytics} (Google Analytics 4 via Firebase) and optional Mixpanel.
 * Tracks booking trends, hour-of-day (peak-hour) signals, and simple new vs returning customer retention.
 * No raw PII (names/emails) is sent — only coarse flags and categories.
 */
public final class CutifyAnalytics {

    private static FirebaseAnalytics firebaseAnalytics;
    private static MixpanelAPI mixpanel;

    private CutifyAnalytics() {}

    /**
     * Call from {@link android.app.Application#onCreate()}.
     *
     * @param mixpanelProjectToken optional; if null/blank, only Firebase receives events
     */
    public static void init(android.app.Application app, String mixpanelProjectToken) {
        firebaseAnalytics = FirebaseAnalytics.getInstance(app);
        if (mixpanelProjectToken != null && !mixpanelProjectToken.trim().isEmpty()) {
            mixpanel = MixpanelAPI.getInstance(app, mixpanelProjectToken.trim(), false);
        }
    }

    /** In-shop app booking or home service (customer flow). */
    public static void logBookingCreated(Context context, String channel, int hourLocal,
                                         boolean hasCustomerProfile, boolean isReturningCustomer) {
        if (firebaseAnalytics == null && mixpanel == null) {
            return;
        }
        Bundle b = new Bundle();
        b.putString("booking_channel", channel);
        b.putLong("hour_local", hourLocal);
        b.putLong("has_customer_profile", hasCustomerProfile ? 1L : 0L);
        b.putLong("customer_is_returning", isReturningCustomer ? 1L : 0L);
        logFirebase("booking_created", b);
        JSONObject j = new JSONObject();
        try {
            j.put("booking_channel", channel);
            j.put("hour_local", hourLocal);
            j.put("has_customer_profile", hasCustomerProfile);
            j.put("customer_is_returning", isReturningCustomer);
        } catch (org.json.JSONException ignored) {}
        trackMixpanel("booking_created", j);
    }

    public static void logBookingCompleted(Context context, boolean homeService, int waitMinutesApprox) {
        if (firebaseAnalytics == null && mixpanel == null) {
            return;
        }
        int clamped = Math.max(0, Math.min(waitMinutesApprox, 24 * 60));
        Bundle b = new Bundle();
        b.putLong("is_home_service", homeService ? 1L : 0L);
        b.putLong("completion_wait_minutes", clamped);
        logFirebase("booking_completed", b);
        JSONObject j = new JSONObject();
        try {
            j.put("is_home_service", homeService);
            j.put("completion_wait_minutes", clamped);
        } catch (org.json.JSONException ignored) {}
        trackMixpanel("booking_completed", j);
    }

    public static void logBookingCancelled(Context context, boolean homeService) {
        if (firebaseAnalytics == null && mixpanel == null) {
            return;
        }
        Bundle b = new Bundle();
        b.putLong("is_home_service", homeService ? 1L : 0L);
        logFirebase("booking_cancelled", b);
        JSONObject j = new JSONObject();
        try {
            j.put("is_home_service", homeService);
        } catch (org.json.JSONException ignored) {}
        trackMixpanel("booking_cancelled", j);
    }

    public static void logSuperAdminPdfExport(Context context, String periodKey) {
        if (firebaseAnalytics == null && mixpanel == null) {
            return;
        }
        Bundle b = new Bundle();
        b.putString("report_period", periodKey);
        logFirebase("super_admin_pdf_export", b);
        JSONObject j = new JSONObject();
        try {
            j.put("report_period", periodKey);
        } catch (org.json.JSONException ignored) {}
        trackMixpanel("super_admin_pdf_export", j);
    }

    public static void logSuperAdminRecordsDeleted(Context context, String periodKey,
                                                   int appointmentsRemoved, int reportsRemoved) {
        if (firebaseAnalytics == null && mixpanel == null) {
            return;
        }
        Bundle b = new Bundle();
        b.putString("delete_period", periodKey);
        b.putLong("appointments_removed", appointmentsRemoved);
        b.putLong("reports_removed", reportsRemoved);
        logFirebase("super_admin_records_deleted", b);
        JSONObject j = new JSONObject();
        try {
            j.put("delete_period", periodKey);
            j.put("appointments_removed", appointmentsRemoved);
            j.put("reports_removed", reportsRemoved);
        } catch (org.json.JSONException ignored) {}
        trackMixpanel("super_admin_records_deleted", j);
    }

    private static void logFirebase(String name, Bundle params) {
        if (firebaseAnalytics != null) {
            firebaseAnalytics.logEvent(name, params);
        }
    }

    private static void trackMixpanel(String name, JSONObject props) {
        if (mixpanel != null) {
            mixpanel.track(name, props);
        }
    }
}
