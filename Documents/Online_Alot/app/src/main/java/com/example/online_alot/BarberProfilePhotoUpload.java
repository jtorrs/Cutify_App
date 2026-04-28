package com.example.online_alot;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.webkit.MimeTypeMap;
import android.widget.ImageView;
import android.widget.Toast;

import com.bumptech.glide.Glide;

import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.FilterOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Uploads picked gallery images to Cloudinary and exposes an HTTPS URL for all clients.
 */
public final class BarberProfilePhotoUpload {

    private static final String TAG = "BarberPhotoUpload";
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final int MAX_FILE_BYTES = 10 * 1024 * 1024; // 10MB input guard
    private static final int MAX_UPLOAD_DIMENSION = 1600;

    public interface UploadCallbacks {
        void onStarted();
        void onProgress(int percent);
        void onSuccess(String httpsUrl);
        void onError(String message);
    }

    private BarberProfilePhotoUpload() {}

    public static void uploadAndApply(Context context, String barberEmail, Uri localUri,
                                     ImageView preview, Runnable onDone) {
        uploadAndApply(context, barberEmail, localUri, preview, onDone, null);
    }

    public static void uploadAndApply(Context context, String barberEmail, Uri localUri,
                                      ImageView preview, Runnable onDone, UploadCallbacks callbacks) {
        if (context == null || barberEmail == null || barberEmail.isEmpty() || localUri == null) {
            return;
        }

        String cloudName = context.getString(R.string.cloudinary_cloud_name).trim();
        String uploadPreset = context.getString(R.string.cloudinary_upload_preset).trim();
        if (cloudName.isEmpty() || uploadPreset.isEmpty()
                || cloudName.startsWith("PASTE_") || uploadPreset.startsWith("PASTE_")) {
            Toast.makeText(context, R.string.photo_upload_cloudinary_not_configured, Toast.LENGTH_LONG).show();
            return;
        }

        String normId = barberEmail.trim().toLowerCase(Locale.ROOT);
        preview.setAlpha(0.7f);
        if (callbacks != null) {
            callbacks.onStarted();
            callbacks.onProgress(0);
        }
        EXECUTOR.execute(() -> {
            try {
                String https = uploadToCloudinary(context, localUri, cloudName, uploadPreset, callbacks);
                MAIN.post(() -> {
                    AdminProfileManager apm = new AdminProfileManager(context, barberEmail);
                    apm.savePhotoUri(https);
                    Glide.with(context.getApplicationContext()).load(https).fitCenter().into(preview);
                    QueueManager.getInstance().updateBarberPhotoUrl(normId, https);
                    BarberFirestoreSync.mergePhotoUrl(normId, https);
                    preview.setAlpha(1f);
                    Toast.makeText(context, R.string.photo_upload_ok, Toast.LENGTH_SHORT).show();
                    if (callbacks != null) {
                        callbacks.onProgress(100);
                        callbacks.onSuccess(https);
                    }
                    if (onDone != null) {
                        onDone.run();
                    }
                });
            } catch (Exception e) {
                AppLogger.e(TAG, "Cloudinary upload failed", e);
                String msg = e.getMessage();
                MAIN.post(() -> {
                    preview.setAlpha(1f);
                    if (callbacks != null) {
                        callbacks.onError(msg);
                    }
                    if (!TextUtils.isEmpty(msg)) {
                        Toast.makeText(context,
                                context.getString(R.string.photo_upload_failed_with_reason, msg),
                                Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(context, R.string.photo_upload_failed, Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    private static String uploadToCloudinary(
            Context context, Uri localUri, String cloudName, String uploadPreset, UploadCallbacks callbacks
    ) throws Exception {
        String boundary = "----CutifyCloudinaryBoundary" + System.currentTimeMillis();
        String endpoint = "https://api.cloudinary.com/v1_1/" + cloudName + "/image/upload";
        HttpURLConnection conn = (HttpURLConnection) new URL(endpoint).openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setConnectTimeout(20000);
        conn.setReadTimeout(30000);
        conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);

        byte[] fileBytes = readAndCompressImage(context, localUri);
        String mimeType = detectMimeType(context, localUri);
        String ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType);
        if (ext == null || ext.trim().isEmpty()) {
            ext = "jpg";
        }
        String fileName = "avatar." + ext;
        try (OutputStream rawOut = conn.getOutputStream()) {
            writeFormField(rawOut, boundary, "upload_preset", uploadPreset);
            // Unsigned upload presets may reject extra params; keep payload minimal.
            ProgressOutputStream progressOut = new ProgressOutputStream(rawOut, fileBytes.length, callbacks);
            writeFileField(progressOut, boundary, "file", fileName, mimeType, fileBytes);
            progressOut.write(("--" + boundary + "--\r\n").getBytes());
            progressOut.flush();
        }

        int code = conn.getResponseCode();
        InputStream stream = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
        String body = readStream(stream);
        if (code < 200 || code >= 300) {
            String reason = readCloudinaryErrorMessage(body);
            throw new IllegalStateException(reason.isEmpty()
                    ? "Upload failed. Please check internet and try again."
                    : reason);
        }
        JSONObject json = new JSONObject(body);
        String secureUrl = json.optString("secure_url", "").trim();
        if (secureUrl.isEmpty()) {
            throw new IllegalStateException("Missing secure_url in Cloudinary response");
        }
        return secureUrl;
    }

    private static String detectMimeType(Context context, Uri uri) {
        String type = context.getContentResolver().getType(uri);
        if (type == null || type.trim().isEmpty()) {
            return "image/jpeg";
        }
        if (!type.startsWith("image/")) {
            throw new IllegalArgumentException("Only image files are allowed.");
        }
        return type;
    }

    private static byte[] readAndCompressImage(Context context, Uri uri) throws Exception {
        try (InputStream in = new BufferedInputStream(context.getContentResolver().openInputStream(uri));
             ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            if (in == null) {
                throw new IllegalStateException("Could not open image stream");
            }
            int totalRead = 0;
            byte[] buffer = new byte[8192];
            int n;
            while ((n = in.read(buffer)) != -1) {
                bos.write(buffer, 0, n);
                totalRead += n;
                if (totalRead > MAX_FILE_BYTES) {
                    throw new IllegalArgumentException("Image is too large. Please use 10MB or less.");
                }
            }
            byte[] source = bos.toByteArray();
            Bitmap raw = BitmapFactory.decodeByteArray(source, 0, source.length);
            if (raw == null) {
                throw new IllegalArgumentException("Invalid image file.");
            }
            Bitmap scaled = scaleDown(raw, MAX_UPLOAD_DIMENSION);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            scaled.compress(Bitmap.CompressFormat.JPEG, 85, out);
            if (scaled != raw) {
                scaled.recycle();
            }
            raw.recycle();
            return out.toByteArray();
        }
    }

    private static Bitmap scaleDown(Bitmap src, int maxDim) {
        int w = src.getWidth();
        int h = src.getHeight();
        int largest = Math.max(w, h);
        if (largest <= maxDim) {
            return src;
        }
        float ratio = maxDim / (float) largest;
        int nw = Math.max(1, Math.round(w * ratio));
        int nh = Math.max(1, Math.round(h * ratio));
        return Bitmap.createScaledBitmap(src, nw, nh, true);
    }

    private static void writeFormField(OutputStream out, String boundary, String name, String value) throws Exception {
        out.write(("--" + boundary + "\r\n").getBytes());
        out.write(("Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n").getBytes());
        out.write((value + "\r\n").getBytes());
    }

    private static void writeFileField(
            OutputStream out, String boundary, String name, String fileName, String contentType, byte[] bytes
    ) throws Exception {
        out.write(("--" + boundary + "\r\n").getBytes());
        out.write(("Content-Disposition: form-data; name=\"" + name + "\"; filename=\"" + fileName + "\"\r\n")
                .getBytes());
        out.write(("Content-Type: " + contentType + "\r\n\r\n").getBytes());
        out.write(bytes);
        out.write("\r\n".getBytes());
    }

    private static String readStream(InputStream stream) throws Exception {
        if (stream == null) {
            return "";
        }
        try (BufferedReader br = new BufferedReader(new InputStreamReader(stream))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }
            return sb.toString();
        }
    }

    private static String readCloudinaryErrorMessage(String body) {
        if (body == null || body.trim().isEmpty()) {
            return "";
        }
        try {
            JSONObject json = new JSONObject(body);
            JSONObject err = json.optJSONObject("error");
            if (err != null) {
                String msg = err.optString("message", "").trim();
                if (!msg.isEmpty()) {
                    return msg;
                }
            }
        } catch (Exception ignored) {
        }
        return body;
    }

    private static final class ProgressOutputStream extends FilterOutputStream {
        private final long totalFileBytes;
        private final UploadCallbacks callbacks;
        private long writtenFileBytes = 0L;
        private int lastPercent = -1;

        ProgressOutputStream(OutputStream out, long totalFileBytes, UploadCallbacks callbacks) {
            super(out);
            this.totalFileBytes = Math.max(1L, totalFileBytes);
            this.callbacks = callbacks;
        }

        @Override
        public void write(byte[] b, int off, int len) throws java.io.IOException {
            out.write(b, off, len);
            writtenFileBytes += len;
            dispatchProgress();
        }

        @Override
        public void write(int b) throws java.io.IOException {
            out.write(b);
            writtenFileBytes += 1;
            dispatchProgress();
        }

        private void dispatchProgress() {
            if (callbacks == null) {
                return;
            }
            int percent = (int) Math.min(100, Math.max(0, (writtenFileBytes * 100L) / totalFileBytes));
            if (percent == lastPercent) {
                return;
            }
            lastPercent = percent;
            MAIN.post(() -> callbacks.onProgress(percent));
        }
    }
}
