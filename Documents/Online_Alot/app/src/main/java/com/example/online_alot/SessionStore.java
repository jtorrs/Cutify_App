package com.example.online_alot;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import java.util.Locale;

/**
 * Keeps the signed-in staff session on device until explicit logout.
 */
public final class SessionStore {
    private static final String PREFS = "staff_session";
    private static final String KEY_ROLE = "role";
    private static final String KEY_EMAIL = "email";
    private static final String KEY_SAVED_AT = "saved_at";

    // Auto-expire stale sessions after 14 days.
    private static final long SESSION_TTL_MS = 14L * 24L * 60L * 60L * 1000L;

    private static final String ROLE_SUPER_ADMIN = "super_admin";
    private static final String ROLE_ADMIN = "admin";

    private SessionStore() {}

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static void saveSuperAdminSession(Context context) {
        prefs(context).edit()
                .putString(KEY_ROLE, ROLE_SUPER_ADMIN)
                .putLong(KEY_SAVED_AT, System.currentTimeMillis())
                .remove(KEY_EMAIL)
                .apply();
    }

    public static void saveAdminSession(Context context, String email) {
        String normalized = SuperAdminFirebase.roleDocumentId(email);
        prefs(context).edit()
                .putString(KEY_ROLE, ROLE_ADMIN)
                .putString(KEY_EMAIL, normalized)
                .putLong(KEY_SAVED_AT, System.currentTimeMillis())
                .apply();
    }

    public static Session read(Context context) {
        SharedPreferences p = prefs(context);
        long savedAt = p.getLong(KEY_SAVED_AT, 0L);
        if (savedAt <= 0L || System.currentTimeMillis() - savedAt > SESSION_TTL_MS) {
            clear(context);
            return Session.none();
        }
        String role = p.getString(KEY_ROLE, "");
        String email = p.getString(KEY_EMAIL, "");
        if (TextUtils.isEmpty(role)) {
            return Session.none();
        }
        if (ROLE_SUPER_ADMIN.equals(role)) {
            return Session.superAdmin();
        }
        if (ROLE_ADMIN.equals(role)) {
            if (TextUtils.isEmpty(email)) {
                return Session.none();
            }
            return Session.admin(email.toLowerCase(Locale.ROOT));
        }
        return Session.none();
    }

    public static void clear(Context context) {
        prefs(context).edit().clear().apply();
    }

    public static final class Session {
        public enum Type { NONE, SUPER_ADMIN, ADMIN }

        public final Type type;
        public final String email;

        private Session(Type type, String email) {
            this.type = type;
            this.email = email;
        }

        private static Session none() {
            return new Session(Type.NONE, "");
        }

        private static Session superAdmin() {
            return new Session(Type.SUPER_ADMIN, "");
        }

        private static Session admin(String email) {
            return new Session(Type.ADMIN, email);
        }
    }
}
