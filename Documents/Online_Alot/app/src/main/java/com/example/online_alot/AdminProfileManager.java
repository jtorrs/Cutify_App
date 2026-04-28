package com.example.online_alot;

import android.content.Context;
import android.content.SharedPreferences;

public class AdminProfileManager {
    private static final String PREFS_PREFIX = "admin_profile_";
    private static final String KEY_NAME = "name";
    private static final String KEY_ADDRESS = "address";
    private static final String KEY_PHOTO_URI = "photo_uri";
    private static final String KEY_AVAILABILITY_HOURS = "availability_hours";
    private static final String KEY_AVAILABILITY_DAYS = "availability_days";

    private final SharedPreferences prefs;

    public AdminProfileManager(Context context, String adminEmail) {
        String safeName = adminEmail.replaceAll("[^a-zA-Z0-9]", "_");
        prefs = context.getSharedPreferences(PREFS_PREFIX + safeName, Context.MODE_PRIVATE);
    }

    public void saveName(String name) {
        prefs.edit().putString(KEY_NAME, name).apply();
    }

    public String getName() {
        return prefs.getString(KEY_NAME, "");
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

    public void saveAvailabilityHours(String hours) {
        prefs.edit().putString(KEY_AVAILABILITY_HOURS, hours).apply();
    }

    public String getAvailabilityHours() {
        return prefs.getString(KEY_AVAILABILITY_HOURS, "");
    }

    public void saveAvailabilityDays(String days) {
        prefs.edit().putString(KEY_AVAILABILITY_DAYS, days).apply();
    }

    public String getAvailabilityDays() {
        return prefs.getString(KEY_AVAILABILITY_DAYS, "");
    }

    public void saveProfile(String name, String address) {
        prefs.edit()
                .putString(KEY_NAME, name)
                .putString(KEY_ADDRESS, address)
                .apply();
    }
}
