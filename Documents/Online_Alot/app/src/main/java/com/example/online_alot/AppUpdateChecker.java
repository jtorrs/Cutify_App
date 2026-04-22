package com.example.online_alot;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.text.TextUtils;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.firestore.DocumentSnapshot;

/**
 * Startup app-version gate backed by Firestore config.
 */
public final class AppUpdateChecker {

    private static final String DOC_MOBILE_UPDATE = "mobile_update";

    private AppUpdateChecker() {}

    public static void checkOnStartup(AppCompatActivity activity, Runnable onContinue) {
        AppFirestore.db()
                .collection(AppFirestore.COL_APP_CONFIG)
                .document(DOC_MOBILE_UPDATE)
                .get()
                .addOnSuccessListener(snapshot -> evaluateConfig(activity, snapshot, onContinue))
                .addOnFailureListener(e -> onContinue.run());
    }

    private static void evaluateConfig(AppCompatActivity activity, DocumentSnapshot snapshot, Runnable onContinue) {
        if (!snapshot.exists()) {
            onContinue.run();
            return;
        }

        String latestVersion = trimToEmpty(snapshot.getString("latestVersion"));
        String minSupportedVersion = trimToEmpty(snapshot.getString("minSupportedVersion"));
        String apkUrl = trimToEmpty(snapshot.getString("apkUrl"));
        Boolean forceFlag = snapshot.getBoolean("forceUpdate");

        String currentVersion = trimToEmpty(BuildConfig.VERSION_NAME);
        boolean belowLatest = isOlderVersion(currentVersion, latestVersion);
        boolean belowMin = isOlderVersion(currentVersion, minSupportedVersion);

        if (!belowLatest && !belowMin) {
            onContinue.run();
            return;
        }

        boolean forceUpdate = belowMin || Boolean.TRUE.equals(forceFlag);
        showUpdateDialog(activity, currentVersion, latestVersion, apkUrl, forceUpdate, onContinue);
    }

    private static void showUpdateDialog(
            AppCompatActivity activity,
            String currentVersion,
            String latestVersion,
            String apkUrl,
            boolean forceUpdate,
            Runnable onContinue
    ) {
        String safeLatest = TextUtils.isEmpty(latestVersion) ? activity.getString(R.string.app_update_unknown_version) : latestVersion;
        String message = activity.getString(
                forceUpdate ? R.string.app_update_message_force : R.string.app_update_message_soft,
                currentVersion,
                safeLatest
        );

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.app_update_title)
                .setMessage(message)
                .setCancelable(!forceUpdate)
                .setPositiveButton(R.string.app_update_now, (dialog, which) -> openUpdateLink(activity, apkUrl, forceUpdate));

        if (!forceUpdate) {
            builder.setNegativeButton(R.string.app_update_later, (dialog, which) -> onContinue.run());
        }

        AlertDialog dialog = builder.create();
        if (forceUpdate) {
            dialog.setOnCancelListener(d -> closeApp(activity));
        }
        dialog.show();
    }

    private static void openUpdateLink(AppCompatActivity activity, String apkUrl, boolean forceUpdate) {
        if (TextUtils.isEmpty(apkUrl)) {
            Toast.makeText(activity, R.string.app_update_link_missing, Toast.LENGTH_LONG).show();
            if (forceUpdate) {
                closeApp(activity);
            }
            return;
        }

        try {
            Intent openLink = new Intent(Intent.ACTION_VIEW, Uri.parse(apkUrl));
            activity.startActivity(openLink);
            if (forceUpdate) {
                closeApp(activity);
            }
        } catch (ActivityNotFoundException e) {
            Toast.makeText(activity, R.string.app_update_link_open_failed, Toast.LENGTH_LONG).show();
            if (forceUpdate) {
                closeApp(activity);
            }
        }
    }

    private static void closeApp(AppCompatActivity activity) {
        activity.finishAffinity();
    }

    private static String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private static boolean isOlderVersion(String current, String target) {
        if (TextUtils.isEmpty(current) || TextUtils.isEmpty(target)) {
            return false;
        }
        int[] currentParts = parseVersion(current);
        int[] targetParts = parseVersion(target);
        int max = Math.max(currentParts.length, targetParts.length);
        for (int i = 0; i < max; i++) {
            int c = i < currentParts.length ? currentParts[i] : 0;
            int t = i < targetParts.length ? targetParts[i] : 0;
            if (c < t) {
                return true;
            }
            if (c > t) {
                return false;
            }
        }
        return false;
    }

    private static int[] parseVersion(String version) {
        String[] rawParts = version.split("\\.");
        int[] parsed = new int[rawParts.length];
        for (int i = 0; i < rawParts.length; i++) {
            parsed[i] = parseLeadingNumber(rawParts[i]);
        }
        return parsed;
    }

    private static int parseLeadingNumber(String part) {
        if (TextUtils.isEmpty(part)) {
            return 0;
        }
        StringBuilder digits = new StringBuilder();
        for (int i = 0; i < part.length(); i++) {
            char ch = part.charAt(i);
            if (Character.isDigit(ch)) {
                digits.append(ch);
            } else {
                break;
            }
        }
        if (digits.length() == 0) {
            return 0;
        }
        try {
            return Integer.parseInt(digits.toString());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
