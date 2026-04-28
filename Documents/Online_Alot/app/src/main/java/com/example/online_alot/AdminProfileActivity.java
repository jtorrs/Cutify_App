package com.example.online_alot;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.timepicker.MaterialTimePicker;
import com.google.android.material.timepicker.TimeFormat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class AdminProfileActivity extends AppCompatActivity {

    private String barberEmail;
    private EditText etName, etAddress, etAvailabilityHours;
    private ImageView ivPhoto;
    private TextView tvProfileEmail, tvTotalCuts, tvRating, tvUploadProgress, tvAvailabilitySummary;
    private View layoutUploadProgress;
    private ProgressBar progressUpload;
    private AdminProfileManager apm;
    private ActivityResultLauncher<String> photoPickerLauncher;
    private Handler refreshHandler;
    private Runnable refreshRunnable;
    private final Map<String, Chip> dayChips = new LinkedHashMap<>();
    private boolean uploadInProgress = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_profile);

        barberEmail = getIntent().getStringExtra(AdminMainActivity.EXTRA_BARBER_EMAIL);
        if (barberEmail == null) barberEmail = "";

        apm = new AdminProfileManager(this, barberEmail);

        etName = findViewById(R.id.et_name);
        etAddress = findViewById(R.id.et_address);
        etAvailabilityHours = findViewById(R.id.et_availability_hours);
        ivPhoto = findViewById(R.id.iv_profile_photo);
        tvProfileEmail = findViewById(R.id.tv_profile_email);
        tvTotalCuts = findViewById(R.id.tv_total_cuts);
        tvRating = findViewById(R.id.tv_rating);
        tvUploadProgress = findViewById(R.id.tv_upload_progress);
        tvAvailabilitySummary = findViewById(R.id.tv_availability_summary);
        layoutUploadProgress = findViewById(R.id.layout_upload_progress);
        progressUpload = findViewById(R.id.progress_upload);
        Button btnSave = findViewById(R.id.btn_save);
        ChipGroup dayGroup = findViewById(R.id.chip_group_days);
        dayGroup.clearCheck();
        bindDayChips();
        etAvailabilityHours.setOnClickListener(v -> openStartTimePicker());

        photoPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null) {
                        try {
                            getContentResolver().takePersistableUriPermission(uri,
                                    Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        } catch (SecurityException ignored) {}
                        BarberProfilePhotoUpload.uploadAndApply(
                                this,
                                barberEmail,
                                uri,
                                ivPhoto,
                                null,
                                new BarberProfilePhotoUpload.UploadCallbacks() {
                                    @Override
                                    public void onStarted() {
                                        setUploadInProgress(true);
                                        setUploadProgress(0);
                                    }

                                    @Override
                                    public void onProgress(int percent) {
                                        setUploadProgress(percent);
                                    }

                                    @Override
                                    public void onSuccess(String httpsUrl) {
                                        setUploadInProgress(false);
                                    }

                                    @Override
                                    public void onError(String message) {
                                        setUploadInProgress(false);
                                    }
                                }
                        );
                    }
                }
        );

        findViewById(R.id.layout_photo).setOnClickListener(v ->
                photoPickerLauncher.launch("image/*"));

        btnSave.setOnClickListener(v -> saveProfile());

        setupNav();
        tvProfileEmail.setText(barberEmail);
        loadProfile();

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
        loadStats();
        refreshHandler.postDelayed(refreshRunnable, 3000);
    }

    @Override
    protected void onPause() {
        super.onPause();
        refreshHandler.removeCallbacks(refreshRunnable);
    }

    private void loadProfile() {
        etName.setText(apm.getName());
        etAddress.setText(apm.getAddress());
        etAvailabilityHours.setText(apm.getAvailabilityHours());
        applySavedDays(apm.getAvailabilityDays());
        updateAvailabilitySummary();

        String photoUri = apm.getPhotoUri();
        if (photoUri != null && !photoUri.isEmpty()) {
            if (photoUri.startsWith("http://") || photoUri.startsWith("https://")) {
                Glide.with(this).load(photoUri).centerCrop().into(ivPhoto);
            } else {
                try {
                    ivPhoto.setImageURI(Uri.parse(photoUri));
                } catch (Exception ignored) {}
            }
        }
    }

    private void loadStats() {
        QueueManager qm = QueueManager.getInstance();
        Barber barber = qm.getBarber(barberEmail);

        int haircuts = qm.getHaircutsForBarber(barberEmail);
        tvTotalCuts.setText(String.valueOf(haircuts));

        if (barber != null && barber.getRatingCount() > 0) {
            tvRating.setText(String.format("★ %.1f", barber.getRating()));
        } else {
            tvRating.setText("—");
        }
    }

    private void saveProfile() {
        if (uploadInProgress) {
            Toast.makeText(this, R.string.photo_upload_wait_finish, Toast.LENGTH_SHORT).show();
            return;
        }
        String name = etName.getText().toString().trim();
        String address = etAddress.getText().toString().trim();
        String availabilityHours = etAvailabilityHours.getText().toString().trim();
        String availabilityDays = buildSelectedDaysCsv();

        if (!availabilityDays.isEmpty() && availabilityHours.isEmpty()) {
            Toast.makeText(this, R.string.admin_availability_invalid_hours, Toast.LENGTH_LONG).show();
            return;
        }
        if (!availabilityHours.isEmpty() && availabilityDays.isEmpty()) {
            Toast.makeText(this, R.string.admin_availability_invalid_days, Toast.LENGTH_LONG).show();
            return;
        }

        apm.saveProfile(name, address);
        apm.saveAvailabilityHours(availabilityHours);
        apm.saveAvailabilityDays(availabilityDays);

        if (!name.isEmpty()) {
            QueueManager.getInstance().updateBarberDisplayName(barberEmail, name);
        }
        QueueManager.getInstance().updateBarberAvailability(barberEmail, availabilityHours, availabilityDays);
        BarberFirestoreSync.mergeProfile(barberEmail, name, availabilityHours, availabilityDays);

        Toast.makeText(this, R.string.profile_saved, Toast.LENGTH_SHORT).show();
    }

    private void setUploadInProgress(boolean inProgress) {
        uploadInProgress = inProgress;
        layoutUploadProgress.setVisibility(inProgress ? View.VISIBLE : View.GONE);
        if (!inProgress) {
            progressUpload.setProgress(0);
            tvUploadProgress.setText(R.string.photo_upload_progress_idle);
        }
        findViewById(R.id.layout_photo).setEnabled(!inProgress);
        findViewById(R.id.btn_save).setEnabled(!inProgress);
        etName.setEnabled(!inProgress);
        etAddress.setEnabled(!inProgress);
        etAvailabilityHours.setEnabled(!inProgress);
        for (Chip chip : dayChips.values()) {
            chip.setEnabled(!inProgress);
        }
    }

    private void setUploadProgress(int percent) {
        int safe = Math.max(0, Math.min(100, percent));
        progressUpload.setProgress(safe);
        tvUploadProgress.setText(getString(R.string.photo_upload_progress_format, safe));
    }

    private void bindDayChips() {
        dayChips.clear();
        dayChips.put("Mon", findViewById(R.id.chip_day_mon));
        dayChips.put("Tue", findViewById(R.id.chip_day_tue));
        dayChips.put("Wed", findViewById(R.id.chip_day_wed));
        dayChips.put("Thu", findViewById(R.id.chip_day_thu));
        dayChips.put("Fri", findViewById(R.id.chip_day_fri));
        dayChips.put("Sat", findViewById(R.id.chip_day_sat));
        dayChips.put("Sun", findViewById(R.id.chip_day_sun));
        for (Chip chip : dayChips.values()) {
            chip.setOnCheckedChangeListener((buttonView, isChecked) -> updateAvailabilitySummary());
        }
    }

    private void applySavedDays(String csv) {
        List<String> values = parseDays(csv);
        for (Map.Entry<String, Chip> e : dayChips.entrySet()) {
            e.getValue().setChecked(values.contains(e.getKey()));
        }
    }

    private List<String> parseDays(String csv) {
        if (TextUtils.isEmpty(csv)) {
            return new ArrayList<>();
        }
        List<String> out = new ArrayList<>();
        for (String raw : Arrays.asList(csv.split(","))) {
            String t = raw.trim();
            if (dayChips.containsKey(t) && !out.contains(t)) {
                out.add(t);
            }
        }
        return out;
    }

    private String buildSelectedDaysCsv() {
        List<String> picked = new ArrayList<>();
        for (Map.Entry<String, Chip> e : dayChips.entrySet()) {
            if (e.getValue().isChecked()) {
                picked.add(e.getKey());
            }
        }
        return TextUtils.join(", ", picked);
    }

    private void openStartTimePicker() {
        int[] start = nowHourMinute();
        MaterialTimePicker picker = new MaterialTimePicker.Builder()
                .setTimeFormat(DateFormat.is24HourFormat(this) ? TimeFormat.CLOCK_24H : TimeFormat.CLOCK_12H)
                .setHour(start[0])
                .setMinute(start[1])
                .setTitleText(R.string.admin_availability_pick_start)
                .build();
        picker.addOnPositiveButtonClickListener(v -> openEndTimePicker(picker.getHour(), picker.getMinute()));
        picker.show(getSupportFragmentManager(), "startTimePicker");
    }

    private void openEndTimePicker(int startHour, int startMinute) {
        MaterialTimePicker picker = new MaterialTimePicker.Builder()
                .setTimeFormat(DateFormat.is24HourFormat(this) ? TimeFormat.CLOCK_24H : TimeFormat.CLOCK_12H)
                .setHour(startHour)
                .setMinute(startMinute)
                .setTitleText(R.string.admin_availability_pick_end)
                .build();
        picker.addOnPositiveButtonClickListener(v -> {
            int endHour = picker.getHour();
            int endMinute = picker.getMinute();
            String range = formatTime(startHour, startMinute) + " - " + formatTime(endHour, endMinute);
            etAvailabilityHours.setText(range);
            updateAvailabilitySummary();
        });
        picker.show(getSupportFragmentManager(), "endTimePicker");
    }

    private void updateAvailabilitySummary() {
        String availabilityHours = etAvailabilityHours.getText().toString().trim();
        String availabilityDays = buildSelectedDaysCsv();
        if (TextUtils.isEmpty(availabilityHours) || TextUtils.isEmpty(availabilityDays)) {
            tvAvailabilitySummary.setText(R.string.admin_availability_summary_empty);
            return;
        }
        tvAvailabilitySummary.setText(
                getString(R.string.admin_availability_summary_format, availabilityDays, availabilityHours)
        );
    }

    private int[] nowHourMinute() {
        Calendar c = Calendar.getInstance();
        return new int[]{c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE)};
    }

    private String formatTime(int hour24, int minute) {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, hour24);
        c.set(Calendar.MINUTE, minute);
        return new java.text.SimpleDateFormat("h:mm a", Locale.getDefault()).format(c.getTime());
    }

    private boolean isAdminSessionValid() {
        SessionStore.Session session = SessionStore.read(this);
        if (session.type != SessionStore.Session.Type.ADMIN
                || session.email == null
                || !session.email.equalsIgnoreCase(barberEmail)) {
            Toast.makeText(this, R.string.session_expired_sign_in_again, Toast.LENGTH_LONG).show();
            SessionStore.clear(this);
            Intent i = new Intent(this, SignInActivity.class);
            i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(i);
            finish();
            return false;
        }
        return true;
    }

    private void setupNav() {
        findViewById(R.id.nav_queue).setOnClickListener(v -> {
            Intent intent = new Intent(this, AdminMainActivity.class);
            intent.putExtra(AdminMainActivity.EXTRA_BARBER_EMAIL, barberEmail);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            finish();
        });
        findViewById(R.id.nav_stats).setOnClickListener(v -> {
            Intent intent = new Intent(this, AdminStatsActivity.class);
            intent.putExtra(AdminMainActivity.EXTRA_BARBER_EMAIL, barberEmail);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
        });
    }
}
