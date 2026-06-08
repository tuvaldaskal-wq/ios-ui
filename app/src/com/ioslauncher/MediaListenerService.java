package com.ioslauncher;

import android.service.notification.NotificationListenerService;

/**
 * Empty notification listener. Its only purpose is to exist and be enabled by
 * the user so the launcher is allowed to read active media sessions for the
 * Dynamic Island's "Now Playing" feature.
 */
public class MediaListenerService extends NotificationListenerService {
}
