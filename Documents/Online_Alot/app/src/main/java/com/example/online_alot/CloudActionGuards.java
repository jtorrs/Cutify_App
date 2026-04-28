package com.example.online_alot;

import com.google.firebase.functions.FirebaseFunctions;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Cloud Functions guard layer for sensitive admin actions.
 */
public final class CloudActionGuards {
    private CloudActionGuards() {}

    public static void deleteBarberStaffAccount(String email, Consumer<Boolean> onDone) {
        Map<String, Object> data = new HashMap<>();
        data.put("email", email);
        FirebaseFunctions.getInstance()
                .getHttpsCallable("deleteBarberStaffAccount")
                .call(data)
                .addOnCompleteListener(task -> onDone.accept(task.isSuccessful()));
    }
}
