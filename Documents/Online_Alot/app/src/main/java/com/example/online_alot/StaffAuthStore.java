package com.example.online_alot;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;
import android.util.Log;
import android.util.Patterns;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.SetOptions;

import java.security.SecureRandom;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Set;

/**
 * Local "backend" for staff auth: passwords, invite tokens, lockout, 2FA, audit trail.
 * Replace with a real server + database when you add Firebase/API.
 */
public final class StaffAuthStore {

    private static final String TAG = "StaffAuthStore";

    public enum CloudAdminSignIn {
        /** No document for this email in Firestore. */
        NOT_FOUND,
        /** Document exists but password does not match. */
        WRONG_PASSWORD,
        /** Matched; credentials cached on this device. */
        OK,
        /** Firestore read failed (network, rules, project mismatch) — not the same as wrong password. */
        CLOUD_ERROR
    }

    /** After local password matches: Firestore must still have an active invite-based admin doc. */
    public enum InvitedAdminCloudStatus {
        /** {@code admin_accounts/{email}} exists with {@code viaInvite == true}. */
        ACTIVE,
        /** Removed by Super Admin or doc missing / not invite-based. */
        REVOKED,
        /** Could not reach Firestore; do not clear local state. */
        VERIFY_FAILED
    }

    private static final String PREFS = "staff_auth";
    private static final int MAX_ATTEMPTS = 5;
    private static final long LOCKOUT_MS = 15L * 60L * 1000L;
    private static final long INVITE_VALID_MS = 7L * 24L * 60L * 60L * 1000L;
    private static final int AUDIT_MAX = 80;

    private static volatile StaffAuthStore instance;
    private final SharedPreferences p;
    private final SecureRandom random = new SecureRandom();

    private StaffAuthStore(Context appContext) {
        p = appContext.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        seedIfNeeded();
    }

    public static void init(Context context) {
        if (instance == null) {
            synchronized (StaffAuthStore.class) {
                if (instance == null) {
                    instance = new StaffAuthStore(context.getApplicationContext());
                }
            }
        }
    }

    public static StaffAuthStore get() {
        if (instance == null) {
            throw new IllegalStateException("Call StaffAuthStore.init() in Application");
        }
        return instance;
    }

