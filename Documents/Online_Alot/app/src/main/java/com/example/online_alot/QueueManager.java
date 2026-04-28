package com.example.online_alot;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.List;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class QueueManager {
    private static QueueManager instance;
    private Context context;

    private final List<Barber> barbers = new ArrayList<>();
    private final Set<String> barbersOnDuty = new HashSet<>();
    private final Map<String, List<String>> queues = new HashMap<>();
    private final List<Appointment> appointments = new ArrayList<>();
    private final List<Long> bookingTimestamps = new ArrayList<>();
    private final List<PendingFeedback> pendingFeedbacks = new ArrayList<>();
    private final List<BarberReport> reports = new ArrayList<>();

    private final CopyOnWriteArrayList<Runnable> appointmentSyncListeners = new CopyOnWriteArrayList<>();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private static final String PREFS_NAME = "queue_manager_prefs";
    private static final String KEY_BARBERS = "barbers_json";
    private static final String KEY_TIMESTAMPS = "booking_timestamps";
    private static final String KEY_APPOINTMENTS = "appointments_json";
    private static final String KEY_REPORTS = "reports_json";
    private static final String KEY_REPORTS_FS_MIGRATED = "reports_firestore_migrated_v1";

    /** Firestore delete may lag behind the listener; ignore re-added docs until delete acks. */
    private final Set<Long> pendingDeletedReportTimestamps = Collections.synchronizedSet(new HashSet<>());

    private QueueManager() {}

    public static void init(Context context) {
        if (instance == null) {
            instance = new QueueManager();
        }
        instance.context = context.getApplicationContext();
        instance.loadPersistedBarbers();
        instance.loadPersistedTimestamps();
        instance.loadPersistedAppointments();
        instance.loadPersistedReports();
        AppointmentFirestoreSync.attach(instance);
        BarberFirestoreSync.attach(instance);
        ReportFirestoreSync.attach(instance);
    }

    public static QueueManager getInstance() {
        if (instance == null) {
            instance = new QueueManager();
        }
        return instance;
    }

    // ── Barber management ──

    public void addBarberFromAdminSignIn(String email, String displayName) {
        for (Barber b : barbers) {
            if (b.getId().equals(email)) {
                barbersOnDuty.add(email);
                BarberFirestoreSync.upsert(b, true);
                notifyAppointmentSyncListeners();
                return;
            }
        }
        String name = (displayName != null && !displayName.isEmpty())
                ? displayName
                : email.split("@")[0];
        Barber barber = new Barber(email, name);
        barbers.add(barber);
        barbersOnDuty.add(email);
        persistBarbers();
        BarberFirestoreSync.upsert(barber, true);
        notifyAppointmentSyncListeners();
    }

    public void updateBarberDisplayName(String barberId, String name) {
        for (Barber b : barbers) {
            if (b.getId().equals(barberId)) {
                b.setDisplayName(name);
                persistBarbers();
                BarberFirestoreSync.upsert(b, barbersOnDuty.contains(barberId));
                notifyAppointmentSyncListeners();
                return;
            }
        }
    }

    /**
     * Public profile photo (HTTPS). Synced via {@link BarberFirestoreSync} so customers can load it.
     * Email matching is case-insensitive (Firestore barber doc ids are lowercase).
     *
     * @return true if a local {@link Barber} row was updated
     */
    public boolean updateBarberPhotoUrl(String barberId, String photoUrlHttps) {
        if (barberId == null || barberId.isEmpty()) {
            return false;
        }
        String needle = barberId.trim();
        synchronized (this) {
            for (Barber b : barbers) {
                if (b.getId().equalsIgnoreCase(needle)) {
                    b.setPhotoUrl(photoUrlHttps != null ? photoUrlHttps : "");
                    persistBarbers();
                    BarberFirestoreSync.upsert(b, barbersOnDuty.contains(b.getId()));
                    notifyAppointmentSyncListeners();
                    return true;
                }
            }
        }
        return false;
    }

    public boolean updateBarberAvailability(String barberId, String hours, String days) {
        if (barberId == null || barberId.isEmpty()) {
            return false;
        }
        String needle = barberId.trim();
        synchronized (this) {
            for (Barber b : barbers) {
                if (b.getId().equalsIgnoreCase(needle)) {
                    b.setAvailabilityHours(hours);
                    b.setAvailabilityDays(days);
                    persistBarbers();
                    BarberFirestoreSync.upsert(b, barbersOnDuty.contains(b.getId()));
                    notifyAppointmentSyncListeners();
                    return true;
                }
            }
        }
        return false;
    }

    /** Remove barber from queue state (e.g. Super Admin deleted their account). */
    public void removeBarberAndQueues(String barberId) {
        if (barberId == null || barberId.isEmpty()) {
            return;
        }
        synchronized (this) {
            barbers.removeIf(b -> b.getId().equals(barberId));
            barbersOnDuty.remove(barberId);
            queues.remove(barberId);
            appointments.removeIf(a -> barberId.equals(a.barberId));
            persistBarbers();
            persistAppointments();
            BarberFirestoreSync.delete(barberId);
            notifyAppointmentSyncListeners();
        }
    }

    public List<Barber> getBarbers() {
        return new ArrayList<>(barbers);
    }

    public Barber getBarber(String barberId) {
        if (barberId == null || barberId.trim().isEmpty()) {
            return null;
        }
        String n = barberId.trim();
        for (Barber b : barbers) {
            if (b.getId().equalsIgnoreCase(n)) {
                return b;
            }
        }
        return null;
    }

    public boolean isBarberOnDuty(String barberId) {
        return barbersOnDuty.contains(barberId);
    }

    public void setBarberOnDuty(String barberId, boolean onDuty) {
        if (onDuty) {
            barbersOnDuty.add(barberId);
        } else {
            barbersOnDuty.remove(barberId);
        }
        Barber b = getBarber(barberId);
        if (b != null) {
            BarberFirestoreSync.upsert(b, onDuty);
        }
        notifyAppointmentSyncListeners();
    }

    public List<Barber> getBarbersOnDuty() {
        List<Barber> result = new ArrayList<>();
        for (Barber b : barbers) {
            if (barbersOnDuty.contains(b.getId())) result.add(b);
        }
        return result;
    }

    // ── Queue management ──

    /** Returned by {@link #bookAppointment} when this customer already has any Waiting appointment. */
    public static final long BOOKING_DUPLICATE = -1L;

    /** Returned when the customer name is empty after trim. */
    public static final long BOOKING_EMPTY_NAME = -2L;

    /** In-shop only: barber must be on duty. Home service ({@code homeService == true}) skips this. */
    public static final long BOOKING_BARBER_INACTIVE = -3L;

    /** Home service requires a preferred date & time. */
    public static final long BOOKING_HOME_TIME_REQUIRED = -4L;

    /** Home service preferred time must be in the future. */
    public static final long BOOKING_HOME_TIME_PAST = -5L;

    private static String normalizeCustomerName(String name) {
        return name == null ? "" : name.trim();
    }

    private static String normalizeCustomerSyncId(String syncId) {
        return syncId == null ? "" : syncId.trim();
    }

    /** Call only while holding {@code synchronized (this)}. */
    private boolean hasAnyWaitingBookingLocked(String customerNameNorm, String customerSyncIdNorm) {
        String sid = normalizeCustomerSyncId(customerSyncIdNorm);
        for (Appointment a : appointments) {
            if (!"Waiting".equals(a.status)) {
                continue;
            }
            if (normalizeCustomerName(a.customerName).equalsIgnoreCase(customerNameNorm)) {
                return true;
            }
            if (!sid.isEmpty() && sid.equals(normalizeCustomerSyncId(a.customerSyncId))) {
                return true;
            }
        }
        return false;
    }

    /**
     * True if this customer already has a Waiting appointment with any barber
     * (only one active booking at a time until cancelled or completed).
     */
    public boolean hasAnyWaitingBooking(String customerName) {
        return hasAnyWaitingBooking(customerName, "");
    }

    /** Same as name-only check, but also treats matching {@link Appointment#customerSyncId} as the same customer. */
    public boolean hasAnyWaitingBooking(String customerName, String customerSyncId) {
        String cn = normalizeCustomerName(customerName);
        String sid = normalizeCustomerSyncId(customerSyncId);
        if (cn.isEmpty() && sid.isEmpty()) {
            return false;
        }
        synchronized (this) {
            return hasAnyWaitingBookingLocked(cn, sid);
        }
    }

    /**
     * @return booking timestamp (unique id for this booking); use for reminders / cancel alarm;
     *         or {@link #BOOKING_DUPLICATE} if already waiting anywhere,
     *         or {@link #BOOKING_BARBER_INACTIVE} for in-shop when the barber is not on duty
     */
    public long bookAppointment(String barberId, String customerName) {
        return bookAppointment(barberId, customerName, "");
    }

    /** Books in-shop; stores {@code customerSyncId} on the appointment for cross-device Appointments + feedback. */
    public long bookAppointment(String barberId, String customerName, String customerSyncId) {
        return bookAppointment(barberId, customerName, false, 0L, customerSyncId, "");
    }

    /**
     * In-shop booking with requested haircut / service type (shown to the barber).
     */
    public long bookAppointment(String barberId, String customerName, String customerSyncId, String serviceType) {
        return bookAppointment(barberId, customerName, false, 0L, customerSyncId, serviceType);
    }

    /**
     * @param homeService true when booked from home-service flow ({@link PrivateBookActivity});
     *                    advance booking allowed even if the barber is not on duty yet
     */
    public long bookAppointment(String barberId, String customerName, boolean homeService) {
        return bookAppointment(barberId, customerName, homeService, 0L, "");
    }

    /**
     * Home service: {@code homeServicePreferredMillis} is when the customer wants the visit (must be &gt; 0 and
     * in the future). Used for {@link Appointment#scheduledArrivalMillis} and reminders.
     */
    public long bookAppointment(String barberId, String customerName, boolean homeService,
                                long homeServicePreferredMillis) {
        return bookAppointment(barberId, customerName, homeService, homeServicePreferredMillis, "");
    }

    public long bookAppointment(String barberId, String customerName, boolean homeService,
                                long homeServicePreferredMillis, String customerSyncId) {
        return bookAppointment(barberId, customerName, homeService, homeServicePreferredMillis, customerSyncId, "");
    }

    public long bookAppointment(String barberId, String customerName, boolean homeService,
                                long homeServicePreferredMillis, String customerSyncId, String serviceType) {
        return bookAppointmentWithHomeExtras(barberId, customerName, homeService, homeServicePreferredMillis,
                "", "", false, 0, 0, customerSyncId, serviceType);
    }

    /**
     * Home service booking with address / phone / map pin for the barber queue (saved on the appointment).
     * Pass {@link Double#NaN} for lat/lon when GPS is unknown.
     */
    public long bookHomeServiceAppointment(String barberId, String customerName, long homeServicePreferredMillis,
                                           String serviceAddress, String servicePhone,
                                           double lat, double lon) {
        return bookHomeServiceAppointment(barberId, customerName, homeServicePreferredMillis,
                serviceAddress, servicePhone, lat, lon, "");
    }

    public long bookHomeServiceAppointment(String barberId, String customerName, long homeServicePreferredMillis,
                                           String serviceAddress, String servicePhone,
                                           double lat, double lon, String customerSyncId) {
        return bookHomeServiceAppointment(barberId, customerName, homeServicePreferredMillis,
                serviceAddress, servicePhone, lat, lon, customerSyncId, "");
    }

    public long bookHomeServiceAppointment(String barberId, String customerName, long homeServicePreferredMillis,
                                           String serviceAddress, String servicePhone,
                                           double lat, double lon, String customerSyncId, String serviceType) {
        boolean hasCoords = homeServiceCoordsLookValid(lat, lon);
        String addr = serviceAddress != null ? serviceAddress.trim() : "";
        String phone = servicePhone != null ? servicePhone.trim() : "";
        return bookAppointmentWithHomeExtras(barberId, customerName, true, homeServicePreferredMillis,
                addr, phone, hasCoords, hasCoords ? lat : 0, hasCoords ? lon : 0, customerSyncId, serviceType);
    }

    /** Same checks as admin maps: NaN, range, and 0,0 treated as no pin. */
    static boolean homeServiceCoordsLookValid(double lat, double lon) {
        if (Double.isNaN(lat) || Double.isNaN(lon)) {
            return false;
        }
        if (Math.abs(lat) > 90 || Math.abs(lon) > 180) {
            return false;
        }
        return lat != 0 || lon != 0;
    }

    private long bookAppointmentWithHomeExtras(String barberId, String customerName, boolean homeService,
                                               long homeServicePreferredMillis,
                                               String homeAddress, String homePhone,
                                               boolean homeHasCoordinates, double homeLat, double homeLon,
                                               String customerSyncId, String serviceType) {
        String cn = normalizeCustomerName(customerName);
        String csid = normalizeCustomerSyncId(customerSyncId);
        String st = serviceType != null ? serviceType.trim() : "";
        synchronized (this) {
            if (cn.isEmpty()) {
                return BOOKING_EMPTY_NAME;
            }
            if (hasAnyWaitingBookingLocked(cn, csid)) {
                return BOOKING_DUPLICATE;
            }
            if (!homeService && !barbersOnDuty.contains(barberId)) {
                return BOOKING_BARBER_INACTIVE;
            }
            if (homeService) {
                if (homeServicePreferredMillis <= 0) {
                    return BOOKING_HOME_TIME_REQUIRED;
                }
                if (homeServicePreferredMillis <= System.currentTimeMillis()) {
                    return BOOKING_HOME_TIME_PAST;
                }
            }

            List<String> queue = queues.get(barberId);
            if (queue == null) {
                queue = new ArrayList<>();
                queues.put(barberId, queue);
            }
            queue.add(cn);

            long now = System.currentTimeMillis();
            bookingTimestamps.add(now);
            persistTimestamps();

            Barber barber = getBarber(barberId);
            String barberName = barber != null ? barber.getDisplayName() : barberId;
            int idx = queue.size() - 1;
            long scheduledArrivalMillis;
            long preferredStored = 0L;
            if (homeService && homeServicePreferredMillis > 0) {
                scheduledArrivalMillis = homeServicePreferredMillis;
                preferredStored = homeServicePreferredMillis;
            } else {
                scheduledArrivalMillis = now + (30L + idx * 15L) * 60_000L;
            }
            String hAddr = homeService ? homeAddress : "";
            String hPhone = homeService ? homePhone : "";
            boolean hCoord = homeService && homeHasCoordinates;
            double hLat = hCoord ? homeLat : 0;
            double hLon = hCoord ? homeLon : 0;
            Appointment ap = new Appointment(barberId, barberName, cn, now, "Waiting",
                    scheduledArrivalMillis, homeService, preferredStored,
                    hAddr, hPhone, hCoord, hLat, hLon, csid, st, 0L);
            appointments.add(ap);
            persistAppointments();
            AppointmentFirestoreSync.upsert(ap);
            if (context != null) {
                Calendar cal = Calendar.getInstance();
                cal.setTimeInMillis(now);
                int hour = cal.get(Calendar.HOUR_OF_DAY);
                int priorDone = countPriorDoneBookingsLocked(cn, csid, now);
                String channel = homeService ? "home" : "in_shop";
                CutifyAnalytics.logBookingCreated(context, channel, hour, !csid.isEmpty(), priorDone > 0);
            }
            notifyAppointmentSyncListeners();
            return now;
        }
    }

    /**
     * Barber adds someone who walked in without using the app (no phone). Same as an in-shop booking but
     * does not require the barber to be marked “on duty” in the app. Still blocks duplicate active bookings
     * for the same customer name. Does not schedule a device reminder (customer has no app).
     *
     * @return booking timestamp, or {@link #BOOKING_EMPTY_NAME}, {@link #BOOKING_DUPLICATE}
     */
    public long addWalkInForBarber(String barberId, String customerName) {
        String cn = normalizeCustomerName(customerName);
        synchronized (this) {
            if (cn.isEmpty()) {
                return BOOKING_EMPTY_NAME;
            }
            if (hasAnyWaitingBookingLocked(cn, "")) {
                return BOOKING_DUPLICATE;
            }

            List<String> queue = queues.get(barberId);
            if (queue == null) {
                queue = new ArrayList<>();
                queues.put(barberId, queue);
            }
            queue.add(cn);

            long now = System.currentTimeMillis();
            bookingTimestamps.add(now);
            persistTimestamps();

            Barber barber = getBarber(barberId);
            String barberName = barber != null ? barber.getDisplayName() : barberId;
            int idx = queue.size() - 1;
            long scheduledArrivalMillis = now + (30L + idx * 15L) * 60_000L;
            Appointment ap = new Appointment(barberId, barberName, cn, now, "Waiting",
                    scheduledArrivalMillis, false, 0L, "", "", false, 0, 0, "", "", 0L);
            appointments.add(ap);
            persistAppointments();
            AppointmentFirestoreSync.upsert(ap);
            if (context != null) {
                Calendar cal = Calendar.getInstance();
                cal.setTimeInMillis(now);
                int hour = cal.get(Calendar.HOUR_OF_DAY);
                int priorDone = countPriorDoneBookingsLocked(cn, "", now);
                CutifyAnalytics.logBookingCreated(context, "walk_in", hour, false, priorDone > 0);
            }
            notifyAppointmentSyncListeners();
            return now;
        }
    }

    public static long startOfTodayMillis() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }

    /** Haircuts marked Done today (local calendar day). */
    public int getHaircutsTodayForBarber(String barberId) {
        long start = startOfTodayMillis();
        synchronized (this) {
            int n = 0;
            for (Appointment a : appointments) {
                if (a.barberId.equals(barberId) && "Done".equals(a.status) && a.timestamp >= start) {
                    n++;
                }
            }
            return n;
        }
    }

    /** Home-service bookings created today for this barber. */
    public int getHomeServiceBookingsTodayForBarber(String barberId) {
        long start = startOfTodayMillis();
        synchronized (this) {
            int n = 0;
            for (Appointment a : appointments) {
                if (a.barberId.equals(barberId) && a.homeService && a.timestamp >= start) {
                    n++;
                }
            }
            return n;
        }
    }

    public int getBarbersOnDutyCount() {
        return barbersOnDuty.size();
    }

    public List<BarberReport> getReportsForBarberDisplayName(String barberDisplayName) {
        List<BarberReport> out = new ArrayList<>();
        if (barberDisplayName == null) {
            return out;
        }
        for (BarberReport r : reports) {
            if (barberDisplayName.equals(r.barberName)) {
                out.add(r);
            }
        }
        return out;
    }

    public Appointment findAppointment(long bookingTimestamp) {
        synchronized (this) {
            for (Appointment a : appointments) {
                if (a.timestamp == bookingTimestamp) {
                    return a;
                }
            }
            return null;
        }
    }

    /**
     * Remove customer from queue and mark appointment cancelled.
     * @param bookingTs if &gt; 0, only cancel that exact booking; otherwise first waiting match
     * @return booking timestamp for alarm cancel, or -1 if nothing cancelled
     */
    public long cancelQueueEntry(String barberId, String customerName, long bookingTs) {
        return cancelQueueEntry(barberId, customerName, bookingTs, "");
    }

    /**
     * Cancels a waiting row if the customer matches by name and/or {@link Appointment#customerSyncId}.
     */
    public long cancelQueueEntry(String barberId, String customerName, long bookingTs, String customerSyncId) {
        String cn = normalizeCustomerName(customerName);
        String sid = normalizeCustomerSyncId(customerSyncId);
        synchronized (this) {
            Appointment target = null;
            for (Appointment a : appointments) {
                if (!a.barberId.equals(barberId)) {
                    continue;
                }
                boolean nameOk = normalizeCustomerName(a.customerName).equalsIgnoreCase(cn);
                boolean syncOk = !sid.isEmpty() && sid.equals(normalizeCustomerSyncId(a.customerSyncId));
                if (!nameOk && !syncOk) {
                    continue;
                }
                if (!"Waiting".equals(a.status)) {
                    continue;
                }
                if (bookingTs > 0 && a.timestamp != bookingTs) {
                    continue;
                }
                target = a;
                break;
            }
            if (target == null) {
                return -1;
            }
            boolean home = target.homeService;
            target.status = "Cancelled";
            rebuildQueuesFromWaitingAppointments();
            persistAppointments();
            AppointmentFirestoreSync.upsert(target);
            if (context != null) {
                CutifyAnalytics.logBookingCancelled(context, home);
            }
            long ts = target.timestamp;
            notifyAppointmentSyncListeners();
            return ts;
        }
    }

    public List<String> getQueue(String barberId) {
        synchronized (this) {
            List<String> queue = queues.get(barberId);
            return queue != null ? new ArrayList<>(queue) : new ArrayList<>();
        }
    }

    public int getQueueSize(String barberId) {
        synchronized (this) {
            List<String> queue = queues.get(barberId);
            return queue != null ? queue.size() : 0;
        }
    }

    public void recordHaircutCompleted(String barberId, String customerName) {
        recordHaircutCompleted(barberId, customerName, -1L);
    }

    /**
     * @param bookingTs booking {@link Appointment#timestamp}; if &gt; 0, marks that exact waiting row Done
     *                  (needed when the same name appears twice). If &lt;= 0, first matching name wins.
     */
    public void recordHaircutCompleted(String barberId, String customerName, long bookingTs) {
        String cn = normalizeCustomerName(customerName);
        long doneBookingTs = -1;
        Appointment updated = null;
        String customerForNotify = cn;
        synchronized (this) {
            for (Appointment a : appointments) {
                if (!a.barberId.equals(barberId) || !"Waiting".equals(a.status)) {
                    continue;
                }
                if (!normalizeCustomerName(a.customerName).equalsIgnoreCase(cn)) {
                    continue;
                }
                if (bookingTs > 0 && a.timestamp != bookingTs) {
                    continue;
                }
                a.status = "Done";
                a.completedAtMillis = System.currentTimeMillis();
                doneBookingTs = a.timestamp;
                customerForNotify = a.customerName;
                updated = a;
                break;
            }
            rebuildQueuesFromWaitingAppointments();
            persistAppointments();
            if (updated != null) {
                AppointmentFirestoreSync.upsert(updated);
            }
        }
        notifyAppointmentSyncListeners();

        if (context != null && doneBookingTs >= 0) {
            BookingNotificationScheduler.cancelReminder(context, doneBookingTs, barberId, customerForNotify);
        }

        if (updated != null && context != null) {
            long waitMs = System.currentTimeMillis() - updated.timestamp;
            int waitMin = (int) Math.min(waitMs / 60000L, 24 * 60L);
            CutifyAnalytics.logBookingCompleted(context, updated.homeService, waitMin);
        }

        // Rate/report runs on the customer's device when Firestore syncs Waiting → Done
        // (see AppointmentFirestoreSync#maybeEnqueueCustomerFeedbackAfterRemoteMerge).
    }

    /** Waiting appointments for this barber, in stable list order (for admin queue UI). */
    public List<Appointment> getWaitingAppointmentsForBarber(String barberId) {
        if (barberId == null) {
            return new ArrayList<>();
        }
        synchronized (this) {
            List<Appointment> out = new ArrayList<>();
            for (Appointment a : appointments) {
                if (barberId.equals(a.barberId) && "Waiting".equals(a.status)) {
                    out.add(a);
                }
            }
            out.sort(waitingOrderComparator());
            return out;
        }
    }

    // ── Appointments ──

    public List<Appointment> getAppointments() {
        synchronized (this) {
            return new ArrayList<>(appointments);
        }
    }

    public List<Appointment> getAppointmentsForUser(String userName) {
        return getAppointmentsForUser(userName, "");
    }

    /**
     * Lists bookings for this Cutify guest: same display name and/or same {@link Appointment#customerSyncId}
     * (so a second phone with linked profile still sees appointments).
     */
    public List<Appointment> getAppointmentsForUser(String userName, String customerSyncId) {
        String u = normalizeCustomerName(userName);
        String sid = normalizeCustomerSyncId(customerSyncId);
        synchronized (this) {
            List<Appointment> result = new ArrayList<>();
            if (u.isEmpty() && sid.isEmpty()) {
                return result;
            }
            for (Appointment a : appointments) {
                boolean byName = !u.isEmpty() && normalizeCustomerName(a.customerName).equalsIgnoreCase(u);
                boolean bySync = !sid.isEmpty() && sid.equals(normalizeCustomerSyncId(a.customerSyncId));
                if (byName || bySync) {
                    result.add(a);
                }
            }
            return result;
        }
    }

    /**
     * 0-based index in this barber’s in-shop waiting line (by booking time), or -1 if this row is not
     * an in-shop Waiting appointment.
     */
    public synchronized int getInShopWaitingZeroBasedIndex(long bookingTimestamp) {
        Appointment self = null;
        for (Appointment a : appointments) {
            if (a.timestamp == bookingTimestamp) {
                self = a;
                break;
            }
        }
        if (self == null || !"Waiting".equals(self.status) || self.homeService) {
            return -1;
        }
        List<Appointment> line = new ArrayList<>();
        for (Appointment a : appointments) {
            if (!self.barberId.equals(a.barberId)) {
                continue;
            }
            if (!"Waiting".equals(a.status) || a.homeService) {
                continue;
            }
            line.add(a);
        }
        Collections.sort(line, Comparator.comparingLong(a -> a.timestamp));
        for (int i = 0; i < line.size(); i++) {
            if (line.get(i).timestamp == bookingTimestamp) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Before a Firestore merge: each of this user’s in-shop Waiting bookings → current 0-based line index.
     */
    public synchronized Map<Long, Integer> snapshotInShopWaitingIndicesForLocalUser(
            String userName, String customerSyncId) {
        Map<Long, Integer> m = new HashMap<>();
        List<Appointment> mine = getAppointmentsForUser(userName, customerSyncId);
        for (Appointment a : mine) {
            if (!"Waiting".equals(a.status) || a.homeService) {
                continue;
            }
            int idx = getInShopWaitingZeroBasedIndex(a.timestamp);
            if (idx >= 0) {
                m.put(a.timestamp, idx);
            }
        }
        return m;
    }

    /** Snapshot of {@code timestamp → status} before applying a remote Firestore merge (for feedback detection). */
    synchronized Map<Long, String> copyAppointmentStatusesByTimestamp() {
        Map<Long, String> m = new HashMap<>();
        for (Appointment a : appointments) {
            m.put(a.timestamp, a.status);
        }
        return m;
    }

    /** Application context set in {@link #init(Context)}; used by sync layer. */
    Context getQueueContext() {
        return context;
    }

    /** Thread-safe copy for Firestore seeding (same package). */
    synchronized List<Appointment> copyAppointmentsSnapshot() {
        return new ArrayList<>(appointments);
    }

    synchronized List<Barber> copyBarbersSnapshot() {
        return new ArrayList<>(barbers);
    }

    synchronized Set<String> copyOnDutySnapshot() {
        return new HashSet<>(barbersOnDuty);
    }

    /** Apply cloud snapshot on main thread; updates prefs + queues. */
    synchronized void applyRemoteAppointmentsSnapshot(List<Appointment> remote) {
        appointments.clear();
        appointments.addAll(remote);
        Collections.sort(appointments, Comparator.comparingLong(a -> a.timestamp));
        rebuildQueuesFromWaitingAppointments();
        rebuildBookingTimestampsFromAppointments();
        persistAppointments();
        persistTimestamps();
        notifyAppointmentSyncListeners();
    }

    /** Apply real-time barber rows (display/rating/on-duty) from Firestore. */
    synchronized void applyRemoteBarberState(List<Barber> remoteBarbers, Set<String> onDutyIds) {
        if (remoteBarbers == null) {
            return;
        }
        barbers.clear();
        barbers.addAll(remoteBarbers);
        barbersOnDuty.clear();
        if (onDutyIds != null) {
            barbersOnDuty.addAll(onDutyIds);
        }
        persistBarbers();
        notifyAppointmentSyncListeners();
    }

    private void rebuildBookingTimestampsFromAppointments() {
        bookingTimestamps.clear();
        for (Appointment a : appointments) {
            bookingTimestamps.add(a.timestamp);
        }
    }

    /**
     * Prior {@code Done} rows for retention analytics (same sync id if set, else same name).
     * Must run with {@code synchronized (this)}; {@code beforeExclusive} is usually the new booking time.
     */
    private int countPriorDoneBookingsLocked(String customerNameNorm, String syncIdNorm, long beforeExclusive) {
        int cnt = 0;
        for (Appointment a : appointments) {
            if (!"Done".equals(a.status) || a.timestamp >= beforeExclusive) {
                continue;
            }
            if (!syncIdNorm.isEmpty()) {
                if (syncIdNorm.equals(normalizeCustomerSyncId(a.customerSyncId))) {
                    cnt++;
                }
            } else {
                if (customerNameNorm.equalsIgnoreCase(normalizeCustomerName(a.customerName))) {
                    cnt++;
                }
            }
        }
        return cnt;
    }

    private void notifyAppointmentSyncListeners() {
        mainHandler.post(() -> {
            for (Runnable r : appointmentSyncListeners) {
                try {
                    r.run();
                } catch (Exception ignored) {}
            }
        });
    }

    public void addAppointmentSyncListener(Runnable r) {
        if (r != null) {
            appointmentSyncListeners.add(r);
        }
    }

    public void removeAppointmentSyncListener(Runnable r) {
        appointmentSyncListeners.remove(r);
    }

    // ── Statistics ──

    public StatResult getDayStats() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return computeStats(cal.getTimeInMillis());
    }

    public StatResult getWeekStats() {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_YEAR, -7);
        return computeStats(cal.getTimeInMillis());
    }

    public StatResult getMonthStats() {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.MONTH, -1);
        return computeStats(cal.getTimeInMillis());
    }

    public StatResult getYearStats() {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.YEAR, -1);
        return computeStats(cal.getTimeInMillis());
    }

    /**
     * Same time windows as the dashboard tabs: 0 = today, 1 = last 7 days, 2 = last month, 3 = last year.
     */
    public long sinceMillisForDashboardPeriod(int period) {
        Calendar cal = Calendar.getInstance();
        switch (period) {
            case 1:
                cal.add(Calendar.DAY_OF_YEAR, -7);
                return cal.getTimeInMillis();
            case 2:
                cal.add(Calendar.MONTH, -1);
                return cal.getTimeInMillis();
            case 3:
                cal.add(Calendar.YEAR, -1);
                return cal.getTimeInMillis();
            default:
                cal.set(Calendar.HOUR_OF_DAY, 0);
                cal.set(Calendar.MINUTE, 0);
                cal.set(Calendar.SECOND, 0);
                cal.set(Calendar.MILLISECOND, 0);
                return cal.getTimeInMillis();
        }
    }

    public StatResult getStatsForDashboardPeriod(int period) {
        return computeStats(sinceMillisForDashboardPeriod(period));
    }

    /**
     * Deletes historical data in the rolling window {@code [since, now]}:
     * <ul>
     *     <li>Appointments that are not {@code Waiting} (Done / Cancelled)</li>
     *     <li>Staff reports ({@link BarberReport})</li>
     * </ul>
     * Active queue rows are never removed. Booking timestamps are rebuilt from remaining appointments
     * (dashboard “Peak hours” and totals update from what is left — there is no separate peak-hours store).
     */
    public synchronized SuperAdminDeleteResult deleteHistoricalRecordsForPeriod(int period) {
        long since = sinceMillisForDashboardPeriod(period);
        long now = System.currentTimeMillis();
        int apptRemoved = 0;
        int reportsRemoved = 0;

        Iterator<Appointment> aIt = appointments.iterator();
        while (aIt.hasNext()) {
            Appointment a = aIt.next();
            if (a.timestamp < since || a.timestamp > now) {
                continue;
            }
            if ("Waiting".equals(a.status)) {
                continue;
            }
            aIt.remove();
            AppointmentFirestoreSync.delete(a.timestamp);
            apptRemoved++;
        }

        Iterator<BarberReport> rIt = reports.iterator();
        while (rIt.hasNext()) {
            BarberReport r = rIt.next();
            if (r.timestamp < since || r.timestamp > now) {
                continue;
            }
            long ts = r.timestamp;
            pendingDeletedReportTimestamps.add(ts);
            rIt.remove();
            ReportFirestoreSync.delete(ts, () -> pendingDeletedReportTimestamps.remove(ts));
            reportsRemoved++;
        }

        rebuildBookingTimestampsFromAppointments();
        persistAppointments();
        persistTimestamps();
        persistReports();
        notifyAppointmentSyncListeners();
        return new SuperAdminDeleteResult(apptRemoved, reportsRemoved);
    }

    /** Result of {@link #deleteHistoricalRecordsForPeriod(int)}. */
    public static final class SuperAdminDeleteResult {
        public final int appointmentsRemoved;
        public final int reportsRemoved;

        public SuperAdminDeleteResult(int appointmentsRemoved, int reportsRemoved) {
            this.appointmentsRemoved = appointmentsRemoved;
            this.reportsRemoved = reportsRemoved;
        }
    }

    private StatResult computeStats(long sinceMillis) {
        int total = 0;
        Map<String, Integer> perBarber = new LinkedHashMap<>();
        Map<Integer, Integer> peakHours = new TreeMap<>();

        Calendar cal = Calendar.getInstance();
        for (Long ts : bookingTimestamps) {
            if (ts >= sinceMillis) {
                total++;
                cal.setTimeInMillis(ts);
                int hour = cal.get(Calendar.HOUR_OF_DAY);
                peakHours.put(hour, peakHours.getOrDefault(hour, 0) + 1);
            }
        }

        Map<String, Integer> perBarberHome = new LinkedHashMap<>();
        synchronized (this) {
            for (Appointment a : appointments) {
                if (a.timestamp >= sinceMillis) {
                    String bn = a.barberName;
                    perBarber.put(bn, perBarber.getOrDefault(bn, 0) + 1);
                    if (a.homeService) {
                        perBarberHome.put(bn, perBarberHome.getOrDefault(bn, 0) + 1);
                    }
                }
            }
        }

        return new StatResult(total, perBarber, perBarberHome, peakHours);
    }

    /**
     * Peak-hours dashboard: Mon–Sun × 9 AM–8 PM heatmap combines <strong>new bookings</strong> (booking time)
     * and <strong>completions</strong> (when admin taps Done, {@link Appointment#completedAtMillis}).
     * Top service uses completed cuts with a non-empty {@link Appointment#serviceType} in the window.
     */
    public PeakHoursDashboardData computePeakHoursDashboard(long sinceMillis) {
        long now = System.currentTimeMillis();
        int[][] heat = new int[7][12];
        int[] hourly24 = new int[24];
        int[] dayTotals = new int[7];
        int bookingEventsInPeriod = 0;
        Calendar cal = Calendar.getInstance();
        Map<String, Integer> topServiceCounts = new HashMap<>();
        Map<String, String> topServiceLabels = new HashMap<>();
        synchronized (this) {
            for (Long ts : bookingTimestamps) {
                if (ts >= sinceMillis && ts <= now) {
                    bookingEventsInPeriod++;
                }
                accumulatePeakInstant(ts, sinceMillis, now, heat, hourly24, dayTotals, cal);
            }
            for (Appointment a : appointments) {
                if (!"Done".equals(a.status) || a.completedAtMillis <= 0) {
                    continue;
                }
                accumulatePeakInstant(a.completedAtMillis, sinceMillis, now, heat, hourly24, dayTotals, cal);
            }
            for (Appointment a : appointments) {
                if (!"Done".equals(a.status)) {
                    continue;
                }
                long whenDone = a.completedAtMillis > 0 ? a.completedAtMillis : a.timestamp;
                if (whenDone < sinceMillis || whenDone > now) {
                    continue;
                }
                String raw = a.serviceType != null ? a.serviceType.trim() : "";
                if (raw.isEmpty()) {
                    continue;
                }
                String key = raw.toLowerCase(Locale.ROOT);
                topServiceLabels.putIfAbsent(key, raw);
                topServiceCounts.put(key, topServiceCounts.getOrDefault(key, 0) + 1);
            }
        }

        int busiestDayIndex = 0;
        int busiestTotal = -1;
        for (int d = 0; d < 7; d++) {
            if (dayTotals[d] > busiestTotal) {
                busiestTotal = dayTotals[d];
                busiestDayIndex = d;
            }
        }
        if (busiestTotal < 0) {
            busiestTotal = 0;
        }

        int peakWindowStart = 0;
        int peakWindowSum = -1;
        for (int w = 0; w < 23; w++) {
            int s = hourly24[w] + hourly24[w + 1];
            if (s > peakWindowSum) {
                peakWindowSum = s;
                peakWindowStart = w;
            }
        }
        if (peakWindowSum < 0) {
            peakWindowSum = 0;
        }

        double spanMs = Math.max(1, now - sinceMillis);
        int spanDays = (int) Math.max(1, Math.ceil(spanMs / 86400000.0));
        float avgPerDay = bookingEventsInPeriod / (float) spanDays;

        String topName = "";
        int topCount = 0;
        int labeledTotal = 0;
        for (Map.Entry<String, Integer> e : topServiceCounts.entrySet()) {
            labeledTotal += e.getValue();
            if (e.getValue() > topCount) {
                topCount = e.getValue();
                topName = topServiceLabels.getOrDefault(e.getKey(), e.getKey());
            }
        }
        float topPct = labeledTotal > 0 ? (100f * topCount) / labeledTotal : 0f;
        boolean hasTop = topCount > 0 && !topName.isEmpty();

        return new PeakHoursDashboardData(heat, hourly24, busiestDayIndex, busiestTotal,
                peakWindowStart, peakWindowSum, avgPerDay, bookingEventsInPeriod,
                topName, topCount, topPct, hasTop);
    }

    /**
     * Counts each instant toward day-of-week totals and full 24h histogram. Heatmap only uses 9 AM–8 PM.
     */
    private static void accumulatePeakInstant(long ts, long sinceMillis, long now,
            int[][] heat, int[] hourly24, int[] dayTotals, Calendar cal) {
        if (ts < sinceMillis || ts > now) {
            return;
        }
        cal.setTimeInMillis(ts);
        int dow = cal.get(Calendar.DAY_OF_WEEK);
        int row = mondayBasedRow(dow);
        dayTotals[row]++;
        int hour = cal.get(Calendar.HOUR_OF_DAY);
        if (hour >= 0 && hour < 24) {
            hourly24[hour]++;
        }
        if (hour >= 9 && hour <= 20) {
            int col = hour - 9;
            heat[row][col]++;
        }
    }

    private static int mondayBasedRow(int calendarDayOfWeek) {
        if (calendarDayOfWeek == Calendar.SUNDAY) {
            return 6;
        }
        return calendarDayOfWeek - Calendar.MONDAY;
    }

    public static final class PeakHoursDashboardData {
        /** Rows Mon–Sun, columns 9 AM–8 PM (12 slots). */
        public final int[][] heatmap;
        /** Full-day histogram (hour 0–23) for the hourly bar chart. */
        public final int[] hourly24;
        public final int busiestDayIndex;
        public final int busiestDayTotal;
        /** Start hour (0–22) of the busiest 2-hour window. */
        public final int peakWindowStartHour;
        public final int peakWindowTotalBookings;
        public final float avgVisitsPerDay;
        /** New bookings in the window (for avg / empty checks). */
        public final int totalBookingsInPeriod;
        /** Most common {@link Appointment#serviceType} among completed cuts in the window. */
        public final String topServiceName;
        public final int topServiceCount;
        /** Share of labeled completed cuts (0–100). */
        public final float topServicePercent;
        public final boolean hasTopService;

        public PeakHoursDashboardData(int[][] heatmap, int[] hourly24, int busiestDayIndex,
                int busiestDayTotal, int peakWindowStartHour, int peakWindowTotalBookings,
                float avgVisitsPerDay, int totalBookingsInPeriod,
                String topServiceName, int topServiceCount, float topServicePercent, boolean hasTopService) {
            this.heatmap = heatmap;
            this.hourly24 = hourly24;
            this.busiestDayIndex = busiestDayIndex;
            this.busiestDayTotal = busiestDayTotal;
            this.peakWindowStartHour = peakWindowStartHour;
            this.peakWindowTotalBookings = peakWindowTotalBookings;
            this.avgVisitsPerDay = avgVisitsPerDay;
            this.totalBookingsInPeriod = totalBookingsInPeriod;
            this.topServiceName = topServiceName != null ? topServiceName : "";
            this.topServiceCount = topServiceCount;
            this.topServicePercent = topServicePercent;
            this.hasTopService = hasTopService;
        }
    }

    // ── Feedback ──

    public void addPendingFeedback(String barberId, String barberName, String customerName) {
        pendingFeedbacks.add(new PendingFeedback(barberId, barberName, customerName));
        notifyAppointmentSyncListeners();
    }

    public List<PendingFeedback> consumePendingFeedbacks() {
        List<PendingFeedback> result = new ArrayList<>(pendingFeedbacks);
        pendingFeedbacks.clear();
        return result;
    }

    public void rateBarber(String barberId, float rating) {
        for (Barber b : barbers) {
            if (b.getId().equals(barberId)) {
                b.addRating(rating);
                persistBarbers();
                BarberFirestoreSync.upsert(b, barbersOnDuty.contains(barberId));
                notifyAppointmentSyncListeners();
                return;
            }
        }
    }

    // ── Reports ──

    public void addReport(String barberName, String description, String reporterName) {
        BarberReport report = new BarberReport(barberName, description, reporterName, System.currentTimeMillis());
        reports.add(report);
        persistReports();
        ReportFirestoreSync.upsert(report);
        notifyAppointmentSyncListeners();
    }

    public List<BarberReport> getReports() {
        return new ArrayList<>(reports);
    }

    public void deleteReport(long timestamp) {
        pendingDeletedReportTimestamps.add(timestamp);
        synchronized (this) {
            reports.removeIf(r -> r.timestamp == timestamp);
            persistReports();
        }
        ReportFirestoreSync.delete(timestamp, () -> pendingDeletedReportTimestamps.remove(timestamp));
        notifyAppointmentSyncListeners();
    }

    synchronized List<BarberReport> copyReportsSnapshot() {
        return new ArrayList<>(reports);
    }

    /**
     * One-time: push local-only reports to Firestore when remote collection is still empty.
     * After this, empty remote snapshots clear local (server is source of truth).
     */
    boolean shouldMigrateLocalReportsToRemoteOnce() {
        SharedPreferences prefs = getPrefs();
        if (prefs == null) {
            return false;
        }
        return !prefs.getBoolean(KEY_REPORTS_FS_MIGRATED, false);
    }

    void markReportsFirestoreMigrationDone() {
        SharedPreferences prefs = getPrefs();
        if (prefs != null) {
            prefs.edit().putBoolean(KEY_REPORTS_FS_MIGRATED, true).apply();
        }
    }

    synchronized void applyRemoteReportsSnapshot(List<BarberReport> remote) {
        reports.clear();
        if (remote != null) {
            for (BarberReport r : remote) {
                if (pendingDeletedReportTimestamps.contains(r.timestamp)) {
                    continue;
                }
                reports.add(r);
            }
            reports.sort(Comparator.comparingLong(r -> r.timestamp));
        }
        persistReports();
        notifyAppointmentSyncListeners();
    }

    // ── Barber-specific stats ──

    public int getHaircutsForBarber(String barberId) {
        synchronized (this) {
            int count = 0;
            for (Appointment a : appointments) {
                if (a.barberId.equals(barberId) && "Done".equals(a.status)) {
                    count++;
                }
            }
            return count;
        }
    }

    // ── Persistence ──

    private SharedPreferences getPrefs() {
        if (context == null) return null;
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    private void persistBarbers() {
        SharedPreferences prefs = getPrefs();
        if (prefs == null) return;
        JSONArray arr = new JSONArray();
        try {
            for (Barber b : barbers) {
                JSONObject o = new JSONObject();
                o.put("id", b.getId());
                o.put("displayName", b.getDisplayName());
                o.put("totalRating", b.getTotalRating());
                o.put("ratingCount", b.getRatingCount());
                o.put("photoUrl", b.getPhotoUrl());
                o.put("availabilityHours", b.getAvailabilityHours());
                o.put("availabilityDays", b.getAvailabilityDays());
                arr.put(o);
            }
        } catch (JSONException e) {
            return;
        }
        prefs.edit().putString(KEY_BARBERS, arr.toString()).apply();
    }

    private void loadPersistedBarbers() {
        SharedPreferences prefs = getPrefs();
        if (prefs == null) return;
        String data = prefs.getString(KEY_BARBERS, "");
        if (data.isEmpty()) return;
        barbers.clear();
        if (data.trim().startsWith("[")) {
            try {
                JSONArray arr = new JSONArray(data);
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject o = arr.getJSONObject(i);
                    Barber b = new Barber(o.optString("id"), o.optString("displayName"));
                    b.setTotalRating((float) o.optDouble("totalRating", 0));
                    b.setRatingCount(o.optInt("ratingCount", 0));
                    b.setPhotoUrl(o.optString("photoUrl", ""));
                    b.setAvailabilityHours(o.optString("availabilityHours", ""));
                    b.setAvailabilityDays(o.optString("availabilityDays", ""));
                    barbers.add(b);
                }
            } catch (JSONException e) {
                barbers.clear();
                loadPersistedBarbersLegacy(data);
            }
        } else {
            loadPersistedBarbersLegacy(data);
        }
    }

    /** Legacy pipe-delimited format (before photoUrl; URLs break ::: splits). */
    private void loadPersistedBarbersLegacy(String data) {
        String[] items = data.split("\\|\\|\\|");
        for (String item : items) {
            String[] parts = item.split(":::");
            if (parts.length >= 2) {
                Barber b = new Barber(parts[0], parts[1]);
                if (parts.length >= 4) {
                    try {
                        b.setTotalRating(Float.parseFloat(parts[2]));
                        b.setRatingCount(Integer.parseInt(parts[3]));
                    } catch (NumberFormatException ignored) {}
                }
                barbers.add(b);
            }
        }
    }

    private void persistTimestamps() {
        SharedPreferences prefs = getPrefs();
        if (prefs == null) return;
        StringBuilder sb = new StringBuilder();
        for (Long ts : bookingTimestamps) {
            if (sb.length() > 0) sb.append(",");
            sb.append(ts);
        }
        prefs.edit().putString(KEY_TIMESTAMPS, sb.toString()).apply();
    }

    private void loadPersistedTimestamps() {
        SharedPreferences prefs = getPrefs();
        if (prefs == null) return;
        String data = prefs.getString(KEY_TIMESTAMPS, "");
        if (data.isEmpty()) return;
        bookingTimestamps.clear();
        String[] parts = data.split(",");
        for (String p : parts) {
            try {
                bookingTimestamps.add(Long.parseLong(p.trim()));
            } catch (NumberFormatException ignored) {}
        }
    }

    private void persistAppointments() {
        SharedPreferences prefs = getPrefs();
        if (prefs == null) return;
        JSONArray arr = new JSONArray();
        for (Appointment a : appointments) {
            try {
                JSONObject o = new JSONObject();
                o.put("barberId", a.barberId);
                o.put("barberName", a.barberName);
                o.put("customerName", a.customerName);
                o.put("timestamp", a.timestamp);
                o.put("status", a.status);
                o.put("scheduledArrivalMillis", a.scheduledArrivalMillis);
                o.put("homeService", a.homeService);
                o.put("homeServicePreferredMillis", a.homeServicePreferredMillis);
                o.put("homeServiceAddress", a.homeServiceAddress);
                o.put("homeServicePhone", a.homeServicePhone);
                o.put("homeServiceHasCoordinates", a.homeServiceHasCoordinates);
                o.put("homeServiceLat", a.homeServiceLat);
                o.put("homeServiceLon", a.homeServiceLon);
                o.put("customerSyncId", a.customerSyncId != null ? a.customerSyncId : "");
                o.put("serviceType", a.serviceType != null ? a.serviceType : "");
                o.put("completedAtMillis", a.completedAtMillis);
                arr.put(o);
            } catch (JSONException ignored) {}
        }
        prefs.edit().putString(KEY_APPOINTMENTS, arr.toString()).apply();
    }

    private void loadPersistedAppointments() {
        synchronized (this) {
            SharedPreferences prefs = getPrefs();
            if (prefs == null) {
                return;
            }
            String data = prefs.getString(KEY_APPOINTMENTS, "");
            if (data.isEmpty()) {
                rebuildQueuesFromWaitingAppointments();
                return;
            }
            appointments.clear();
            try {
                JSONArray arr = new JSONArray(data);
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject o = arr.getJSONObject(i);
                    String barberId = o.getString("barberId");
                    String barberName = o.optString("barberName", barberId);
                    appointments.add(new Appointment(
                            barberId,
                            barberName,
                            o.getString("customerName"),
                            o.getLong("timestamp"),
                            o.optString("status", "Waiting"),
                            o.optLong("scheduledArrivalMillis", o.getLong("timestamp")),
                            o.optBoolean("homeService", false),
                            o.optLong("homeServicePreferredMillis", 0L),
                            o.optString("homeServiceAddress", ""),
                            o.optString("homeServicePhone", ""),
                            o.optBoolean("homeServiceHasCoordinates", false),
                            o.optDouble("homeServiceLat", 0),
                            o.optDouble("homeServiceLon", 0),
                            o.optString("customerSyncId", ""),
                            o.optString("serviceType", ""),
                            o.optLong("completedAtMillis", 0L)));
                }
            } catch (JSONException e) {
                appointments.clear();
            }
            rebuildQueuesFromWaitingAppointments();
        }
    }

    private void persistReports() {
        SharedPreferences prefs = getPrefs();
        if (prefs == null) return;
        JSONArray arr = new JSONArray();
        for (BarberReport r : reports) {
            try {
                JSONObject o = new JSONObject();
                o.put("barberName", r.barberName);
                o.put("description", r.description);
                o.put("reporterName", r.reporterName);
                o.put("timestamp", r.timestamp);
                arr.put(o);
            } catch (JSONException ignored) {}
        }
        prefs.edit().putString(KEY_REPORTS, arr.toString()).apply();
    }

    private void loadPersistedReports() {
        SharedPreferences prefs = getPrefs();
        if (prefs == null) return;
        String data = prefs.getString(KEY_REPORTS, "");
        if (data.isEmpty()) return;
        reports.clear();
        try {
            JSONArray arr = new JSONArray(data);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                reports.add(new BarberReport(
                        o.optString("barberName", ""),
                        o.optString("description", ""),
                        o.optString("reporterName", ""),
                        o.optLong("timestamp", System.currentTimeMillis())));
            }
        } catch (JSONException ignored) {
            reports.clear();
        }
    }

    /** Restore per-barber queues from persisted Waiting appointments (after restart). */
    private void rebuildQueuesFromWaitingAppointments() {
        queues.clear();
        List<Appointment> orderedWaiting = new ArrayList<>();
        for (Appointment a : appointments) {
            if (!"Waiting".equals(a.status)) {
                continue;
            }
            orderedWaiting.add(a);
        }
        // Keep queue reconstruction deterministic and FIFO across restarts/sync merges.
        orderedWaiting.sort(waitingOrderComparator());
        for (Appointment a : orderedWaiting) {
            List<String> q = queues.get(a.barberId);
            if (q == null) {
                q = new ArrayList<>();
                queues.put(a.barberId, q);
            }
            q.add(a.customerName);
        }
    }

    /**
     * Queue order for waiting rows:
     * - In-shop: use scheduled arrival first (computed from queue position when booked/walk-in),
     *   then booking timestamp as tie-breaker.
     * - Home service: keep by preferred/scheduled time, then booking timestamp.
     *
     * Using scheduled arrival reduces wrong reordering when different devices have clock skew.
     */
    private static Comparator<Appointment> waitingOrderComparator() {
        return Comparator
                .comparingLong((Appointment a) -> a.scheduledArrivalMillis)
                .thenComparingLong(a -> a.timestamp);
    }

    // ── Inner classes ──

    public static class Appointment {
        public String barberId;
        public String barberName;
        public String customerName;
        public long timestamp;
        public String status;
        public long scheduledArrivalMillis;
        public boolean homeService;
        /** When {@code homeService}: millis for customer-requested visit time; 0 if unset (legacy). */
        public long homeServicePreferredMillis;
        public String homeServiceAddress;
        public String homeServicePhone;
        public boolean homeServiceHasCoordinates;
        public double homeServiceLat;
        public double homeServiceLon;
        /** Guest profile sync id from booking device; empty for walk-ins / legacy rows. */
        public String customerSyncId;
        /** Requested haircut / service (e.g. fade, trim); empty for walk-ins / legacy. */
        public String serviceType;
        /** Set when status becomes Done (wall clock); 0 if not done yet or legacy row. */
        public long completedAtMillis;

        public Appointment(String barberId, String barberName, String customerName,
                           long timestamp, String status, long scheduledArrivalMillis,
                           boolean homeService) {
            this(barberId, barberName, customerName, timestamp, status, scheduledArrivalMillis,
                    homeService, 0L);
        }

        public Appointment(String barberId, String barberName, String customerName,
                           long timestamp, String status, long scheduledArrivalMillis,
                           boolean homeService, long homeServicePreferredMillis) {
            this(barberId, barberName, customerName, timestamp, status, scheduledArrivalMillis,
                    homeService, homeServicePreferredMillis, "", "", false, 0, 0, "", "", 0L);
        }

        public Appointment(String barberId, String barberName, String customerName,
                           long timestamp, String status, long scheduledArrivalMillis,
                           boolean homeService, long homeServicePreferredMillis,
                           String homeServiceAddress, String homeServicePhone,
                           boolean homeServiceHasCoordinates, double homeServiceLat,
                           double homeServiceLon) {
            this(barberId, barberName, customerName, timestamp, status, scheduledArrivalMillis,
                    homeService, homeServicePreferredMillis, homeServiceAddress, homeServicePhone,
                    homeServiceHasCoordinates, homeServiceLat, homeServiceLon, "", "", 0L);
        }

        public Appointment(String barberId, String barberName, String customerName,
                           long timestamp, String status, long scheduledArrivalMillis,
                           boolean homeService, long homeServicePreferredMillis,
                           String homeServiceAddress, String homeServicePhone,
                           boolean homeServiceHasCoordinates, double homeServiceLat,
                           double homeServiceLon, String customerSyncId) {
            this(barberId, barberName, customerName, timestamp, status, scheduledArrivalMillis,
                    homeService, homeServicePreferredMillis, homeServiceAddress, homeServicePhone,
                    homeServiceHasCoordinates, homeServiceLat, homeServiceLon, customerSyncId, "", 0L);
        }

        public Appointment(String barberId, String barberName, String customerName,
                           long timestamp, String status, long scheduledArrivalMillis,
                           boolean homeService, long homeServicePreferredMillis,
                           String homeServiceAddress, String homeServicePhone,
                           boolean homeServiceHasCoordinates, double homeServiceLat,
                           double homeServiceLon, String customerSyncId, String serviceType) {
            this(barberId, barberName, customerName, timestamp, status, scheduledArrivalMillis,
                    homeService, homeServicePreferredMillis, homeServiceAddress, homeServicePhone,
                    homeServiceHasCoordinates, homeServiceLat, homeServiceLon, customerSyncId, serviceType, 0L);
        }

        public Appointment(String barberId, String barberName, String customerName,
                           long timestamp, String status, long scheduledArrivalMillis,
                           boolean homeService, long homeServicePreferredMillis,
                           String homeServiceAddress, String homeServicePhone,
                           boolean homeServiceHasCoordinates, double homeServiceLat,
                           double homeServiceLon, String customerSyncId, String serviceType,
                           long completedAtMillis) {
            this.barberId = barberId;
            this.barberName = barberName;
            this.customerName = customerName;
            this.timestamp = timestamp;
            this.status = status;
            this.scheduledArrivalMillis = scheduledArrivalMillis;
            this.homeService = homeService;
            this.homeServicePreferredMillis = homeServicePreferredMillis;
            this.homeServiceAddress = homeServiceAddress != null ? homeServiceAddress : "";
            this.homeServicePhone = homeServicePhone != null ? homeServicePhone : "";
            this.homeServiceHasCoordinates = homeServiceHasCoordinates;
            this.homeServiceLat = homeServiceLat;
            this.homeServiceLon = homeServiceLon;
            this.customerSyncId = customerSyncId == null ? "" : customerSyncId.trim();
            this.serviceType = serviceType != null ? serviceType.trim() : "";
            this.completedAtMillis = completedAtMillis;
        }
    }

    public static class PendingFeedback {
        public String barberId;
        public String barberName;
        public String customerName;

        public PendingFeedback(String barberId, String barberName, String customerName) {
            this.barberId = barberId;
            this.barberName = barberName;
            this.customerName = customerName;
        }
    }

    public static class BarberReport {
        public String barberName;
        public String description;
        public String reporterName;
        public long timestamp;

        public BarberReport(String barberName, String description,
                            String reporterName, long timestamp) {
            this.barberName = barberName;
            this.description = description;
            this.reporterName = reporterName;
            this.timestamp = timestamp;
        }
    }

    public static class StatResult {
        public int totalBookings;
        public Map<String, Integer> perBarber;
        /** Home service bookings in the same window, keyed by barber display name. */
        public Map<String, Integer> perBarberHomeService;
        public Map<Integer, Integer> peakHours;

        public StatResult(int totalBookings, Map<String, Integer> perBarber,
                          Map<String, Integer> perBarberHomeService,
                          Map<Integer, Integer> peakHours) {
            this.totalBookings = totalBookings;
            this.perBarber = perBarber;
            this.perBarberHomeService = perBarberHomeService;
            this.peakHours = peakHours;
        }
    }
}
