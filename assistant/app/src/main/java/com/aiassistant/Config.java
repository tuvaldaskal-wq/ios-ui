package com.aiassistant;

import android.content.Context;

/**
 * Configuration, hardcoded into the build via BuildConfig fields populated from
 * local.properties (gitignored — your key stays on your machine).
 *
 * Public/paid build: leave ANTHROPIC_API_KEY empty and set BACKEND_URL to your
 * server (which holds the key and verifies the subscription).
 */
public final class Config {

    public final String apiKey;
    public final String model;
    public final String backendUrl;

    public Config(Context ctx) {
        apiKey = BuildConfig.ANTHROPIC_API_KEY == null ? "" : BuildConfig.ANTHROPIC_API_KEY.trim();
        String m = BuildConfig.ANTHROPIC_MODEL == null ? "" : BuildConfig.ANTHROPIC_MODEL.trim();
        model = m.isEmpty() ? "claude-haiku-4-5" : m;
        backendUrl = BuildConfig.BACKEND_URL == null ? "" : BuildConfig.BACKEND_URL.trim();
    }

    public boolean useBackend() {
        return backendUrl != null && backendUrl.length() > 0;
    }

    public boolean isConfigured() {
        return useBackend() || (apiKey != null && apiKey.length() > 0);
    }
}