    private static String norm(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private void seedIfNeeded() {
        if (p.getBoolean("seeded_v4_invite_only", false)) {
            return;
        }
        p.edit().putBoolean("seeded_v4_invite_only", true).apply();
    }

    /** Firestore field: only accounts created via invite registration may sign in from cloud. */
    private static final String CLOUD_FIELD_VIA_INVITE = "viaInvite";

    private String pwKey(String email) {
        return "pw_" + norm(email);
    }

    /** Firestore may store millis as long, double, or Timestamp — {@link DocumentSnapshot#getLong} alone often returns null. */
    private static Long readInviteExpiresAtMillis(DocumentSnapshot d) {
        if (d == null || !d.exists()) {
            return null;
        }
        Long l = d.getLong("expiresAt");
        if (l != null && l > 0L) {
            return l;
        }
        Double dbl = d.getDouble("expiresAt");
        if (dbl != null && dbl > 0d) {
            return dbl.longValue();
        }
        Timestamp ts = d.getTimestamp("expiresAt");
        if (ts != null) {
            return ts.toDate().getTime();
        }
        Object raw = d.get("expiresAt");
        if (raw instanceof Number) {
            long v = ((Number) raw).longValue();
            return v > 0 ? v : null;
        }
        return null;
    }

    private static String readAdminPasswordField(DocumentSnapshot doc) {
        if (doc == null || !doc.exists()) {
            return null;
        }
        String s = doc.getString("password");
        if (s != null) {
            return s;
        }
        Object v = doc.get("password");
        return v == null ? null : String.valueOf(v);
    }

    private static boolean passwordsMatchForCloud(String typed, String stored) {
        if (typed == null || stored == null) {
            return false;
        }
        return typed.trim().equals(stored.trim());
    }

    /** Strip trailing junk from invite tokens copied from email (e.g. {@code ABC>)} → {@code ABC}). */
    public static String sanitizeInviteToken(String raw) {
        if (raw == null) {
            return "";
        }
        String t = raw.trim();
        while (t.length() > 0) {
            char c = t.charAt(t.length() - 1);
            if (Character.isLetterOrDigit(c)) {
                break;
            }
            t = t.substring(0, t.length() - 1).trim();
        }
        return t;
    }

    public boolean isLegacyAdminEmail(String email) {
        return AuthManager.isAdmin(email);
    }

    public boolean isInvitedAdminEmail(String email) {
        return getInvitedEmails().contains(norm(email));
    }

    public boolean isAdminEmail(String email) {
        String n = norm(email);
        return isLegacyAdminEmail(n) || isInvitedAdminEmail(n);
    }

    private Set<String> getInvitedEmails() {
        return new HashSet<>(p.getStringSet("invited_admin_emails", Collections.emptySet()));
    }

    public boolean verifyAdminPassword(String email, String password) {
        String stored = p.getString(pwKey(email), null);
        return stored != null && stored.equals(password);
    }

    /**
     * Load invited email for this token from Firestore (works on the invitee's phone).
     */
    public void fetchInvitePreview(String tokenRaw, java.util.function.Consumer<String> onEmail) {
        String t = sanitizeInviteToken(tokenRaw).toUpperCase(Locale.ROOT);
        if (t.isEmpty()) {
            onEmail.accept(null);
            return;
        }
        try {
            AppFirestore.db().collection(AppFirestore.COL_ADMIN_INVITES).document(t).get()
                    .addOnCompleteListener(task -> {
                        if (!task.isSuccessful() || task.getResult() == null || !task.getResult().exists()) {
                            Log.w(TAG, "fetchInvitePreview: missing doc or Firestore error token=" + t,
                                    task.getException());
                            onEmail.accept(null);
                            return;
                        }
                        DocumentSnapshot d = task.getResult();
                        String email = d.getString("email");
                        Long exp = readInviteExpiresAtMillis(d);
                        if (email == null || exp == null || exp <= System.currentTimeMillis()) {
                            if (email != null && exp == null) {
                                Log.w(TAG, "fetchInvitePreview: unreadable expiresAt token=" + t);
                            }
                            onEmail.accept(null);
                            return;
                        }
                        onEmail.accept(norm(email));
                    });
        } catch (Exception e) {
            Log.w(TAG, "fetchInvitePreview", e);
            onEmail.accept(null);
        }
    }

    /**
     * After the invitee sets password + confirm, consume the invite in Firestore and create the admin account.
     */
    public void completeAdminInviteRegistration(String emailRaw, String password, String tokenRaw,
                                                java.util.function.Consumer<Boolean> onDone) {
        String n = norm(emailRaw);
        if (n.isEmpty() || password == null || password.isEmpty()) {
            onDone.accept(false);
            return;
        }
        if (isAdminEmail(n)) {
            onDone.accept(false);
            return;
        }
        String t = sanitizeInviteToken(tokenRaw).toUpperCase(Locale.ROOT);
        if (t.isEmpty()) {
            onDone.accept(false);
            return;
        }
        try {
            AppFirestore.db().collection(AppFirestore.COL_ADMIN_INVITES).document(t).get()
                    .addOnCompleteListener(task -> {
                        if (!task.isSuccessful() || task.getResult() == null || !task.getResult().exists()) {
                            Log.w(TAG, "completeAdminInviteRegistration: invite read failed token=" + t,
                                    task.getException());
                            onDone.accept(false);
                            return;
                        }
                        DocumentSnapshot d = task.getResult();
                        String bound = d.getString("email");
                        Long exp = readInviteExpiresAtMillis(d);
                        if (bound == null || !norm(bound).equals(n)
                                || exp == null || exp <= System.currentTimeMillis()) {
                            onDone.accept(false);
                            return;
                        }
                        AppFirestore.db().collection(AppFirestore.COL_ADMIN_INVITES).document(t).delete()
                                .addOnCompleteListener(del -> {
                                    if (!del.isSuccessful()) {
                                        Log.w(TAG, "completeAdminInviteRegistration delete failed",
                                                del.getException());
                                        onDone.accept(false);
                                        return;
                                    }
                                    consumeInviteLocalOnly(t);
                                    finishAdminRegistration(n, password, t);
                                    onDone.accept(true);
                                });
                    });
        } catch (Exception e) {
            Log.w(TAG, "completeAdminInviteRegistration", e);
            onDone.accept(false);
        }
    }

    private void finishAdminRegistration(String normalizedEmail, String password, String tokenForAudit) {
        SharedPreferences.Editor e = p.edit();
        Set<String> invited = getInvitedEmails();
        Set<String> next = new HashSet<>(invited);
        next.add(normalizedEmail);
        e.putStringSet("invited_admin_emails", next);
        e.putString(pwKey(normalizedEmail), password);
        e.apply();
        logAudit("ADMIN_REGISTERED email=" + normalizedEmail + " token=" + tokenForAudit);
        pushAdminPasswordToCloud(normalizedEmail, password);
    }

    /** Sync barber password to Firestore so the same account works on other phones (demo — not for production). */
    private void pushAdminPasswordToCloud(String normalizedEmail, String password) {
        try {
            Map<String, Object> m = new HashMap<>();
            m.put("password", password);
            m.put("updatedAt", System.currentTimeMillis());
            m.put(CLOUD_FIELD_VIA_INVITE, true);
            AppFirestore.db().collection(AppFirestore.COL_ADMIN_ACCOUNTS).document(normalizedEmail)
                    .set(m)
                    .addOnFailureListener(e -> Log.w(TAG, "pushAdminPasswordToCloud failed", e));
        } catch (Exception e) {
            Log.w(TAG, "pushAdminPasswordToCloud", e);
        }
    }

    private void cacheAdminFromCloud(String normalizedEmail, String password) {
        Set<String> invited = getInvitedEmails();
        Set<String> next = new HashSet<>(invited);
        next.add(normalizedEmail);
        p.edit().putStringSet("invited_admin_emails", next).putString(pwKey(normalizedEmail), password).apply();
    }

    /**
     * Removes cached invite + password for this email on this device (e.g. after Super Admin deleted the account).
     */
    public void clearInvitedAdminLocal(String emailRaw) {
        String n = norm(emailRaw);
        if (n.isEmpty()) {
            return;
        }
        boolean hadInvite = getInvitedEmails().contains(n);
        boolean hadPw = p.contains(pwKey(n));
        if (!hadInvite && !hadPw) {
            return;
        }
        Set<String> next = new HashSet<>(getInvitedEmails());
        next.remove(n);
        SharedPreferences.Editor ed = p.edit()
                .remove(pwKey(n))
                .remove("fails_" + n)
                .remove("lock_until_" + n);
        if (next.isEmpty()) {
            ed.remove("invited_admin_emails");
        } else {
            ed.putStringSet("invited_admin_emails", next);
        }
        ed.apply();
        logAudit("ADMIN_LOCAL_CLEARED email=" + n);
    }

    /**
     * Whether Firestore still allows this barber admin to sign in (not deleted by Super Admin).
     */
    public void verifyInvitedAdminStillAuthorized(String emailRaw,
                                                  java.util.function.Consumer<InvitedAdminCloudStatus> callback) {
        String n = norm(emailRaw);
        if (n.isEmpty()) {
            callback.accept(InvitedAdminCloudStatus.REVOKED);
            return;
        }
        try {
            AppFirestore.db().collection(AppFirestore.COL_ADMIN_ACCOUNTS).document(n).get()
                    .addOnCompleteListener(task -> {
                        if (!task.isSuccessful() || task.getResult() == null) {
                            callback.accept(InvitedAdminCloudStatus.VERIFY_FAILED);
                            return;
                        }
                        DocumentSnapshot doc = task.getResult();
                        if (!doc.exists() || !Boolean.TRUE.equals(doc.getBoolean(CLOUD_FIELD_VIA_INVITE))) {
                            callback.accept(InvitedAdminCloudStatus.REVOKED);
                            return;
                        }
                        callback.accept(InvitedAdminCloudStatus.ACTIVE);
                    });
        } catch (Exception e) {
            Log.w(TAG, "verifyInvitedAdminStillAuthorized", e);
            callback.accept(InvitedAdminCloudStatus.VERIFY_FAILED);
        }
    }

    /**
     * If this email was registered on another device, load credentials from Firestore and cache locally.
     */
    public void tryCloudAdminSignIn(String emailRaw, String password,
                                    java.util.function.Consumer<CloudAdminSignIn> callback) {
        String n = norm(emailRaw);
        if (n.isEmpty() || password == null || password.isEmpty()) {
            callback.accept(CloudAdminSignIn.NOT_FOUND);
            return;
        }
        try {
            AppFirestore.db().collection(AppFirestore.COL_ADMIN_ACCOUNTS).document(n).get()
                    .addOnCompleteListener(task -> {
                        if (!task.isSuccessful() || task.getResult() == null) {
                            Log.w(TAG, "tryCloudAdminSignIn Firestore error email=" + n, task.getException());
                            callback.accept(CloudAdminSignIn.CLOUD_ERROR);
                            return;
                        }
                        DocumentSnapshot doc = task.getResult();
                        if (!doc.exists()) {
                            callback.accept(CloudAdminSignIn.NOT_FOUND);
                            return;
                        }
                        if (!Boolean.TRUE.equals(doc.getBoolean(CLOUD_FIELD_VIA_INVITE))) {
                            callback.accept(CloudAdminSignIn.NOT_FOUND);
                            return;
                        }
                        String remote = readAdminPasswordField(doc);
                        if (remote == null || !passwordsMatchForCloud(password, remote)) {
                            callback.accept(CloudAdminSignIn.WRONG_PASSWORD);
                            return;
                        }
                        cacheAdminFromCloud(n, password);
                        callback.accept(CloudAdminSignIn.OK);
                    });
        } catch (Exception e) {
            Log.w(TAG, "tryCloudAdminSignIn", e);
            callback.accept(CloudAdminSignIn.CLOUD_ERROR);
        }
    }

    /**
     * Super Admin only: set a new password for a barber who has an {@code admin_accounts} doc with
     * {@code viaInvite == true}. Does not change Firebase Authentication (barber sign-in is app + Firestore).
     */
    public void superAdminResetBarberPassword(String emailRaw, String newPassword,
                                              java.util.function.Consumer<Boolean> onDone) {
        String n = norm(emailRaw);
        if (n.isEmpty() || newPassword == null || newPassword.length() < 6) {
            onDone.accept(false);
            return;
        }
        try {
            AppFirestore.db().collection(AppFirestore.COL_ADMIN_ACCOUNTS).document(n).get()
                    .addOnCompleteListener(task -> {
                        if (!task.isSuccessful() || task.getResult() == null || !task.getResult().exists()) {
                            onDone.accept(false);
                            return;
                        }
                        DocumentSnapshot doc = task.getResult();
                        if (!Boolean.TRUE.equals(doc.getBoolean(CLOUD_FIELD_VIA_INVITE))) {
                            onDone.accept(false);
                            return;
                        }
                        Map<String, Object> m = new HashMap<>();
                        m.put("password", newPassword);
                        m.put("updatedAt", System.currentTimeMillis());
                        m.put(CLOUD_FIELD_VIA_INVITE, true);
                        AppFirestore.db().collection(AppFirestore.COL_ADMIN_ACCOUNTS).document(n)
                                .set(m, SetOptions.merge())
                                .addOnCompleteListener(t2 -> {
                                    if (t2.isSuccessful()) {
                                        logAudit("SUPER_RESET_BARBER_PW email=" + n);
                                    } else {
                                        Log.w(TAG, "superAdminResetBarberPassword write failed",
                                                t2.getException());
                                    }
                                    onDone.accept(t2.isSuccessful());
                                });
                    });
        } catch (Exception e) {
            Log.w(TAG, "superAdminResetBarberPassword", e);
            onDone.accept(false);
        }
    }

    /**
     * Super Admin: create a one-time invite tied to this email. Runs Firestore check (superadmin emails blocked).
     * Callback on main thread is caller's responsibility.
     */
    public void createInviteForEmailAsync(String emailRaw, java.util.function.Consumer<String> onResult) {
        String n = norm(emailRaw);
        if (n.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(n).matches()) {
            onResult.accept(null);
            return;
        }
        if (isAdminEmail(n)) {
            onResult.accept(null);
            return;
        }
        try {
            AppFirestore.db().collection(AppFirestore.COL_STAFF_ROLES).document(n).get()
                    .addOnCompleteListener(task -> {
                        if (!task.isSuccessful()) {
                            Log.w(TAG, "createInviteForEmailAsync staff_roles read failed", task.getException());
                            onResult.accept(null);
                            return;
                        }
                        if (task.getResult() != null && task.getResult().exists()) {
                            String role = task.getResult().getString(SuperAdminFirebase.FIELD_ROLE);
                            if (role != null
                                    && SuperAdminFirebase.ROLE_SUPERADMIN.equalsIgnoreCase(role.trim())) {
                                onResult.accept(null);
                                return;
                            }
                        }
                        createInviteAndSyncToCloud(n, onResult);
                    });
        } catch (Exception e) {
            Log.w(TAG, "createInviteForEmailAsync", e);
            onResult.accept(null);
        }
    }

    /**
     * Writes invite locally (Super Admin device) and to Firestore so any device can accept the link.
     */
    private void createInviteAndSyncToCloud(String normalizedEmail, java.util.function.Consumer<String> onResult) {
        String token;
        do {
            token = randomToken(10);
        } while (p.contains("invite_" + token.toUpperCase(Locale.ROOT)));
        String t = token.toUpperCase(Locale.ROOT);
        long exp = System.currentTimeMillis() + INVITE_VALID_MS;
        p.edit()
                .putLong("invite_" + t, exp)
                .putString("invite_bind_" + t, normalizedEmail)
                .apply();

        Map<String, Object> cloud = new HashMap<>();
        cloud.put("email", normalizedEmail);
        cloud.put("expiresAt", exp);
        try {
            AppFirestore.db().collection(AppFirestore.COL_ADMIN_INVITES).document(t).set(cloud)
                    .addOnCompleteListener(task -> {
                        if (!task.isSuccessful()) {
                            Log.w(TAG, "createInviteAndSyncToCloud failed", task.getException());
                            p.edit().remove("invite_" + t).remove("invite_bind_" + t).apply();
                            onResult.accept(null);
                            return;
                        }
                        logAudit("INVITE_EMAIL email=" + normalizedEmail + " token=" + t);
                        onResult.accept(t);
                    });
        } catch (Exception e) {
            Log.w(TAG, "createInviteAndSyncToCloud", e);
            p.edit().remove("invite_" + t).remove("invite_bind_" + t).apply();
            onResult.accept(null);
        }
    }

    /** Deep link after HTTPS page redirect (still used by {@code public/invite.html}). */
    public static String buildInviteDeepLink(String token) {
        String t = token.trim().toUpperCase(Locale.ROOT);
        return "cutify://invite/accept?token=" + t;
    }

    private void consumeInviteLocalOnly(String tokenUpper) {
        p.edit().remove("invite_" + tokenUpper).remove("invite_bind_" + tokenUpper).apply();
        logAudit("INVITE_CONSUMED token=" + tokenUpper);
    }

    private static final String TOKEN_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private String randomToken(int len) {
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            sb.append(TOKEN_CHARS.charAt(random.nextInt(TOKEN_CHARS.length())));
        }
        return sb.toString();
    }

