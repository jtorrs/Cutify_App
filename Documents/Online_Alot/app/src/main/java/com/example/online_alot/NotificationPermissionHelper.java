package com.example.online_alot;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/**
 * After a successful booking, shows the Cutify notification card (policy + opt-in). On Android 13+,
 * if {@link Manifest.permission#POST_NOTIFICATIONS} is not granted, Allow requests it; otherwise
 * the primary button is “Got it”.
 */
public final class NotificationPermissionHelper {

    public static final int REQUEST_POST_NOTIFICATIONS = 9913;

    private NotificationPermissionHelper() {}

    public static boolean isPostNotificationsGranted(AppCompatActivity activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return true;
        }
        return ContextCompat.checkSelfPermission(activity, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * @param messageResId e.g. {@link R.string#notif_inshop_book_success_body} or
     *                     {@link R.string#notif_homeservice_book_success_body} (must take app name as %1$s)
     */
    public static void showAfterBookingSuccess(AppCompatActivity activity, @StringRes int messageResId) {
        View dialogView = LayoutInflater.from(activity).inflate(R.layout.dialog_notification_permission, null);
        TextView tvQuestion = dialogView.findViewById(R.id.tv_notif_question);
        tvQuestion.setText(activity.getString(messageResId, activity.getString(R.string.app_name)));

        Button btnAllow = dialogView.findViewById(R.id.btn_allow);
        Button btnDontAllow = dialogView.findViewById(R.id.btn_dont_allow);

        final boolean needRuntimePermission =
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                        && ContextCompat.checkSelfPermission(activity, Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED;

        if (needRuntimePermission) {
            btnAllow.setText(R.string.notif_allow);
            btnDontAllow.setVisibility(View.VISIBLE);
        } else {
            btnAllow.setText(R.string.notif_got_it);
            btnDontAllow.setVisibility(View.GONE);
        }

        AlertDialog dialog = new MaterialAlertDialogBuilder(activity)
                .setView(dialogView)
                .setCancelable(true)
                .create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        btnAllow.setOnClickListener(v -> {
            dialog.dismiss();
            if (needRuntimePermission) {
                ActivityCompat.requestPermissions(activity,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS},
                        REQUEST_POST_NOTIFICATIONS);
            }
        });

        btnDontAllow.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    /**
     * @return true if this was the POST_NOTIFICATIONS request (consume before other handlers).
     */
    public static boolean onRequestPermissionsResult(
            int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        return requestCode == REQUEST_POST_NOTIFICATIONS;
    }
}
