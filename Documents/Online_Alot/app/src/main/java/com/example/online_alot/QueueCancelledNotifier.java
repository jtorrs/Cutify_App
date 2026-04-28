package com.example.online_alot;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import java.util.List;
import java.util.Map;

/**
 * When a barber cancels the customer’s waiting slot (e.g. no-show / late), Firestore syncs
 * {@code Waiting → Cancelled} on the customer’s phone — show a notification and drop any reminder alarm.
 */
public final class QueueCancelledNotifier {

    private static final int NOTIF_BASE = 93100;

    private QueueCancelledNotifier() {}

    static void notifyIfBarberCancelledRemote(QueueManager qm, Map<Long, String> oldStatuses,
                                             List<QueueManager.Appointment> afterList) {
        if (qm.getQueueContext() == null || oldStatuses == null || afterList == null) {
            return;
        }
        Context ctx = qm.getQueueContext();
        UserProfileManager upm = new UserProfileManager(ctx);
        String localName = upm.getName() != null ? upm.getName().trim() : "";
        String localSync = upm.getGuestSyncId() != null ? upm.getGuestSyncId().trim() : "";
        if (localName.isEmpty() && localSync.isEmpty()) {
            return;
        }

        CutifyNotificationChannels.ensureChannels(ctx);
        if (!NotificationManagerCompat.from(ctx).areNotificationsEnabled()) {
            return;
        }

        for (QueueManager.Appointment a : afterList) {
            if (!"Cancelled".equals(a.status)) {
                continue;
            }
            String prev = oldStatuses.get(a.timestamp);
            if (!"Waiting".equals(prev)) {
                continue;
            }

            boolean nameMatch = !localName.isEmpty()
                    && a.customerName != null
                    && a.customerName.trim().equalsIgnoreCase(localName);
            String aSync = a.customerSyncId != null ? a.customerSyncId.trim() : "";
            boolean syncMatch = !localSync.isEmpty() && localSync.equals(aSync);
            if (!nameMatch && !syncMatch) {
                continue;
            }

            Barber b = qm.getBarber(a.barberId);
            String barberLabel = b != null && b.getDisplayName() != null && !b.getDisplayName().trim().isEmpty()
                    ? b.getDisplayName().trim()
                    : (a.barberName != null ? a.barberName : a.barberId);

            BookingNotificationScheduler.cancelReminder(ctx, a.timestamp, a.barberId, a.customerName);

            Intent tap = new Intent(ctx, AppointmentsActivity.class);
            tap.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent tapPi = PendingIntent.getActivity(
                    ctx,
                    (int) (Math.abs(a.timestamp) % 20000) + 5000,
                    tap,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

            String title = ctx.getString(R.string.notif_queue_removed_title);
            String text = ctx.getString(R.string.notif_queue_removed_text, barberLabel);
            String big = ctx.getString(R.string.notif_queue_removed_big, barberLabel);

            int notifId = NOTIF_BASE + (int) (Math.abs(a.timestamp) % 8000);
            NotificationCompat.Builder nb = new NotificationCompat.Builder(ctx, CutifyNotificationChannels.CHANNEL_APPOINTMENTS_ID)
                    .setSmallIcon(R.drawable.ic_notification_bell)
                    .setContentTitle(title)
                    .setContentText(text)
                    .setStyle(new NotificationCompat.BigTextStyle().bigText(big))
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setCategory(NotificationCompat.CATEGORY_STATUS)
                    .setContentIntent(tapPi)
                    .setAutoCancel(true);

            try {
                NotificationManagerCompat.from(ctx).notify(notifId, nb.build());
            } catch (SecurityException ignored) {
            }
        }
    }
}