    public boolean isAccountLocked(String email) {
        long until = p.getLong("lock_until_" + norm(email), 0L);
        return until > System.currentTimeMillis();
    }

    public long getLockoutRemainingMs(String email) {
        long until = p.getLong("lock_until_" + norm(email), 0L);
        return Math.max(0L, until - System.currentTimeMillis());
    }

    public void recordFailedAttempt(String email) {
        String k = norm(email);
        if (k.isEmpty()) {
            return;
        }
        int n = p.getInt("fails_" + k, 0) + 1;
        SharedPreferences.Editor e = p.edit().putInt("fails_" + k, n);
        if (n >= MAX_ATTEMPTS) {
            e.putLong("lock_until_" + k, System.currentTimeMillis() + LOCKOUT_MS);
            e.putInt("fails_" + k, 0);
            logAudit("LOCKOUT email=" + k);
        }
        e.apply();
    }

    public void clearFailedAttempts(String email) {
        p.edit().remove("fails_" + norm(email)).apply();
    }

    public void logAudit(String message) {
        String line = new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(new Date())
                + " " + message;
        String prev = p.getString("audit_lines", "");
        List<String> lines = new ArrayList<>();
        lines.add(line);
        if (!prev.isEmpty()) {
            String[] split = prev.split("\n", -1);
            for (String s : split) {
                if (!s.isEmpty()) {
                    lines.add(s);
                }
            }
        }
        while (lines.size() > AUDIT_MAX) {
            lines.remove(lines.size() - 1);
        }
        p.edit().putString("audit_lines", TextUtils.join("\n", lines)).apply();
    }

    public List<String> getAuditLines() {
        String s = p.getString("audit_lines", "");
        if (s.isEmpty()) {
            return Collections.emptyList();
        }
        return Arrays.asList(s.split("\n"));
    }

    /**
     * If this barber was only on-device before cloud sync existed, push password to Firestore once they log in.
     */
    public void syncInvitedAdminToCloud(String email) {
        String n = norm(email);
        if (isLegacyAdminEmail(n)) {
            return;
        }
        if (!isInvitedAdminEmail(n)) {
            return;
        }
        String pw = p.getString(pwKey(n), null);
        if (pw != null) {
            pushAdminPasswordToCloud(n, pw);
        }
    }
}
