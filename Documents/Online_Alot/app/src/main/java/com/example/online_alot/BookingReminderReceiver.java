package com.example.online_alot;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

public class BookingReminderReceiver extends BroadcastReceiver {

    public static final String EXTRA_BARBER_ID = "barberId";
    public static final String EXTRA_BARBER_NAME = "barberName";
    public static final String EXTRA_CUSTOMER_NAME = "customerName";
    public static final String EXTRA_BOOKING_TS = "bookingTs";

    private static final int NOTIF_BASE_ID = 7100;

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) {
            return;
        }
        String barberId = intent.getStringExtra(EXTRA_BARBER_ID);
        String barberName = intent.getStringExtra(EXTRA_BARBER_NAME);
        String customerName = intent.getStringExtra(EXTRA_CUSTOMER_NAME);
        long bookingTs = intent.getLongExtra(EXTRA_BOOKING_TS, 0L);

        CutifyNotificationChannels.ensureChannels(context);

        int notifId = NOTIF_BASE_ID + (int) (Math.abs(bookingTs) % 5000);

        Intent cancelIntent = new Intent(context, BookingCancelFromNotificationReceiver.class);
        cancelIntent.putExtra(EXTRA_BARBER_ID, barberId);
        cancelIntent.putExtra(EXTRA_CUSTOMER_NAME, customerName);
        cancelIntent.putExtra(EXTRA_BOOKING_TS, bookingTs);
        cancelIntent.putExtra(BookingCancelFromNotificationReceiver.EXTRA_NOTIF_ID, notifId);

        PendingIntent cancelPi = PendingIntent.getBroadcast(
                context,
                (int) ((bookingTs % 10000) + 9000),
                cancelIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent tap = new Intent(context, HomeActivity.class);
        tap.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent tapPi = PendingIntent.getActivity(
                context,
                (int) (bookingTs % 10000),
                tap,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        String title = context.getString(R.string.notif_reminder_title);
        String text = context.getString(R.string.notif_reminder_body, barberName != null ? barberName : "");

        NotificationCompat.Builder b = new NotificationCompat.Builder(context, CutifyNotificationChannels.CHANNEL_APPOINTMENTS_ID)
                .setSmallIcon(R.drawable.ic_notification_bell)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(
                        context.getString(R.string.notif_reminder_big, barberName != null ? barberName : "")))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(tapPi)
                .setAutoCancel(true)
                .addAction(0, context.getString(R.string.queue_cancel_action), cancelPi);

        if (NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            NotificationManagerCompat.from(context).notify(notifId, b.build());
        }
    }
}
