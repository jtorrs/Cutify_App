package com.example.online_alot;

import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.button.MaterialButton;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/**
 * Shared Cutify-styled confirmation sheets (teal / gold / red accents).
 */
public final class CutifyDialogs {

    /** Fired when the user confirms name and requested service in the Book Appointment sheet. */
    @FunctionalInterface
    public interface BookAppointmentCallback {
        void onConfirmed(String fullName, String serviceType);
    }

    private CutifyDialogs() {}

    /**
     * Cutify-styled “Book Appointment” sheet (matches {@code dialog_admin_walk_in} / action confirm cards).
     *
     * @param prefillService optional last requested service (local profile)
     */
    public static void showBookAppointment(AppCompatActivity activity, String prefillName,
                                           String prefillService, BookAppointmentCallback onConfirmed) {
        View root = LayoutInflater.from(activity).inflate(R.layout.dialog_book_appointment, null, false);
        EditText et = root.findViewById(R.id.et_book_appointment_name);
        EditText etService = root.findViewById(R.id.et_book_appointment_service);
        if (prefillName != null && !prefillName.trim().isEmpty()) {
            String p = prefillName.trim();
            et.setText(p);
            et.setSelection(p.length());
        }
        if (prefillService != null && !prefillService.trim().isEmpty()) {
            String s = prefillService.trim();
            etService.setText(s);
            etService.setSelection(s.length());
        }

        AlertDialog dlg = new MaterialAlertDialogBuilder(activity)
                .setView(root)
                .create();
        View.OnClickListener dismiss = v -> dlg.dismiss();
        root.findViewById(R.id.btn_book_appointment_close).setOnClickListener(dismiss);
        root.findViewById(R.id.btn_book_appointment_cancel).setOnClickListener(dismiss);
        root.findViewById(R.id.btn_book_appointment_confirm).setOnClickListener(v -> {
            String name = et.getText().toString().trim();
            if (name.isEmpty()) {
                Toast.makeText(activity, R.string.field_required, Toast.LENGTH_SHORT).show();
                return;
            }
            String service = etService.getText().toString().trim();
            if (service.isEmpty()) {
                Toast.makeText(activity, R.string.booking_service_required, Toast.LENGTH_SHORT).show();
                return;
            }
            dlg.dismiss();
            onConfirmed.onConfirmed(name, service);
        });
        dlg.show();
        if (dlg.getWindow() != null) {
            dlg.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
    }

    public static void showSignOutConfirm(AppCompatActivity activity, Runnable onConfirmedSignOut) {
        View root = LayoutInflater.from(activity).inflate(R.layout.dialog_sign_out_confirm, null, false);
        AlertDialog dlg = new MaterialAlertDialogBuilder(activity)
                .setView(root)
                .create();
        View.OnClickListener dismiss = v -> dlg.dismiss();
        root.findViewById(R.id.btn_sign_out_dialog_close).setOnClickListener(dismiss);
        root.findViewById(R.id.btn_sign_out_cancel).setOnClickListener(dismiss);
        root.findViewById(R.id.btn_sign_out_confirm).setOnClickListener(v -> {
            dlg.dismiss();
            onConfirmedSignOut.run();
        });
        dlg.show();
        if (dlg.getWindow() != null) {
            dlg.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
    }

    public static void showDeleteStaffConfirm(AppCompatActivity activity, String email,
                                              Runnable onConfirmedDelete) {
        View root = LayoutInflater.from(activity).inflate(R.layout.dialog_delete_staff_confirm, null, false);
        TextView msg = root.findViewById(R.id.tv_delete_confirm_message);
        msg.setText(activity.getString(R.string.super_delete_staff_confirm, email));

        AlertDialog dlg = new MaterialAlertDialogBuilder(activity)
                .setView(root)
                .create();
        View.OnClickListener dismiss = v -> dlg.dismiss();
        root.findViewById(R.id.btn_delete_confirm_close).setOnClickListener(dismiss);
        root.findViewById(R.id.btn_delete_cancel).setOnClickListener(dismiss);
        root.findViewById(R.id.btn_delete_confirm).setOnClickListener(v -> {
            dlg.dismiss();
            onConfirmedDelete.run();
        });
        dlg.show();
        if (dlg.getWindow() != null) {
            dlg.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
    }

    public static void showActionConfirm(AppCompatActivity activity,
                                         String badge,
                                         String title,
                                         String message,
                                         int confirmBackgroundRes,
                                         Runnable onConfirmed) {
        View root = LayoutInflater.from(activity).inflate(R.layout.dialog_action_confirm, null, false);
        ((TextView) root.findViewById(R.id.tv_action_confirm_badge)).setText(badge);
        ((TextView) root.findViewById(R.id.tv_action_confirm_title)).setText(title);
        ((TextView) root.findViewById(R.id.tv_action_confirm_message)).setText(message);
        MaterialButton btnYes = root.findViewById(R.id.btn_action_confirm_yes);
        btnYes.setBackgroundResource(confirmBackgroundRes);
        btnYes.setBackgroundTintList(null);
        if (confirmBackgroundRes == R.drawable.bg_btn_gold) {
            btnYes.setTextColor(ContextCompat.getColor(activity, R.color.book_now_label));
        } else {
            btnYes.setTextColor(ContextCompat.getColor(activity, R.color.white));
        }

        AlertDialog dlg = new MaterialAlertDialogBuilder(activity)
                .setView(root)
                .create();
        root.findViewById(R.id.btn_action_confirm_no).setOnClickListener(v -> dlg.dismiss());
        root.findViewById(R.id.btn_action_confirm_yes).setOnClickListener(v -> {
            dlg.dismiss();
            onConfirmed.run();
        });
        dlg.show();
        if (dlg.getWindow() != null) {
            dlg.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
    }
}
