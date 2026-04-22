package com.example.online_alot;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;

public class SplashActivity extends AppCompatActivity {

    private static final int SPLASH_DELAY = 3200;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        ImageView logo = findViewById(R.id.splash_logo);
        TextView appName = findViewById(R.id.splash_app_name);
        TextView tagline = findViewById(R.id.splash_tagline);
        View loadingBar = findViewById(R.id.splash_loading_bar);

        // Logo: scale bounce + fade
        Animation logoAnim = AnimationUtils.loadAnimation(this, R.anim.logo_scale_bounce);
        logo.startAnimation(logoAnim);

        logoAnim.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {}

            @Override
            public void onAnimationEnd(Animation animation) {
                // Start subtle pulse after logo lands
                Animation pulse = AnimationUtils.loadAnimation(SplashActivity.this, R.anim.logo_pulse);
                pulse.setRepeatCount(2);
                pulse.setRepeatMode(Animation.REVERSE);
                logo.startAnimation(pulse);
            }

            @Override
            public void onAnimationRepeat(Animation animation) {}
        });

        // App name: slide up with bounce
        Animation nameAnim = AnimationUtils.loadAnimation(this, R.anim.slide_up_bounce);
        appName.startAnimation(nameAnim);

        // Tagline: fade in delayed
        Animation taglineAnim = AnimationUtils.loadAnimation(this, R.anim.tagline_fade);
        tagline.startAnimation(taglineAnim);

        // Loading bar: expand animation
        Animation barAnim = AnimationUtils.loadAnimation(this, R.anim.loading_bar_expand);
        loadingBar.startAnimation(barAnim);

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            AppUpdateChecker.checkOnStartup(this, this::continueToNextScreen);
        }, SPLASH_DELAY);
    }

    private void continueToNextScreen() {
        Intent next = resolveNextIntent();
        startActivity(next);
        finish();
    }

    private Intent resolveNextIntent() {
        SessionStore.Session session = SessionStore.read(this);
        switch (session.type) {
            case SUPER_ADMIN:
                if (FirebaseAuth.getInstance().getCurrentUser() == null) {
                    SessionStore.clear(this);
                    return new Intent(this, SignInActivity.class);
                }
                return new Intent(this, SuperAdminActivity.class);
            case ADMIN:
                if (session.email == null || session.email.isEmpty()) {
                    SessionStore.clear(this);
                    return new Intent(this, SignInActivity.class);
                }
                Intent adminIntent = new Intent(this, AdminMainActivity.class);
                adminIntent.putExtra(AdminMainActivity.EXTRA_BARBER_EMAIL, session.email);
                return adminIntent;
            case NONE:
            default:
                return new Intent(this, SignInActivity.class);
        }
    }
}
