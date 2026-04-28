package com.example.online_alot;

import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.List;

/** Scrollable popup listing customers in a barber’s queue (user-facing). */
public final class QueueListDialog {

    private QueueListDialog() {}

    public static void show(AppCompatActivity activity, String barberDisplayName, List<String> queueNames) {
        View root = LayoutInflater.from(activity).inflate(R.layout.dialog_queue_list, null, false);
        TextView title = root.findViewById(R.id.tv_queue_dialog_title);
        TextView sub = root.findViewById(R.id.tv_queue_dialog_subtitle);
        TextView section = root.findViewById(R.id.tv_queue_dialog_section);
        LinearLayout list = root.findViewById(R.id.layout_queue_names);

        title.setText(activity.getString(R.string.queue_dialog_title, barberDisplayName));

        if (queueNames == null || queueNames.isEmpty()) {
            sub.setText(R.string.queue_dialog_empty_detail);
            section.setVisibility(View.GONE);
        } else {
            sub.setText(activity.getString(R.string.queue_dialog_intro));
            section.setVisibility(View.VISIBLE);
            sub.append("\n");
            sub.append(activity.getString(R.string.queue_dialog_count, queueNames.size()));
            LayoutInflater inflater = LayoutInflater.from(activity);
            int i = 1;
            for (String name : queueNames) {
                View card = inflater.inflate(R.layout.item_queue_user_card, list, false);
                TextView tvPos = card.findViewById(R.id.tv_position);
                TextView tvName = card.findViewById(R.id.tv_customer_name);
                tvPos.setText(String.valueOf(i));
                tvName.setText(name != null ? name : "");
                list.addView(card);
                i++;
            }
        }

        AlertDialog dlg = new MaterialAlertDialogBuilder(activity)
                .setView(root)
                .create();
        root.findViewById(R.id.btn_queue_dialog_close).setOnClickListener(v -> dlg.dismiss());
        dlg.show();
        if (dlg.getWindow() != null) {
            dlg.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
    }
}
