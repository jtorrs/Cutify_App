package com.example.online_alot;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class AppointmentsActivity extends AppCompatActivity {

    private LinearLayout layoutAppointments;
    private ScrollView scrollAppointments;
    private TextView tvEmpty;
    private Handler refreshHandler;
    private Runnable refreshRunnable;
    private final Runnable firestoreAppointmentsRefresh = this::refreshFromFirestore;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_appointments);

        layoutAppointments = findViewById(R.id.layout_appointments);
        scrollAppointments = findViewById(R.id.scroll_appointments);
        tvEmpty = findViewById(R.id.tv_empty);
        setupNav();

        refreshHandler = new Handler(Looper.getMainLooper());
        refreshRunnable = () -> {
            loadAppointments();
            refreshHandler.postDelayed(refreshRunnable, 3000);
        };
    }

    @Override
    protected void onResume() {
        super.onResume();
        QueueManager.getInstance().addAppointmentSyncListener(firestoreAppointmentsRefresh);
        FeedbackHelper.checkAndShowFeedback(this);
        loadAppointments();
        refreshHandler.postDelayed(refreshRunnable, 3000);
    }

    @Override
    protected void onPause() {
        super.onPause();
        QueueManager.getInstance().removeAppointmentSyncListener(firestoreAppointmentsRefresh);
        refreshHandler.removeCallbacks(refreshRunnable);
    }

    private void loadAppointments() {
        layoutAppointments.removeAllViews();

        UserProfileManager upm = new UserProfileManager(this);
        String profileName = upm.getName() != null ? upm.getName().trim() : "";
        String syncId = upm.getGuestSyncId() != null ? upm.getGuestSyncId().trim() : "";

        if (profileName.isEmpty() && syncId.isEmpty()) {
            scrollAppointments.setVisibility(View.GONE);
            tvEmpty.setVisibility(View.VISIBLE);
            tvEmpty.setText(R.string.appointments_need_profile_name);
            return;
        }

        QueueManager qm = QueueManager.getInstance();
        List<QueueManager.Appointment> appointments = qm.getAppointmentsForUser(profileName, syncId);

        if (appointments.isEmpty()) {
            scrollAppointments.setVisibility(View.GONE);
            tvEmpty.setVisibility(View.VISIBLE);
            tvEmpty.setText(R.string.no_appointments);
            return;
        }

        scrollAppointments.setVisibility(View.VISIBLE);
        tvEmpty.setVisibility(View.GONE);

        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault());

        for (int i = appointments.size() - 1; i >= 0; i--) {
            QueueManager.Appointment a = appointments.get(i);
            View card = LayoutInflater.from(this).inflate(R.layout.item_appointment_card, layoutAppointments, false);

            TextView tvBarber = card.findViewById(R.id.tv_barber_name);
            TextView tvBookingType = card.findViewById(R.id.tv_booking_type);
            TextView tvCustomer = card.findViewById(R.id.tv_customer_name);
            TextView tvServiceType = card.findViewById(R.id.tv_service_type);
            TextView tvTime = card.findViewById(R.id.tv_time);
            TextView tvHomeVisit = card.findViewById(R.id.tv_home_visit_time);
            TextView tvStatus = card.findViewById(R.id.tv_status);
            MaterialButton btnCancel = card.findViewById(R.id.btn_cancel_appointment);

            tvBarber.setText(a.barberName);
            if (a.homeService) {
                tvBookingType.setText(R.string.appointment_type_home_service);
                tvBookingType.setVisibility(View.VISIBLE);
            } else {
                tvBookingType.setText(R.string.appointment_type_in_shop);
                tvBookingType.setVisibility(View.VISIBLE);
            }
            tvCustomer.setText(a.customerName);
            String st = a.serviceType != null ? a.serviceType.trim() : "";
            if (!st.isEmpty()) {
                tvServiceType.setText(getString(R.string.appointment_service_line, st));
                tvServiceType.setVisibility(View.VISIBLE);
            } else {
                tvServiceType.setVisibility(View.GONE);
            }
            tvTime.setText(sdf.format(new Date(a.timestamp)));

            if (a.homeService && a.homeServicePreferredMillis > 0) {
                SimpleDateFormat sdfVisit = new SimpleDateFormat("EEE, MMM d · h:mm a", Locale.getDefault());
                tvHomeVisit.setText(getString(R.string.appointment_preferred_visit,
                        sdfVisit.format(new Date(a.homeServicePreferredMillis))));
                tvHomeVisit.setVisibility(View.VISIBLE);
            } else {
                tvHomeVisit.setVisibility(View.GONE);
            }

            String statusLabel = a.status;
            if ("Cancelled".equals(a.status)) {
                statusLabel = getString(R.string.appointment_status_cancelled);
            }
            tvStatus.setText(statusLabel);

            if ("Done".equals(a.status)) {
                tvStatus.setTextColor(0xFF4CAF50);
                tvStatus.setBackgroundResource(R.drawable.bg_badge_active);
            } else if ("Cancelled".equals(a.status)) {
                tvStatus.setTextColor(0xFF9CA3AF);
                tvStatus.setBackgroundResource(R.drawable.bg_badge_inactive);
            } else {
                tvStatus.setTextColor(0xFFD4AF37);
                tvStatus.setBackgroundResource(R.drawable.bg_badge_inactive);
            }

            boolean nameMatch = !profileName.isEmpty()
                    && a.customerName != null
                    && a.customerName.trim().equalsIgnoreCase(profileName);
            boolean syncMatch = !syncId.isEmpty()
                    && syncId.equals(a.customerSyncId != null ? a.customerSyncId.trim() : "");
            boolean canCancel = "Waiting".equals(a.status) && (nameMatch || syncMatch);
            btnCancel.setVisibility(canCancel ? View.VISIBLE : View.GONE);
            if (canCancel) {
                btnCancel.setOnClickListener(v -> confirmCancelAppointment(a));
            }

            layoutAppointments.addView(card);
        }
    }

    private void confirmCancelAppointment(QueueManager.Appointment a) {
        CutifyDialogs.showActionConfirm(
                this,
                getString(R.string.dialog_badge_action_cancel),
                getString(R.string.appointment_cancel),
                getString(R.string.appointment_cancel_confirm),
                R.drawable.bg_btn_danger,
                () -> {
                    UserProfileManager upm = new UserProfileManager(AppointmentsActivity.this);
                    long ts = QueueManager.getInstance().cancelQueueEntry(
                            a.barberId, upm.getName() != null ? upm.getName().trim() : "",
                            a.timestamp,
                            upm.getGuestSyncId() != null ? upm.getGuestSyncId().trim() : "");
                    if (ts >= 0) {
                        BookingNotificationScheduler.cancelReminder(
                                this, ts, a.barberId, a.customerName);
                        Toast.makeText(this, R.string.queue_cancelled_toast, Toast.LENGTH_SHORT).show();
                        loadAppointments();
                    } else {
                        Toast.makeText(this, R.string.queue_cancel_failed, Toast.LENGTH_SHORT).show();
                    }
                }
        );
    }

    private void setupNav() {
        UserBottomNavHelper.bind(this, UserBottomNavHelper.TAB_APPOINTMENTS);
    }

    private void refreshFromFirestore() {
        loadAppointments();
        FeedbackHelper.checkAndShowFeedback(this);
    }
}
