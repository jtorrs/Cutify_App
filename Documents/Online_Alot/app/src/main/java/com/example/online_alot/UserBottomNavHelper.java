package com.example.online_alot;

import android.content.Intent;
import android.graphics.Typeface;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

/**
 * Same bottom bar on Home, Appointments, and Home Service; highlights the current tab.
 */
public final class UserBottomNavHelper {

    public static final int TAB_HOME = 0;
    public static final int TAB_APPOINTMENTS = 1;
    public static final int TAB_HOME_SERVICE = 2;

    /** Selected tab: Cutify teal (see user-facing headers). */
    private static int selectedColor(AppCompatActivity activity) {
        return ContextCompat.getColor(activity, R.color.welcome_teal);
    }

    private static int inactiveColor(AppCompatActivity activity) {
        return ContextCompat.getColor(activity, R.color.splash_subtext);
    }

    private UserBottomNavHelper() {}

    public static void bind(AppCompatActivity activity, int selectedTab) {
        int[] tabs = {R.id.nav_my_alot, R.id.nav_appointments, R.id.nav_explore};
        for (int i = 0; i < tabs.length; i++) {
            LinearLayout row = activity.findViewById(tabs[i]);
            if (row == null || row.getChildCount() < 2) {
                continue;
            }
            ImageView iv = (ImageView) row.getChildAt(0);
            TextView tv = (TextView) row.getChildAt(1);
            boolean sel = (i == selectedTab);
            int on = selectedColor(activity);
            int off = inactiveColor(activity);
            iv.setColorFilter(sel ? on : off);
            tv.setTextColor(sel ? on : off);
            tv.setTypeface(null, sel ? Typeface.BOLD : Typeface.NORMAL);
        }

        activity.findViewById(R.id.nav_my_alot).setOnClickListener(v -> open(activity, HomeActivity.class));
        activity.findViewById(R.id.nav_appointments).setOnClickListener(v -> open(activity, AppointmentsActivity.class));
        activity.findViewById(R.id.nav_explore).setOnClickListener(v -> open(activity, PrivateBookActivity.class));
    }

    private static void open(AppCompatActivity from, Class<?> dest) {
        if (dest.isInstance(from)) {
            return;
        }
        Intent i = new Intent(from, dest);
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        from.startActivity(i);
    }
}
