package com.example.online_alot;

import java.util.Locale;

/**
 * Barber admins are <b>not</b> listed here — they must be invited by Super Admin and complete the invite link.
 * <p>
 * Super Admin: Firebase Authentication + Firestore {@link AppFirestore#COL_STAFF_ROLES}.
 */
public class AuthManager {

    private static String normEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * Legacy hardcoded barber list removed — always false so only {@link StaffAuthStore} invited emails can sign in.
     */
    public static boolean isAdmin(String email) {
        return false;
    }
}
