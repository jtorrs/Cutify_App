package com.example.online_alot;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.util.Locale;

public class AdminStatsActivity extends AppCompatActivity {

    private String barberEmail;
    private TextView tvTotalHaircuts, tvRating, tvRatingCount;
    private TextView tvTodayHaircuts, tvTodayHome;
    private Handler refreshHandler;
    private Runnable refreshRunnable;
    private final Runnable firestoreStatsRefresh = this::loadStats;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_stats);

        barberEmail = getIntent().getStringExtra(AdminMainActivity.EXTRA_BARBER_EMAIL);
        if (barberEmail == null) {
            barberEmail = "";
        }

        tvTotalHaircuts = findViewById(R.id.tv_total);
        tvRating = findViewById(R.id.tv_rating);
        tvRatingCount = findViewById(R.id.tv_rating_count);
        tvTodayHaircuts = findViewById(R.id.tv_today_haircuts);
        tvTodayHome = findViewById(R.id.tv_today_home);

        setupNav();

        refreshHandler = new Handler(Looper.getMainLooper());
        refreshRunnable = () -> {
            loadStats();
            refreshHandler.postDelayed(refreshRunnable, 3000);
        };
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!isAdminSessionValid()) {
            return;
        }
        QueueManager.getInstance().addAppointmentSyncListener(firestoreStatsRefresh);
        loadStats();
        refreshHandler.postDelayed(refreshRunnable, 3000);
    }

    @Override
    protected void onPause() {
        QueueManager.getInstance().removeAppointmentSyncListener(firestoreStatsRefresh);
        super.onPause();
        refreshHandler.removeCallbacks(refreshRunnable);
    }

    private void loadStats() {
        QueueManager qm = QueueManager.getInstance();
        Barber barber = qm.getBarber(barberEmail);

        int haircuts = qm.getHaircutsForBarber(barberEmail);
        tvTotalHaircuts.setText(String.valueOf(haircuts));

        tvTodayHaircuts.setText(String.valueOf(qm.getHaircutsTodayForBarber(barberEmail)));
        tvTodayHome.setText(String.valueOf(qm.getHomeServiceBookingsTodayForBarber(barberEmail)));

        if (barber != null && barber.getRatingCount() > 0) {
            tvRating.setText(String.format(Locale.getDefault(), "%.1f", barber.getRating()));
            tvRatingCount.setText(String.format(getString(R.string.admin_rating_count), barber.getRatingCount()));
        } else {
            tvRating.setText("0.0");
            tvRatingCount.setText(R.string.admin_no_ratings);
        }
    }

    private void setupNav() {
        findViewById(R.id.nav_queue).setOnClickListener(v -> {
            Intent intent = new Intent(this, AdminMainActivity.class);
            intent.putExtra(AdminMainActivity.EXTRA_BARBER_EMAIL, barberEmail);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            finish();
        });
        findViewById(R.id.nav_profile).setOnClickListener(v -> {
            Intent intent = new Intent(this, AdminProfileActivity.class);
            intent.putExtra(AdminMainActivity.EXTRA_BARBER_EMAIL, barberEmail);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
        });
    }

    private boolean isAdminSessionValid() {
        SessionStore.Session session = SessionStore.read(this);
        if (session.type != SessionStore.Session.Type.ADMIN
                || session.email == null
                || !session.email.equalsIgnoreCase(barberEmail)) {
            android.widget.Toast.makeText(this, R.string.session_expired_sign_in_again, android.widget.Toast.LENGTH_LONG).show();
            SessionStore.clear(this);
            Intent i = new Intent(this, SignInActivity.class);
            i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(i);
            finish();
            return false;
        }
        return true;
    }
}
