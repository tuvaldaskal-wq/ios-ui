package com.aiassistant;

import android.content.Context;
import android.content.SharedPreferences;

/** Persists which Avatar (orb look) the user picked for Aria. */
public final class AvatarPrefs {

    private static final String PREFS = "aria_avatar";
    private static final String KEY = "avatar_id";

    public static Avatar get(Context ctx) {
        return Avatar.byId(prefs(ctx).getString(KEY, null));
    }

    public static void set(Context ctx, Avatar avatar) {
        prefs(ctx).edit().putString(KEY, avatar.id).apply();
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
