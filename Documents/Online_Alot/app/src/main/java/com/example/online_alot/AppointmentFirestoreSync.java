package com.example.online_alot;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Writes {@link QueueManager.Appointment} rows to Firestore and listens for real-time updates
 * so multiple devices see the same queue/appointments.
 * <p>
 * Requires Firestore rules that allow read/write on {@link AppFirestore#COL_APPOINTMENTS}
 * (see {@code firestore.rules} in project root — paste into Firebase Console if you use production mode).
 */
public final class AppointmentFirestoreSync {

    private static final String TAG = "AppointmentFirestore";

    private static ListenerRegistration registration;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private AppointmentFirestoreSync() {}

    public static void attach(QueueManager qm) {
        if (registration != null) {
            return;
        }
        try {
            registration = AppFirestore.db()
                    .collection(AppFirestore.COL_APPOINTMENTS)
                    .addSnapshotListener((snapshot, error) -> {
                        if (error != null) {
                            Log.w(TAG, "Listen failed", error);
                            return;
                        }
                        if (snapshot == null) {
                            return;
                        }
                        MAIN.post(() -> handleSnapshot(qm, snapshot));
                    });
        } catch (Exception e) {
            Log.w(TAG, "Could not attach Firestore listener", e);
        }
    }

    private static void handleSnapshot(QueueManager qm, QuerySnapshot snapshot) {
        Map<Long, String> oldStatuses = qm.copyAppointmentStatusesByTimestamp();
        if (!snapshot.isEmpty()) {
            applySnapshot(qm, snapshot, oldStatuses);
            return;
        }
        List<QueueManager.Appointment> local = qm.copyAppointmentsSnapshot();
        if (!local.isEmpty()) {
            for (QueueManager.Appointment a : local) {
                upsert(a);
            }
            return;
        }
        applySnapshot(qm, snapshot, oldStatuses);
    }

    private static void applySnapshot(QueueManager qm, QuerySnapshot snapshot,
                                      Map<Long, String> oldStatuses) {
        List<QueueManager.Appointment> list = new ArrayList<>();
        for (QueryDocumentSnapshot doc : snapshot) {
            QueueManager.Appointment a = fromDoc(doc);
            if (a != null) {
                list.add(a);
            }
        }
        Collections.sort(list, Comparator.comparingLong(a -> a.timestamp));

        Map<Long, Integer> oldInShopIndices = new HashMap<>();
        Context qCtx = qm.getQueueContext();
        if (qCtx != null) {
            UserProfileManager upm = new UserProfileManager(qCtx);
            String n = upm.getName() != null ? upm.getName().trim() : "";
            String s = upm.getGuestSyncId() != null ? upm.getGuestSyncId().trim() : "";
            if (!n.isEmpty() || !s.isEmpty()) {
                oldInShopIndices = qm.snapshotInShopWaitingIndicesForLocalUser(n, s);
            }
        }

        qm.applyRemoteAppointmentsSnapshot(list);
        maybeEnqueueCustomerFeedbackAfterRemoteMerge(qm, oldStatuses, list);
        QueueCancelledNotifier.notifyIfBarberCancelledRemote(qm, oldStatuses, list);
        QueueAdvanceNotifier.notifyIfInShopAdvanced(qm, oldInShopIndices);
    }

    /**
     * When the barber marks a visit Done on another device, the customer's phone only sees it via Firestore.
     * Enqueue the same rate/report prompt that used to run only on the barber's app.
     */
    private static void maybeEnqueueCustomerFeedbackAfterRemoteMerge(QueueManager qm,
            Map<Long, String> oldStatuses, List<QueueManager.Appointment> afterList) {
        if (qm.getQueueContext() == null || oldStatuses == null || afterList == null) {
            return;
        }
        UserProfileManager upm = new UserProfileManager(qm.getQueueContext());
        String localName = upm.getName() != null ? upm.getName().trim() : "";
        String localSync = upm.getGuestSyncId() != null ? upm.getGuestSyncId().trim() : "";
        if (localName.isEmpty() && localSync.isEmpty()) {
            return;
        }
        for (QueueManager.Appointment a : afterList) {
            if (!"Done".equals(a.status)) {
                continue;
            }
            String prev = oldStatuses.get(a.timestamp);
            if (prev == null || !"Waiting".equals(prev)) {
                continue;
            }
            boolean nameMatch = !localName.isEmpty()
                    && a.customerName != null
                    && a.customerName.trim().equalsIgnoreCase(localName);
            String aSync = a.customerSyncId != null ? a.customerSyncId.trim() : "";
            boolean syncMatch = !localSync.isEmpty() && localSync.equals(aSync);
            if (!nameMatch && !syncMatch) {
                continue;
            }
            Barber b = qm.getBarber(a.barberId);
            String barberName = b != null ? b.getDisplayName() : a.barberName;
            qm.addPendingFeedback(a.barberId, barberName, a.customerName);
        }
    }

    private static QueueManager.Appointment fromDoc(DocumentSnapshot doc) {
        try {
            String barberId = doc.getString("barberId");
            String customerName = doc.getString("customerName");
            if (barberId == null || customerName == null) {
                return null;
            }
            long ts = readLong(doc, "timestamp");
            if (ts <= 0) {
                return null;
            }
            String barberName = doc.getString("barberName");
            if (barberName == null) {
                barberName = barberId;
            }
            String status = doc.getString("status");
            if (status == null) {
                status = "Waiting";
            }
            long scheduled = readLong(doc, "scheduledArrivalMillis");
            if (scheduled <= 0) {
                scheduled = ts;
            }
            boolean home = Boolean.TRUE.equals(doc.getBoolean("homeService"));
            long preferredHome = readLong(doc, "homeServicePreferredMillis");
            String hAddr = doc.getString("homeServiceAddress");
            if (hAddr == null) {
                hAddr = "";
            }
            String hPhone = doc.getString("homeServicePhone");
            if (hPhone == null) {
                hPhone = "";
            }
            double hLat = readDouble(doc, "homeServiceLat");
            double hLon = readDouble(doc, "homeServiceLon");
            boolean hCoord = Boolean.TRUE.equals(doc.getBoolean("homeServiceHasCoordinates"))
                    || QueueManager.homeServiceCoordsLookValid(hLat, hLon);
            String csid = doc.getString("customerSyncId");
            if (csid == null) {
                csid = "";
            }
            String serviceType = doc.getString("serviceType");
            if (serviceType == null) {
                serviceType = "";
            }
            long completedAt = readLong(doc, "completedAtMillis");
            return new QueueManager.Appointment(barberId, barberName, customerName, ts, status,
                    scheduled, home, preferredHome, hAddr, hPhone, hCoord, hLat, hLon, csid, serviceType,
                    completedAt);
        } catch (Exception e) {
            Log.w(TAG, "Bad appointment doc " + doc.getId(), e);
            return null;
        }
    }

    private static long readLong(DocumentSnapshot doc, String key) {
        Long l = doc.getLong(key);
        if (l != null) {
            return l;
        }
        Double d = doc.getDouble(key);
        if (d != null) {
            return d.longValue();
        }
        return 0L;
    }

    private static double readDouble(DocumentSnapshot doc, String key) {
        Double d = doc.getDouble(key);
        if (d != null) {
            return d;
        }
        Long l = doc.getLong(key);
        if (l != null) {
            return l.doubleValue();
        }
        return 0;
    }

    public static void upsert(QueueManager.Appointment a) {
        if (a == null) {
            return;
        }
        Map<String, Object> m = new HashMap<>();
        m.put("barberId", a.barberId);
        m.put("barberName", a.barberName);
        m.put("customerName", a.customerName);
        m.put("timestamp", a.timestamp);
        m.put("status", a.status);
        m.put("scheduledArrivalMillis", a.scheduledArrivalMillis);
        m.put("homeService", a.homeService);
        m.put("homeServicePreferredMillis", a.homeServicePreferredMillis);
        m.put("homeServiceAddress", a.homeServiceAddress != null ? a.homeServiceAddress : "");
        m.put("homeServicePhone", a.homeServicePhone != null ? a.homeServicePhone : "");
        m.put("homeServiceHasCoordinates", a.homeServiceHasCoordinates);
        m.put("homeServiceLat", a.homeServiceLat);
        m.put("homeServiceLon", a.homeServiceLon);
        m.put("customerSyncId", a.customerSyncId != null ? a.customerSyncId : "");
        m.put("serviceType", a.serviceType != null ? a.serviceType : "");
        m.put("completedAtMillis", a.completedAtMillis);

        try {
            AppFirestore.db().collection(AppFirestore.COL_APPOINTMENTS)
                    .document(String.valueOf(a.timestamp))
                    .set(m)
                    .addOnFailureListener(e -> Log.w(TAG, "Firestore upsert failed", e));
        } catch (Exception e) {
            Log.w(TAG, "Firestore upsert exception", e);
        }
    }

    /** Removes one appointment document (doc id = {@code String.valueOf(timestamp)}). */
    public static void delete(long timestamp) {
        if (timestamp <= 0) {
            return;
        }
        try {
            AppFirestore.db().collection(AppFirestore.COL_APPOINTMENTS)
                    .document(String.valueOf(timestamp))
                    .delete()
                    .addOnFailureListener(e -> Log.w(TAG, "Appointment delete failed", e));
        } catch (Exception e) {
            Log.w(TAG, "Appointment delete exception", e);
        }
    }
}
