package com.example.online_alot;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.SetOptions;

import java.util.Map;

/**
 * Real-time sync of {@link UserProfileManager} fields to {@link AppFirestore#COL_CUSTOMER_PROFILES}.
 * Same guest sync ID on two phones → same Firestore doc → profile + home-service fields stay aligned.
 */
public final class CustomerProfileFirestoreSync {

    private static final String TAG = "CustomerProfileSync";

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static ListenerRegistration registration;
    private static Runnable debouncedUpsert;
    private static volatile boolean applyingRemote;

    private CustomerProfileFirestoreSync() {}

    public static void attach(Context context) {
        Context app = context.getApplicationContext();
        detach();
        UserProfileManager upm = new UserProfileManager(app);
        String id = upm.getOrCreateGuestSyncId();
        try {
            registration = AppFirestore.db()
                    .collection(AppFirestore.COL_CUSTOMER_PROFILES)
                    .document(id)
                    .addSnapshotListener((snapshot, error) ->
                            MAIN.post(() -> onSnapshot(app, snapshot, error)));
        } catch (Exception e) {
            Log.w(TAG, "attach failed", e);
        }
    }

    public static void detach() {
        if (registration != null) {
            registration.remove();
            registration = null;
        }
    }

    /** Call after changing guest sync ID (link another device). */
    public static void rebind(Context context) {
        attach(context);
    }

    private static void onSnapshot(Context app, DocumentSnapshot snap, FirebaseFirestoreException error) {
        if (error != null) {
            Log.w(TAG, "listener", error);
            return;
        }
        UserProfileManager upm = new UserProfileManager(app);
        if (snap == null || !snap.exists()) {
            upsertNow(app, upm, false);
            return;
        }
        applyingRemote = true;
        try {
            upm.applyFromFirestore(snap);
        } finally {
            applyingRemote = false;
        }
    }

    /** Debounced push after local edits (profile, home service form, location). */
    public static void requestUpsert(Context context) {
        if (applyingRemote) {
            return;
        }
        Context app = context.getApplicationContext();
        if (debouncedUpsert != null) {
            MAIN.removeCallbacks(debouncedUpsert);
        }
        debouncedUpsert = () -> {
            UserProfileManager upm = new UserProfileManager(app);
            upsertNow(app, upm, true);
        };
        MAIN.postDelayed(debouncedUpsert, 400);
    }

    private static void upsertNow(Context app, UserProfileManager upm, boolean mergeOnly) {
        if (applyingRemote) {
            return;
        }
        String id = upm.getGuestSyncId();
        if (id.isEmpty()) {
            id = upm.getOrCreateGuestSyncId();
        }
        Map<String, Object> data = upm.toFirestoreMap();
        if (data.isEmpty() && mergeOnly) {
            return;
        }
        try {
            AppFirestore.db().collection(AppFirestore.COL_CUSTOMER_PROFILES)
                    .document(id)
                    .set(data, SetOptions.merge())
                    .addOnFailureListener(e -> Log.w(TAG, "upsert failed", e));
        } catch (Exception e) {
            Log.w(TAG, "upsert", e);
        }
    }
}
