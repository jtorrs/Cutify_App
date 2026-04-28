package com.example.online_alot;

import android.util.Log;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.SetOptions;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Syncs barber display/rating/on-duty state through Firestore for real-time cross-device updates.
 */
public final class BarberFirestoreSync {

    private static final String TAG = "BarberFirestoreSync";
    private static ListenerRegistration registration;

    private BarberFirestoreSync() {}

    public static synchronized void attach(QueueManager qm) {
        if (registration != null) {
            return;
        }
        try {
            registration = AppFirestore.db()
                    .collection(AppFirestore.COL_BARBERS)
                    .addSnapshotListener((snapshot, error) -> {
                        if (error != null) {
                            Log.w(TAG, "listen failed", error);
                            return;
                        }
                        if (snapshot == null) {
                            return;
                        }
                        applySnapshot(qm, snapshot);
                    });
        } catch (Exception e) {
            Log.w(TAG, "attach failed", e);
        }
    }

    private static void applySnapshot(QueueManager qm, QuerySnapshot snapshot) {
        if (snapshot.isEmpty()) {
            // Firestore is source of truth for discoverable barbers.
            // If all barber docs were removed (e.g., Super Admin deleted all staff),
            // clear local cache so customer screens show no available barbers.
            qm.applyRemoteBarberState(new ArrayList<>(), new HashSet<>());
            return;
        }
        List<Barber> candidates = new ArrayList<>();
        Set<String> onDutyIds = new HashSet<>();
        for (QueryDocumentSnapshot doc : snapshot) {
            Barber b = fromDoc(doc);
            if (b == null) {
                continue;
            }
            candidates.add(b);
            boolean isOnDuty = Boolean.TRUE.equals(doc.getBoolean("onDuty"));
            if (isOnDuty) {
                onDutyIds.add(b.getId());
            }
        }
        applyOnlyExistingAdminAccounts(qm, candidates, onDutyIds);
    }

    /**
     * Keep only barbers whose admin account still exists.
     * - Card appears after first admin sign-in (barber doc gets created).
     * - Card stays visible but inactive when admin logs out (onDuty=false).
     * - Card disappears permanently when Super Admin deletes account.
     */
    private static void applyOnlyExistingAdminAccounts(QueueManager qm,
                                                       List<Barber> candidates,
                                                       Set<String> onDutyIds) {
        if (candidates == null || candidates.isEmpty()) {
            qm.applyRemoteBarberState(new ArrayList<>(), new HashSet<>());
            return;
        }
        List<Barber> filtered = new ArrayList<>();
        Set<String> filteredOnDuty = new HashSet<>();
        AtomicInteger pending = new AtomicInteger(candidates.size());
        for (Barber b : candidates) {
            if (b == null || b.getId() == null || b.getId().trim().isEmpty()) {
                if (pending.decrementAndGet() == 0) {
                    qm.applyRemoteBarberState(filtered, filteredOnDuty);
                }
                continue;
            }
            String barberId = b.getId().trim().toLowerCase(Locale.ROOT);
            AppFirestore.db().collection(AppFirestore.COL_ADMIN_ACCOUNTS).document(barberId).get()
                    .addOnCompleteListener(task -> {
                        boolean keep = false;
                        if (task.isSuccessful() && task.getResult() != null && task.getResult().exists()) {
                            keep = Boolean.TRUE.equals(
                                    task.getResult().getBoolean(StaffDirectoryFirestore.FIELD_VIA_INVITE));
                        }
                        if (keep) {
                            synchronized (filtered) {
                                filtered.add(b);
                                if (onDutyIds.contains(b.getId())) {
                                    filteredOnDuty.add(b.getId());
                                }
                            }
                        }
                        if (pending.decrementAndGet() == 0) {
                            qm.applyRemoteBarberState(filtered, filteredOnDuty);
                        }
                    });
        }
    }

