package com.example.online_alot;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.List;

public class FeedbackHelper {

    public static void checkAndShowFeedback(Activity activity) {
        List<QueueManager.PendingFeedback> feedbacks =
                QueueManager.getInstance().consumePendingFeedbacks();
        for (QueueManager.PendingFeedback fb : feedbacks) {
            showFeedbackDialog(activity, fb, false);
        }
    }

    /** Opens rate/report for a barber when the user taps their name or photo (optional feedback). */
    public static void showFeedbackForBarber(Activity activity, String barberId, String barberName) {
        showFeedbackDialog(activity, new QueueManager.PendingFeedback(barberId, barberName, ""), true);
    }

    private static void showFeedbackDialog(Activity activity, QueueManager.PendingFeedback fb,
                                           boolean cancelable) {
        View view = LayoutInflater.from(activity).inflate(R.layout.dialog_feedback, null);

        TextView tvTitle = view.findViewById(R.id.tv_title);
        tvTitle.setText(String.format(activity.getString(R.string.feedback_title_format), fb.barberName));

        TextView[] stars = new TextView[]{
                view.findViewById(R.id.star_1),
                view.findViewById(R.id.star_2),
                view.findViewById(R.id.star_3),
                view.findViewById(R.id.star_4),
                view.findViewById(R.id.star_5)
        };

        final int[] selectedRating = {0};
        int goldColor = ContextCompat.getColor(activity, R.color.splash_accent);
        int grayColor = ContextCompat.getColor(activity, R.color.splash_subtext);

        for (int i = 0; i < stars.length; i++) {
            final int rating = i + 1;
            stars[i].setOnClickListener(v -> {
                selectedRating[0] = rating;
                for (int j = 0; j < stars.length; j++) {
                    stars[j].setTextColor(j < rating ? goldColor : grayColor);
                }
            });
        }

        MaterialAlertDialogBuilder b = new MaterialAlertDialogBuilder(activity)
                .setView(view)
                .setCancelable(cancelable);
        if (cancelable) {
            b.setNegativeButton(android.R.string.cancel, (d, w) -> d.dismiss());
        }
        AlertDialog dialog = b.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        view.findViewById(R.id.btn_rate).setOnClickListener(v -> {
            if (selectedRating[0] > 0) {
                QueueManager.getInstance().rateBarber(fb.barberId, selectedRating[0]);
                Toast.makeText(activity, R.string.rating_submitted, Toast.LENGTH_SHORT).show();
                dialog.dismiss();
            }
        });

        view.findViewById(R.id.btn_report).setOnClickListener(v -> {
            dialog.dismiss();
            showReportDialog(activity, fb);
        });

        dialog.show();
    }

    private static void showReportDialog(Activity activity, QueueManager.PendingFeedback fb) {
        View view = LayoutInflater.from(activity).inflate(R.layout.dialog_report, null);

        TextView tvBarber = view.findViewById(R.id.tv_barber_name);
        tvBarber.setText(String.format(activity.getString(R.string.report_barber_label), fb.barberName));

        EditText etDesc = view.findViewById(R.id.et_description);

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setView(view)
                .setCancelable(true)
                .create();

        view.findViewById(R.id.btn_submit).setOnClickListener(v -> {
            String desc = etDesc.getText().toString().trim();
            if (desc.isEmpty()) {
                Toast.makeText(activity, R.string.error_report_empty, Toast.LENGTH_SHORT).show();
                return;
            }

            UserProfileManager upm = new UserProfileManager(activity);
            String reporterName = upm.getName();
            if (reporterName.isEmpty()) reporterName = "Anonymous";

            QueueManager.getInstance().addReport(fb.barberName, desc, reporterName);
            Toast.makeText(activity, R.string.report_submitted, Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });

        dialog.show();
    }
}
