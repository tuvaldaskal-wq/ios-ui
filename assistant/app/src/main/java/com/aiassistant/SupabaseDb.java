package com.aiassistant;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

/**
 * Thin PostgREST client for the signed-in user's data: their subscription
 * plan (entitlement lives on the Supabase account, so it follows the login)
 * and chat logging for the admin panel. Row-level security limits every call
 * to the current user's own rows.
 */
public final class SupabaseDb {

    /** Returns the current user's plan ("free"/"pro"/…), or "" on failure. */
    public static String getPlan(Context ctx) {
        try {
            String body = request(ctx, "GET",
                    "/rest/v1/profiles?select=plan", null, "return=representation");
            JSONArray arr = new JSONArray(body);
            if (arr.length() > 0) {
                return arr.getJSONObject(0).optString("plan", "free");
            }
        } catch (Exception ignored) {
        }
        return "";
    }

    /** True if the account may use Aria: a paid plan OR an admin account. */
    public static boolean isEntitled(Context ctx) {
        try {
            String body = request(ctx, "GET",
                    "/rest/v1/profiles?select=plan,is_admin", null, "return=representation");
            JSONArray arr = new JSONArray(body);
            if (arr.length() > 0) {
                JSONObject o = arr.getJSONObject(0);
                if (o.optBoolean("is_admin", false)) {
                    return true;
                }
                return "pro".equalsIgnoreCase(o.optString("plan", "free"));
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    /** Mark the signed-in account as subscribed (called after a Play purchase). */
    public static void setPlanPro(Context ctx) {
        try {
            JSONObject body = new JSONObject();
            body.put("plan", "pro");
            request(ctx, "PATCH", "/rest/v1/profiles", body, "return=minimal");
        } catch (Exception ignored) {
        }
    }

    /** Log one chat turn (user_id defaults to auth.uid() server-side). */
    public static void logMessage(Context ctx, String role, String content) {
        try {
            JSONObject body = new JSONObject();
            body.put("role", role);
            body.put("content", content);
            request(ctx, "POST", "/rest/v1/messages", body, "return=minimal");
        } catch (Exception ignored) {
        }
    }

    private static String request(Context ctx, String method, String path,
                                  JSONObject body, String prefer) throws Exception {
        URL url = new URL(Supabase.url(ctx).replaceAll("/+$", "") + path);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod(method);
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(20000);
        conn.setRequestProperty("apikey", Supabase.anonKey(ctx));
        conn.setRequestProperty("Authorization", "Bearer " + SupabaseAuth.getToken(ctx));
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Prefer", prefer);
        if (body != null) {
            conn.setDoOutput(true);
            OutputStream os = conn.getOutputStream();
            os.write(body.toString().getBytes("UTF-8"));
            os.close();
        }
        int code = conn.getResponseCode();
        InputStream is = code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream();
        String text = readAll(is);
        conn.disconnect();
        return text;
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
}
