package com.example.online_alot;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;

/**
 * One-shot forward geocode via Nominatim (OpenStreetMap). Use sparingly; respect usage policy.
 */
public final class OpenStreetMapGeocoder {

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final String USER_AGENT = "OnlineAlot/1.0 (home service; contact via app store)";

    public interface Callback {
        void onResult(double lat, double lon);

        void onFailure();
    }

    public static void geocode(String address, Callback callback) {
        String q = address == null ? "" : address.trim();
        if (q.isEmpty()) {
            MAIN.post(callback::onFailure);
            return;
        }
        Executors.newSingleThreadExecutor().execute(() -> {
            HttpURLConnection conn = null;
            try {
                String enc = URLEncoder.encode(q, StandardCharsets.UTF_8.name());
                URL url = new URL("https://nominatim.openstreetmap.org/search?format=json&limit=1&q=" + enc);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestProperty("User-Agent", USER_AGENT);
                conn.setConnectTimeout(12_000);
                conn.setReadTimeout(12_000);
                conn.setInstanceFollowRedirects(true);
                int code = conn.getResponseCode();
                if (code < 200 || code >= 300) {
                    MAIN.post(callback::onFailure);
                    return;
                }
                StringBuilder body = new StringBuilder();
                try (BufferedReader r = new BufferedReader(
                        new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = r.readLine()) != null) {
                        body.append(line);
                    }
                }
                JSONArray arr = new JSONArray(body.toString());
                if (arr.length() == 0) {
                    MAIN.post(callback::onFailure);
                    return;
                }
                JSONObject o = arr.getJSONObject(0);
                double lat = Double.parseDouble(o.getString("lat"));
                double lon = Double.parseDouble(o.getString("lon"));
                MAIN.post(() -> callback.onResult(lat, lon));
            } catch (Exception e) {
                MAIN.post(callback::onFailure);
            } finally {
                if (conn != null) {
                    conn.disconnect();
                }
            }
        });
    }

    private OpenStreetMapGeocoder() {}
}
