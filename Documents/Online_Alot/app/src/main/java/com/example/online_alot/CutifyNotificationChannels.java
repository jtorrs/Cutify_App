package com.example.online_alot;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;

import androidx.core.app.NotificationManagerCompat;

public final class CutifyNotificationChannels {

    public static final String CHANNEL_APPOINTMENTS_ID = "cutify_appointments";
    public static final String CHANNEL_SUPER_INVITES_ID = "cutify_super_invites";

    public static void ensureChannels(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        NotificationManager nm = context.getSystemService(NotificationManager.class);
        if (nm == null) {
            return;
        }
        NotificationChannel ch = new NotificationChannel(
                CHANNEL_APPOINTMENTS_ID,
                context.getString(R.string.notif_channel_appointments_name),
                NotificationManager.IMPORTANCE_HIGH);
        ch.setDescription(context.getString(R.string.notif_channel_appointments_desc));
        nm.createNotificationChannel(ch);

        NotificationChannel inv = new NotificationChannel(
                CHANNEL_SUPER_INVITES_ID,
                context.getString(R.string.notif_channel_super_invites_name),
                NotificationManager.IMPORTANCE_DEFAULT);
        inv.setDescription(context.getString(R.string.notif_channel_super_invites_desc));
        nm.createNotificationChannel(inv);
    }

    public static boolean canPostNotifications(Context context) {
        return NotificationManagerCompat.from(context).areNotificationsEnabled();
    }
}
