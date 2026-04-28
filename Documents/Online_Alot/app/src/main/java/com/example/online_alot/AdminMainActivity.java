package com.example.online_alot;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.auth.FirebaseAuth;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class AdminMainActivity extends AppCompatActivity {

    private static final double HS_MAP_DELTA = 0.006;

    public static final String EXTRA_BARBER_EMAIL = "EXTRA_BARBER_EMAIL";
    private String barberEmail;
    private LinearLayout layoutQueue;
    private Handler refreshHandler;
    private Runnable refreshRunnable;
    private final Runnable firestoreQueueRefresh = this::loadQueue;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_main);

        barberEmail = getIntent().getStringExtra(EXTRA_BARBER_EMAIL);
        if (barberEmail == null) barberEmail = "";

        layoutQueue = findViewById(R.id.layout_queue);

        QueueManager.getInstance().setBarberOnDuty(barberEmail, true);

        AdminProfileManager apm = new AdminProfileManager(this, barberEmail);
        String savedName = apm.getName();
        if (!savedName.isEmpty()) {
            QueueManager.getInstance().updateBarberDisplayName(barberEmail, savedName);
        }

        findViewById(R.id.btn_sign_out).setOnClickListener(v -> confirmSignOut());
        findViewById(R.id.btn_add_walk_in).setOnClickListener(v -> showAddWalkInDialog());

        setupNav(barberEmail);

        refreshHandler = new Handler(Looper.getMainLooper());
        refreshRunnable = () -> {
            loadQueue();
            refreshHandler.postDelayed(refreshRunnable, 3000);
        };
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!isAdminSessionValid()) {
            return;
        }
        QueueManager.getInstance().addAppointmentSyncListener(firestoreQueueRefresh);
        loadQueue();
        refreshHandler.postDelayed(refreshRunnable, 3000);
    }

    private boolean isAdminSessionValid() {
        SessionStore.Session session = SessionStore.read(this);
        if (session.type != SessionStore.Session.Type.ADMIN
                || session.email == null
                || !session.email.equalsIgnoreCase(barberEmail)) {
            AppLogger.w("AdminMainActivity", "Session mismatch; forcing sign-in");
            forceBackToSignIn();
            return false;
        }
        return true;
    }

    private void forceBackToSignIn() {
        Toast.makeText(this, R.string.session_expired_sign_in_again, Toast.LENGTH_LONG).show();
        SessionStore.clear(this);
        FirebaseAuth.getInstance().signOut();
        Intent i = new Intent(this, SignInActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
        finish();
    }

    @Override
    protected void onPause() {
        super.onPause();
        QueueManager.getInstance().removeAppointmentSyncListener(firestoreQueueRefresh);
        refreshHandler.removeCallbacks(refreshRunnable);
    }

    private void updateStatusCard() {
        QueueManager qm = QueueManager.getInstance();
        Barber barber = qm.getBarber(barberEmail);

        TextView tvBarberName = findViewById(R.id.tv_barber_name);
        TextView tvStatus = findViewById(R.id.tv_status);
        TextView tvQueueCount = findViewById(R.id.tv_queue_count);

        if (barber != null) {
            tvBarberName.setText(barber.getDisplayName());
        } else {
            tvBarberName.setText(barberEmail);
        }

        View statusDot = findViewById(R.id.view_status_dot);
        boolean onDuty = qm.isBarberOnDuty(barberEmail);
        if (onDuty) {
            tvStatus.setText(R.string.status_active);
            tvStatus.setTextColor(ContextCompat.getColor(this, R.color.status_active));
            statusDot.setBackgroundResource(R.drawable.dot_status_active);
        } else {
            tvStatus.setText(R.string.status_not_active);
            tvStatus.setTextColor(ContextCompat.getColor(this, R.color.splash_subtext));
            statusDot.setBackgroundResource(R.drawable.dot_status_inactive);
        }

        int waiting = qm.getWaitingAppointmentsForBarber(barberEmail).size();
        tvQueueCount.setText(String.valueOf(waiting));
    }

    private void loadQueue() {
        layoutQueue.removeAllViews();
        updateStatusCard();
        QueueManager qm = QueueManager.getInstance();
        List<QueueManager.Appointment> waiting = qm.getWaitingAppointmentsForBarber(barberEmail);

        List<QueueManager.Appointment> inShop = new ArrayList<>();
        List<QueueManager.Appointment> homeSvc = new ArrayList<>();
        for (QueueManager.Appointment a : waiting) {
            if (a.homeService) {
                homeSvc.add(a);
            } else {
                inShop.add(a);
            }
        }

        if (inShop.isEmpty() && homeSvc.isEmpty()) {
            View empty = LayoutInflater.from(this).inflate(R.layout.layout_admin_empty_queue, layoutQueue, false);
            layoutQueue.addView(empty);
            return;
        }

        boolean firstSection = true;
        if (!inShop.isEmpty()) {
            addQueueSectionHeader(R.string.admin_queue_section_in_shop, firstSection);
            firstSection = false;
            int pos = 1;
            for (QueueManager.Appointment a : inShop) {
                addQueueRow(qm, a, pos++);
            }
        }
        if (!homeSvc.isEmpty()) {
            addQueueSectionHeader(R.string.admin_queue_section_home_service, firstSection);
            int pos = 1;
            for (QueueManager.Appointment a : homeSvc) {
                addQueueRow(qm, a, pos++);
            }
        }
    }

    private void addQueueSectionHeader(int titleRes, boolean isFirstSection) {
        TextView tv = new TextView(this);
        float d = getResources().getDisplayMetrics().density;
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = isFirstSection ? (int) (4 * d) : (int) (18 * d);
        lp.bottomMargin = (int) (6 * d);
        tv.setLayoutParams(lp);
        tv.setText(titleRes);
        tv.setTextColor(ContextCompat.getColor(this, R.color.splash_accent));
        tv.setTextSize(11);
        tv.setLetterSpacing(0.15f);
        tv.setGravity(Gravity.CENTER);
        layoutQueue.addView(tv);
    }

    private void addQueueRow(QueueManager qm, QueueManager.Appointment a, int position) {
        View card = LayoutInflater.from(this).inflate(R.layout.item_queue_card, layoutQueue, false);

        TextView tvPosition = card.findViewById(R.id.tv_position);
        TextView tvName = card.findViewById(R.id.tv_customer_name);
        TextView tvDetail = card.findViewById(R.id.tv_queue_detail);
        ImageButton btnViewHome = card.findViewById(R.id.btn_view_home_details);
        Button btnDone = card.findViewById(R.id.btn_done);
        Button btnCancel = card.findViewById(R.id.btn_cancel_queue);

        tvPosition.setText(String.valueOf(position));
        tvName.setText(a.customerName);

        String svc = a.serviceType != null ? a.serviceType.trim() : "";
        if (a.homeService) {
            btnViewHome.setVisibility(View.VISIBLE);
            btnViewHome.setOnClickListener(v -> showHomeServiceBookingDetail(a));
            tvDetail.setVisibility(View.VISIBLE);
            StringBuilder sb = new StringBuilder();
            if (!svc.isEmpty()) {
                sb.append(getString(R.string.appointment_service_line, svc));
            }
            if (a.homeServicePreferredMillis > 0) {
                SimpleDateFormat sdf = new SimpleDateFormat("EEE, MMM d · h:mm a", Locale.getDefault());
                if (sb.length() > 0) {
                    sb.append('\n');
                }
                sb.append(getString(R.string.admin_queue_visit_scheduled,
                        sdf.format(new Date(a.homeServicePreferredMillis))));
            } else {
                if (sb.length() > 0) {
                    sb.append('\n');
                }
                sb.append(getString(R.string.admin_queue_home_no_time));
            }
            tvDetail.setText(sb.toString().trim());
        } else {
            btnViewHome.setVisibility(View.GONE);
            if (!svc.isEmpty()) {
                tvDetail.setVisibility(View.VISIBLE);
                tvDetail.setText(getString(R.string.appointment_service_line, svc));
            } else {
                tvDetail.setVisibility(View.GONE);
            }
        }

        final long bookingTs = a.timestamp;
        final String customerName = a.customerName;

        boolean firstInLine = (position == 1);
        btnDone.setEnabled(firstInLine);
        btnCancel.setEnabled(firstInLine);
        float actionAlpha = firstInLine ? 1f : 0.42f;
        btnDone.setAlpha(actionAlpha);
        btnCancel.setAlpha(actionAlpha);

        if (firstInLine) {
            btnDone.setOnClickListener(v -> CutifyDialogs.showActionConfirm(
                    this,
                    getString(R.string.dialog_badge_action_done),
                    getString(R.string.admin_done_confirm_title),
                    getString(R.string.admin_done_confirm_message, customerName),
                    R.drawable.bg_btn_gold,
                    () -> {
                        qm.recordHaircutCompleted(barberEmail, customerName, bookingTs);
                        loadQueue();
                    }));

            btnCancel.setOnClickListener(v -> CutifyDialogs.showActionConfirm(
                    this,
                    getString(R.string.dialog_badge_action_cancel),
                    getString(R.string.admin_cancel_confirm_title),
                    getString(R.string.admin_cancel_confirm_message, customerName),
                    R.drawable.bg_btn_gold,
                    () -> {
                        long ts = qm.cancelQueueEntry(barberEmail, customerName, bookingTs);
                        if (ts >= 0) {
                            BookingNotificationScheduler.cancelReminder(this, ts, barberEmail, customerName);
                        }
                        loadQueue();
                    }));
        } else {
            btnDone.setOnClickListener(null);
            btnCancel.setOnClickListener(null);
        }

        layoutQueue.addView(card);
    }

    private void showHomeServiceBookingDetail(QueueManager.Appointment a) {
        if (!a.homeService) {
            return;
        }
        View root = LayoutInflater.from(this).inflate(R.layout.dialog_home_service_detail, null, false);
        TextView tvName = root.findViewById(R.id.tv_hs_detail_name);
        View layoutPhone = root.findViewById(R.id.layout_hs_phone_block);
        TextView tvPhone = root.findViewById(R.id.tv_hs_detail_phone);
        TextView tvBooked = root.findViewById(R.id.tv_hs_detail_booked);
        TextView tvVisit = root.findViewById(R.id.tv_hs_detail_visit);
        TextView tvAddr = root.findViewById(R.id.tv_hs_detail_address);
        TextView tvNoPin = root.findViewById(R.id.tv_hs_no_map_pin);
        FrameLayout frameMap = root.findViewById(R.id.frame_hs_map);
        WebView wv = root.findViewById(R.id.wv_hs_detail_map);
        MaterialButton btnMaps = root.findViewById(R.id.btn_open_in_maps);
        ImageButton btnClose = root.findViewById(R.id.btn_hs_detail_close);

        SimpleDateFormat sdfFull = new SimpleDateFormat("MMM d, yyyy · h:mm a", Locale.getDefault());
        tvName.setText(a.customerName);
        View layoutService = root.findViewById(R.id.layout_hs_service_block);
        TextView tvService = root.findViewById(R.id.tv_hs_detail_service);
        String reqService = a.serviceType != null ? a.serviceType.trim() : "";
        if (!reqService.isEmpty()) {
            layoutService.setVisibility(View.VISIBLE);
            tvService.setText(reqService);
        } else {
            layoutService.setVisibility(View.GONE);
        }
        String phone = a.homeServicePhone != null ? a.homeServicePhone.trim() : "";
        if (!phone.isEmpty()) {
            layoutPhone.setVisibility(View.VISIBLE);
            tvPhone.setText(phone);
        } else {
            layoutPhone.setVisibility(View.GONE);
        }
        tvBooked.setText(sdfFull.format(new Date(a.timestamp)));
        if (a.homeServicePreferredMillis > 0) {
            tvVisit.setText(sdfFull.format(new Date(a.homeServicePreferredMillis)));
        } else {
            tvVisit.setText(R.string.admin_home_detail_visit_unknown);
        }
        String addr = a.homeServiceAddress != null ? a.homeServiceAddress.trim() : "";
        if (!addr.isEmpty()) {
            tvAddr.setText(addr);
        } else {
            tvAddr.setText(R.string.admin_home_detail_no_address);
        }

        Runnable openMaps = () -> openHomeServiceInMaps(a);
        btnMaps.setOnClickListener(v -> openMaps.run());
        frameMap.setOnClickListener(v -> openMaps.run());

        if (coordsLookValid(a.homeServiceLat, a.homeServiceLon)) {
            tvNoPin.setVisibility(View.GONE);
            frameMap.setVisibility(View.VISIBLE);
            WebSettings s = wv.getSettings();
            s.setJavaScriptEnabled(true);
            s.setDomStorageEnabled(true);
            s.setLoadWithOverviewMode(true);
            s.setUseWideViewPort(true);
            wv.setWebChromeClient(new WebChromeClient());
            wv.setWebViewClient(new WebViewClient());
            double lat = a.homeServiceLat;
            double lon = a.homeServiceLon;
            double minLon = lon - HS_MAP_DELTA;
            double minLat = lat - HS_MAP_DELTA;
            double maxLon = lon + HS_MAP_DELTA;
            double maxLat = lat + HS_MAP_DELTA;
            String url = String.format(Locale.US,
                    "https://www.openstreetmap.org/export/embed.html?bbox=%f,%f,%f,%f&layer=mapnik&marker=%f,%f",
                    minLon, minLat, maxLon, maxLat, lat, lon);
            wv.loadUrl(url);
        } else {
            tvNoPin.setVisibility(View.VISIBLE);
            frameMap.setVisibility(View.GONE);
        }

        AlertDialog dlg = new MaterialAlertDialogBuilder(this)
                .setView(root)
                .create();
        btnClose.setOnClickListener(v -> dlg.dismiss());
        dlg.setOnDismissListener(d -> {
            wv.stopLoading();
            wv.loadUrl("about:blank");
            ViewGroup parent = (ViewGroup) wv.getParent();
            if (parent != null) {
                parent.removeView(wv);
            }
            wv.destroy();
        });
        dlg.show();
        if (dlg.getWindow() != null) {
            dlg.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
    }

    private void openHomeServiceInMaps(QueueManager.Appointment a) {
        String addr = a.homeServiceAddress != null ? a.homeServiceAddress.trim() : "";
        // Prefer actual lat/lon (Firestore used to omit them; flag alone is unreliable).
        boolean hasCoords = coordsLookValid(a.homeServiceLat, a.homeServiceLon);

        Uri geoUri;
        Uri httpsUri;
        if (hasCoords) {
            double la = a.homeServiceLat;
            double lo = a.homeServiceLon;
            String q = la + "," + lo;
            geoUri = Uri.parse("geo:" + la + "," + lo + "?q=" + Uri.encode(q));
            httpsUri = Uri.parse("https://www.google.com/maps/search/?api=1&query=" + Uri.encode(q));
        } else if (!addr.isEmpty()) {
            geoUri = Uri.parse("geo:0,0?q=" + Uri.encode(addr));
            httpsUri = Uri.parse("https://www.google.com/maps/search/?api=1&query=" + Uri.encode(addr));
        } else {
            Toast.makeText(this, R.string.admin_home_detail_no_location, Toast.LENGTH_SHORT).show();
            return;
        }

        PackageManager pm = getPackageManager();

        Intent geo = new Intent(Intent.ACTION_VIEW, geoUri);
        if (geo.resolveActivity(pm) != null) {
            try {
                startActivity(geo);
                return;
            } catch (Exception ignored) {
            }
        }

        Intent web = new Intent(Intent.ACTION_VIEW, httpsUri);
        web.addCategory(Intent.CATEGORY_BROWSABLE);
        try {
            if (web.resolveActivity(pm) != null) {
                startActivity(web);
                return;
            }
        } catch (Exception ignored) {
        }

        try {
            startActivity(Intent.createChooser(web, getString(R.string.admin_open_maps_chooser)));
        } catch (Exception e) {
            Toast.makeText(this, R.string.admin_maps_open_failed, Toast.LENGTH_LONG).show();
        }
    }

    private static boolean coordsLookValid(double lat, double lon) {
        if (Double.isNaN(lat) || Double.isNaN(lon)) {
            return false;
        }
        if (Math.abs(lat) > 90 || Math.abs(lon) > 180) {
            return false;
        }
        // Treat unset 0,0 as invalid for home visits (unlikely real customer home)
        return lat != 0 || lon != 0;
    }

    private void showAddWalkInDialog() {
        View root = LayoutInflater.from(this).inflate(R.layout.dialog_admin_walk_in, null, false);
        EditText input = root.findViewById(R.id.et_walk_in_name);

        AlertDialog dlg = new MaterialAlertDialogBuilder(this)
                .setView(root)
                .create();
        Runnable tryAdd = () -> {
            String name = input.getText().toString().trim();
            if (name.isEmpty()) {
                Toast.makeText(this, R.string.field_required, Toast.LENGTH_SHORT).show();
                return;
            }
            long ts = QueueManager.getInstance().addWalkInForBarber(barberEmail, name);
            if (ts == QueueManager.BOOKING_DUPLICATE) {
                Toast.makeText(this, R.string.booking_duplicate_active, Toast.LENGTH_LONG).show();
                return;
            }
            if (ts == QueueManager.BOOKING_EMPTY_NAME) {
                Toast.makeText(this, R.string.field_required, Toast.LENGTH_SHORT).show();
                return;
            }
            Toast.makeText(this, getString(R.string.admin_walk_in_added, name), Toast.LENGTH_SHORT).show();
            dlg.dismiss();
            loadQueue();
        };
        root.findViewById(R.id.btn_walk_in_close).setOnClickListener(v -> dlg.dismiss());
        root.findViewById(R.id.btn_walk_in_cancel).setOnClickListener(v -> dlg.dismiss());
        root.findViewById(R.id.btn_walk_in_add).setOnClickListener(v -> tryAdd.run());
        dlg.show();
        if (dlg.getWindow() != null) {
            dlg.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
    }

    private void confirmSignOut() {
        CutifyDialogs.showSignOutConfirm(this, () -> {
            QueueManager.getInstance().setBarberOnDuty(barberEmail, false);
            RecaptchaGate.clearStaffRecaptcha(this);
            SessionStore.clear(this);
            FirebaseAuth.getInstance().signOut();
            Intent intent = new Intent(this, SignInActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });
    }

    private void setupNav(String email) {
        findViewById(R.id.nav_stats).setOnClickListener(v -> {
            Intent intent = new Intent(this, AdminStatsActivity.class);
            intent.putExtra(EXTRA_BARBER_EMAIL, email);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
        });
        findViewById(R.id.nav_profile).setOnClickListener(v -> {
            Intent intent = new Intent(this, AdminProfileActivity.class);
            intent.putExtra(EXTRA_BARBER_EMAIL, email);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
        });
    }
}
