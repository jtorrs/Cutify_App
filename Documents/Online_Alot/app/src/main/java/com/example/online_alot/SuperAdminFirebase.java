package com.example.online_alot;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.Locale;

/**
 * Super Admin is defined only in Firebase (Auth user + Firestore role). No password in app source.
 * <p>
 * Setup: see project docs / Firebase Console — collection {@link AppFirestore#COL_STAFF_ROLES},
 * field {@link #FIELD_ROLE} = {@link #ROLE_SUPERADMIN}, document id = same email as in Authentication (lowercase).
 */
public final class SuperAdminFirebase {

    public static final String FIELD_ROLE = "role";
    public static final String ROLE_SUPERADMIN = "superadmin";

    private SuperAdminFirebase() {}

    /** Document id must match normalized email used when creating the doc in Console. */
    public static String roleDocumentId(String email) {
        if (email == null) {
            return "";
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }

    public static String roleDocumentId(FirebaseUser user) {
        if (user == null || user.getEmail() == null) {
            return "";
        }
        return roleDocumentId(user.getEmail());
    }

    public static Task<Boolean> userHasSuperAdminRole(FirebaseUser user) {
        String id = roleDocumentId(user);
        if (id.isEmpty()) {
            return Tasks.forResult(false);
        }
        FirebaseFirestore db = AppFirestore.db();
        String col = AppFirestore.COL_STAFF_ROLES;
        return documentIsSuperAdmin(db, col, id).continueWithTask(task -> {
            if (!task.isSuccessful()) {
                return Tasks.forResult(false);
            }
            if (Boolean.TRUE.equals(task.getResult())) {
                return Tasks.forResult(true);
            }
            // Legacy: Firestore document id was created with different casing than normalized email.
            String raw = user.getEmail() != null ? user.getEmail().trim() : "";
            if (raw.isEmpty() || raw.equals(id)) {
                return Tasks.forResult(false);
            }
            return documentIsSuperAdmin(db, col, raw);
        });
    }

    private static Task<Boolean> documentIsSuperAdmin(FirebaseFirestore db, String col, String docId) {
        return db.collection(col).document(docId).get()
                .continueWith(task -> {
                    if (!task.isSuccessful() || task.getResult() == null) {
                        return false;
                    }
                    DocumentSnapshot snap = task.getResult();
                    if (!snap.exists()) {
                        return false;
                    }
                    String role = snap.getString(FIELD_ROLE);
                    return role != null && ROLE_SUPERADMIN.equalsIgnoreCase(role.trim());
                });
    }
}
