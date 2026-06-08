package com.aiassistant;

import android.content.Context;

/**
 * Supabase project configuration, baked in from local.properties via
 * BuildConfig (SUPABASE_URL, SUPABASE_ANON_KEY). The anon key is public by
 * design; the service-role key must never ship in the app.
 */
public final class Supabase {

    public static String url(Context ctx) {
        return BuildConfig.SUPABASE_URL == null ? "" : BuildConfig.SUPABASE_URL.trim();
    }

    public static String anonKey(Context ctx) {
        return BuildConfig.SUPABASE_ANON_KEY == null ? "" : BuildConfig.SUPABASE_ANON_KEY.trim();
    }

    public static boolean isConfigured(Context ctx) {
        return url(ctx).length() > 0 && anonKey(ctx).length() > 0;
    }
}
