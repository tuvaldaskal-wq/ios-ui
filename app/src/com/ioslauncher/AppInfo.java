package com.ioslauncher;

import android.graphics.drawable.Drawable;

/** Lightweight model describing a launchable application. */
public class AppInfo {
    public final CharSequence label;
    public final String packageName;
    public final String activityName;
    public final Drawable icon;

    public AppInfo(CharSequence label, String packageName, String activityName, Drawable icon) {
        this.label = label;
        this.packageName = packageName;
        this.activityName = activityName;
        this.icon = icon;
    }
}
