package com.example.online_alot;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.Locale;

/**
 * Opens from invitation deep link ({@code cutify://invite/accept?token=...}).
 * Loads invite from Firestore so it works on the invitee's device; admin sets password + confirm, then signs in.
 */
public class AcceptInviteActivity extends AppCompatActivity {

    public static final String EXTRA_TOKEN = "EXTRA_INVITE_TOKEN";

    private String inviteToken;
    private String boundEmail;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_accept_invite);

        inviteToken = extractToken(getIntent());
        if (inviteToken == null || inviteToken.isEmpty()) {
            Toast.makeText(this, R.string.invite_link_invalid, Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        ProgressBar progress = findViewById(R.id.progress_invite_load);
        ScrollView scrollForm = findViewById(R.id.scroll_invite_form);
        TextView tvEmail = findViewById(R.id.tv_invite_email);
        EditText etPw = findViewById(R.id.et_invite_password);
        EditText etPw2 = findViewById(R.id.et_invite_password_confirm);
        Button btn = findViewById(R.id.btn_invite_confirm);

        StaffAuthStore.get().fetchInvitePreview(inviteToken, email -> runOnUiThread(() -> {
            progress.setVisibility(View.GONE);
            if (email == null || email.isEmpty()) {
                Toast.makeText(this, R.string.invite_link_invalid, Toast.LENGTH_LONG).show();
                finish();
                return;
            }
            boundEmail = email;
            tvEmail.setText(email);
            scrollForm.setVisibility(View.VISIBLE);
        }));

        btn.setOnClickListener(v -> {
            String p1 = etPw.getText().toString();
            String p2 = etPw2.getText().toString();
            if (p1.length() < 4) {
                Toast.makeText(this, R.string.password_too_short, Toast.LENGTH_SHORT).show();
                return;
            }
            if (!p1.equals(p2)) {
                Toast.makeText(this, R.string.password_mismatch, Toast.LENGTH_SHORT).show();
                return;
            }
            if (boundEmail == null) {
                return;
            }
            btn.setEnabled(false);
            StaffAuthStore.get().completeAdminInviteRegistration(boundEmail, p1, inviteToken, ok ->
                    runOnUiThread(() -> {
                        btn.setEnabled(true);
                        if (!Boolean.TRUE.equals(ok)) {
                            Toast.makeText(this, R.string.invite_invalid, Toast.LENGTH_LONG).show();
                            finish();
                            return;
                        }
                        Toast.makeText(this, R.string.admin_account_registered, Toast.LENGTH_LONG).show();
                        Intent next = new Intent(this, SignInActivity.class);
                        next.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(next);
                        finish();
                    }));
        });
    }

    static String extractToken(Intent intent) {
        if (intent == null) {
            return null;
        }
        Uri data = intent.getData();
        if (data != null) {
            if ("cutify".equalsIgnoreCase(data.getScheme())
                    && "invite".equalsIgnoreCase(data.getHost())) {
                String t = StaffAuthStore.sanitizeInviteToken(data.getQueryParameter("token"));
                if (!t.isEmpty()) {
                    return t.toUpperCase(Locale.ROOT);
                }
            }
            if ("https".equalsIgnoreCase(data.getScheme())
                    && InviteLinks.isInviteHttpsHost(data.getHost())) {
                String path = data.getPath();
                if (path != null && path.contains("invite")) {
                    String t = StaffAuthStore.sanitizeInviteToken(data.getQueryParameter("token"));
                    if (!t.isEmpty()) {
                        return t.toUpperCase(Locale.ROOT);
                    }
                }
            }
        }
        String extra = intent.getStringExtra(EXTRA_TOKEN);
        if (extra != null) {
            String t = StaffAuthStore.sanitizeInviteToken(extra);
            if (!t.isEmpty()) {
                return t.toUpperCase(Locale.ROOT);
            }
        }
        return null;
    }
}
