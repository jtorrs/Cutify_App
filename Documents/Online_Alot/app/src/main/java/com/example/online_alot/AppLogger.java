package com.example.online_alot;

import android.util.Log;

/**
 * Centralized app logger to keep tags/messages consistent.
 */
public final class AppLogger {
    private AppLogger() {}

    public static void i(String tag, String message) {
        Log.i(tag, message);
    }

    public static void w(String tag, String message) {
        Log.w(tag, message);
    }

    public static void w(String tag, String message, Throwable t) {
        Log.w(tag, message, t);
    }

    public static void e(String tag, String message, Throwable t) {
        Log.e(tag, message, t);
    }
}
