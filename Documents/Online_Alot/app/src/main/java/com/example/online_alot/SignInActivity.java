package com.example.online_alot;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.SignInMethodQueryResult;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.android.gms.tasks.Task;

import java.util.List;
import java.util.Locale;


/**
 * Staff sign-in at the top (Super Admin + barber admins) — email and password only.
 * Customers use Get started at the bottom.
 * New barbers complete the invitation link first ({@link AcceptInviteActivity}), then sign in here.
 */
public class SignInActivity extends AppCompatActivity {
    private View btnSignIn;
    private View btnGetStarted;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sign_in);

        EditText etEmail = findViewById(R.id.et_email);
        EditText etPassword = findViewById(R.id.et_password);
        btnSignIn = findViewById(R.id.btn_sign_in);
        btnGetStarted = findViewById(R.id.btn_get_started);

        btnSignIn.setOnClickListener(v ->
                handleSignIn(etEmail.getText().toString().trim(),
                        etPassword.getText().toString().trim()));

        btnGetStarted.setOnClickListener(v -> handleGetStarted());

    }

    private void handleSignIn(String emailRaw, String password) {
        StaffAuthStore staff = StaffAuthStore.get();
        // Match Firebase + Firestore staff_roles doc ids (lowercase email).
        String email = SuperAdminFirebase.roleDocumentId(emailRaw);
        String emailRawTrimmed = emailRaw == null ? "" : emailRaw.trim();

        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, R.string.field_required, Toast.LENGTH_SHORT).show();
            return;
        }

        if (staff.isAccountLocked(email)) {
            long ms = staff.getLockoutRemainingMs(email);
            int min = (int) Math.ceil(ms / 60000.0);
            Toast.makeText(this, getString(R.string.account_locked, min), Toast.LENGTH_LONG).show();
            return;
        }

        if (RecaptchaGate.isStaffRecaptchaRequired(this, email)) {
            runRecaptchaThenSignIn(email, emailRawTrimmed, password, staff);
            return;
        }
        tryFirebaseSuperAdminThenBarber(email, emailRawTrimmed, password, staff);
    }

    private void handleGetStarted() {
        if (!RecaptchaGate.isUserRecaptchaRequiredToday(this)) {
            goUserMain();
            return;
        }
        runRecaptchaGuarded(() -> {
            RecaptchaGate.markUserRecaptchaPassedToday(this);
            goUserMain();
        }, btnGetStarted);
    }

    private void goUserMain() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }

    private void runRecaptchaThenSignIn(String email, String emailRawTrimmed, String password, StaffAuthStore staff) {
        runRecaptchaGuarded(() -> {
            RecaptchaGate.markStaffRecaptchaPassed(this, email);
            tryFirebaseSuperAdminThenBarber(email, emailRawTrimmed, password, staff);
        }, btnSignIn);
    }

    private void runRecaptchaGuarded(Runnable onSuccess, View triggerButton) {
        String siteKey = getString(R.string.recaptcha_site_key).trim();
        if (siteKey.isEmpty() || siteKey.startsWith("PASTE_")) {
            Toast.makeText(this, R.string.recaptcha_missing_site_key, Toast.LENGTH_LONG).show();
            // Temporary fail-open: allow usage while site key is not configured.
            onSuccess.run();
            return;
        }
        triggerButton.setEnabled(false);
        showRecaptchaDialog(siteKey, () -> {
            triggerButton.setEnabled(true);
            onSuccess.run();
        }, () -> triggerButton.setEnabled(true));
    }

    private void showRecaptchaDialog(String siteKey, Runnable onVerified, Runnable onClosed) {
        View root = LayoutInflater.from(this).inflate(R.layout.dialog_recaptcha_challenge, null, false);
        WebView webView = root.findViewById(R.id.web_recap_dialog);
        View btnCancel = root.findViewById(R.id.btn_recap_dialog_cancel);
        AlertDialog dialog = new MaterialAlertDialogBuilder(this).setView(root).create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        final boolean[] solved = {false};
        btnCancel.setOnClickListener(v -> dialog.dismiss());
        dialog.setOnDismissListener(d -> {
            webView.stopLoading();
            webView.loadUrl("about:blank");
            webView.destroy();
            if (!solved[0]) {
                Toast.makeText(this, R.string.recaptcha_verify_failed, Toast.LENGTH_LONG).show();
            }
            onClosed.run();
        });

        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        webView.setWebChromeClient(new WebChromeClient());
        webView.addJavascriptInterface(new Object() {
            @JavascriptInterface
            public void onToken(String token) {
                runOnUiThread(() -> {
                    if (token == null || token.trim().isEmpty()) {
                        return;
                    }
                    solved[0] = true;
                    dialog.dismiss();
                    onVerified.run();
                });
            }
        }, "AndroidBridge");
        webView.loadDataWithBaseURL(
                "https://www.google.com",
                buildRecaptchaDialogHtml(siteKey),
                "text/html",
                "UTF-8",
                null
        );
        dialog.show();
    }

    private String buildRecaptchaDialogHtml(String siteKey) {
        return "<!doctype html><html><head><meta name='viewport' content='width=device-width, initial-scale=1'/>"
                + "<script src='https://www.google.com/recaptcha/api.js' async defer></script>"
                + "<style>body{font-family:sans-serif;background:#0f172a;color:#fff;padding:10px;text-align:center}"
                + ".card{background:#111827;border:1px solid #334155;border-radius:12px;padding:12px;margin-top:4px}</style>"
                + "</head><body><div class='card'>"
                + "<div class='g-recaptcha' data-sitekey='" + siteKey + "' data-callback='onSolved'></div>"
                + "</div><script>function onSolved(token){AndroidBridge.onToken(token);}</script></body></html>";
    }

    /** When manual Super Admin login fails Firebase but Firestore says superadmin — explain (not barber invite). */
    private void showSuperAdminManualFailedDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.sign_in_super_admin_manual_title)
                .setMessage(R.string.sign_in_super_admin_manual_message)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    /**
     * If Firebase email/password did not yield Super Admin, run barber paths only when this address is not
     * {@code staff_roles} Super Admin — otherwise users saw the barber “not invited” message by mistake.
     * Tries lowercase doc id first, then the exact typed email (legacy mixed-case Firestore ids).
     */
    private void maybeRunBarberStaffSignInAfterFirebaseMiss(
            String emailNorm, String emailRawTrimmed, String password, StaffAuthStore staff) {
        String primaryId = SuperAdminFirebase.roleDocumentId(emailNorm);
        if (primaryId.isEmpty()) {
            runBarberStaffSignIn(emailNorm, password, staff);
            return;
        }
        String secondaryId = "";
        if (!emailRawTrimmed.isEmpty() && !emailRawTrimmed.equals(primaryId)) {
            secondaryId = emailRawTrimmed;
        }
        final String altDocId = secondaryId;
        AppFirestore.db().collection(AppFirestore.COL_STAFF_ROLES).document(primaryId).get()
                .addOnCompleteListener(task -> {
                    if (isStaffRolesDocSuperAdmin(task)) {
                        runOnUiThread(this::showSuperAdminManualFailedDialog);
                        return;
                    }
                    if (altDocId.isEmpty()) {
                        runOnUiThread(() -> runBarberStaffSignIn(emailNorm, password, staff));
                        return;
                    }
                    AppFirestore.db().collection(AppFirestore.COL_STAFF_ROLES).document(altDocId).get()
                            .addOnCompleteListener(task2 -> {
                                if (isStaffRolesDocSuperAdmin(task2)) {
                                    runOnUiThread(this::showSuperAdminManualFailedDialog);
                                } else {
                                    runOnUiThread(() -> runBarberStaffSignIn(emailNorm, password, staff));
                                }
                            });
                });
    }

    private static boolean isStaffRolesDocSuperAdmin(Task<DocumentSnapshot> task) {
        if (!task.isSuccessful() || task.getResult() == null || !task.getResult().exists()) {
            return false;
        }
        String role = task.getResult().getString(SuperAdminFirebase.FIELD_ROLE);
        return role != null && SuperAdminFirebase.ROLE_SUPERADMIN.equalsIgnoreCase(role.trim());
    }

    /**
     * Super Admin: Firebase Auth + Firestore {@code staff_roles} (manual setup in Console). Otherwise barber admin paths.
     */
    private void tryFirebaseSuperAdminThenBarber(String email, String emailRawTrimmed, String password, StaffAuthStore staff) {
        FirebaseAuth auth = FirebaseAuth.getInstance();
        auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (!task.isSuccessful()) {
                        auth.fetchSignInMethodsForEmail(email)
                                .addOnCompleteListener(fetchTask -> {
                                    if (!fetchTask.isSuccessful()) { 
                                        runOnUiThread(() -> maybeRunBarberStaffSignInAfterFirebaseMiss(
                                                email, emailRawTrimmed, password, staff));
                                        return;
                                    }
                                    SignInMethodQueryResult q = fetchTask.getResult();
                                    List<String> methods = q != null ? q.getSignInMethods() : null;
                                    boolean hasPassword = methods != null
                                            && methods.contains(EmailAuthProvider.EMAIL_PASSWORD_SIGN_IN_METHOD);
                                    boolean hasGoogle = methods != null
                                            && methods.contains(GoogleAuthProvider.PROVIDER_ID);
                                    if (hasGoogle && !hasPassword) {
                                         runOnUiThread(() -> Toast.makeText(this,
                                                R.string.sign_in_email_not_set_use_google_or_firebase,
                                                Toast.LENGTH_LONG).show());
                                        return;
                                    }
                                    runOnUiThread(() -> maybeRunBarberStaffSignInAfterFirebaseMiss(
                                            email, emailRawTrimmed, password, staff));
                                });
                        return;
                    }
                    FirebaseUser user = task.getResult() != null ? task.getResult().getUser() : null;
                    if (user == null) {
                        auth.signOut();
                        runOnUiThread(() -> maybeRunBarberStaffSignInAfterFirebaseMiss(
                                email, emailRawTrimmed, password, staff));
                        return;
                    }
                    SuperAdminFirebase.userHasSuperAdminRole(user)
                            .addOnCompleteListener(t2 -> {
                                boolean isSuper = t2.isSuccessful()
                                        && Boolean.TRUE.equals(t2.getResult());
                                if (!isSuper) {
                                    auth.signOut();
                                    runOnUiThread(() -> maybeRunBarberStaffSignInAfterFirebaseMiss(
                                            email, emailRawTrimmed, password, staff));
                                    return;
                                }
                                String key = SuperAdminFirebase.roleDocumentId(user);
                                runOnUiThread(() -> {
                                    staff.clearFailedAttempts(email);
                                    staff.logAudit("LOGIN_SUPER_FIREBASE email=" + key);
                                    goSuperAdminDashboard();
                                });
                            });
                });
    }

    private void runBarberStaffSignIn(String email, String password, StaffAuthStore staff) {
        if (staff.isAdminEmail(email)) {
            if (!staff.verifyAdminPassword(email, password)) {
                staff.recordFailedAttempt(email);
                staff.logAudit("FAIL_PW_ADMIN email=" + email.toLowerCase(Locale.ROOT));
                Toast.makeText(this, R.string.sign_in_error, Toast.LENGTH_SHORT).show();
                return;
            }
            staff.verifyInvitedAdminStillAuthorized(email, status -> runOnUiThread(() -> {
                switch (status) {
                    case ACTIVE:
                        staff.clearFailedAttempts(email);
                        staff.logAudit("LOGIN_ADMIN email=" + email.toLowerCase(Locale.ROOT));
                        proceedAfterAdminVerified(email, staff);
                        break;
                    case REVOKED:
                        staff.clearInvitedAdminLocal(email);
                        staff.logAudit("LOGIN_DENIED_DELETED email=" + email.toLowerCase(Locale.ROOT));
                        Toast.makeText(this, R.string.sign_in_admin_revoked, Toast.LENGTH_LONG).show();
                        break;
                    case VERIFY_FAILED:
                        Toast.makeText(this, R.string.sign_in_verify_network, Toast.LENGTH_LONG).show();
                        break;
                }
            }));
            return;
        }

        staff.tryCloudAdminSignIn(email, password, result ->
                onCloudAdminSignInResult(email, password, staff, result));
    }

    private void onCloudAdminSignInResult(String email, String password, StaffAuthStore staff,
                                          StaffAuthStore.CloudAdminSignIn result) {
        switch (result) {
            case NOT_FOUND:
                Toast.makeText(this, R.string.sign_in_not_staff, Toast.LENGTH_LONG).show();
                break;
            case CLOUD_ERROR:
                Toast.makeText(this, R.string.sign_in_verify_network, Toast.LENGTH_LONG).show();
                break;
            case WRONG_PASSWORD:
                staff.recordFailedAttempt(email);
                staff.logAudit("FAIL_PW_ADMIN_CLOUD email=" + email.toLowerCase(Locale.ROOT));
                Toast.makeText(this, R.string.sign_in_error, Toast.LENGTH_SHORT).show();
                break;
            case OK:
                staff.clearFailedAttempts(email);
                staff.logAudit("LOGIN_ADMIN_CLOUD email=" + email.toLowerCase(Locale.ROOT));
                proceedAfterAdminVerified(email, staff);
                break;
            default:
                break;
        }
    }

    private void proceedAfterAdminVerified(String email, StaffAuthStore staff) {
        ensureBarberInQueueManager(email);
        syncBarberProfile(email);
        staff.syncInvitedAdminToCloud(email);
        maybePromptAvailabilityHoursThenGo(email);
    }

    private void maybePromptAvailabilityHoursThenGo(String email) {
        AdminProfileManager apm = new AdminProfileManager(this, email);
        EditText input = new EditText(this);
        input.setHint(R.string.admin_availability_hint);
        input.setText(apm.getAvailabilityHours());
        if (input.getText() != null) {
            input.setSelection(input.getText().length());
        }
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        input.setPadding(pad, pad, pad, pad);
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.admin_availability_title)
                .setMessage(R.string.admin_availability_message)
                .setView(input)
                .setCancelable(false)
                .setNegativeButton(R.string.admin_availability_skip, (d, w) -> goAdminDashboard(email))
                .setPositiveButton(R.string.admin_availability_save, (d, w) -> {
                    String val = input.getText() != null ? input.getText().toString().trim() : "";
                    if (!val.isEmpty()) {
                        apm.saveAvailabilityHours(val);
                        QueueManager.getInstance().updateBarberAvailability(email, val, apm.getAvailabilityDays());
                        BarberFirestoreSync.mergeProfile(email, apm.getName(), val, apm.getAvailabilityDays());
                    }
                    goAdminDashboard(email);
                })
                .show();
    }

    private void ensureBarberInQueueManager(String email) {
        QueueManager qm = QueueManager.getInstance();
        if (qm.getBarber(email) == null) {
            AdminProfileManager apm = new AdminProfileManager(this, email);
            String name = apm.getName();
            qm.addBarberFromAdminSignIn(email, name != null && !name.isEmpty() ? name : null);
        }
    }

    private void syncBarberProfile(String email) {
        AdminProfileManager apm = new AdminProfileManager(this, email);
        String savedName = apm.getName();
        if (!savedName.isEmpty()) {
            QueueManager.getInstance().updateBarberDisplayName(email, savedName);
        }
        QueueManager.getInstance().updateBarberAvailability(
                email, apm.getAvailabilityHours(), apm.getAvailabilityDays());
        BarberFirestoreSync.mergeProfile(email, savedName, apm.getAvailabilityHours(), apm.getAvailabilityDays());
    }

    private void goSuperAdminDashboard() {
        SessionStore.saveSuperAdminSession(this);
        Intent intent = new Intent(this, SuperAdminActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void goAdminDashboard(String email) {
        SessionStore.saveAdminSession(this, email);
        Intent intent = new Intent(this, AdminMainActivity.class);
        intent.putExtra(AdminMainActivity.EXTRA_BARBER_EMAIL, email);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
