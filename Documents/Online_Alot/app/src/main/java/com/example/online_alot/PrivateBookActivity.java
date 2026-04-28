package com.example.online_alot;

import android.Manifest;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.tasks.CancellationTokenSource;
import com.google.android.material.button.MaterialButton;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class PrivateBookActivity extends AppCompatActivity {

    private static final int REQ_LOCATION = 4101;
    /** Default map center (Cebu PH) when GPS not available yet */
    private static final double DEFAULT_LAT = 10.3157;
    private static final double DEFAULT_LON = 123.8854;
    /** Tighter bbox (~block scale) for a clearer map around the pin */
    private static final double MAP_DELTA = 0.006;

    private UserProfileManager upm;
    private FusedLocationProviderClient fused;
    private EditText etName, etService, etAge, etPhone, etAddress;
    private TextView tvLocationStatus;
    private TextView tvHomePinStatus;
    private TextView tvPreferredTime;
    private MaterialButton btnPickDatetime;
    private MaterialButton btnSaveMapLocation;
    /** Epoch millis for when the customer wants the home visit; 0 = not chosen */
    private long homeServicePreferredAt;
    /** After user taps "Save map location", show a toast when GPS is applied (or clear on failure). */
    private boolean notifyOnNextApplyLocation;
    private Button btnTurnOnLocation, btnOpenSettings;
    private WebView wvMap;
    private LinearLayout layoutBarberList;
    /** True after user denied location (avoid showing settings before first prompt completes). */
    private boolean locationDeniedByUser;
    private final Runnable firestoreBarbersRefresh = this::refreshFromFirestore;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_private_book);

        fused = LocationServices.getFusedLocationProviderClient(this);
        upm = new UserProfileManager(this);
        etName = findViewById(R.id.et_hs_name);
        etService = findViewById(R.id.et_hs_service);
        etAge = findViewById(R.id.et_hs_age);
        etPhone = findViewById(R.id.et_hs_phone);
        etAddress = findViewById(R.id.et_hs_address);
        tvPreferredTime = findViewById(R.id.tv_hs_preferred_time);
        btnPickDatetime = findViewById(R.id.btn_hs_pick_datetime);
        tvLocationStatus = findViewById(R.id.tv_hs_location_status);
        tvHomePinStatus = findViewById(R.id.tv_home_pin_status);
        btnTurnOnLocation = findViewById(R.id.btn_turn_on_location);
        btnOpenSettings = findViewById(R.id.btn_open_location_settings);
        wvMap = findViewById(R.id.wv_map);
        layoutBarberList = findViewById(R.id.layout_barbers);
        btnSaveMapLocation = findViewById(R.id.btn_save_map_location);

        UserBottomNavHelper.bind(this, UserBottomNavHelper.TAB_HOME_SERVICE);

        setupWebView();
        loadFormFromPrefs();
        showMapForSavedOrDefault();
        updateHomePinStatus();

        btnPickDatetime.setOnClickListener(v -> showPreferredDateTimePicker());
        btnSaveMapLocation.setOnClickListener(v -> saveMapLocationForBooking());

        btnTurnOnLocation.setOnClickListener(v -> tryEnableLocation());
        bindAppSettingsButton();

        loadBarbers();

        if (hasFineLocationPermission()) {
            fetchAccurateLocation();
        } else {
            ActivityCompat.requestPermissions(this, new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            }, REQ_LOCATION);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        QueueManager.getInstance().addAppointmentSyncListener(firestoreBarbersRefresh);
        updateHomePinStatus();
        if (hasFineLocationPermission()) {
            fetchAccurateLocation();
        } else if (locationDeniedByUser) {
            tvLocationStatus.setText(R.string.home_service_location_need_permission);
            btnOpenSettings.setVisibility(View.VISIBLE);
            bindAppSettingsButton();
        }
    }

    private void bindAppSettingsButton() {
        btnOpenSettings.setText(R.string.home_service_open_settings);
        btnOpenSettings.setOnClickListener(v -> {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(Uri.fromParts("package", getPackageName(), null));
            startActivity(intent);
        });
    }

    private void bindDeviceLocationButton() {
        btnOpenSettings.setText(R.string.home_service_open_device_location);
        btnOpenSettings.setOnClickListener(v ->
                startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)));
    }

    private boolean hasFineLocationPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    private boolean isDeviceLocationEnabled() {
        LocationManager lm = (LocationManager) getSystemService(LOCATION_SERVICE);
        if (lm == null) {
            return false;
        }
        return lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
                || lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
    }

    private void setupWebView() {
        WebSettings s = wvMap.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        wvMap.setWebChromeClient(new WebChromeClient());
        wvMap.setWebViewClient(new WebViewClient());
    }

    private void loadFormFromPrefs() {
        if (!upm.getName().isEmpty()) {
            etName.setText(upm.getName());
        }
        if (!upm.getAge().isEmpty()) {
            etAge.setText(upm.getAge());
        }
        if (!upm.getPhone().isEmpty()) {
            etPhone.setText(upm.getPhone());
        }
        if (!upm.getServiceType().isEmpty()) {
            etService.setText(upm.getServiceType());
        }
        String addr = upm.getAddress();
        if (!addr.isEmpty()) {
            etAddress.setText(addr);
        }
        homeServicePreferredAt = upm.getHomeServicePreferredAt();
        updatePreferredTimeLabel();
        restoreHomePinFromSavedLocationIfPossible();
    }

    /** If we already have a saved GPS fix, use it as the booking pin (e.g. returning user). */
    private void restoreHomePinFromSavedLocationIfPossible() {
        if (!hasFineLocationPermission()) {
            return;
        }
        double lat = upm.getLastLat();
        double lon = upm.getLastLon();
        if (coordsLookValid(lat, lon)) {
            upm.saveHomeServiceBookingPin(lat, lon);
        }
    }

    private void updateHomePinStatus() {
        if (tvHomePinStatus == null) {
            return;
        }
        boolean pinOk = upm.hasHomeServiceBookingPin()
                && coordsLookValid(upm.getHomeServiceBookingPinLat(), upm.getHomeServiceBookingPinLon());
        if (pinOk) {
            tvHomePinStatus.setText(R.string.home_service_pin_ok);
            tvHomePinStatus.setTextColor(ContextCompat.getColor(this, R.color.status_active));
        } else {
            tvHomePinStatus.setText(R.string.home_service_pin_need_gps);
            tvHomePinStatus.setTextColor(ContextCompat.getColor(this, R.color.splash_subtext));
        }
    }

    private void saveFormToPrefs() {
        upm.saveName(etName.getText().toString().trim());
        upm.saveServiceType(etService.getText().toString().trim());
        upm.saveAge(etAge.getText().toString().trim());
        upm.savePhone(etPhone.getText().toString().trim());
        upm.saveAddress(etAddress.getText().toString().trim());
        upm.saveHomeServicePreferredAt(homeServicePreferredAt);
        CustomerProfileFirestoreSync.requestUpsert(this);
    }

    private void updatePreferredTimeLabel() {
        if (homeServicePreferredAt <= 0) {
            tvPreferredTime.setText(R.string.home_service_preferred_time_none);
            return;
        }
        SimpleDateFormat sdf = new SimpleDateFormat("EEE, MMM d, yyyy · h:mm a", Locale.getDefault());
        tvPreferredTime.setText(sdf.format(new Date(homeServicePreferredAt)));
    }

    private void showPreferredDateTimePicker() {
        Calendar cal = Calendar.getInstance();
        if (homeServicePreferredAt > 0) {
            cal.setTimeInMillis(homeServicePreferredAt);
        }
        new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
            final Calendar picked = Calendar.getInstance();
            picked.set(Calendar.YEAR, year);
            picked.set(Calendar.MONTH, month);
            picked.set(Calendar.DAY_OF_MONTH, dayOfMonth);
            new TimePickerDialog(this, (tv, hourOfDay, minute) -> {
                picked.set(Calendar.HOUR_OF_DAY, hourOfDay);
                picked.set(Calendar.MINUTE, minute);
                picked.set(Calendar.SECOND, 0);
                picked.set(Calendar.MILLISECOND, 0);
                homeServicePreferredAt = picked.getTimeInMillis();
                updatePreferredTimeLabel();
                upm.saveHomeServicePreferredAt(homeServicePreferredAt);
                CustomerProfileFirestoreSync.requestUpsert(PrivateBookActivity.this);
            }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), false).show();
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();
    }

    @Override
    protected void onPause() {
        QueueManager.getInstance().removeAppointmentSyncListener(firestoreBarbersRefresh);
        super.onPause();
        saveFormToPrefs();
    }

    private void tryEnableLocation() {
        if (hasFineLocationPermission()) {
            btnOpenSettings.setVisibility(View.GONE);
            fetchAccurateLocation();
            return;
        }
        ActivityCompat.requestPermissions(this, new String[]{
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
        }, REQ_LOCATION);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        if (NotificationPermissionHelper.onRequestPermissionsResult(requestCode, permissions, grantResults)) {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
            return;
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQ_LOCATION) {
            return;
        }
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            locationDeniedByUser = false;
            btnOpenSettings.setVisibility(View.GONE);
            fetchAccurateLocation();
        } else {
            locationDeniedByUser = true;
            if (notifyOnNextApplyLocation) {
                notifyOnNextApplyLocation = false;
                Toast.makeText(this, R.string.home_service_save_map_need_permission, Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(this, R.string.home_service_location_need_permission, Toast.LENGTH_LONG).show();
            }
            tvLocationStatus.setText(R.string.home_service_location_need_permission);
            btnOpenSettings.setVisibility(View.VISIBLE);
            bindAppSettingsButton();
        }
    }

    private void fetchAccurateLocation() {
        if (!hasFineLocationPermission()) {
            return;
        }
        if (!isDeviceLocationEnabled()) {
            tvLocationStatus.setText(R.string.home_service_location_gps_off);
            btnOpenSettings.setVisibility(View.VISIBLE);
            bindDeviceLocationButton();
            showMapForSavedOrDefault();
            return;
        }

        btnOpenSettings.setVisibility(View.GONE);
        tvLocationStatus.setText(R.string.home_service_location_getting);

        CancellationTokenSource cts = new CancellationTokenSource();
        fused.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.getToken())
                .addOnSuccessListener(this, loc -> {
                    if (isFinishing()) {
                        return;
                    }
                    if (loc != null) {
                        applyLocation(loc);
                    } else {
                        tryFusedLastLocation();
                    }
                })
                .addOnFailureListener(this, e -> tryFusedLastLocation());
    }

    private void tryFusedLastLocation() {
        if (!hasFineLocationPermission()) {
            return;
        }
        fused.getLastLocation()
                .addOnSuccessListener(this, loc -> {
                    if (isFinishing()) {
                        return;
                    }
                    if (loc != null) {
                        applyLocation(loc);
                    } else {
                        readLegacyLastKnownLocation();
                    }
                })
                .addOnFailureListener(this, e -> readLegacyLastKnownLocation());
    }

    private void readLegacyLastKnownLocation() {
        try {
            LocationManager lm = (LocationManager) getSystemService(LOCATION_SERVICE);
            if (lm == null || !hasFineLocationPermission()) {
                onLocationReadFailed();
                return;
            }
            Location loc = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if (loc == null) {
                loc = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            }
            if (loc != null) {
                applyLocation(loc);
            } else {
                onLocationReadFailed();
            }
        } catch (Exception e) {
            onLocationReadFailed();
        }
    }

    private void applyLocation(Location loc) {
        double la = loc.getLatitude();
        double lo = loc.getLongitude();
        if (!coordsLookValid(la, lo)) {
            onLocationReadFailed();
            return;
        }
        upm.saveLastKnownLocation(la, lo);
        upm.saveHomeServiceBookingPin(la, lo);
        CustomerProfileFirestoreSync.requestUpsert(this);
        loadMap(la, lo);
        tvLocationStatus.setText(R.string.home_service_location_ok);
        btnOpenSettings.setVisibility(View.GONE);
        updateHomePinStatus();
        if (notifyOnNextApplyLocation) {
            notifyOnNextApplyLocation = false;
            Toast.makeText(this, R.string.home_service_save_map_location_ok, Toast.LENGTH_SHORT).show();
        }
    }

    private void onLocationReadFailed() {
        if (notifyOnNextApplyLocation) {
            notifyOnNextApplyLocation = false;
            Toast.makeText(this, R.string.home_service_save_map_location_failed, Toast.LENGTH_LONG).show();
        }
        tvLocationStatus.setText(R.string.home_service_location_unavailable);
        showMapForSavedOrDefault();
    }

    /** Same rules as admin maps: reject NaN, out-of-range, and 0,0. */
    private static boolean coordsLookValid(double lat, double lon) {
        if (Double.isNaN(lat) || Double.isNaN(lon)) {
            return false;
        }
        if (Math.abs(lat) > 90 || Math.abs(lon) > 180) {
            return false;
        }
        return lat != 0 || lon != 0;
    }

    private void saveMapLocationForBooking() {
        if (!hasFineLocationPermission()) {
            Toast.makeText(this, R.string.home_service_save_map_need_permission, Toast.LENGTH_LONG).show();
            ActivityCompat.requestPermissions(this, new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            }, REQ_LOCATION);
            return;
        }
        if (!isDeviceLocationEnabled()) {
            Toast.makeText(this, R.string.home_service_location_gps_off, Toast.LENGTH_LONG).show();
            tvLocationStatus.setText(R.string.home_service_location_gps_off);
            btnOpenSettings.setVisibility(View.VISIBLE);
            bindDeviceLocationButton();
            showMapForSavedOrDefault();
            return;
        }
        notifyOnNextApplyLocation = true;
        Toast.makeText(this, R.string.home_service_save_map_location_saving, Toast.LENGTH_SHORT).show();
        fetchAccurateLocation();
    }

    private void showMapForSavedOrDefault() {
        double lat = upm.getLastLat();
        double lon = upm.getLastLon();
        if (Double.isNaN(lat) || Double.isNaN(lon)) {
            loadMap(DEFAULT_LAT, DEFAULT_LON);
        } else {
            loadMap(lat, lon);
        }
    }

    private void loadMap(double lat, double lon) {
        double minLon = lon - MAP_DELTA;
        double minLat = lat - MAP_DELTA;
        double maxLon = lon + MAP_DELTA;
        double maxLat = lat + MAP_DELTA;
        String url = String.format(Locale.US,
                "https://www.openstreetmap.org/export/embed.html?bbox=%f,%f,%f,%f&layer=mapnik&marker=%f,%f",
                minLon, minLat, maxLon, maxLat, lat, lon);
        wvMap.loadUrl(url);
    }

    private void loadBarbers() {
        layoutBarberList.removeAllViews();
        if (!NetworkStatus.isOnline(this)) {
            TextView offline = new TextView(this);
            offline.setText(R.string.user_no_internet_barbers);
            offline.setTextColor(ContextCompat.getColor(this, R.color.splash_subtext));
            offline.setTextSize(14);
            layoutBarberList.addView(offline);
            return;
        }
        QueueManager qm = QueueManager.getInstance();
        List<Barber> barbers = qm.getBarbers();

        if (barbers.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText(R.string.no_barbers_available);
            empty.setTextColor(ContextCompat.getColor(this, R.color.splash_subtext));
            empty.setTextSize(14);
            layoutBarberList.addView(empty);
            return;
        }

        float density = getResources().getDisplayMetrics().density;
        int gap = Math.round(10 * density);
        int cardWidthPx = Math.round(158 * density);

        for (Barber barber : barbers) {
            View card = LayoutInflater.from(this).inflate(R.layout.item_barber_book_portrait, layoutBarberList, false);
            bindHomeServiceBarberPortraitCard(card, barber, qm);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    cardWidthPx,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMarginEnd(gap);
            layoutBarberList.addView(card, lp);
        }
    }

    private void bindHomeServiceBarberPortraitCard(View card, Barber barber, QueueManager qm) {
        TextView tvName = card.findViewById(R.id.tv_barber_name);
        TextView tvActiveBadge = card.findViewById(R.id.tv_barber_active_badge);
        TextView tvTagline = card.findViewById(R.id.tv_barber_tagline);
        TextView tvAvailability = card.findViewById(R.id.tv_availability);
        ImageView ivPhoto = card.findViewById(R.id.iv_barber_photo);
        ImageButton btnViewQueue = card.findViewById(R.id.btn_view_queue);
        Button btnBook = card.findViewById(R.id.btn_book);

        tvName.setText(barber.getDisplayName());

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

        tvAvailability.setText(getString(R.string.user_barber_home_visit_line));
        btnBook.setEnabled(true);
        btnBook.setAlpha(1f);

        BarberPhotoHelper.loadBarberPhoto(this, barber, ivPhoto);

        String barberId = barber.getId();
        int qSize = qm.getQueueSize(barberId);
        btnViewQueue.setContentDescription(getString(R.string.queue_badge_content_desc, qSize));
        View.OnClickListener openFeedback = v -> FeedbackHelper.showFeedbackForBarber(
                PrivateBookActivity.this, barberId, barber.getDisplayName());
        tvName.setOnClickListener(openFeedback);
        ivPhoto.setOnClickListener(openFeedback);
        btnViewQueue.setOnClickListener(v -> {
            List<String> names = new ArrayList<>(qm.getQueue(barberId));
            QueueListDialog.show(PrivateBookActivity.this, barber.getDisplayName(), names);
        });
        btnBook.setOnClickListener(v -> bookBarber(barberId));
    }

    private String resolveBookingName() {
        String name = etName.getText().toString().trim();
        if (name.isEmpty()) {
            name = upm.getName() != null ? upm.getName().trim() : "";
        }
        return name;
    }

    private void bookBarber(String barberId) {
        if (!NetworkStatus.isOnline(this)) {
            Toast.makeText(this, R.string.user_no_internet_booking, Toast.LENGTH_SHORT).show();
            return;
        }
        saveFormToPrefs();
        if (homeServicePreferredAt <= 0) {
            Toast.makeText(this, R.string.home_service_time_required, Toast.LENGTH_SHORT).show();
            return;
        }
        if (homeServicePreferredAt <= System.currentTimeMillis()) {
            Toast.makeText(this, R.string.home_service_time_past, Toast.LENGTH_SHORT).show();
            return;
        }
        CutifyDialogs.showActionConfirm(
                this,
                getString(R.string.home_service_confirm_badge),
                getString(R.string.home_service_confirm_title),
                getString(R.string.home_service_confirm_message),
                R.drawable.bg_btn_discover,
                () -> {
                    String name = resolveBookingName();
                    if (name.isEmpty()) {
                        Toast.makeText(this, R.string.field_required, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    String service = etService.getText().toString().trim();
                    if (service.isEmpty()) {
                        Toast.makeText(this, R.string.booking_service_required, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    upm.saveName(name);
                    upm.saveServiceType(service);
                    executeHomeServiceBooking(barberId, name);
                });
    }

    private void executeHomeServiceBooking(String barberId, String name) {
        String addr = etAddress.getText().toString().trim();
        String phone = etPhone.getText().toString().trim();
        double lat = Double.NaN;
        double lon = Double.NaN;
        if (upm.hasHomeServiceBookingPin()) {
            lat = upm.getHomeServiceBookingPinLat();
            lon = upm.getHomeServiceBookingPinLon();
        }
        if (!coordsLookValid(lat, lon) && upm.hasSavedLocation()) {
            lat = upm.getLastLat();
            lon = upm.getLastLon();
        }
        if (coordsLookValid(lat, lon)) {
            completeBookHomeService(barberId, name, addr, phone, lat, lon);
            return;
        }
        if (!addr.isEmpty()) {
            Toast.makeText(this, R.string.home_service_geocoding_address, Toast.LENGTH_SHORT).show();
            final double fallbackLat = lat;
            final double fallbackLon = lon;
            OpenStreetMapGeocoder.geocode(addr, new OpenStreetMapGeocoder.Callback() {
                @Override
                public void onResult(double la, double lo) {
                    double useLat = fallbackLat;
                    double useLon = fallbackLon;
                    if (coordsLookValid(la, lo)) {
                        useLat = la;
                        useLon = lo;
                        upm.saveHomeServiceBookingPin(la, lo);
                        upm.saveLastKnownLocation(la, lo);
                        CustomerProfileFirestoreSync.requestUpsert(PrivateBookActivity.this);
                        if (!isFinishing()) {
                            loadMap(la, lo);
                            updateHomePinStatus();
                        }
                    }
                    if (!isFinishing()) {
                        completeBookHomeService(barberId, name, addr, phone, useLat, useLon);
                    }
                }

                @Override
                public void onFailure() {
                    if (!isFinishing()) {
                        completeBookHomeService(barberId, name, addr, phone, fallbackLat, fallbackLon);
                    }
                }
            });
            return;
        }
        Toast.makeText(this, R.string.home_service_booking_need_location_hint, Toast.LENGTH_SHORT).show();
        completeBookHomeService(barberId, name, addr, phone, lat, lon);
    }

    private void completeBookHomeService(String barberId, String name, String addr, String phone,
                                         double lat, double lon) {
        String serviceType = etService.getText().toString().trim();
        if (serviceType.isEmpty()) {
            Toast.makeText(this, R.string.booking_service_required, Toast.LENGTH_SHORT).show();
            return;
        }
        String customerSyncId = new UserProfileManager(this).getOrCreateGuestSyncId();
        long bookingTs = QueueManager.getInstance().bookHomeServiceAppointment(barberId, name,
                homeServicePreferredAt, addr, phone, lat, lon, customerSyncId, serviceType);
        if (bookingTs == QueueManager.BOOKING_DUPLICATE) {
            Toast.makeText(this, R.string.booking_duplicate_active, Toast.LENGTH_SHORT).show();
            return;
        }
        if (bookingTs == QueueManager.BOOKING_EMPTY_NAME) {
            Toast.makeText(this, R.string.field_required, Toast.LENGTH_SHORT).show();
            return;
        }
        if (bookingTs == QueueManager.BOOKING_HOME_TIME_REQUIRED) {
            Toast.makeText(this, R.string.home_service_time_required, Toast.LENGTH_SHORT).show();
            return;
        }
        if (bookingTs == QueueManager.BOOKING_HOME_TIME_PAST) {
            Toast.makeText(this, R.string.home_service_time_past, Toast.LENGTH_SHORT).show();
            return;
        }
        BookingNotificationScheduler.scheduleReminder(this, bookingTs);
        Toast.makeText(this, R.string.booking_success, Toast.LENGTH_SHORT).show();
        NotificationPermissionHelper.showAfterBookingSuccess(this, R.string.notif_homeservice_book_success_body);
        loadBarbers();
    }

    private void refreshFromFirestore() {
        loadBarbers();
        FeedbackHelper.checkAndShowFeedback(this);
    }
}
