package com.ioslauncher;

import android.os.Handler;
import android.os.Looper;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import java.util.HashMap;
import java.util.Map;

/**
 * Notification listener used for two features:
 *   1. Allows the launcher to read active media sessions (Dynamic Island).
 *   2. Tracks the count of active, dismissable notifications per package so the
 *      launcher can draw iOS-style red badges on app icons.
 */
public class MediaListenerService extends NotificationListenerService {

    public interface BadgeListener {
        void onBadgesChanged();
    }

    private static final Map<String, Integer> COUNTS = new HashMap<String, Integer>();
    private static BadgeListener listener;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    public static synchronized int getCount(String pkg) {
        Integer c = COUNTS.get(pkg);
        return c == null ? 0 : c.intValue();
    }

    public static void setBadgeListener(BadgeListener l) {
        listener = l;
    }

    @Override
    public void onListenerConnected() {
        rebuild();
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        rebuild();
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        rebuild();
    }

    private void rebuild() {
        Map<String, Integer> fresh = new HashMap<String, Integer>();
        try {
            StatusBarNotification[] active = getActiveNotifications();
            if (active != null) {
                for (int i = 0; i < active.length; i++) {
                    StatusBarNotification sbn = active[i];
                    if (!sbn.isClearable()) {
                        continue; // skip ongoing (music, downloads, etc.)
                    }
                    String pkg = sbn.getPackageName();
                    Integer cur = fresh.get(pkg);
                    fresh.put(pkg, Integer.valueOf(cur == null ? 1 : cur.intValue() + 1));
                }
            }
        } catch (Exception ignored) {
        }
        synchronized (MediaListenerService.class) {
            COUNTS.clear();
            COUNTS.putAll(fresh);
        }
        MAIN.post(new Runnable() {
            @Override
            public void run() {
                if (listener != null) {
                    listener.onBadgesChanged();
                }
            }
        });
    }
}