    private static Barber fromDoc(DocumentSnapshot doc) {
        try {
            String id = doc.getId();
            String name = doc.getString("displayName");
            if (name == null || name.trim().isEmpty()) {
                name = id;
            }
            Barber b = new Barber(id, name);
            Double total = doc.getDouble("totalRating");
            if (total != null) {
                b.setTotalRating(total.floatValue());
            }
            Long count = doc.getLong("ratingCount");
            if (count != null) {
                b.setRatingCount(Math.max(0, count.intValue()));
            }
            String pu = doc.getString("photoUrl");
            if (pu != null && !pu.trim().isEmpty()) {
                b.setPhotoUrl(pu.trim());
            }
            String availabilityHours = doc.getString("availabilityHours");
            if (availabilityHours != null) {
                b.setAvailabilityHours(availabilityHours.trim());
            }
            String availabilityDays = doc.getString("availabilityDays");
            if (availabilityDays != null) {
                b.setAvailabilityDays(availabilityDays.trim());
            }
            return b;
        } catch (Exception e) {
            Log.w(TAG, "fromDoc", e);
            return null;
        }
    }

    public static void upsert(Barber barber, boolean onDuty) {
        if (barber == null || barber.getId() == null || barber.getId().trim().isEmpty()) {
            return;
        }
        String id = barber.getId().trim().toLowerCase(Locale.ROOT);
        try {
            Map<String, Object> m = new java.util.HashMap<>();
            m.put("displayName", barber.getDisplayName());
            m.put("totalRating", barber.getTotalRating());
            m.put("ratingCount", barber.getRatingCount());
            m.put("photoUrl", barber.getPhotoUrl());
            m.put("availabilityHours", barber.getAvailabilityHours());
            m.put("availabilityDays", barber.getAvailabilityDays());
            m.put("onDuty", onDuty);
            m.put("updatedAt", System.currentTimeMillis());
            AppFirestore.db().collection(AppFirestore.COL_BARBERS).document(id)
                    .set(m, SetOptions.merge())
                    .addOnFailureListener(e -> Log.w(TAG, "upsert failed", e));
        } catch (Exception e) {
            Log.w(TAG, "upsert", e);
        }
    }

    /**
     * Merges only {@code photoUrl} (and {@code updatedAt}) — use when local {@link QueueManager}
     * has no matching barber row yet, so customers still receive the image from Firestore.
     */
    public static void mergePhotoUrl(String barberEmailOrId, String httpsUrl) {
        if (barberEmailOrId == null || barberEmailOrId.trim().isEmpty()) {
            return;
        }
        if (httpsUrl == null || httpsUrl.trim().isEmpty()) {
            return;
        }
        String id = barberEmailOrId.trim().toLowerCase(Locale.ROOT);
        try {
            Map<String, Object> m = new java.util.HashMap<>();
            m.put("photoUrl", httpsUrl.trim());
            m.put("updatedAt", System.currentTimeMillis());
            AppFirestore.db().collection(AppFirestore.COL_BARBERS).document(id)
                    .set(m, SetOptions.merge())
                    .addOnFailureListener(e -> Log.w(TAG, "mergePhotoUrl failed", e));
        } catch (Exception e) {
            Log.w(TAG, "mergePhotoUrl", e);
        }
    }

    public static void mergeProfile(String barberEmailOrId, String displayName, String hours, String days) {
        if (barberEmailOrId == null || barberEmailOrId.trim().isEmpty()) {
            return;
        }
        String id = barberEmailOrId.trim().toLowerCase(Locale.ROOT);
        try {
            Map<String, Object> m = new java.util.HashMap<>();
            m.put("displayName", displayName != null ? displayName.trim() : "");
            m.put("availabilityHours", hours != null ? hours.trim() : "");
            m.put("availabilityDays", days != null ? days.trim() : "");
            m.put("updatedAt", System.currentTimeMillis());
            AppFirestore.db().collection(AppFirestore.COL_BARBERS).document(id)
                    .set(m, SetOptions.merge())
                    .addOnFailureListener(e -> Log.w(TAG, "mergeProfile failed", e));
        } catch (Exception e) {
            Log.w(TAG, "mergeProfile", e);
        }
    }

    public static void delete(String barberId) {
        if (barberId == null || barberId.trim().isEmpty()) {
            return;
        }
        String id = barberId.trim().toLowerCase(Locale.ROOT);
        try {
            AppFirestore.db().collection(AppFirestore.COL_BARBERS).document(id).delete()
                    .addOnFailureListener(e -> Log.w(TAG, "delete failed", e));
        } catch (Exception e) {
            Log.w(TAG, "delete", e);
        }
    }
}
