package com.example.online_alot;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class HomeActivity extends AppCompatActivity {

    private LinearLayout layoutBarberList;
    private View loadingRow;
    private Button btnRetry;
    private EditText etSearch;
    private Handler refreshHandler;
    private Runnable refreshRunnable;
    private final Runnable firestoreBarbersRefresh = this::refreshFromFirestore;
    private boolean firstLoadDone = false;
    private Runnable searchDebounceRunnable;
    private String lastRenderSignature = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        etSearch = findViewById(R.id.et_search);

        layoutBarberList = findViewById(R.id.layout_barbers);
        loadingRow = findViewById(R.id.layout_home_loading);
        btnRetry = findViewById(R.id.btn_retry_home);
        refreshHandler = new Handler(Looper.getMainLooper());
        btnRetry.setOnClickListener(v -> {
            showLoadingState();
            loadBarbers();
        });

        setupNav();
        setupSearch();
        refreshRunnable = () -> {
            loadBarbers();
            refreshHandler.postDelayed(refreshRunnable, 3000);
        };
    }

    @Override
    protected void onResume() {
        super.onResume();
        QueueManager.getInstance().addAppointmentSyncListener(firestoreBarbersRefresh);
        FeedbackHelper.checkAndShowFeedback(this);
        showLoadingState();
        loadBarbers();
        refreshHandler.postDelayed(refreshRunnable, 3000);
    }

    @Override
    protected void onPause() {
        super.onPause();
        QueueManager.getInstance().removeAppointmentSyncListener(firestoreBarbersRefresh);
        refreshHandler.removeCallbacks(refreshRunnable);
    }

    private void setupSearch() {
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (searchDebounceRunnable != null) {
                    refreshHandler.removeCallbacks(searchDebounceRunnable);
                }
                searchDebounceRunnable = HomeActivity.this::loadBarbers;
                refreshHandler.postDelayed(searchDebounceRunnable, 250);
            }
            @Override public void afterTextChanged(Editable s) {}
        });
    }

    private void loadBarbers() {
        if (layoutBarberList == null) return;

        if (!NetworkStatus.isOnline(this)) {
            showMessageState(getString(R.string.user_no_internet_barbers), true);
            return;
        }

        QueueManager qm = QueueManager.getInstance();
        List<Barber> barbers = qm.getBarbers();
        String filter = etSearch.getText().toString().trim().toLowerCase();

        if (barbers.isEmpty()) {
            showMessageState(getString(R.string.no_barbers_available), false);
            return;
        }

        List<Barber> visible = new ArrayList<>();
        for (Barber b : barbers) {
            if (filter.isEmpty() || b.getDisplayName().toLowerCase().contains(filter)) {
                visible.add(b);
            }
        }
        if (visible.isEmpty()) {
            showMessageState(getString(R.string.no_barbers_available), false);
            return;
        }

        String signature = buildRenderSignature(visible, qm, filter);
        if (signature.equals(lastRenderSignature)) {
            hideLoadingAndRetry();
            return;
        }
        lastRenderSignature = signature;
        layoutBarberList.removeAllViews();

        float density = getResources().getDisplayMetrics().density;
        int gap = Math.round(8 * density);

        int idx = 0;
        while (idx < visible.size()) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setBaselineAligned(false);
            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            rowLp.bottomMargin = Math.round(10 * density);
            layoutBarberList.addView(row, rowLp);

            int inRow = Math.min(2, visible.size() - idx);
            for (int j = 0; j < inRow; j++) {
                Barber barber = visible.get(idx++);
                View card = LayoutInflater.from(this).inflate(R.layout.item_barber_book_portrait, row, false);
                bindInShopBarberPortraitCard(card, barber, qm);

                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
                if (inRow == 2) {
                    if (j == 0) {
                        lp.setMarginEnd(gap / 2);
                    } else {
                        lp.setMarginStart(gap / 2);
                    }
                }
                row.addView(card, lp);
            }
            if (inRow == 1) {
                View spacer = new View(this);
                row.addView(spacer, new LinearLayout.LayoutParams(0, 0, 1f));
            }
        }
        firstLoadDone = true;
        hideLoadingAndRetry();
    }

    private String buildRenderSignature(List<Barber> visible, QueueManager qm, String filter) {
        StringBuilder sb = new StringBuilder();
        sb.append(filter).append('|').append(visible.size()).append('|');
        for (Barber b : visible) {
            sb.append(b.getId()).append(':')
                    .append(b.getDisplayName()).append(':')
                    .append(b.getPhotoUrl()).append(':')
                    .append(b.getAvailabilityHours()).append(':')
                    .append(b.getAvailabilityDays()).append(':')
                    .append(b.getRating()).append(':')
                    .append(b.getRatingCount()).append(':')
                    .append(qm.isBarberOnDuty(b.getId())).append(':')
                    .append(qm.getQueueSize(b.getId())).append(';');
        }
        return sb.toString();
    }

    private void showLoadingState() {
        if (!firstLoadDone && loadingRow != null) {
            loadingRow.setVisibility(View.VISIBLE);
        }
        if (btnRetry != null) {
            btnRetry.setVisibility(View.GONE);
        }
    }

    private void hideLoadingAndRetry() {
        if (loadingRow != null) {
            loadingRow.setVisibility(View.GONE);
        }
        if (btnRetry != null) {
            btnRetry.setVisibility(View.GONE);
        }
    }

    private void showMessageState(String message, boolean showRetry) {
        firstLoadDone = true;
        lastRenderSignature = "state:" + message + ":" + showRetry;
        if (layoutBarberList != null) {
            layoutBarberList.removeAllViews();
        }
        if (loadingRow != null) {
            loadingRow.setVisibility(View.GONE);
        }
        if (btnRetry != null) {
            btnRetry.setVisibility(showRetry ? View.VISIBLE : View.GONE);
        }
        TextView text = new TextView(this);
        text.setText(message);
        text.setTextColor(ContextCompat.getColor(this, R.color.splash_subtext));
        text.setTextSize(14);
        layoutBarberList.addView(text);
    }

    private void bindInShopBarberPortraitCard(View card, Barber barber, QueueManager qm) {
        TextView tvName = card.findViewById(R.id.tv_barber_name);
        TextView tvActiveBadge = card.findViewById(R.id.tv_barber_active_badge);
        TextView tvTagline = card.findViewById(R.id.tv_barber_tagline);
        TextView tvAvailability = card.findViewById(R.id.tv_availability);
        ImageView ivPhoto = card.findViewById(R.id.iv_barber_photo);
        ImageButton btnViewQueue = card.findViewById(R.id.btn_view_queue);
        Button btnBook = card.findViewById(R.id.btn_book);

        String barberId = barber.getId();
        int qSize = qm.getQueueSize(barberId);
        btnViewQueue.setContentDescription(getString(R.string.queue_badge_content_desc, qSize));
        tvName.setText(barber.getDisplayName());
        View.OnClickListener openFeedback = v -> FeedbackHelper.showFeedbackForBarber(
                HomeActivity.this, barberId, barber.getDisplayName());
        tvName.setOnClickListener(openFeedback);
        ivPhoto.setOnClickListener(openFeedback);

        if (barber.getRatingCount() > 0) {
            tvTagline.setVisibility(View.VISIBLE);
            tvTagline.setText(String.format(Locale.getDefault(), "★ %.1f (%d)",
                    barber.getRating(), barber.getRatingCount()));
        } else {
            tvTagline.setText("");
            tvTagline.setVisibility(View.INVISIBLE);
        }

        boolean onDuty = qm.isBarberOnDuty(barber.getId());
        if (onDuty) {
            tvActiveBadge.setText(R.string.user_barber_badge_active_now);
            tvActiveBadge.setBackgroundResource(R.drawable.bg_badge_barber_active);
        } else {
            tvActiveBadge.setText(R.string.user_barber_badge_not_in_shop);
            tvActiveBadge.setBackgroundResource(R.drawable.bg_badge_barber_away);
        }

        tvAvailability.setText(onDuty
                ? resolveAvailabilityLine(barber)
                : getString(R.string.user_barber_inshop_off_duty_line));

        btnBook.setEnabled(onDuty);
        btnBook.setAlpha(onDuty ? 1f : 0.45f);

        BarberPhotoHelper.loadBarberPhoto(this, barber, ivPhoto);

        btnViewQueue.setOnClickListener(v -> {
            List<String> names = new ArrayList<>(qm.getQueue(barberId));
            QueueListDialog.show(HomeActivity.this, barber.getDisplayName(), names);
        });
        if (onDuty) {
            btnBook.setOnClickListener(v -> bookBarber(barberId, barber.getDisplayName()));
        } else {
            btnBook.setOnClickListener(null);
        }
    }

    private String resolveAvailabilityLine(Barber barber) {
        if (barber == null) {
            return getString(R.string.user_barber_hours_line);
        }
        String customHours = barber.getAvailabilityHours();
        String customDays = barber.getAvailabilityDays();
        if (customHours != null && !customHours.trim().isEmpty()) {
            if (customDays != null && !customDays.trim().isEmpty()) {
                return getString(R.string.user_barber_hours_days_line, customHours.trim(), customDays.trim());
            }
            return customHours.trim();
        }
        return getString(R.string.user_barber_hours_line);
    }

    private void applyBookingResult(long bookingTs) {
        if (bookingTs == QueueManager.BOOKING_DUPLICATE) {
            Toast.makeText(this, R.string.booking_duplicate_active, Toast.LENGTH_SHORT).show();
            return;
        }
        if (bookingTs == QueueManager.BOOKING_BARBER_INACTIVE) {
            Toast.makeText(this, R.string.booking_barber_not_active, Toast.LENGTH_SHORT).show();
            return;
        }
        if (bookingTs == QueueManager.BOOKING_EMPTY_NAME) {
            Toast.makeText(this, R.string.field_required, Toast.LENGTH_SHORT).show();
            return;
        }
        BookingNotificationScheduler.scheduleReminder(this, bookingTs);
        Toast.makeText(this, R.string.booking_success, Toast.LENGTH_SHORT).show();
        NotificationPermissionHelper.showAfterBookingSuccess(this, R.string.notif_inshop_book_success_body);
        loadBarbers();
    }

    private void bookBarber(String barberId, String barberName) {
        if (!NetworkStatus.isOnline(this)) {
            Toast.makeText(this, R.string.user_no_internet_booking, Toast.LENGTH_SHORT).show();
            return;
        }
        UserProfileManager upm = new UserProfileManager(this);
        String savedName = upm.getName() != null ? upm.getName().trim() : "";
        String savedService = upm.getServiceType() != null ? upm.getServiceType().trim() : "";
        String syncId = upm.getOrCreateGuestSyncId();

        CutifyDialogs.showBookAppointment(this, savedName, savedService, (fullName, serviceType) -> {
            upm.saveName(fullName);
            upm.saveServiceType(serviceType);
            long bookingTs = QueueManager.getInstance().bookAppointment(barberId, fullName, syncId, serviceType);
            applyBookingResult(bookingTs);
        });
    }

    private void setupNav() {
        UserBottomNavHelper.bind(this, UserBottomNavHelper.TAB_HOME);
    }

    private void refreshFromFirestore() {
        loadBarbers();
        FeedbackHelper.checkAndShowFeedback(this);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        if (NotificationPermissionHelper.onRequestPermissionsResult(requestCode, permissions, grantResults)) {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
            return;
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }
}
