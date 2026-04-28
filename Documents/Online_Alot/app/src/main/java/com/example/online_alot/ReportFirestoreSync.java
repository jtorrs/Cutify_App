package com.example.online_alot;

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

/** Real-time sync for {@link QueueManager.BarberReport} across devices. */
public final class ReportFirestoreSync {
    private static final String TAG = "ReportFirestoreSync";
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static ListenerRegistration registration;

    private ReportFirestoreSync() {}

    public static void attach(QueueManager qm) {
        if (registration != null) {
            return;
        }
        try {
            registration = AppFirestore.db().collection(AppFirestore.COL_REPORTS)
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
            Log.w(TAG, "Could not attach reports listener", e);
        }
    }

    private static void handleSnapshot(QueueManager qm, QuerySnapshot snapshot) {
        if (!snapshot.isEmpty()) {
            applySnapshot(qm, snapshot);
            return;
        }
        // Remote empty: only push local reports once (legacy migration). Otherwise apply empty —
        // re-upserting local here re-created "deleted" reports on the server.
        List<QueueManager.BarberReport> local = qm.copyReportsSnapshot();
        if (!local.isEmpty() && qm.shouldMigrateLocalReportsToRemoteOnce()) {
            for (QueueManager.BarberReport r : local) {
                upsert(r);
            }
            qm.markReportsFirestoreMigrationDone();
            return;
        }
        applySnapshot(qm, snapshot);
    }

    private static void applySnapshot(QueueManager qm, QuerySnapshot snapshot) {
        if (!snapshot.isEmpty()) {
            qm.markReportsFirestoreMigrationDone();
        }
        List<QueueManager.BarberReport> list = new ArrayList<>();
        for (QueryDocumentSnapshot doc : snapshot) {
            QueueManager.BarberReport r = fromDoc(doc);
            if (r != null) {
                list.add(r);
            }
        }
        Collections.sort(list, Comparator.comparingLong(r -> r.timestamp));
        qm.applyRemoteReportsSnapshot(list);
    }

    private static QueueManager.BarberReport fromDoc(DocumentSnapshot doc) {
        try {
            String barberName = doc.getString("barberName");
            String description = doc.getString("description");
            String reporterName = doc.getString("reporterName");
            if (barberName == null) barberName = "";
            if (description == null) description = "";
            if (reporterName == null) reporterName = "";
            Long ts = doc.getLong("timestamp");
            if (ts == null) {
                Double d = doc.getDouble("timestamp");
                ts = d != null ? d.longValue() : 0L;
            }
            if (ts == null || ts <= 0) {
                return null;
            }
            return new QueueManager.BarberReport(barberName, description, reporterName, ts);
        } catch (Exception e) {
            Log.w(TAG, "Bad report doc " + doc.getId(), e);
            return null;
        }
    }

    public static void upsert(QueueManager.BarberReport r) {
        if (r == null) {
            return;
        }
        Map<String, Object> m = new HashMap<>();
        m.put("barberName", r.barberName != null ? r.barberName : "");
        m.put("description", r.description != null ? r.description : "");
        m.put("reporterName", r.reporterName != null ? r.reporterName : "");
        m.put("timestamp", r.timestamp);
        try {
            AppFirestore.db().collection(AppFirestore.COL_REPORTS)
                    .document(String.valueOf(r.timestamp))
                    .set(m)
                    .addOnFailureListener(e -> Log.w(TAG, "Report upsert failed", e));
        } catch (Exception e) {
            Log.w(TAG, "Report upsert exception", e);
        }
    }

    public static void delete(long timestamp) {
        delete(timestamp, null);
    }

    /**
     * @param onComplete run on main thread after delete finishes (success or failure)
     */
    public static void delete(long timestamp, Runnable onComplete) {
        if (timestamp <= 0) {
            if (onComplete != null) {
                MAIN.post(onComplete);
            }
            return;
        }
        try {
            AppFirestore.db().collection(AppFirestore.COL_REPORTS)
                    .document(String.valueOf(timestamp))
                    .delete()
                    .addOnCompleteListener(task -> {
                        if (!task.isSuccessful()) {
                            Log.w(TAG, "Report delete failed", task.getException());
                        }
                        if (onComplete != null) {
                            MAIN.post(onComplete);
                        }
                    });
        } catch (Exception e) {
            Log.w(TAG, "Report delete exception", e);
            if (onComplete != null) {
                MAIN.post(onComplete);
            }
        }
    }
}
