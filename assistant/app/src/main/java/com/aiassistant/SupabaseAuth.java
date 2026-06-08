package com.aiassistant;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Minimal Supabase Auth (GoTrue) client over raw HTTPS — no SDK dependency.
 * Signing in/up returns a JWT that is stored on the device and sent to the
 * backend Edge Function, so a user's account and plan survive a reinstall.
 */
public final class SupabaseAuth {

    private static final String PREFS = "aria_auth";

    public static String getToken(Context ctx) {
        String t = prefs(ctx).getString("access_token", "");
        return t.isEmpty() ? null : t;
    }

    public static String getEmail(Context ctx) {
        return prefs(ctx).getString("email", "");
    }

    public static void signOut(Context ctx) {
        prefs(ctx).edit().clear().apply();
    }

    /** Sign in with email + password. Returns null on success, else an error message. */
    public static String signIn(Context ctx, String email, String password) {
        try {
            JSONObject body = new JSONObject();
            body.put("email", email);
            body.put("password", password);
            JSONObject resp = post(ctx, "/auth/v1/token?grant_type=password", body);
            String token = resp.optString("access_token", "");
            if (token.isEmpty()) {
                return errorOf(resp, "Sign-in failed.");
            }
            store(ctx, token, resp.optString("refresh_token", ""), email);
            return null;
        } catch (Exception e) {
            return e.getMessage();
        }
    }

    /** Sign up. Returns null on success (or a "check your email" notice), else an error. */
    public static String signUp(Context ctx, String email, String password) {
        try {
            JSONObject body = new JSONObject();
            body.put("email", email);
            body.put("password", password);
            JSONObject resp = post(ctx, "/auth/v1/signup", body);
            String token = resp.optString("access_token", "");
            if (!token.isEmpty()) {
                store(ctx, token, resp.optString("refresh_token", ""), email);
                return null;
            }
            if (resp.has("id") || resp.has("user")) {
                // Created but needs email confirmation.
                return "Account created — please confirm your email, then log in.";
            }
            return errorOf(resp, "Sign-up failed.");
        } catch (Exception e) {
            return e.getMessage();
        }
    }

    private static void store(Context ctx, String token, String refresh, String email) {
        prefs(ctx).edit()
                .putString("access_token", token)
                .putString("refresh_token", refresh)
                .putString("email", email)
                .apply();
    }

    private static String errorOf(JSONObject resp, String fallback) {
        String m = resp.optString("msg", resp.optString("error_description",
                resp.optString("message", resp.optString("error", ""))));
        return m.isEmpty() ? fallback : m;
    }

    private static JSONObject post(Context ctx, String path, JSONObject body) throws Exception {
        URL url = new URL(Supabase.url(ctx).replaceAll("/+$", "") + path);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(20000);
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("apikey", Supabase.anonKey(ctx));
        conn.setRequestProperty("Authorization", "Bearer " + Supabase.anonKey(ctx));

        OutputStream os = conn.getOutputStream();
        os.write(body.toString().getBytes("UTF-8"));
        os.close();

        int code = conn.getResponseCode();
        InputStream is = code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream();
        String text = readAll(is);
        conn.disconnect();
        return text.isEmpty() ? new JSONObject() : new JSONObject(text);
    }

    private static String readAll(InputStream is) throws Exception {
        if (is == null) {
            return "";
        }
        BufferedReader r = new BufferedReader(new InputStreamReader(is, "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = r.readLine()) != null) {
            sb.append(line);
        }
        r.close();
        return sb.toString();
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
