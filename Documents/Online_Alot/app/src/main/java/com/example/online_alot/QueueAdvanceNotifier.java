package com.example.online_alot;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import java.util.Map;

/**
 * When the barber marks someone Done (or cancels ahead of you), Firestore syncs and your in-shop
 * queue position moves up. We post a local notification so you can head to the shop in time.
 */
public final class QueueAdvanceNotifier {

    private static final int NOTIF_BASE = 92000;

    private QueueAdvanceNotifier() {}

    static void notifyIfInShopAdvanced(QueueManager qm, Map<Long, Integer> oldIndexByBookingTs) {
        if (oldIndexByBookingTs == null || oldIndexByBookingTs.isEmpty()) {
            return;
        }
        Context ctx = qm.getQueueContext();
        if (ctx == null) {
            return;
        }
        CutifyNotificationChannels.ensureChannels(ctx);
        if (!NotificationManagerCompat.from(ctx).areNotificationsEnabled()) {
            return;
        }

        for (Map.Entry<Long, Integer> e : oldIndexByBookingTs.entrySet()) {
            long bookingTs = e.getKey();
            int oldIdx = e.getValue();
            if (oldIdx < 0) {
                continue;
            }
            int newIdx = qm.getInShopWaitingZeroBasedIndex(bookingTs);
            if (newIdx < 0 || newIdx >= oldIdx) {
                continue;
            }

            QueueManager.Appointment ap = findAppointment(qm, bookingTs);
            if (ap == null || !"Waiting".equals(ap.status) || ap.homeService) {
                continue;
            }

            Barber b = qm.getBarber(ap.barberId);
            String barberLabel = b != null && b.getDisplayName() != null && !b.getDisplayName().trim().isEmpty()
                    ? b.getDisplayName().trim()
                    : ap.barberName;

            Intent tap = new Intent(ctx, HomeActivity.class);
            tap.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent tapPi = PendingIntent.getActivity(
                    ctx,
                    (int) (Math.abs(bookingTs) % 20000) + 3000,
                    tap,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

            int displayPos = newIdx + 1;
            String title;
            String text;
            String big;
            if (newIdx == 0) {
                title = ctx.getString(R.string.notif_queue_next_title);
                text = ctx.getString(R.string.notif_queue_next_text, barberLabel);
                big = ctx.getString(R.string.notif_queue_next_big, barberLabel);
            } else {
                title = ctx.getString(R.string.notif_queue_move_title);
                text = ctx.getString(R.string.notif_queue_move_text, displayPos, barberLabel);
                big = ctx.getString(R.string.notif_queue_move_big, displayPos, barberLabel);
            }

            int notifId = NOTIF_BASE + (int) (Math.abs(bookingTs) % 8000);
            NotificationCompat.Builder nb = new NotificationCompat.Builder(ctx, CutifyNotificationChannels.CHANNEL_APPOINTMENTS_ID)
                    .setSmallIcon(R.drawable.ic_notification_bell)
                    .setContentTitle(title)
                    .setContentText(text)
                    .setStyle(new NotificationCompat.BigTextStyle().bigText(big))
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setCategory(NotificationCompat.CATEGORY_REMINDER)
                    .setContentIntent(tapPi)
                    .setAutoCancel(true);

            try {
                NotificationManagerCompat.from(ctx).notify(notifId, nb.build());
            } catch (SecurityException ignored) {
                // POST_NOTIFICATIONS not granted on API 33+
            }
        }
    }

    private static QueueManager.Appointment findAppointment(QueueManager qm, long bookingTs) {
        for (QueueManager.Appointment a : qm.getAppointments()) {
            if (a.timestamp == bookingTs) {
                return a;
            }
        }
        return null;
    }
}
