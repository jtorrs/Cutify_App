package com.example.online_alot;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;

public class RecaptchaChallengeActivity extends AppCompatActivity {

    public static final String EXTRA_SITE_KEY = "EXTRA_SITE_KEY";
    public static final String EXTRA_TOKEN = "EXTRA_TOKEN";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_recaptcha_challenge);

        String siteKey = getIntent().getStringExtra(EXTRA_SITE_KEY);
        if (siteKey == null || siteKey.trim().isEmpty()) {
            Toast.makeText(this, R.string.recaptcha_verify_failed, Toast.LENGTH_LONG).show();
            setResult(Activity.RESULT_CANCELED);
            finish();
            return;
        }

        WebView webView = findViewById(R.id.web_recap);
        MaterialButton btnCancel = findViewById(R.id.btn_recap_cancel);
        btnCancel.setOnClickListener(v -> {
            setResult(Activity.RESULT_CANCELED);
            finish();
        });

        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        webView.setWebChromeClient(new WebChromeClient());
        webView.addJavascriptInterface(new CaptchaBridge(), "AndroidBridge");
        webView.loadDataWithBaseURL(
                "https://www.google.com",
                buildCaptchaHtml(siteKey),
                "text/html",
                "UTF-8",
                null
        );
    }

    private String buildCaptchaHtml(String siteKey) {
        return "<!doctype html><html><head><meta name='viewport' content='width=device-width, initial-scale=1'/>"
                + "<script src='https://www.google.com/recaptcha/api.js' async defer></script>"
                + "<style>body{font-family:sans-serif;background:#0f172a;color:#fff;padding:16px;text-align:center}"
                + ".card{background:#111827;border-radius:12px;padding:16px;margin-top:12px}"
                + "</style></head><body><h3>Verify to continue</h3><div class='card'>"
                + "<div class='g-recaptcha' data-sitekey='" + siteKey + "' data-callback='onSolved'></div>"
                + "</div><script>function onSolved(token){AndroidBridge.onToken(token);}</script>"
                + "</body></html>";
    }

    private final class CaptchaBridge {
        @JavascriptInterface
        public void onToken(String token) {
            runOnUiThread(() -> {
                Intent data = new Intent();
                data.putExtra(EXTRA_TOKEN, token);
                setResult(Activity.RESULT_OK, data);
                finish();
            });
        }
    }
}
