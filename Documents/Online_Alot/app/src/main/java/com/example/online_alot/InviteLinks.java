package com.example.online_alot;

import android.net.Uri;

import java.util.Locale;

/**
 * Invitation URLs. {@link #HTTPS_HOST} must match Firebase Hosting and {@code AndroidManifest} intent-filters.
 */
public final class InviteLinks {

    /** Default site from {@code google-services.json} project_id. Change if you use another Firebase project. */
    public static final String HTTPS_HOST = "barbershop-481c0.web.app";

    public static final String HTTPS_HOST_ALT = "barbershop-481c0.firebaseapp.com";

    private static final String INVITE_HTML_PATH = "/invite.html";

    private InviteLinks() {}

    /** Use in emails — https links are tappable in Gmail; page redirects to the Cutify app. */
    public static String buildHttpsInviteUrl(String token) {
        String t = token == null ? "" : token.trim().toUpperCase(Locale.ROOT);
        return "https://" + HTTPS_HOST + INVITE_HTML_PATH + "?token=" + Uri.encode(t);
    }

    public static boolean isInviteHttpsHost(String host) {
        if (host == null) {
            return false;
        }
        String h = host.toLowerCase(Locale.ROOT);
        return HTTPS_HOST.equalsIgnoreCase(h) || HTTPS_HOST_ALT.equalsIgnoreCase(h);
    }
}
