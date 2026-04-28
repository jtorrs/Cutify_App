package com.example.online_alot;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

import androidx.core.app.NotificationManagerCompat;

public class BookingCancelFromNotificationReceiver extends BroadcastReceiver {

    public static final String EXTRA_NOTIF_ID = "notifId";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) {
            return;
        }
        int notifId = intent.getIntExtra(EXTRA_NOTIF_ID, -1);
        String barberId = intent.getStringExtra(BookingReminderReceiver.EXTRA_BARBER_ID);
        String customerName = intent.getStringExtra(BookingReminderReceiver.EXTRA_CUSTOMER_NAME);
        long bookingTs = intent.getLongExtra(BookingReminderReceiver.EXTRA_BOOKING_TS, 0L);

        if (barberId == null || customerName == null) {
            return;
        }

        long ts = QueueManager.getInstance().cancelQueueEntry(barberId, customerName, bookingTs);
        if (ts >= 0) {
            BookingNotificationScheduler.cancelReminder(context, ts, barberId, customerName);
            Toast.makeText(context, R.string.queue_cancelled_toast, Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(context, R.string.queue_cancel_failed, Toast.LENGTH_SHORT).show();
        }

        if (notifId >= 0) {
            NotificationManagerCompat.from(context).cancel(notifId);
        }
    }
}
