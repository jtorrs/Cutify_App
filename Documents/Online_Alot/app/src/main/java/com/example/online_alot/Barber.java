package com.example.online_alot;

public class Barber {
    private String id;
    private String displayName;
    private float totalRating;
    private int ratingCount;
    /** HTTPS URL from Firebase Storage — visible on all devices (not a local content:// URI). */
    private String photoUrl;
    private String availabilityHours;
    private String availabilityDays;

    public Barber(String id, String displayName) {
        this.id = id;
        this.displayName = displayName;
        this.totalRating = 0;
        this.ratingCount = 0;
        this.photoUrl = "";
        this.availabilityHours = "";
        this.availabilityDays = "";
    }

    public String getId() { return id; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String name) { this.displayName = name; }

    public String getPhotoUrl() {
        return photoUrl != null ? photoUrl : "";
    }

    public void setPhotoUrl(String photoUrl) {
        this.photoUrl = photoUrl != null ? photoUrl : "";
    }

    public String getAvailabilityHours() {
        return availabilityHours != null ? availabilityHours : "";
    }

    public void setAvailabilityHours(String availabilityHours) {
        this.availabilityHours = availabilityHours != null ? availabilityHours : "";
    }

    public String getAvailabilityDays() {
        return availabilityDays != null ? availabilityDays : "";
    }

    public void setAvailabilityDays(String availabilityDays) {
        this.availabilityDays = availabilityDays != null ? availabilityDays : "";
    }

    public float getRating() {
        if (ratingCount == 0) return 0;
        return totalRating / ratingCount;
    }

    public int getRatingCount() { return ratingCount; }
    public float getTotalRating() { return totalRating; }
    public void setTotalRating(float totalRating) { this.totalRating = totalRating; }
    public void setRatingCount(int ratingCount) { this.ratingCount = ratingCount; }

    public void addRating(float rating) {
        this.totalRating += rating;
        this.ratingCount++;
    }
}
