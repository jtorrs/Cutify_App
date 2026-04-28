package com.example.online_alot;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Patterns;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.core.widget.ImageViewCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.auth.FirebaseAuth;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class SuperAdminActivity extends AppCompatActivity {

    private static final int TAB_DAY = 0;
    private static final int TAB_WEEK = 1;
    private static final int TAB_MONTH = 2;
    private static final int TAB_YEAR = 3;
    private static final int COLOR_NAV_ACTIVE = 0xFFD4AF37;
    private static final int COLOR_NAV_INACTIVE = 0xFF6B7280;

    /** Matches tab order; used for analytics (Firebase / Mixpanel). */
    private static final String[] ANALYTICS_PERIOD_KEYS = {"day", "week", "month", "year"};

    private TextView tabDay, tabWeek, tabMonth, tabYear;
    private LinearLayout layoutStatContent, layoutReports;
    private PeakHoursHeatmapView peakHeatmap;
    private PeakHoursBarChartView peakBarChart;
    private TextView peakTvBusiestDayName;
    private TextView peakTvBusiestDayCount;
    private TextView peakTvPeakWindow;
    private TextView peakTvAvgVisits;
    private TextView peakTvTopService;
    private TextView peakTvTopServicePct;
    private LinearLayout layoutTodayBarbers;
    private TextView tvOnDutySummary;
    private ScrollView scrollStaff, scrollOverview;
    private TextView tvNavSuperStaff, tvNavSuperStats;
    private ImageView ivNavSuperStaff, ivNavSuperStats;
    private LinearLayout layoutManageStaffList;
    private TextView tvManageStaffEmpty;
    private EditText etInviteAdminEmail;
    private int currentTab = TAB_DAY;
    private boolean overviewVisible = true;
    private Handler refreshHandler;
    private Runnable refreshRunnable;
    private final Runnable firestoreSuperRefresh = this::refreshSuperFromFirestore;

    private ActivityResultLauncher<String> pdfSaveLauncher;

    /** Which Day/Week/Month/Year window the PDF export uses (set before save picker). */
    private int pendingPdfPeriod = TAB_DAY;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_super_admin);

        pdfSaveLauncher = registerForActivityResult(
                new ActivityResultContracts.CreateDocument("application/pdf"),
                this::onPdfSaveResult);

        tabDay = findViewById(R.id.tab_day);
        tabWeek = findViewById(R.id.tab_week);
        tabMonth = findViewById(R.id.tab_month);
        tabYear = findViewById(R.id.tab_year);
        layoutStatContent = findViewById(R.id.layout_stat_content);
        layoutReports = findViewById(R.id.layout_reports);
        peakHeatmap = findViewById(R.id.peak_heatmap);
        peakBarChart = findViewById(R.id.peak_bar_chart);
        peakTvBusiestDayName = findViewById(R.id.peak_tv_busiest_day_name);
        peakTvBusiestDayCount = findViewById(R.id.peak_tv_busiest_day_count);
        peakTvPeakWindow = findViewById(R.id.peak_tv_peak_window);
        peakTvAvgVisits = findViewById(R.id.peak_tv_avg_visits);
        peakTvTopService = findViewById(R.id.peak_tv_top_service);
        peakTvTopServicePct = findViewById(R.id.peak_tv_top_service_pct);
        layoutTodayBarbers = findViewById(R.id.layout_today_barbers);
        tvOnDutySummary = findViewById(R.id.tv_on_duty_summary);
        scrollStaff = findViewById(R.id.scroll_staff);
        scrollOverview = findViewById(R.id.scroll_overview);
        tvNavSuperStaff = findViewById(R.id.tv_nav_super_staff);
        tvNavSuperStats = findViewById(R.id.tv_nav_super_stats);
        ivNavSuperStaff = findViewById(R.id.iv_nav_super_staff);
        ivNavSuperStats = findViewById(R.id.iv_nav_super_stats);

        findViewById(R.id.nav_super_staff).setOnClickListener(v -> showStaffPanel());
        findViewById(R.id.nav_super_stats).setOnClickListener(v -> showOverviewPanel());

        tabDay.setOnClickListener(v -> selectTab(TAB_DAY));
        tabWeek.setOnClickListener(v -> selectTab(TAB_WEEK));
        tabMonth.setOnClickListener(v -> selectTab(TAB_MONTH));
        tabYear.setOnClickListener(v -> selectTab(TAB_YEAR));

        findViewById(R.id.btn_sign_out).setOnClickListener(v -> confirmSignOut());

        layoutManageStaffList = findViewById(R.id.layout_manage_staff_list);
        tvManageStaffEmpty = findViewById(R.id.tv_manage_staff_empty);
        etInviteAdminEmail = findViewById(R.id.et_invite_admin_email);

        findViewById(R.id.btn_delete_staff).setOnClickListener(v -> openDeleteStaffPicker());

        TextView tvInviteStatus = findViewById(R.id.tv_invite_status);
        findViewById(R.id.btn_send_admin_invite).setOnClickListener(v -> {
            String email = etInviteAdminEmail.getText().toString().trim();
            if (email.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                Toast.makeText(this, R.string.super_invite_email_invalid, Toast.LENGTH_LONG).show();
                return;
            }
            showInviteConfirmDialog(email, tvInviteStatus);
        });
        findViewById(R.id.btn_view_audit_log).setOnClickListener(v -> showAuditLogDialog());
        findViewById(R.id.btn_export_super_pdf).setOnClickListener(v -> showDownloadReportPeriodPicker());
        findViewById(R.id.btn_reset_period_records).setOnClickListener(v -> showResetRecordsPeriodPicker());
        refreshSecuritySection();

        refreshHandler = new Handler(Looper.getMainLooper());
        refreshRunnable = () -> {
            refreshSecuritySection();
            if (!overviewVisible) {
                refreshManageStaffList();
            }
            if (overviewVisible) {
                refreshTodayOverview();
                refreshStats();
                refreshReports();
            }
            refreshHandler.postDelayed(refreshRunnable, 3000);
        };

        selectTab(TAB_DAY);
        showOverviewPanel();
    }

    private void showOverviewPanel() {
        overviewVisible = true;
        scrollOverview.setVisibility(View.VISIBLE);
        scrollStaff.setVisibility(View.GONE);
        tvNavSuperStats.setTextColor(COLOR_NAV_ACTIVE);
        tvNavSuperStats.setTypeface(null, Typeface.BOLD);
        tvNavSuperStaff.setTextColor(COLOR_NAV_INACTIVE);
        tvNavSuperStaff.setTypeface(null, Typeface.NORMAL);
        ImageViewCompat.setImageTintList(ivNavSuperStats, ColorStateList.valueOf(COLOR_NAV_ACTIVE));
        ImageViewCompat.setImageTintList(ivNavSuperStaff, ColorStateList.valueOf(COLOR_NAV_INACTIVE));
        refreshTodayOverview();
        refreshStats();
        refreshReports();
    }

    private void showStaffPanel() {
        overviewVisible = false;
        scrollStaff.setVisibility(View.VISIBLE);
        scrollOverview.setVisibility(View.GONE);
        tvNavSuperStaff.setTextColor(COLOR_NAV_ACTIVE);
        tvNavSuperStaff.setTypeface(null, Typeface.BOLD);
        tvNavSuperStats.setTextColor(COLOR_NAV_INACTIVE);
        tvNavSuperStats.setTypeface(null, Typeface.NORMAL);
        ImageViewCompat.setImageTintList(ivNavSuperStaff, ColorStateList.valueOf(COLOR_NAV_ACTIVE));
        ImageViewCompat.setImageTintList(ivNavSuperStats, ColorStateList.valueOf(COLOR_NAV_INACTIVE));
        refreshSecuritySection();
        refreshManageStaffList();
    }

    private void refreshTodayOverview() {
        QueueManager qm = QueueManager.getInstance();
        int onDuty = qm.getBarbersOnDutyCount();
        tvOnDutySummary.setText(getString(R.string.super_on_duty_summary, onDuty));

        layoutTodayBarbers.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);
        for (Barber b : qm.getBarbers()) {
            View row = inflater.inflate(R.layout.item_super_barber_overview, layoutTodayBarbers, false);
            String id = b.getId();
            ((TextView) row.findViewById(R.id.tv_barber_name)).setText(b.getDisplayName());
            boolean on = qm.isBarberOnDuty(id);
            TextView tvDuty = row.findViewById(R.id.tv_duty);
            tvDuty.setText(on ? R.string.super_barber_on_duty : R.string.super_barber_off_duty);
            tvDuty.setTextColor(ContextCompat.getColor(this,
                    on ? R.color.super_neon_green : R.color.splash_subtext));
            tvDuty.setBackgroundResource(on ? R.drawable.bg_super_duty_on : R.drawable.bg_super_duty_off);
            int cuts = qm.getHaircutsTodayForBarber(id);
            int home = qm.getHomeServiceBookingsTodayForBarber(id);
            ((TextView) row.findViewById(R.id.tv_haircuts_today)).setText(
                    getString(R.string.super_haircuts_today_line, cuts));
            ((TextView) row.findViewById(R.id.tv_home_today)).setText(
                    getString(R.string.super_home_today_line, home));
            layoutTodayBarbers.addView(row);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!isSuperAdminSessionValid()) {
            return;
        }
        SuperAdminFirebase.userHasSuperAdminRole(FirebaseAuth.getInstance().getCurrentUser())
                .addOnCompleteListener(task -> {
                    boolean ok = task.isSuccessful() && Boolean.TRUE.equals(task.getResult());
                    if (!ok) {
                        AppLogger.w("SuperAdminActivity", "Role check failed on resume");
                        runOnUiThread(this::forceBackToSignIn);
                    }
                });
        QueueManager.getInstance().addAppointmentSyncListener(firestoreSuperRefresh);
        refreshSecuritySection();
        if (!overviewVisible) {
            refreshManageStaffList();
        }
        if (overviewVisible) {
            refreshTodayOverview();
            refreshStats();
            refreshReports();
        }
        refreshHandler.postDelayed(refreshRunnable, 3000);
    }

    private boolean isSuperAdminSessionValid() {
        SessionStore.Session session = SessionStore.read(this);
        if (session.type != SessionStore.Session.Type.SUPER_ADMIN) {
            forceBackToSignIn();
            return false;
        }
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            forceBackToSignIn();
            return false;
        }
        return true;
    }

    private void forceBackToSignIn() {
        Toast.makeText(this, R.string.session_expired_sign_in_again, Toast.LENGTH_LONG).show();
        SessionStore.clear(this);
        FirebaseAuth.getInstance().signOut();
        Intent i = new Intent(this, SignInActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
        finish();
    }

    private void refreshSuperFromFirestore() {
        refreshSecuritySection();
        if (!overviewVisible) {
            refreshManageStaffList();
        }
        if (overviewVisible) {
            refreshTodayOverview();
            refreshStats();
            refreshReports();
        }
    }

    private void refreshSecuritySection() {
        ((TextView) findViewById(R.id.tv_super_2fa_code)).setText(R.string.super_admin_login_from_code);
    }

    private void refreshManageStaffList() {
        if (layoutManageStaffList == null || tvManageStaffEmpty == null) {
            return;
        }
        StaffDirectoryFirestore.fetchInvitedBarberEmails(list -> runOnUiThread(() -> {
            layoutManageStaffList.removeAllViews();
            if (list.isEmpty()) {
                tvManageStaffEmpty.setVisibility(View.VISIBLE);
                return;
            }
            tvManageStaffEmpty.setVisibility(View.GONE);
            LayoutInflater inflater = LayoutInflater.from(this);
            for (String email : list) {
                View row = inflater.inflate(R.layout.item_super_staff_email_row, layoutManageStaffList, false);
                ((TextView) row.findViewById(R.id.tv_staff_email)).setText(email);
                layoutManageStaffList.addView(row);
            }
        }));
    }

    private void openDeleteStaffPicker() {
        StaffDirectoryFirestore.fetchInvitedBarberEmails(list -> runOnUiThread(() -> {
            if (list.isEmpty()) {
                Toast.makeText(this, R.string.super_staff_list_empty, Toast.LENGTH_SHORT).show();
                return;
            }
            View root = LayoutInflater.from(this).inflate(R.layout.dialog_staff_pick_list, null, false);
            LinearLayout listLayout = root.findViewById(R.id.layout_staff_pick_list);
            LayoutInflater inflater = LayoutInflater.from(this);
            AlertDialog dlg = new MaterialAlertDialogBuilder(this)
                    .setView(root)
                    .create();
            root.findViewById(R.id.btn_staff_pick_close).setOnClickListener(v -> dlg.dismiss());
            for (String email : list) {
                View row = inflater.inflate(R.layout.item_dialog_staff_pick_row, listLayout, false);
                ((TextView) row.findViewById(R.id.tv_staff_pick_email)).setText(email);
                row.setOnClickListener(v -> {
                    dlg.dismiss();
                    confirmDeleteStaff(email);
                });
                listLayout.addView(row);
            }
            dlg.show();
            if (dlg.getWindow() != null) {
                dlg.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            }
        }));
    }

    private void confirmDeleteStaff(String email) {
        CutifyDialogs.showDeleteStaffConfirm(this, email, () ->
                StaffDirectoryFirestore.deleteBarberStaffAccount(email, ok ->
                        runOnUiThread(() -> {
                            if (!Boolean.TRUE.equals(ok)) {
                                Toast.makeText(this, R.string.super_delete_staff_failed, Toast.LENGTH_LONG).show();
                                return;
                            }
                            StaffAuthStore.get().clearInvitedAdminLocal(email);
                            QueueManager.getInstance().removeBarberAndQueues(email);
                            Toast.makeText(this, R.string.super_staff_deleted, Toast.LENGTH_SHORT).show();
                            refreshManageStaffList();
                            if (overviewVisible) {
                                refreshTodayOverview();
                            }
                        })));
    }

    private void showAuditLogDialog() {
        View root = LayoutInflater.from(this).inflate(R.layout.dialog_super_audit_log, null, false);
        TextView tv = root.findViewById(R.id.tv_audit_log_body);
        StringBuilder sb = new StringBuilder();
        for (String line : StaffAuthStore.get().getAuditLines()) {
            if (sb.length() > 0) {
                sb.append("\n\n");
            }
            sb.append(line);
        }
        tv.setText(sb.length() > 0 ? sb.toString() : getString(R.string.no_data));

        AlertDialog dlg = new MaterialAlertDialogBuilder(this)
                .setView(root)
                .create();
        root.findViewById(R.id.btn_audit_close).setOnClickListener(v -> dlg.dismiss());
        dlg.show();
        if (dlg.getWindow() != null) {
            dlg.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
    }

    private void showInviteConfirmDialog(String email, TextView tvInviteStatus) {
        View root = LayoutInflater.from(this).inflate(R.layout.dialog_super_invite_confirm, null, false);
        TextView body = root.findViewById(R.id.tv_invite_confirm_body);
        body.setText(getString(R.string.super_invite_confirm_message, email));

        AlertDialog dlg = new MaterialAlertDialogBuilder(this)
                .setView(root)
                .create();
        root.findViewById(R.id.btn_invite_confirm_close).setOnClickListener(v -> dlg.dismiss());
        root.findViewById(R.id.btn_invite_confirm_not_now).setOnClickListener(v -> dlg.dismiss());
        root.findViewById(R.id.btn_invite_confirm_send).setOnClickListener(v -> {
            dlg.dismiss();
            sendInvitationAfterConfirmed(email, tvInviteStatus);
        });
        dlg.show();
        if (dlg.getWindow() != null) {
            dlg.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
    }

    @Override
    protected void onPause() {
        QueueManager.getInstance().removeAppointmentSyncListener(firestoreSuperRefresh);
        super.onPause();
        refreshHandler.removeCallbacks(refreshRunnable);
    }

    private void onPdfSaveResult(Uri uri) {
        if (uri == null) {
            return;
        }
        new Thread(() -> {
            List<PdfExportHelper.PdfSegment> segments = buildSuperAdminPdfSegments(pendingPdfPeriod);
            boolean ok = PdfExportHelper.writeStyledSegmentsToUri(getContentResolver(), uri, segments);
            final boolean success = ok;
            if (success) {
                int pk = Math.min(Math.max(pendingPdfPeriod, 0), ANALYTICS_PERIOD_KEYS.length - 1);
                CutifyAnalytics.logSuperAdminPdfExport(SuperAdminActivity.this, ANALYTICS_PERIOD_KEYS[pk]);
            }
            runOnUiThread(() -> Toast.makeText(this,
                    success ? R.string.pdf_saved : R.string.pdf_save_failed,
                    Toast.LENGTH_SHORT).show());
        }).start();
    }

    private enum SuperPeriodPickerMode {
        PDF_EXPORT,
        RESET_RECORDS
    }

    private void showDownloadReportPeriodPicker() {
        showSuperPeriodPickerDialog(SuperPeriodPickerMode.PDF_EXPORT);
    }

    private void showResetRecordsPeriodPicker() {
        showSuperPeriodPickerDialog(SuperPeriodPickerMode.RESET_RECORDS);
    }

    private void showSuperPeriodPickerDialog(SuperPeriodPickerMode mode) {
        View root = LayoutInflater.from(this).inflate(R.layout.dialog_super_period_picker, null);
        TextView tvTitle = root.findViewById(R.id.tv_period_picker_title);
        tvTitle.setText(mode == SuperPeriodPickerMode.PDF_EXPORT
                ? R.string.super_pick_report_period
                : R.string.super_pick_reset_period);

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setView(root)
                .create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        root.findViewById(R.id.btn_period_close).setOnClickListener(v -> dialog.dismiss());
        root.findViewById(R.id.btn_period_cancel).setOnClickListener(v -> dialog.dismiss());

        String[] items = {
                getString(R.string.tab_day),
                getString(R.string.tab_week),
                getString(R.string.tab_month),
                getString(R.string.tab_year)
        };
        String[] slug = {"Day", "Week", "Month", "Year"};
        int[] btnIds = {
                R.id.btn_period_day,
                R.id.btn_period_week,
                R.id.btn_period_month,
                R.id.btn_period_year
        };
        for (int i = 0; i < btnIds.length; i++) {
            int which = i;
            root.findViewById(btnIds[i]).setOnClickListener(v -> {
                dialog.dismiss();
                if (mode == SuperPeriodPickerMode.PDF_EXPORT) {
                    pendingPdfPeriod = which;
                    String stamp = new SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.US).format(new Date());
                    pdfSaveLauncher.launch("Cutify_Report_" + slug[which] + "_" + stamp + ".pdf");
                } else {
                    String label = items[which];
                    new MaterialAlertDialogBuilder(this)
                            .setTitle(R.string.super_reset_confirm_title)
                            .setMessage(getString(R.string.super_reset_confirm_message, label))
                            .setNegativeButton(R.string.admin_cancel_btn, null)
                            .setPositiveButton(R.string.super_reset_period_records, (d2, w) -> {
                                QueueManager.SuperAdminDeleteResult r = QueueManager.getInstance()
                                        .deleteHistoricalRecordsForPeriod(which);
                                int pk = Math.min(Math.max(which, 0), ANALYTICS_PERIOD_KEYS.length - 1);
                                CutifyAnalytics.logSuperAdminRecordsDeleted(SuperAdminActivity.this,
                                        ANALYTICS_PERIOD_KEYS[pk], r.appointmentsRemoved, r.reportsRemoved);
                                Toast.makeText(this,
                                        getString(R.string.super_reset_done,
                                                r.appointmentsRemoved, r.reportsRemoved),
                                        Toast.LENGTH_LONG).show();
                                refreshTodayOverview();
                                refreshStats();
                                refreshReports();
                            })
                            .show();
                }
            });
        }

        dialog.show();
    }

    private List<PdfExportHelper.PdfSegment> buildSuperAdminPdfSegments(int periodTab) {
        SimpleDateFormat sdf = new SimpleDateFormat("MMM d, yyyy h:mm a", Locale.getDefault());
        QueueManager qm = QueueManager.getInstance();
        QueueManager.StatResult stats = qm.getStatsForDashboardPeriod(periodTab);

        List<PdfExportHelper.PdfSegment> segs = new ArrayList<>();
        segs.add(new PdfExportHelper.PdfSegment(PdfExportHelper.PdfSegment.Kind.TITLE,
                getString(R.string.pdf_report_title_super)));
        segs.add(new PdfExportHelper.PdfSegment(PdfExportHelper.PdfSegment.Kind.SUBTITLE,
                getString(R.string.pdf_generated_line, sdf.format(new Date()))));
        segs.add(new PdfExportHelper.PdfSegment(PdfExportHelper.PdfSegment.Kind.SUBTITLE,
                getString(R.string.pdf_report_period_line, periodLabelForTab(periodTab))));
        segs.add(new PdfExportHelper.PdfSegment(PdfExportHelper.PdfSegment.Kind.SPACER, ""));
        segs.add(new PdfExportHelper.PdfSegment(PdfExportHelper.PdfSegment.Kind.HR, ""));
        segs.add(new PdfExportHelper.PdfSegment(PdfExportHelper.PdfSegment.Kind.SECTION,
                getString(R.string.pdf_section_overview)));
        segs.add(new PdfExportHelper.PdfSegment(PdfExportHelper.PdfSegment.Kind.BODY,
                getString(R.string.super_on_duty_summary, qm.getBarbersOnDutyCount())));
        for (Barber b : qm.getBarbers()) {
            String id = b.getId();
            boolean on = qm.isBarberOnDuty(id);
            int cuts = qm.getHaircutsTodayForBarber(id);
            int home = qm.getHomeServiceBookingsTodayForBarber(id);
            String duty = on ? getString(R.string.super_barber_on_duty) : getString(R.string.super_barber_off_duty);
            String line = b.getDisplayName() + " — " + duty
                    + "  ·  " + getString(R.string.super_haircuts_today_line, cuts)
                    + "  ·  " + getString(R.string.super_home_today_line, home);
            segs.add(new PdfExportHelper.PdfSegment(PdfExportHelper.PdfSegment.Kind.BULLET, line));
        }
        segs.add(new PdfExportHelper.PdfSegment(PdfExportHelper.PdfSegment.Kind.SPACER, ""));
        segs.add(new PdfExportHelper.PdfSegment(PdfExportHelper.PdfSegment.Kind.HR, ""));
        segs.add(new PdfExportHelper.PdfSegment(PdfExportHelper.PdfSegment.Kind.SECTION,
                getString(R.string.pdf_section_bookings_period, periodLabelForTab(periodTab))));
        segs.add(new PdfExportHelper.PdfSegment(PdfExportHelper.PdfSegment.Kind.STAT_ROW,
                getString(R.string.total_bookings_label) + "\t" + stats.totalBookings));
        if (stats.totalBookings > 0) {
            segs.add(new PdfExportHelper.PdfSegment(PdfExportHelper.PdfSegment.Kind.SPACER, ""));
            segs.add(new PdfExportHelper.PdfSegment(PdfExportHelper.PdfSegment.Kind.BODY,
                    getString(R.string.pdf_per_barber)));
            for (Map.Entry<String, Integer> entry : stats.perBarber.entrySet()) {
                segs.add(new PdfExportHelper.PdfSegment(PdfExportHelper.PdfSegment.Kind.BULLET,
                        entry.getKey() + " — " + entry.getValue()));
            }
        }
        return segs;
    }

    private String currentPeriodLabel() {
        return periodLabelForTab(currentTab);
    }

    private String periodLabelForTab(int tab) {
        switch (tab) {
            case TAB_WEEK:
                return getString(R.string.tab_week);
            case TAB_MONTH:
                return getString(R.string.tab_month);
            case TAB_YEAR:
                return getString(R.string.tab_year);
            default:
                return getString(R.string.tab_day);
        }
    }

    private QueueManager.StatResult getStatsForCurrentTab() {
        return QueueManager.getInstance().getStatsForDashboardPeriod(currentTab);
    }

    private void selectTab(int tab) {
        currentTab = tab;
        updateTabAppearance();
        if (overviewVisible) {
            refreshStats();
        }
    }

    private void updateTabAppearance() {
        TextView[] tabs = {tabDay, tabWeek, tabMonth, tabYear};
        for (int i = 0; i < tabs.length; i++) {
            if (i == currentTab) {
                tabs[i].setBackgroundResource(R.drawable.bg_super_period_tab_active);
                tabs[i].setTextColor(ContextCompat.getColor(this, R.color.splash_text));
                tabs[i].setTypeface(null, Typeface.BOLD);
            } else {
                tabs[i].setBackgroundResource(R.drawable.bg_super_period_tab_idle);
                tabs[i].setTextColor(ContextCompat.getColor(this, R.color.splash_subtext));
                tabs[i].setTypeface(null, Typeface.NORMAL);
            }
        }
    }

    private void refreshStats() {
        QueueManager.StatResult stats = getStatsForCurrentTab();
        populateStatContent(stats);
        populatePeakHoursDashboard();
    }

    private void populateStatContent(QueueManager.StatResult stats) {
        layoutStatContent.removeAllViews();
        float density = getResources().getDisplayMetrics().density;

        LinearLayout.LayoutParams fullWidth = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);

        TextView tvTitle = new TextView(this);
        tvTitle.setLayoutParams(fullWidth);
        tvTitle.setGravity(Gravity.CENTER_HORIZONTAL);
        tvTitle.setText(R.string.super_total_bookings_per_barber_title);
        tvTitle.setTextColor(ContextCompat.getColor(this, R.color.super_section_gold));
        tvTitle.setShadowLayer(10f, 0, 0, 0x66FFD54F);
        tvTitle.setTextSize(17);
        tvTitle.setTypeface(null, Typeface.BOLD);
        tvTitle.setLetterSpacing(0.04f);
        tvTitle.setPadding(0, 0, 0, (int) (4 * density));
        layoutStatContent.addView(tvTitle);

        if (stats.totalBookings == 0) {
            TextView tvNoData = new TextView(this);
            tvNoData.setLayoutParams(fullWidth);
            tvNoData.setGravity(Gravity.CENTER_HORIZONTAL);
            tvNoData.setText(R.string.no_data);
            tvNoData.setTextColor(ContextCompat.getColor(this, R.color.splash_subtext));
            tvNoData.setTextSize(13);
            tvNoData.setPadding(0, 18, 0, 0);
            layoutStatContent.addView(tvNoData);
            return;
        }

        TextView tvSub = new TextView(this);
        tvSub.setLayoutParams(fullWidth);
        tvSub.setGravity(Gravity.CENTER_HORIZONTAL);
        tvSub.setText(getString(R.string.super_all_bookings_line, stats.totalBookings));
        tvSub.setTextColor(ContextCompat.getColor(this, R.color.splash_subtext));
        tvSub.setTextSize(12);
        layoutStatContent.addView(tvSub);

        if (stats.perBarber.isEmpty()) {
            return;
        }

        HorizontalScrollView hsv = new HorizontalScrollView(this);
        hsv.setLayoutParams(fullWidth);
        hsv.setHorizontalScrollBarEnabled(false);
        hsv.setClipToPadding(false);
        hsv.setPadding(0, (int) (14 * density), 0, (int) (2 * density));

        LinearLayout row = new LinearLayout(this);
        row.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        row.setOrientation(LinearLayout.HORIZONTAL);

        int[] bgRes = {
                R.drawable.bg_super_booking_card_blue,
                R.drawable.bg_super_booking_card_orange,
                R.drawable.bg_super_booking_card_green
        };
        LayoutInflater inflater = LayoutInflater.from(this);
        int idx = 0;
        for (Map.Entry<String, Integer> entry : stats.perBarber.entrySet()) {
            View card = inflater.inflate(R.layout.item_super_barber_booking_card, row, false);
            LinearLayout inner = card.findViewById(R.id.layout_barber_booking_card);
            inner.setBackgroundResource(bgRes[idx % 3]);
            ((TextView) card.findViewById(R.id.tv_barber_name)).setText(entry.getKey());
            ((TextView) card.findViewById(R.id.tv_booking_total)).setText(String.valueOf(entry.getValue()));
            int home = stats.perBarberHomeService != null
                    ? stats.perBarberHomeService.getOrDefault(entry.getKey(), 0)
                    : 0;
            ((TextView) card.findViewById(R.id.tv_home_service)).setText(
                    getString(R.string.super_barber_card_home_service, home));
            row.addView(card);
            idx++;
        }
        hsv.addView(row);
        layoutStatContent.addView(hsv);
    }

    private void populatePeakHoursDashboard() {
        if (peakHeatmap == null || peakBarChart == null) {
            return;
        }
        QueueManager qm = QueueManager.getInstance();
        long since = qm.sinceMillisForDashboardPeriod(currentTab);
        QueueManager.PeakHoursDashboardData d = qm.computePeakHoursDashboard(since);

        int heatMax = 0;
        for (int r = 0; r < 7; r++) {
            for (int c = 0; c < 12; c++) {
                heatMax = Math.max(heatMax, d.heatmap[r][c]);
            }
        }
        peakHeatmap.setHeatmap(d.heatmap, heatMax);

        peakBarChart.setSeries(d.hourly24, 0);
        peakBarChart.post(() -> {
            peakBarChart.requestLayout();
            peakBarChart.invalidate();
        });

        peakTvBusiestDayName.setText(weekdayNameFromMondayFirstIndex(d.busiestDayIndex));
        if (d.totalBookingsInPeriod == 0) {
            peakTvBusiestDayCount.setText(getString(R.string.peak_appointments_format, 0));
        } else {
            peakTvBusiestDayCount.setText(getString(R.string.peak_appointments_format, d.busiestDayTotal));
        }

        if (d.peakWindowTotalBookings <= 0) {
            peakTvPeakWindow.setText(R.string.peak_time_na);
        } else {
            int endHr = d.peakWindowStartHour + 2;
            peakTvPeakWindow.setText(formatHourRange12(d.peakWindowStartHour, endHr));
        }

        peakTvAvgVisits.setText(String.format(Locale.getDefault(), "%.1f", d.avgVisitsPerDay));
        if (d.hasTopService) {
            peakTvTopService.setText(d.topServiceName);
            peakTvTopServicePct.setText(getString(R.string.peak_top_service_pct_format,
                    Math.round(d.topServicePercent)));
        } else {
            peakTvTopService.setText(R.string.peak_top_service_na);
            peakTvTopServicePct.setText("");
        }
    }

    private static String weekdayNameFromMondayFirstIndex(int mon0Sun6) {
        Calendar c = Calendar.getInstance();
        int dow = (mon0Sun6 == 6) ? Calendar.SUNDAY : Calendar.MONDAY + mon0Sun6;
        c.set(Calendar.DAY_OF_WEEK, dow);
        return new SimpleDateFormat("EEEE", Locale.getDefault()).format(c.getTime());
    }

    private static String formatHourRange12(int startInclusive, int endExclusive) {
        return formatHour12(startInclusive) + " – " + formatHour12(endExclusive);
    }

    private static String formatHour12(int hour24) {
        if (hour24 >= 24) {
            return "12:00 AM";
        }
        if (hour24 <= 0) {
            return "12:00 AM";
        }
        boolean pm = hour24 >= 12;
        int h = hour24 % 12;
        if (h == 0) {
            h = 12;
        }
        return String.format(Locale.getDefault(), "%d:00 %s", h, pm ? "PM" : "AM");
    }

    private void refreshReports() {
        layoutReports.removeAllViews();
        var reports = QueueManager.getInstance().getReports();

        if (reports.isEmpty()) {
            TextView empty = new TextView(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            empty.setLayoutParams(lp);
            empty.setGravity(Gravity.CENTER_HORIZONTAL);
            empty.setText(R.string.no_reports_yet);
            empty.setTextColor(ContextCompat.getColor(this, R.color.splash_subtext));
            empty.setTextSize(14);
            empty.setPadding(0, 8, 0, 8);
            layoutReports.addView(empty);
            return;
        }

        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault());

        for (int i = reports.size() - 1; i >= 0; i--) {
            QueueManager.BarberReport report = reports.get(i);
            View card = LayoutInflater.from(this).inflate(R.layout.item_report_card, layoutReports, false);

            ((TextView) card.findViewById(R.id.tv_barber_name)).setText(report.barberName);
            ((TextView) card.findViewById(R.id.tv_time)).setText(sdf.format(new Date(report.timestamp)));
            ((TextView) card.findViewById(R.id.tv_reporter)).setText(
                    String.format(getString(R.string.reported_by_format), report.reporterName));
            ((TextView) card.findViewById(R.id.tv_description)).setText(report.description);
            card.findViewById(R.id.btn_delete_report).setOnClickListener(v ->
                    CutifyDialogs.showActionConfirm(
                            this,
                            getString(R.string.dialog_badge_action_delete),
                            getString(R.string.report_delete_confirm_title),
                            getString(R.string.report_delete_confirm_message),
                            R.drawable.bg_btn_danger,
                            () -> {
                                QueueManager.getInstance().deleteReport(report.timestamp);
                                refreshReports();
                            }));

            layoutReports.addView(card);
        }
    }

    private void sendInvitationAfterConfirmed(String email, TextView tvInviteStatus) {
        StaffAuthStore.get().createInviteForEmailAsync(email, token -> runOnUiThread(() -> {
            if (token == null) {
                Toast.makeText(this, R.string.super_invite_email_invalid, Toast.LENGTH_LONG).show();
                return;
            }
            String link = InviteLinks.buildHttpsInviteUrl(token);
            composeInvitationEmail(email, link);
            tvInviteStatus.setText(getString(R.string.super_invite_last_status, email));
            Toast.makeText(this, R.string.super_invite_sent_toast, Toast.LENGTH_LONG).show();
            showInvitePreparedNotification(email);
        }));
    }

    private void showInvitePreparedNotification(String recipientEmail) {
        if (!CutifyNotificationChannels.canPostNotifications(this)) {
            return;
        }
        NotificationCompat.Builder b = new NotificationCompat.Builder(this,
                CutifyNotificationChannels.CHANNEL_SUPER_INVITES_ID)
                .setSmallIcon(R.drawable.ic_notification_bell)
                .setContentTitle(getString(R.string.super_invite_notif_title))
                .setContentText(getString(R.string.super_invite_notif_body, recipientEmail))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true);
        int nid = (int) (System.currentTimeMillis() % Integer.MAX_VALUE);
        NotificationManagerCompat.from(this).notify(nid, b.build());
    }

    private void composeInvitationEmail(String toEmail, String inviteLink) {
        String subject = getString(R.string.super_invite_email_subject);
        String body = getString(R.string.super_invite_email_body, inviteLink);
        String mailUri = "mailto:" + Uri.encode(toEmail)
                + "?subject=" + Uri.encode(subject)
                + "&body=" + Uri.encode(body);
        Intent send = new Intent(Intent.ACTION_SENDTO, Uri.parse(mailUri));
        try {
            startActivity(Intent.createChooser(send, getString(R.string.super_invite_chooser_title)));
        } catch (Exception e) {
            Toast.makeText(this, R.string.super_invite_no_mail_app, Toast.LENGTH_LONG).show();
        }
    }

    private void confirmSignOut() {
        CutifyDialogs.showSignOutConfirm(this, () -> {
            FirebaseAuth.getInstance().signOut();
            RecaptchaGate.clearStaffRecaptcha(this);
            SessionStore.clear(this);
            Intent intent = new Intent(this, SignInActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });
    }
}
