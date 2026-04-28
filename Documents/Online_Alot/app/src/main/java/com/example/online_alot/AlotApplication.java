package com.example.online_alot;

import android.app.Application;
import android.util.Log;

import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.FirebaseMessaging;

public class AlotApplication extends Application {

    private static final String TAG = "AlotApplication";

    @Override
    public void onCreate() {
        super.onCreate();
        FirebaseApp.initializeApp(this);
        CutifyAnalytics.init(this, getString(R.string.mixpanel_project_token));

        QueueManager.init(this);
        StaffAuthStore.init(this);
        try {
            CustomerProfileFirestoreSync.attach(this);
        } catch (Throwable t) {
            Log.e(TAG, "Customer profile Firestore sync disabled", t);
        }
        CutifyNotificationChannels.ensureChannels(this);

        FirebaseMessaging.getInstance().getToken()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && task.getResult() != null) {
                        FcmTokenRegistrar.register(this, task.getResult());
                    } else if (task.getException() != null) {
                        Log.e(TAG, "FCM getToken failed", task.getException());
                    }
                });
    }
}
