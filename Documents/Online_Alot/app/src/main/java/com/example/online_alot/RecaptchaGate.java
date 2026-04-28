package com.example.online_alot;

import android.content.Context;
import android.content.SharedPreferences;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

final class RecaptchaGate {

    private static final String PREFS = "recaptcha_gate_prefs";
    private static final String KEY_USER_LAST_DAY = "user_last_day";
    private static final String KEY_STAFF_LAST_PASSED_EMAIL = "staff_last_passed_email";

    private RecaptchaGate() {}

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static String todayKey() {
        return new SimpleDateFormat("yyyyMMdd", Locale.US).format(new Date());
    }

    static boolean isUserRecaptchaRequiredToday(Context context) {
        return !todayKey().equals(prefs(context).getString(KEY_USER_LAST_DAY, ""));
    }

    static void markUserRecaptchaPassedToday(Context context) {
        prefs(context).edit().putString(KEY_USER_LAST_DAY, todayKey()).apply();
    }

    private static String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    static boolean isStaffRecaptchaRequired(Context context, String email) {
        String normalized = normalizeEmail(email);
        if (normalized.isEmpty()) {
            return true;
        }
        String last = prefs(context).getString(KEY_STAFF_LAST_PASSED_EMAIL, "");
        return !normalized.equals(last);
    }

    static void markStaffRecaptchaPassed(Context context, String email) {
        String normalized = normalizeEmail(email);
        if (normalized.isEmpty()) {
            return;
        }
        prefs(context).edit().putString(KEY_STAFF_LAST_PASSED_EMAIL, normalized).apply();
    }

    static void clearStaffRecaptcha(Context context) {
        prefs(context).edit().remove(KEY_STAFF_LAST_PASSED_EMAIL).apply();
    }
}
