package com.example.online_alot;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import java.util.Objects;

public final class BookingNotificationScheduler {

    private static final String ACTION_REMINDER = "com.example.online_alot.ACTION_BOOKING_REMINDER";

    private static int requestCode(long bookingTs, String barberId, String customerName) {
        return Math.abs(Objects.hash(bookingTs, barberId, customerName)) & 0x7FFFFFFF;
    }

    public static void scheduleReminder(Context context, long bookingTimestamp) {
        CutifyNotificationChannels.ensureChannels(context);
        QueueManager.Appointment ap = QueueManager.getInstance().findAppointment(bookingTimestamp);
        if (ap == null || !"Waiting".equals(ap.status)) {
            return;
        }
        long reminderAt = ap.scheduledArrivalMillis - 15L * 60_000L;
        if (reminderAt <= System.currentTimeMillis()) {
            return;
        }

        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) {
            return;
        }

        Intent intent = new Intent(context, BookingReminderReceiver.class);
        intent.setAction(ACTION_REMINDER);
        intent.putExtra(BookingReminderReceiver.EXTRA_BARBER_ID, ap.barberId);
        intent.putExtra(BookingReminderReceiver.EXTRA_BARBER_NAME, ap.barberName);
        intent.putExtra(BookingReminderReceiver.EXTRA_CUSTOMER_NAME, ap.customerName);
        intent.putExtra(BookingReminderReceiver.EXTRA_BOOKING_TS, bookingTimestamp);

        int req = requestCode(bookingTimestamp, ap.barberId, ap.customerName);
        PendingIntent pi = PendingIntent.getBroadcast(
                context,
                req,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminderAt, pi);
            } else {
                am.setExact(AlarmManager.RTC_WAKEUP, reminderAt, pi);
            }
        } catch (SecurityException e) {
            am.set(AlarmManager.RTC_WAKEUP, reminderAt, pi);
        }
    }

    public static void cancelReminder(Context context, long bookingTimestamp, String barberId, String customerName) {
        Intent intent = new Intent(context, BookingReminderReceiver.class);
        intent.setAction(ACTION_REMINDER);
        int req = requestCode(bookingTimestamp, barberId, customerName);
        PendingIntent pi = PendingIntent.getBroadcast(
                context,
                req,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am != null) {
            am.cancel(pi);
        }
        pi.cancel();
    }
}
