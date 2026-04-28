package com.example.online_alot;

import android.app.PendingIntent;
import android.content.Intent;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

/**
 * Handles FCM: token refresh (saved via {@link FcmTokenRegistrar}) and incoming messages while the app is in the foreground.
 */
public class CutifyFirebaseMessagingService extends FirebaseMessagingService {

    private static final int NOTIF_ID_FCM_BASE = 9200;

    @Override
    public void onNewToken(@NonNull String token) {
        FcmTokenRegistrar.register(getApplicationContext(), token);
    }

    @Override
    public void onMessageReceived(@NonNull RemoteMessage remoteMessage) {
        CutifyNotificationChannels.ensureChannels(this);

        RemoteMessage.Notification n = remoteMessage.getNotification();
        String title = n != null && !TextUtils.isEmpty(n.getTitle()) ? n.getTitle() : null;
        String body = n != null && !TextUtils.isEmpty(n.getBody()) ? n.getBody() : null;

        if (title == null && remoteMessage.getData().size() > 0) {
            title = remoteMessage.getData().get("title");
            if (body == null) {
                body = remoteMessage.getData().get("body");
            }
        }
        if (TextUtils.isEmpty(title) && TextUtils.isEmpty(body)) {
            return;
        }
        if (title == null) {
            title = getString(R.string.app_name);
        }
        if (body == null) {
            body = "";
        }

        Intent tap = new Intent(this, HomeActivity.class);
        tap.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent tapPi = PendingIntent.getActivity(
                this,
                NOTIF_ID_FCM_BASE,
                tap,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        int nid = NOTIF_ID_FCM_BASE + (int) (System.currentTimeMillis() % 5000);
        NotificationCompat.Builder b = new NotificationCompat.Builder(this, CutifyNotificationChannels.CHANNEL_APPOINTMENTS_ID)
                .setSmallIcon(R.drawable.ic_notification_bell)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(tapPi)
                .setAutoCancel(true);

        if (NotificationManagerCompat.from(this).areNotificationsEnabled()) {
            NotificationManagerCompat.from(this).notify(nid, b.build());
        }
    }
}
