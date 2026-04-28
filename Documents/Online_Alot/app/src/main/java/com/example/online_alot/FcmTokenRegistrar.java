package com.example.online_alot;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

/**
 * Writes the device FCM token to Firestore so you can target users from the Console, Cloud Functions, or Admin SDK.
 */
public final class FcmTokenRegistrar {

    private static final String TAG = "FcmTokenRegistrar";

    private FcmTokenRegistrar() {}

    /**
     * Upserts {@code fcm_tokens/{token}} with guest sync id and optional Firebase Auth uid.
     */
    public static void register(@NonNull Context context, @Nullable String token) {
        if (token == null || token.isEmpty()) {
            return;
        }
        UserProfileManager upm = new UserProfileManager(context);
        String guestId = upm.getOrCreateGuestSyncId();

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        String uid = user != null ? user.getUid() : "";

        Map<String, Object> data = new HashMap<>();
        data.put("token", token);
        data.put("guestSyncId", guestId);
        data.put("uid", uid);
        data.put("updatedAt", FieldValue.serverTimestamp());

        FirebaseFirestore.getInstance()
                .collection(AppFirestore.COL_FCM_TOKENS)
                .document(token)
                .set(data)
                .addOnSuccessListener(unused ->
                        Log.i(TAG, "FCM token saved to Firestore (collection fcm_tokens)"))
                .addOnFailureListener(e -> Log.e(TAG, "Failed to save FCM token", e));
    }
}
