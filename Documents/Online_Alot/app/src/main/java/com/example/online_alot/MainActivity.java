package com.example.online_alot;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), true);
        setContentView(R.layout.activity_main);

        if (findViewById(R.id.btn_continue_user) != null) {
            findViewById(R.id.btn_continue_user).setOnClickListener(v ->
                    startActivity(new Intent(this, DiscoverServicesActivity.class)));
        }

        animateHeroText();
    }

    private void animateHeroText() {
        View top = findViewById(R.id.tv_hero_top);
        View style = findViewById(R.id.tv_hero_style);
        View sub = findViewById(R.id.tv_hero_sub);
        if (top == null || style == null || sub == null) {
            return;
        }
        animateTextItem(top, 0L);
        animateTextItem(style, 130L);
        animateTextItem(sub, 260L);
    }

    private void animateTextItem(View view, long delayMs) {
        view.setAlpha(0f);
        view.setTranslationY(36f);
        view.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay(delayMs)
                .setDuration(650L)
                .start();
    }
}
