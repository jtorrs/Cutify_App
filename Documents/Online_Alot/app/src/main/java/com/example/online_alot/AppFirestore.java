package com.example.online_alot;

import com.google.firebase.firestore.FirebaseFirestore;

/**
 * Firestore entry + suggested collection names for multi-device sync.
 * <p>
 * Before using: Firebase Console → Build → Firestore Database → Create database.
 * For class demos you can start in <b>test mode</b> (expires in 30 days); later switch to locked rules + Auth.
 */
public final class AppFirestore {

    private AppFirestore() {}

    public static FirebaseFirestore db() {
        return FirebaseFirestore.getInstance();
    }

    /** Barber profiles (id = barber email or uid). Fields: displayName, rating, onDuty, … */
    public static final String COL_BARBERS = "barbers";

    /** One document per booking. Fields include barberId, customerName, timestamp, status, homeService, homeServicePreferredMillis, … */
    public static final String COL_APPOINTMENTS = "appointments";
    /** One document per user report (doc id = timestamp). */
    public static final String COL_REPORTS = "reports";

    /**
     * Barber admin accounts (doc id = normalized email). Demo: stores password plaintext — use Auth in production.
     */
    public static final String COL_ADMIN_ACCOUNTS = "admin_accounts";

    /**
     * Staff roles (doc id = lowercase email, same as Firebase Auth). Field {@code role} = {@code superadmin}.
     * Created only in Firebase Console — never from the app UI.
     */
    public static final String COL_STAFF_ROLES = "staff_roles";

    /** Pending barber-admin invites (doc id = token). Synced so the invitee can open the link on any device. */
    public static final String COL_ADMIN_INVITES = "admin_invites";

    /**
     * Customer profile (no login). Document id = guest sync UUID ({@link UserProfileManager#getOrCreateGuestSyncId()}).
     * Real-time sync across devices when the same ID is entered under Profile.
     */
    public static final String COL_CUSTOMER_PROFILES = "customer_profiles";

    /**
     * Optional: queue as subcollection {@code barbers/{barberId}/queue} with order field,
     * or flat docs here with barberId + customerName.
     */
    public static final String COL_QUEUE = "queue";

    /**
     * FCM device tokens for push (doc id = token string). See {@link FcmTokenRegistrar}.
     */
    public static final String COL_FCM_TOKENS = "fcm_tokens";

    /**
     * Public app runtime config (example doc: mobile_update with latestVersion/apkUrl fields).
     */
    public static final String COL_APP_CONFIG = "app_config";
}
