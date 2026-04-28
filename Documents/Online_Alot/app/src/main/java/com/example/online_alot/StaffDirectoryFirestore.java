package com.example.online_alot;

import android.util.Log;

import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Lists and removes barber-admin accounts stored in Firestore ({@link AppFirestore#COL_ADMIN_ACCOUNTS}).
 */
public final class StaffDirectoryFirestore {

    private static final String TAG = "StaffDirectoryFirestore";
    public static final String FIELD_VIA_INVITE = "viaInvite";

    private StaffDirectoryFirestore() {}

    /** Document ids = normalized emails, {@code viaInvite == true}. */
    public static void fetchInvitedBarberEmails(Consumer<List<String>> onResult) {
        AppFirestore.db().collection(AppFirestore.COL_ADMIN_ACCOUNTS)
                .whereEqualTo(FIELD_VIA_INVITE, true)
                .get()
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful() || task.getResult() == null) {
                        Log.w(TAG, "fetchInvitedBarberEmails", task.getException());
                        onResult.accept(Collections.emptyList());
                        return;
                    }
                    List<String> emails = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : task.getResult()) {
                        emails.add(doc.getId());
                    }
                    Collections.sort(emails);
                    onResult.accept(emails);
                });
    }

    /**
     * Deletes admin login + barber profile in Firestore. Barber can no longer sign in (no {@code viaInvite} doc).
     */
    public static void deleteBarberStaffAccount(String emailRaw, Consumer<Boolean> onDone) {
        String id = emailRaw == null ? "" : emailRaw.trim().toLowerCase(Locale.ROOT);
        if (id.isEmpty()) {
            onDone.accept(false);
            return;
        }
        // Free-mode path: try Cloud Function first, then fallback to direct Firestore.
        CloudActionGuards.deleteBarberStaffAccount(id, ok -> {
            if (ok) {
                onDone.accept(true);
                return;
            }
            AppFirestore.db().collection(AppFirestore.COL_ADMIN_ACCOUNTS).document(id).delete()
                    .addOnCompleteListener(t1 -> {
                        if (!t1.isSuccessful()) {
                            Log.w(TAG, "delete admin_accounts", t1.getException());
                            onDone.accept(false);
                            return;
                        }
                        AppFirestore.db().collection(AppFirestore.COL_BARBERS).document(id).delete()
                                .addOnCompleteListener(t2 -> onDone.accept(t2.isSuccessful()));
                    });
        });
    }
}
