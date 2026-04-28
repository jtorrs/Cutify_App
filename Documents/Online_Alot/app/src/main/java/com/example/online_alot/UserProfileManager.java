package com.example.online_alot;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import com.google.firebase.firestore.DocumentSnapshot;

import java.util.UUID;

public class UserProfileManager {
    private static final String PREFS_NAME = "user_profile_prefs";
    private static final String KEY_NAME = "user_name";
    private static final String KEY_ADDRESS = "user_address";
    private static final String KEY_PHOTO_URI = "user_photo_uri";
    private static final String KEY_AGE = "user_age";
    private static final String KEY_PHONE = "user_phone";
    private static final String KEY_LAST_LAT = "user_last_lat";
    private static final String KEY_LAST_LON = "user_last_lon";
    private static final String KEY_HS_PREFERRED_AT = "home_service_preferred_at";
    private static final String KEY_HS_BOOKING_PIN_LAT = "hs_booking_pin_lat";
    private static final String KEY_HS_BOOKING_PIN_LON = "hs_booking_pin_lon";
    private static final String KEY_GUEST_SYNC_ID = "guest_sync_id";
    private static final String KEY_SERVICE_TYPE = "user_service_type";

    private final SharedPreferences prefs;

    public UserProfileManager(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /**
     * Stable ID for Firestore {@code customer_profiles/{id}}. Created on first use; copy to another phone
     * under Profile to sync the same Cutify customer data.
     */
    public String getOrCreateGuestSyncId() {
        String id = prefs.getString(KEY_GUEST_SYNC_ID, "");
        if (id.isEmpty()) {
            id = UUID.randomUUID().toString();
            prefs.edit().putString(KEY_GUEST_SYNC_ID, id).apply();
        }
        return id;
    }

    public String getGuestSyncId() {
        return prefs.getString(KEY_GUEST_SYNC_ID, "");
    }

    /** Point this device at an existing cloud profile (paste ID from your other phone). */
    public void setGuestSyncId(String uuid) {
        if (uuid == null) {
            return;
        }
        String t = uuid.trim();
        if (t.isEmpty()) {
            return;
        }
        prefs.edit().putString(KEY_GUEST_SYNC_ID, t).apply();
    }

    public void saveName(String name) {
        prefs.edit().putString(KEY_NAME, name).apply();
    }

    public String getName() {
        return prefs.getString(KEY_NAME, "");
    }

    /** Last requested haircut / service (prefill on next booking). */
    public void saveServiceType(String serviceType) {
        prefs.edit().putString(KEY_SERVICE_TYPE, serviceType != null ? serviceType.trim() : "").apply();
    }

    public String getServiceType() {
        return prefs.getString(KEY_SERVICE_TYPE, "");
    }

    public void saveAddress(String address) {
        prefs.edit().putString(KEY_ADDRESS, address).apply();
    }

    public String getAddress() {
        return prefs.getString(KEY_ADDRESS, "");
    }

    public void savePhotoUri(String uri) {
        prefs.edit().putString(KEY_PHOTO_URI, uri).apply();
    }

    public String getPhotoUri() {
        return prefs.getString(KEY_PHOTO_URI, "");
    }

    public void saveProfile(String name, String address) {
        prefs.edit()
                .putString(KEY_NAME, name)
                .putString(KEY_ADDRESS, address)
                .apply();
    }

    public void saveAge(String age) {
        prefs.edit().putString(KEY_AGE, age).apply();
    }

    public String getAge() {
        return prefs.getString(KEY_AGE, "");
    }

    public void savePhone(String phone) {
        prefs.edit().putString(KEY_PHONE, phone).apply();
    }

    public String getPhone() {
        return prefs.getString(KEY_PHONE, "");
    }

    public void saveLastKnownLocation(double lat, double lon) {
        prefs.edit()
                .putFloat(KEY_LAST_LAT, (float) lat)
                .putFloat(KEY_LAST_LON, (float) lon)
                .apply();
    }

    public double getLastLat() {
        return prefs.contains(KEY_LAST_LAT) ? prefs.getFloat(KEY_LAST_LAT, 0f) : Double.NaN;
    }

    public double getLastLon() {
        return prefs.contains(KEY_LAST_LON) ? prefs.getFloat(KEY_LAST_LON, 0f) : Double.NaN;
    }

    public boolean hasSavedLocation() {
        return prefs.contains(KEY_LAST_LAT) && prefs.contains(KEY_LAST_LON);
    }

    public void saveHomeServicePreferredAt(long millis) {
        prefs.edit().putLong(KEY_HS_PREFERRED_AT, millis).apply();
    }

    public long getHomeServicePreferredAt() {
        return prefs.getLong(KEY_HS_PREFERRED_AT, 0L);
    }

    public void saveHomeServiceBookingPin(double lat, double lon) {
        prefs.edit()
                .putFloat(KEY_HS_BOOKING_PIN_LAT, (float) lat)
                .putFloat(KEY_HS_BOOKING_PIN_LON, (float) lon)
                .apply();
    }

    public boolean hasHomeServiceBookingPin() {
        return prefs.contains(KEY_HS_BOOKING_PIN_LAT) && prefs.contains(KEY_HS_BOOKING_PIN_LON);
    }

    public double getHomeServiceBookingPinLat() {
        return prefs.getFloat(KEY_HS_BOOKING_PIN_LAT, 0f);
    }

    public double getHomeServiceBookingPinLon() {
        return prefs.getFloat(KEY_HS_BOOKING_PIN_LON, 0f);
    }

    /** Apply remote Firestore fields into local prefs (main thread). */
    public void applyFromFirestore(DocumentSnapshot d) {
        if (d == null || !d.exists()) {
            return;
        }
        SharedPreferences.Editor ed = prefs.edit();
        String v;
        v = d.getString("name");
        if (v != null) {
            ed.putString(KEY_NAME, v);
        }
        v = d.getString("address");
        if (v != null) {
            ed.putString(KEY_ADDRESS, v);
        }
        v = d.getString("age");
        if (v != null) {
            ed.putString(KEY_AGE, v);
        }
        v = d.getString("phone");
        if (v != null) {
            ed.putString(KEY_PHONE, v);
        }
        v = d.getString("serviceType");
        if (v != null) {
            ed.putString(KEY_SERVICE_TYPE, v);
        }
        v = d.getString("photoUri");
        if (v != null) {
            ed.putString(KEY_PHOTO_URI, v);
        }
        if (d.get("lastLat") != null && d.get("lastLon") != null) {
            try {
                Double la = d.getDouble("lastLat");
                Double lo = d.getDouble("lastLon");
                if (la != null && lo != null) {
                    ed.putFloat(KEY_LAST_LAT, la.floatValue());
                    ed.putFloat(KEY_LAST_LON, lo.floatValue());
                }
            } catch (RuntimeException ignored) {
            }
        }
        Long hsPref = d.getLong("homeServicePreferredAt");
        if (hsPref == null && d.get("homeServicePreferredAt") != null) {
            try {
                Double dbl = d.getDouble("homeServicePreferredAt");
                if (dbl != null) {
                    hsPref = dbl.longValue();
                }
            } catch (RuntimeException ignored) {
            }
        }
        if (hsPref != null && hsPref > 0) {
            ed.putLong(KEY_HS_PREFERRED_AT, hsPref);
        }
        if (d.get("hsBookingPinLat") != null && d.get("hsBookingPinLon") != null) {
            try {
                Double plat = d.getDouble("hsBookingPinLat");
                Double plon = d.getDouble("hsBookingPinLon");
                if (plat != null && plon != null) {
                    ed.putFloat(KEY_HS_BOOKING_PIN_LAT, plat.floatValue());
                    ed.putFloat(KEY_HS_BOOKING_PIN_LON, plon.floatValue());
                }
            } catch (RuntimeException ignored) {
            }
        }
        ed.apply();
    }

    /** Build map for Firestore merge (skips empty / NaN where appropriate). */
    public java.util.Map<String, Object> toFirestoreMap() {
        java.util.Map<String, Object> m = new java.util.HashMap<>();
        if (!TextUtils.isEmpty(getName())) {
            m.put("name", getName());
        }
        if (!TextUtils.isEmpty(getAddress())) {
            m.put("address", getAddress());
        }
        if (!TextUtils.isEmpty(getAge())) {
            m.put("age", getAge());
        }
        if (!TextUtils.isEmpty(getPhone())) {
            m.put("phone", getPhone());
        }
        if (!TextUtils.isEmpty(getServiceType())) {
            m.put("serviceType", getServiceType());
        }
        if (!TextUtils.isEmpty(getPhotoUri())) {
            m.put("photoUri", getPhotoUri());
        }
        double la = getLastLat();
        double lo = getLastLon();
        if (!Double.isNaN(la) && !Double.isNaN(lo)) {
            m.put("lastLat", la);
            m.put("lastLon", lo);
        }
        long pref = getHomeServicePreferredAt();
        if (pref > 0) {
            m.put("homeServicePreferredAt", pref);
        }
        if (hasHomeServiceBookingPin()) {
            m.put("hsBookingPinLat", getHomeServiceBookingPinLat());
            m.put("hsBookingPinLon", getHomeServiceBookingPinLon());
        }
        m.put("updatedAt", System.currentTimeMillis());
        return m;
    }
}
