package com.aiassistant;

import android.content.Context;

/**
 * Reads configuration from the (gitignored) secrets.xml resource.
 *
 * Public/paid build: leave the api key empty and set backend_url to your
 * server (which holds the key and verifies the subscription).
 * Local/free build: put the key directly in anthropic_api_key.
 */
public final class Config {

    public final String apiKey;
    public final String model;
    public final String backendUrl;

    public Config(Context ctx) {
        apiKey = str(ctx, "anthropic_api_key");
        String m = str(ctx, "anthropic_model");
        model = m.isEmpty() ? "claude-sonnet-4-6" : m;
        backendUrl = str(ctx, "backend_url");
    }

    public boolean useBackend() {
        return backendUrl != null && backendUrl.length() > 0;
    }

    public boolean isConfigured() {
        return useBackend() || (apiKey != null && apiKey.length() > 0);
    }

    private static String str(Context ctx, String name) {
        int id = ctx.getResources().getIdentifier(name, "string", ctx.getPackageName());
        if (id == 0) {
            return "";
        }
        String s = ctx.getString(id);
        return s == null ? "" : s.trim();
    }
}
