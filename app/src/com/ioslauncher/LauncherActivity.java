package com.ioslauncher;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.AppOpsManager;
import android.app.SearchManager;
import android.app.admin.DevicePolicyManager;
import android.appwidget.AppWidgetHost;
import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.hardware.camera2.CameraManager;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Process;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.TranslateAnimation;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * iOS-style home screen launcher with a curated app layout, hosted home-screen
 * widgets (via {@link AppWidgetHost}), an App Library page, Spotlight search,
 * status bar, dock and home indicator. Long-press the home screen to choose
 * which apps appear and to add/remove widgets.
 */
public class LauncherActivity extends Activity {

    private static final int COLUMNS = 4;
    private static final int DOCK_COUNT = 4;
    private static final int HOST_ID = 0x1A05;
    private static final int REQ_BIND = 1;
    private static final int REQ_CONFIGURE = 2;

    private static final String PREFS = "ilauncher";
    private static final String KEY_HOME_APPS = "home_apps";
    private static final String KEY_WIDGETS = "home_widgets";

    private StatusBarView statusBar;
    private PagedScrollView pager;
    private LinearLayout dotsRow;
    private FrameLayout rootView;
    private View spotlightOverlay;

    private SharedPreferences prefs;
    private AppWidgetManager appWidgetManager;
    private AppWidgetHost appWidgetHost;
    private int pendingWidgetId = -1;

    // Dynamic Island + media
    private LinearLayout island;
    private MediaSessionManager mediaManager;
    private MediaController mediaController;
    private MediaController.Callback mediaCallback;
    private MediaSessionManager.OnActiveSessionsChangedListener sessionsListener;
    private ComponentName listenerComponent;
    private boolean charging;
    private int batteryPct = 100;

    // Lock screen
    private DevicePolicyManager dpm;
    private ComponentName adminComponent;

    // Tracks whether usage access was granted at last UI build, so the
    // Suggestions row can appear as soon as the user returns from Settings.
    private boolean usageGrantedAtBuild;

    // Notification badges: icons currently on screen, refreshed when notifications change.
    private final List<IconRef> iconRefs = new ArrayList<IconRef>();

    // Control Center flashlight
    private CameraManager cameraManager;
    private boolean torchOn;

    private static final String KEY_WALLPAPER = "wallpaper";

    private static final class IconRef {
        final String pkg;
        final ImageView view;
        final Drawable icon;
        final int size;
        IconRef(String pkg, ImageView view, Drawable icon, int size) {
            this.pkg = pkg;
            this.view = view;
            this.icon = icon;
            this.size = size;
        }
    }

    private List<AppInfo> allApps = new ArrayList<AppInfo>();
    private final List<View> dots = new ArrayList<View>();
    private final SimpleDateFormat clockFmt = new SimpleDateFormat("h:mm", Locale.getDefault());

    private final BroadcastReceiver systemReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action == null) {
                return;
            }
            if (Intent.ACTION_BATTERY_CHANGED.equals(action)) {
                int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 100);
                int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
                int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
                boolean charging = status == BatteryManager.BATTERY_STATUS_CHARGING
                        || status == BatteryManager.BATTERY_STATUS_FULL;
                int pct = scale > 0 ? Math.round(level * 100f / scale) : level;
                LauncherActivity.this.charging = charging;
                LauncherActivity.this.batteryPct = pct;
                if (statusBar != null) {
                    statusBar.setBattery(pct, charging);
                }
                updateIsland();
            } else {
                updateClock();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        appWidgetManager = AppWidgetManager.getInstance(this);
        appWidgetHost = new AppWidgetHost(this, HOST_ID);
        mediaManager = (MediaSessionManager) getSystemService(Context.MEDIA_SESSION_SERVICE);
        listenerComponent = new ComponentName(this, MediaListenerService.class);
        dpm = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
        adminComponent = new ComponentName(this, AdminReceiver.class);
        cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        MediaListenerService.setBadgeListener(new MediaListenerService.BadgeListener() {
            @Override
            public void onBadgesChanged() {
                updateBadges();
            }
        });
        applyImmersiveFlags();
        rebuildUi();
        updateClock();
    }

    private void rebuildUi() {
        setContentView(buildUi());
    }

    @Override
    protected void onStart() {
        super.onStart();
        try {
            appWidgetHost.startListening();
        } catch (Exception ignored) {
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        try {
            appWidgetHost.stopListening();
        } catch (Exception ignored) {
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            applyImmersiveFlags();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_TIME_TICK);
        filter.addAction(Intent.ACTION_TIME_CHANGED);
        filter.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        filter.addAction(Intent.ACTION_BATTERY_CHANGED);
        registerReceiver(systemReceiver, filter);
        updateClock();
        connectMedia();
        // If the user just granted usage access in Settings, surface the
        // Suggestions row immediately by rebuilding the home screen.
        if (hasUsageAccess() != usageGrantedAtBuild) {
            rebuildUi();
        }
        updateBadges();
    }

    @Override
    protected void onPause() {
        super.onPause();
        try {
            unregisterReceiver(systemReceiver);
        } catch (IllegalArgumentException ignored) {
        }
        disconnectMedia();
    }

    @Override
    public void onBackPressed() {
        if (spotlightOverlay != null) {
            hideSpotlight();
            return;
        }
        if (pager != null && pager.getCurrentPage() != 0) {
            pager.snapToPage(0);
        }
        // Otherwise swallow: stay on the home screen.
    }

    private void applyImmersiveFlags() {
        int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
        getWindow().getDecorView().setSystemUiVisibility(flags);
    }

    // ------------------------------------------------------------------
    // UI construction
    // ------------------------------------------------------------------

    private View buildUi() {
        iconRefs.clear();
        FrameLayout root = new FrameLayout(this);
        rootView = root;
        root.setBackgroundResource(currentWallpaper());

        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        column.setPadding(0, getStatusBarHeight(), 0, 0);

        statusBar = new StatusBarView(this);
        column.addView(statusBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(26)));

        allApps = loadApps();
        List<AppInfo> dockApps = new ArrayList<AppInfo>();
        for (int i = 0; i < allApps.size() && i < DOCK_COUNT; i++) {
            dockApps.add(allApps.get(i));
        }
        Set<String> dockKeys = new LinkedHashSet<String>();
        for (int i = 0; i < dockApps.size(); i++) {
            dockKeys.add(key(dockApps.get(i)));
        }

        // Curated home apps = chosen set, minus dock apps, in alphabetical order.
        Set<String> chosen = loadChosenKeys(dockKeys);
        List<AppInfo> homeApps = new ArrayList<AppInfo>();
        for (int i = 0; i < allApps.size(); i++) {
            AppInfo a = allApps.get(i);
            if (chosen.contains(key(a)) && !dockKeys.contains(key(a))) {
                homeApps.add(a);
            }
        }

        int screenW = getResources().getDisplayMetrics().widthPixels;

        pager = new PagedScrollView(this);
        pager.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        LinearLayout pagesRow = new LinearLayout(this);
        pagesRow.setOrientation(LinearLayout.HORIZONTAL);
        pagesRow.addView(buildHomePage(homeApps, screenW));
        pagesRow.addView(buildLibraryPage(allApps, screenW));
        pager.addView(pagesRow);
        column.addView(pager);

        // Page dots (2 pages: Home, App Library).
        dots.clear();
        dotsRow = new LinearLayout(this);
        dotsRow.setOrientation(LinearLayout.HORIZONTAL);
        dotsRow.setGravity(Gravity.CENTER);
        for (int p = 0; p < 2; p++) {
            View dot = new View(this);
            dot.setBackgroundResource(p == 0
                    ? R.drawable.page_dot_active : R.drawable.page_dot);
            LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(dp(7), dp(7));
            dlp.leftMargin = dp(4);
            dlp.rightMargin = dp(4);
            dotsRow.addView(dot, dlp);
            dots.add(dot);
        }
        column.addView(dotsRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(22)));
        pager.setOnPageChangeListener(new PagedScrollView.OnPageChangeListener() {
            @Override
            public void onPageChanged(int page) {
                updateDots(page);
            }
        });

        column.addView(buildSearchPill());
        column.addView(buildDock(dockApps));
        column.addView(buildHomeIndicator());

        root.addView(column);

        // Dynamic Island floats on top, centred near the very top of the screen.
        island = new LinearLayout(this);
        island.setOrientation(LinearLayout.HORIZONTAL);
        island.setGravity(Gravity.CENTER_VERTICAL);
        island.setBackgroundResource(R.drawable.island_bg);
        island.setVisibility(View.GONE);
        FrameLayout.LayoutParams islandLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(36));
        islandLp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        islandLp.topMargin = Math.max(dp(6), getStatusBarHeight() - dp(20));
        root.addView(island, islandLp);
        updateIsland();

        return root;
    }

    private View buildHomePage(List<AppInfo> homeApps, int pageWidth) {
        final GestureScrollView scroll = new GestureScrollView(this);
        scroll.setLayoutParams(new LinearLayout.LayoutParams(
                pageWidth, ViewGroup.LayoutParams.MATCH_PARENT));
        scroll.setVerticalScrollBarEnabled(false);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(12), dp(10), dp(12), dp(8));
        content.setMinimumHeight(getResources().getDisplayMetrics().heightPixels);

        // Smart Suggestions row (usage-based), if access has been granted.
        usageGrantedAtBuild = hasUsageAccess();
        View suggestions = buildSuggestionsRow();
        if (suggestions != null) {
            content.addView(suggestions);
        }

        // Widgets first, each in a rounded frosted card.
        List<Integer> widgetIds = loadWidgetIds();
        for (int i = 0; i < widgetIds.size(); i++) {
            View card = buildWidgetCard(widgetIds.get(i));
            if (card != null) {
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.bottomMargin = dp(14);
                content.addView(card, lp);
            }
        }

        // Curated app icons.
        addAppRows(content, homeApps, true);

        // Long-press empty home space = edit menu (reliable OnLongClickListener).
        content.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                showHomeMenu();
                return true;
            }
        });

        // Double-tap = lock, swipe down (at top) = Spotlight. Detector is fed the
        // full touch stream via GestureScrollView.dispatchTouchEvent, so taps are
        // never mistaken for long-presses.
        final GestureDetector gestures = new GestureDetector(this,
                new GestureDetector.SimpleOnGestureListener() {
                    @Override
                    public boolean onDoubleTap(MotionEvent e) {
                        lockScreen();
                        return true;
                    }
                    @Override
                    public boolean onFling(MotionEvent e1, MotionEvent e2,
                                           float velocityX, float velocityY) {
                        if (e1 != null && e2 != null && scroll.getScrollY() == 0
                                && e2.getY() - e1.getY() > dp(110)
                                && Math.abs(velocityY) > Math.abs(velocityX)
                                && velocityY > 0) {
                            showSpotlight();
                            return true;
                        }
                        return false;
                    }
                });
        scroll.setDetector(gestures);

        scroll.addView(content);
        return scroll;
    }

    private View buildLibraryPage(List<AppInfo> apps, int pageWidth) {
        ScrollView scroll = new ScrollView(this);
        scroll.setLayoutParams(new LinearLayout.LayoutParams(
                pageWidth, ViewGroup.LayoutParams.MATCH_PARENT));
        scroll.setVerticalScrollBarEnabled(false);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(12), dp(6), dp(12), dp(8));

        TextView title = new TextView(this);
        title.setText("App Library");
        title.setTextColor(Color.WHITE);
        title.setTextSize(22f);
        title.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        title.setPadding(dp(6), dp(4), 0, dp(10));
        content.addView(title);

        addAppRows(content, apps, false);
        scroll.addView(content);
        return scroll;
    }

    /** Lay apps out in fixed-height rows of {@link #COLUMNS}. */
    private void addAppRows(LinearLayout container, List<AppInfo> apps, boolean home) {
        int rowHeight = dp(96);
        int index = 0;
        while (index < apps.size()) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            for (int c = 0; c < COLUMNS; c++) {
                LinearLayout.LayoutParams cellLp = new LinearLayout.LayoutParams(0, rowHeight, 1f);
                if (index < apps.size()) {
                    row.addView(makeAppCell(apps.get(index), true, home), cellLp);
                } else {
                    row.addView(new View(this), cellLp);
                }
                index++;
            }
            container.addView(row, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
    }

    private View buildWidgetCard(final int widgetId) {
        final AppWidgetProviderInfo info = appWidgetManager.getAppWidgetInfo(widgetId);
        if (info == null) {
            return null;
        }
        AppWidgetHostView hostView;
        try {
            hostView = appWidgetHost.createView(getApplicationContext(), widgetId, info);
            hostView.setAppWidget(widgetId, info);
        } catch (Exception e) {
            return null;
        }

        WidgetCard card = new WidgetCard(this, new WidgetCard.OnHold() {
            @Override
            public void onHold() {
                showWidgetOptions(widgetId, String.valueOf(info.loadLabel(getPackageManager())));
            }
        });
        card.setBackgroundResource(R.drawable.widget_card);
        final float radius = dp(22);
        card.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View v, Outline outline) {
                outline.setRoundRect(0, 0, v.getWidth(), v.getHeight(), radius);
            }
        });
        card.setClipToOutline(true);

        int minH = info.minHeight > 0 ? info.minHeight : dp(110);
        int height = Math.max(dp(96), Math.min(minH, dp(380)));
        card.addView(hostView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        card.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, height));
        return card;
    }

    private View makeAppCell(final AppInfo app, boolean showLabel, boolean home) {
        LinearLayout cell = new LinearLayout(this);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setGravity(Gravity.CENTER_HORIZONTAL);
        cell.setPadding(dp(4), dp(6), dp(4), dp(6));

        ImageView icon = new ImageView(this);
        int iconSize = dp(60);
        icon.setImageDrawable(IconUtils.makeIosIcon(app.icon, iconSize,
                MediaListenerService.getCount(app.packageName)));
        cell.addView(icon, new LinearLayout.LayoutParams(iconSize, iconSize));
        iconRefs.add(new IconRef(app.packageName, icon, app.icon, iconSize));

        if (showLabel) {
            TextView label = new TextView(this);
            label.setText(app.label);
            label.setTextColor(Color.WHITE);
            label.setTextSize(11.5f);
            label.setMaxLines(1);
            label.setEllipsize(android.text.TextUtils.TruncateAt.END);
            label.setGravity(Gravity.CENTER);
            label.setShadowLayer(3f, 0f, 1f, Color.parseColor("#80000000"));
            LinearLayout.LayoutParams lblLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lblLp.topMargin = dp(5);
            cell.addView(label, lblLp);
        }

        cell.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                launchApp(app);
            }
        });
        if (home) {
            cell.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    confirmRemoveApp(app);
                    return true;
                }
            });
        }
        return cell;
    }

    private View buildDock(List<AppInfo> dockApps) {
        FrameLayout wrapper = new FrameLayout(this);
        LinearLayout.LayoutParams wrapLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        wrapLp.leftMargin = dp(10);
        wrapLp.rightMargin = dp(10);
        wrapLp.bottomMargin = dp(2);
        wrapper.setLayoutParams(wrapLp);

        LinearLayout dock = new LinearLayout(this);
        dock.setOrientation(LinearLayout.HORIZONTAL);
        dock.setBackgroundResource(R.drawable.dock_bg);
        dock.setPadding(dp(10), dp(10), dp(10), dp(10));
        dock.setGravity(Gravity.CENTER);
        for (int i = 0; i < dockApps.size(); i++) {
            dock.addView(makeAppCell(dockApps.get(i), false, false),
                    new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        }
        wrapper.addView(dock);
        return wrapper;
    }

    private View buildSearchPill() {
        TextView pill = new TextView(this);
        pill.setText("🔍  Search");
        pill.setTextColor(Color.parseColor("#E6FFFFFF"));
        pill.setTextSize(13f);
        pill.setGravity(Gravity.CENTER);
        pill.setBackgroundResource(R.drawable.search_pill);
        pill.setPadding(dp(16), dp(7), dp(16), dp(7));
        pill.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showSpotlight();
            }
        });

        LinearLayout holder = new LinearLayout(this);
        holder.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams holderLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        holderLp.bottomMargin = dp(8);
        holder.setLayoutParams(holderLp);
        holder.addView(pill, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return holder;
    }

    private View buildHomeIndicator() {
        View bar = new View(this);
        bar.setBackgroundColor(Color.parseColor("#CCFFFFFF"));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(134), dp(5));
        lp.gravity = Gravity.CENTER_HORIZONTAL;
        lp.topMargin = dp(6);
        lp.bottomMargin = dp(8);
        bar.setLayoutParams(lp);

        LinearLayout holder = new LinearLayout(this);
        holder.setGravity(Gravity.CENTER);
        holder.setPadding(0, dp(4), 0, dp(4));
        holder.addView(bar);
        holder.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        holder.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showControlCenter();
            }
        });
        return holder;
    }

    private void updateDots(int active) {
        for (int i = 0; i < dots.size(); i++) {
            dots.get(i).setBackgroundResource(i == active
                    ? R.drawable.page_dot_active : R.drawable.page_dot);
        }
    }

    // ------------------------------------------------------------------
    // Home editing: choose apps + add/remove widgets
    // ------------------------------------------------------------------

    private interface SheetListener {
        void onSelect(int index);
    }

    private void showHomeMenu() {
        showActionSheet("Edit Home Screen",
                new String[]{"Add Widget", "Choose Home Apps", "Wallpaper",
                        "Smart Suggestions Setup", "Dynamic Island Setup", "Lock Screen"},
                new boolean[]{false, false, false, false, false, true},
                new SheetListener() {
                    @Override
                    public void onSelect(int index) {
                        switch (index) {
                            case 0:
                                showWidgetGallery();
                                break;
                            case 1:
                                showAppChooser();
                                break;
                            case 2:
                                showWallpaperPicker();
                                break;
                            case 3:
                                openSettings(Settings.ACTION_USAGE_ACCESS_SETTINGS);
                                break;
                            case 4:
                                openSettings(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
                                break;
                            case 5:
                                lockScreen();
                                break;
                            default:
                                break;
                        }
                    }
                });
    }

    private void openSettings(String action) {
        try {
            startActivity(new Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception ignored) {
        }
    }

    private void lockScreen() {
        if (dpm != null && dpm.isAdminActive(adminComponent)) {
            try {
                dpm.lockNow();
            } catch (Exception ignored) {
            }
        } else {
            try {
                Intent intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
                intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent);
                intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                        "Enable so iLauncher can lock your screen from the Home menu.");
                startActivity(intent);
            } catch (Exception ignored) {
            }
        }
    }

    /** iOS-style bottom action sheet with a separate Cancel button. */
    private void showActionSheet(String title, final String[] options,
                                 final boolean[] destructive, final SheetListener listener) {
        final FrameLayout overlay = new FrameLayout(this);
        overlay.setBackgroundColor(Color.parseColor("#66000000"));
        overlay.setClickable(true);
        overlay.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                rootView.removeView(overlay);
            }
        });

        LinearLayout sheet = new LinearLayout(this);
        sheet.setOrientation(LinearLayout.VERTICAL);
        FrameLayout.LayoutParams sheetLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sheetLp.gravity = Gravity.BOTTOM;
        sheet.setLayoutParams(sheetLp);
        sheet.setPadding(dp(10), dp(10), dp(10), dp(16));

        LinearLayout group = new LinearLayout(this);
        group.setOrientation(LinearLayout.VERTICAL);
        group.setBackgroundResource(R.drawable.action_group_bg);

        if (title != null) {
            TextView t = new TextView(this);
            t.setText(title);
            t.setTextColor(Color.parseColor("#8A8A8E"));
            t.setTextSize(13f);
            t.setGravity(Gravity.CENTER);
            t.setPadding(dp(16), dp(16), dp(16), dp(14));
            group.addView(t);
            group.addView(divider());
        }
        for (int i = 0; i < options.length; i++) {
            final int idx = i;
            boolean dest = destructive != null && i < destructive.length && destructive[i];
            TextView b = new TextView(this);
            b.setText(options[i]);
            b.setTextColor(dest ? Color.parseColor("#FF3B30") : Color.parseColor("#0A84FF"));
            b.setTextSize(19f);
            b.setGravity(Gravity.CENTER);
            b.setPadding(dp(16), dp(17), dp(16), dp(17));
            b.setClickable(true);
            b.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    rootView.removeView(overlay);
                    listener.onSelect(idx);
                }
            });
            group.addView(b);
            if (i < options.length - 1) {
                group.addView(divider());
            }
        }
        sheet.addView(group, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout cancelGroup = new LinearLayout(this);
        cancelGroup.setBackgroundResource(R.drawable.action_group_bg);
        TextView cancel = new TextView(this);
        cancel.setText("Cancel");
        cancel.setTextColor(Color.parseColor("#0A84FF"));
        cancel.setTextSize(19f);
        cancel.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        cancel.setGravity(Gravity.CENTER);
        cancel.setPadding(dp(16), dp(17), dp(16), dp(17));
        cancel.setClickable(true);
        cancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                rootView.removeView(overlay);
            }
        });
        cancelGroup.addView(cancel, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams cgLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cgLp.topMargin = dp(8);
        sheet.addView(cancelGroup, cgLp);

        overlay.addView(sheet);
        rootView.addView(overlay, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        TranslateAnimation anim = new TranslateAnimation(0, 0, dp(360), 0);
        anim.setDuration(220);
        anim.setInterpolator(new DecelerateInterpolator());
        sheet.startAnimation(anim);
    }

    private View divider() {
        View d = new View(this);
        d.setBackgroundColor(Color.parseColor("#D1D1D6"));
        d.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(0.5f))));
        return d;
    }

    private void showAppChooser() {
        final List<AppInfo> apps = allApps;
        final String[] labels = new String[apps.size()];
        final boolean[] checked = new boolean[apps.size()];
        Set<String> chosen = loadChosenKeys(new LinkedHashSet<String>());
        for (int i = 0; i < apps.size(); i++) {
            labels[i] = String.valueOf(apps.get(i).label);
            checked[i] = chosen.contains(key(apps.get(i)));
        }
        new AlertDialog.Builder(this)
                .setTitle("Apps on Home Screen")
                .setMultiChoiceItems(labels, checked,
                        new DialogInterface.OnMultiChoiceClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which, boolean isChecked) {
                                checked[which] = isChecked;
                            }
                        })
                .setPositiveButton("Done", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Set<String> selected = new LinkedHashSet<String>();
                        for (int i = 0; i < apps.size(); i++) {
                            if (checked[i]) {
                                selected.add(key(apps.get(i)));
                            }
                        }
                        prefs.edit().putStringSet(KEY_HOME_APPS, selected).apply();
                        rebuildUi();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    /** Full-screen widget gallery with a live preview image for each widget. */
    private void showWidgetGallery() {
        final List<AppWidgetProviderInfo> providers = appWidgetManager.getInstalledProviders();
        if (providers == null || providers.isEmpty()) {
            showActionSheet("No widgets are available on this device.",
                    new String[]{"OK"}, null, new SheetListener() {
                        @Override
                        public void onSelect(int index) { }
                    });
            return;
        }
        Collections.sort(providers, new Comparator<AppWidgetProviderInfo>() {
            @Override
            public int compare(AppWidgetProviderInfo a, AppWidgetProviderInfo b) {
                return providerLabel(a).compareToIgnoreCase(providerLabel(b));
            }
        });

        final FrameLayout overlay = new FrameLayout(this);
        overlay.setBackgroundColor(Color.parseColor("#F2000000"));
        overlay.setClickable(true);

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(16), getStatusBarHeight() + dp(14), dp(16), dp(8));

        // Header: title + Done.
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = new TextView(this);
        title.setText("Widgets");
        title.setTextColor(Color.WHITE);
        title.setTextSize(26f);
        title.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        header.addView(title, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView done = new TextView(this);
        done.setText("Done");
        done.setTextColor(Color.parseColor("#0A84FF"));
        done.setTextSize(17f);
        done.setPadding(dp(10), dp(8), dp(6), dp(8));
        done.setClickable(true);
        done.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                rootView.removeView(overlay);
            }
        });
        header.addView(done);
        panel.addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView hint = new TextView(this);
        hint.setText("Tap a widget to add it to your Home Screen");
        hint.setTextColor(Color.parseColor("#99FFFFFF"));
        hint.setTextSize(13f);
        hint.setPadding(0, dp(4), 0, dp(12));
        panel.addView(hint);

        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.addView(list);

        int density = getResources().getDisplayMetrics().densityDpi;
        for (int i = 0; i < providers.size(); i++) {
            final AppWidgetProviderInfo info = providers.get(i);
            list.addView(buildGalleryCard(info, density, overlay));
        }

        panel.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        overlay.addView(panel, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        rootView.addView(overlay, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private View buildGalleryCard(final AppWidgetProviderInfo info, int density,
                                  final View overlay) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.widget_card);
        card.setPadding(dp(14), dp(14), dp(14), dp(14));
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardLp.bottomMargin = dp(14);
        card.setLayoutParams(cardLp);

        // Preview image (falls back to the provider/app icon).
        Drawable preview = null;
        try {
            preview = info.loadPreviewImage(this, density);
        } catch (Exception ignored) {
        }
        if (preview == null) {
            try {
                preview = info.loadIcon(this, density);
            } catch (Exception ignored) {
            }
        }
        ImageView img = new ImageView(this);
        img.setAdjustViewBounds(true);
        img.setMaxHeight(dp(180));
        img.setScaleType(ImageView.ScaleType.FIT_CENTER);
        if (preview != null) {
            img.setImageDrawable(preview);
        }
        LinearLayout.LayoutParams imgLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        imgLp.bottomMargin = dp(10);
        card.addView(img, imgLp);

        // Label + estimated size.
        LinearLayout meta = new LinearLayout(this);
        meta.setOrientation(LinearLayout.HORIZONTAL);
        meta.setGravity(Gravity.CENTER_VERTICAL);
        TextView label = new TextView(this);
        label.setText(providerLabel(info));
        label.setTextColor(Color.WHITE);
        label.setTextSize(16f);
        label.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        label.setSingleLine(true);
        label.setEllipsize(android.text.TextUtils.TruncateAt.END);
        meta.addView(label, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView size = new TextView(this);
        size.setText(widgetSizeText(info));
        size.setTextColor(Color.parseColor("#99FFFFFF"));
        size.setTextSize(13f);
        meta.addView(size);
        card.addView(meta);

        card.setClickable(true);
        card.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                rootView.removeView(overlay);
                addWidget(info);
            }
        });
        return card;
    }

    private String widgetSizeText(AppWidgetProviderInfo info) {
        int cell = dp(70);
        int cols = Math.max(1, Math.round(info.minWidth / (float) cell));
        int rows = Math.max(1, Math.round(info.minHeight / (float) cell));
        return cols + "×" + rows;
    }

    private String providerLabel(AppWidgetProviderInfo info) {
        CharSequence l = info.loadLabel(getPackageManager());
        return l == null ? String.valueOf(info.provider) : l.toString();
    }

    private void addWidget(AppWidgetProviderInfo info) {
        int id = appWidgetHost.allocateAppWidgetId();
        pendingWidgetId = id;
        boolean bound;
        try {
            bound = appWidgetManager.bindAppWidgetIdIfAllowed(id, info.provider);
        } catch (Exception e) {
            bound = false;
        }
        if (bound) {
            afterBind(id);
        } else {
            Intent intent = new Intent(AppWidgetManager.ACTION_APPWIDGET_BIND);
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id);
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider);
            startActivityForResult(intent, REQ_BIND);
        }
    }

    private void afterBind(int id) {
        AppWidgetProviderInfo info = appWidgetManager.getAppWidgetInfo(id);
        if (info != null && info.configure != null) {
            try {
                Intent intent = new Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE);
                intent.setComponent(info.configure);
                intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id);
                startActivityForResult(intent, REQ_CONFIGURE);
                return;
            } catch (Exception ignored) {
                // Fall through and just add it.
            }
        }
        commitWidget(id);
    }

    private void commitWidget(int id) {
        List<Integer> ids = loadWidgetIds();
        if (!ids.contains(id)) {
            ids.add(id);
        }
        saveWidgetIds(ids);
        pendingWidgetId = -1;
        rebuildUi();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        int id = data != null
                ? data.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pendingWidgetId)
                : pendingWidgetId;
        if (resultCode == RESULT_OK && id != -1) {
            if (requestCode == REQ_BIND) {
                afterBind(id);
            } else if (requestCode == REQ_CONFIGURE) {
                commitWidget(id);
            }
        } else if (id != -1) {
            // Cancelled: release the allocated id.
            appWidgetHost.deleteAppWidgetId(id);
            pendingWidgetId = -1;
        }
    }

    private void showWidgetOptions(final int widgetId, String label) {
        showActionSheet(label,
                new String[]{"Move Up", "Move Down", "Remove Widget"},
                new boolean[]{false, false, true},
                new SheetListener() {
                    @Override
                    public void onSelect(int index) {
                        if (index == 0) {
                            moveWidget(widgetId, -1);
                        } else if (index == 1) {
                            moveWidget(widgetId, 1);
                        } else {
                            List<Integer> ids = loadWidgetIds();
                            ids.remove(Integer.valueOf(widgetId));
                            saveWidgetIds(ids);
                            appWidgetHost.deleteAppWidgetId(widgetId);
                            rebuildUi();
                        }
                    }
                });
    }

    private void moveWidget(int widgetId, int delta) {
        List<Integer> ids = loadWidgetIds();
        int idx = ids.indexOf(Integer.valueOf(widgetId));
        int target = idx + delta;
        if (idx < 0 || target < 0 || target >= ids.size()) {
            return;
        }
        Integer moved = ids.remove(idx);
        ids.add(target, moved);
        saveWidgetIds(ids);
        rebuildUi();
    }

    private void confirmRemoveApp(final AppInfo app) {
        showActionSheet(String.valueOf(app.label),
                new String[]{"Remove from Home", "App Info"},
                new boolean[]{true, false},
                new SheetListener() {
                    @Override
                    public void onSelect(int index) {
                        if (index == 0) {
                            Set<String> chosen = loadChosenKeys(new LinkedHashSet<String>());
                            chosen.remove(key(app));
                            prefs.edit().putStringSet(KEY_HOME_APPS, chosen).apply();
                            rebuildUi();
                        } else {
                            openAppInfo(app.packageName);
                        }
                    }
                });
    }

    private void openAppInfo(String pkg) {
        try {
            Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(android.net.Uri.parse("package:" + pkg));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (Exception ignored) {
        }
    }

    // ------------------------------------------------------------------
    // Dynamic Island + Now Playing
    // ------------------------------------------------------------------

    private void updateIsland() {
        if (island == null) {
            return;
        }
        island.removeAllViews();
        if (mediaController != null && mediaTitle() != null) {
            buildMediaIsland();
            island.setVisibility(View.VISIBLE);
        } else if (charging) {
            buildChargingIsland();
            island.setVisibility(View.VISIBLE);
        } else {
            island.setVisibility(View.GONE);
        }
    }

    private void buildChargingIsland() {
        island.setPadding(dp(15), 0, dp(17), 0);
        island.addView(glyph("⚡", Color.parseColor("#34C759"), 15f, false));
        TextView pct = glyph(batteryPct + "%", Color.WHITE, 14f, true);
        marginStart(pct, dp(5));
        island.addView(pct);
    }

    private void buildMediaIsland() {
        island.setPadding(dp(8), 0, dp(12), 0);

        ImageView thumb = new ImageView(this);
        int ts = dp(26);
        Bitmap art = mediaArt();
        if (art != null) {
            thumb.setImageDrawable(IconUtils.makeIosIcon(new BitmapDrawable(art), ts));
        } else {
            thumb.setImageDrawable(IconUtils.makeIosIcon(null, ts));
        }
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(ts, ts);
        tlp.rightMargin = dp(8);
        island.addView(thumb, tlp);

        TextView title = new TextView(this);
        title.setText(mediaTitle());
        title.setTextColor(Color.WHITE);
        title.setTextSize(12.5f);
        title.setSingleLine(true);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        title.setMaxWidth(dp(118));
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        title.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                launchMediaApp();
            }
        });
        island.addView(title);

        final boolean playing = isPlaying();
        TextView pp = glyph(playing ? "❚❚" : "▶", Color.WHITE, 13f, false);
        marginStart(pp, dp(10));
        pp.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                togglePlay();
            }
        });
        island.addView(pp);

        TextView next = glyph("⏭", Color.WHITE, 14f, false);
        marginStart(next, dp(10));
        next.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mediaController != null) {
                    mediaController.getTransportControls().skipToNext();
                }
            }
        });
        island.addView(next);
    }

    private TextView glyph(String text, int color, float size, boolean medium) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextColor(color);
        t.setTextSize(size);
        t.setGravity(Gravity.CENTER);
        if (medium) {
            t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        }
        t.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return t;
    }

    private LinearLayout.LayoutParams marginStart(View v, int px) {
        LinearLayout.LayoutParams lp =
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = px;
        v.setLayoutParams(lp);
        return lp;
    }

    private String mediaTitle() {
        if (mediaController == null) {
            return null;
        }
        MediaMetadata md = mediaController.getMetadata();
        if (md == null) {
            return null;
        }
        String t = md.getString(MediaMetadata.METADATA_KEY_TITLE);
        return (t == null || t.trim().length() == 0) ? null : t;
    }

    private Bitmap mediaArt() {
        if (mediaController == null) {
            return null;
        }
        MediaMetadata md = mediaController.getMetadata();
        if (md == null) {
            return null;
        }
        Bitmap b = md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
        if (b == null) {
            b = md.getBitmap(MediaMetadata.METADATA_KEY_ART);
        }
        return b;
    }

    private boolean isPlaying() {
        if (mediaController == null) {
            return false;
        }
        PlaybackState ps = mediaController.getPlaybackState();
        return ps != null && ps.getState() == PlaybackState.STATE_PLAYING;
    }

    private void togglePlay() {
        if (mediaController == null) {
            return;
        }
        if (isPlaying()) {
            mediaController.getTransportControls().pause();
        } else {
            mediaController.getTransportControls().play();
        }
    }

    private void launchMediaApp() {
        if (mediaController == null) {
            return;
        }
        Intent i = getPackageManager().getLaunchIntentForPackage(mediaController.getPackageName());
        if (i != null) {
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        }
    }

    private void connectMedia() {
        if (mediaManager == null) {
            return;
        }
        try {
            List<MediaController> list = mediaManager.getActiveSessions(listenerComponent);
            bindController(pickController(list));
            if (sessionsListener == null) {
                sessionsListener = new MediaSessionManager.OnActiveSessionsChangedListener() {
                    @Override
                    public void onActiveSessionsChanged(List<MediaController> controllers) {
                        bindController(pickController(controllers));
                    }
                };
            }
            mediaManager.addOnActiveSessionsChangedListener(sessionsListener, listenerComponent);
        } catch (SecurityException e) {
            // Notification access not granted yet.
        } catch (Exception ignored) {
        }
    }

    private MediaController pickController(List<MediaController> list) {
        if (list == null || list.isEmpty()) {
            return null;
        }
        for (int i = 0; i < list.size(); i++) {
            PlaybackState ps = list.get(i).getPlaybackState();
            if (ps != null && ps.getState() == PlaybackState.STATE_PLAYING) {
                return list.get(i);
            }
        }
        return list.get(0);
    }

    private void bindController(MediaController c) {
        if (mediaController != null && mediaCallback != null) {
            try {
                mediaController.unregisterCallback(mediaCallback);
            } catch (Exception ignored) {
            }
        }
        mediaController = c;
        if (c != null) {
            mediaCallback = new MediaController.Callback() {
                @Override
                public void onPlaybackStateChanged(PlaybackState state) {
                    updateIsland();
                }
                @Override
                public void onMetadataChanged(MediaMetadata metadata) {
                    updateIsland();
                }
                @Override
                public void onSessionDestroyed() {
                    mediaController = null;
                    updateIsland();
                }
            };
            c.registerCallback(mediaCallback);
        }
        updateIsland();
    }

    private void disconnectMedia() {
        try {
            if (mediaManager != null && sessionsListener != null) {
                mediaManager.removeOnActiveSessionsChangedListener(sessionsListener);
            }
        } catch (Exception ignored) {
        }
        if (mediaController != null && mediaCallback != null) {
            try {
                mediaController.unregisterCallback(mediaCallback);
            } catch (Exception ignored) {
            }
        }
    }

    // ------------------------------------------------------------------
    // Smart Suggestions (usage-based)
    // ------------------------------------------------------------------

    private View buildSuggestionsRow() {
        if (!hasUsageAccess()) {
            return null;
        }
        List<AppInfo> sugg = loadSuggestions(4);
        if (sugg.isEmpty()) {
            return null;
        }
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams boxLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        boxLp.bottomMargin = dp(8);
        box.setLayoutParams(boxLp);

        TextView title = new TextView(this);
        title.setText("SIRI SUGGESTIONS");
        title.setTextColor(Color.parseColor("#B3FFFFFF"));
        title.setTextSize(11f);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        title.setPadding(dp(8), dp(2), 0, dp(2));
        box.addView(title);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < sugg.size(); i++) {
            row.addView(makeAppCell(sugg.get(i), true, false),
                    new LinearLayout.LayoutParams(0, dp(94), 1f));
        }
        for (int i = sugg.size(); i < 4; i++) {
            row.addView(new View(this), new LinearLayout.LayoutParams(0, dp(94), 1f));
        }
        box.addView(row);
        return box;
    }

    private boolean hasUsageAccess() {
        try {
            AppOpsManager ops = (AppOpsManager) getSystemService(Context.APP_OPS_SERVICE);
            int mode = ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(), getPackageName());
            return mode == AppOpsManager.MODE_ALLOWED;
        } catch (Exception e) {
            return false;
        }
    }

    private List<AppInfo> loadSuggestions(int max) {
        List<AppInfo> out = new ArrayList<AppInfo>();
        try {
            UsageStatsManager usm =
                    (UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);
            long now = System.currentTimeMillis();
            List<UsageStats> stats = usm.queryUsageStats(
                    UsageStatsManager.INTERVAL_BEST, now - 1000L * 60 * 60 * 24 * 7, now);
            if (stats == null) {
                return out;
            }
            Map<String, Long> last = new HashMap<String, Long>();
            for (int i = 0; i < stats.size(); i++) {
                UsageStats us = stats.get(i);
                Long cur = last.get(us.getPackageName());
                if (cur == null || us.getLastTimeUsed() > cur.longValue()) {
                    last.put(us.getPackageName(), Long.valueOf(us.getLastTimeUsed()));
                }
            }
            Map<String, AppInfo> byPkg = new HashMap<String, AppInfo>();
            for (int i = 0; i < allApps.size(); i++) {
                AppInfo a = allApps.get(i);
                if (!byPkg.containsKey(a.packageName)) {
                    byPkg.put(a.packageName, a);
                }
            }
            List<Map.Entry<String, Long>> entries =
                    new ArrayList<Map.Entry<String, Long>>(last.entrySet());
            Collections.sort(entries, new Comparator<Map.Entry<String, Long>>() {
                @Override
                public int compare(Map.Entry<String, Long> a, Map.Entry<String, Long> b) {
                    return b.getValue().compareTo(a.getValue());
                }
            });
            String self = getPackageName();
            for (int i = 0; i < entries.size() && out.size() < max; i++) {
                String p = entries.get(i).getKey();
                if (p.equals(self)) {
                    continue;
                }
                AppInfo a = byPkg.get(p);
                if (a != null) {
                    out.add(a);
                }
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    // ------------------------------------------------------------------
    // Notification badges
    // ------------------------------------------------------------------

    private void updateBadges() {
        for (int i = 0; i < iconRefs.size(); i++) {
            IconRef ref = iconRefs.get(i);
            int count = MediaListenerService.getCount(ref.pkg);
            ref.view.setImageDrawable(IconUtils.makeIosIcon(ref.icon, ref.size, count));
        }
    }

    // ------------------------------------------------------------------
    // Wallpaper themes
    // ------------------------------------------------------------------

    private int currentWallpaper() {
        String name = prefs.getString(KEY_WALLPAPER, "ios_wallpaper");
        if ("ocean".equals(name)) {
            return R.drawable.theme_ocean;
        }
        if ("sunset".equals(name)) {
            return R.drawable.theme_sunset;
        }
        if ("dark".equals(name)) {
            return R.drawable.theme_dark;
        }
        return R.drawable.ios_wallpaper;
    }

    private void showWallpaperPicker() {
        final String[] names = {"Aurora", "Ocean", "Sunset", "Midnight"};
        final String[] keys = {"ios_wallpaper", "ocean", "sunset", "dark"};
        showActionSheet("Wallpaper", names, null, new SheetListener() {
            @Override
            public void onSelect(int index) {
                prefs.edit().putString(KEY_WALLPAPER, keys[index]).apply();
                rebuildUi();
            }
        });
    }

    // ------------------------------------------------------------------
    // Control Center
    // ------------------------------------------------------------------

    private void showControlCenter() {
        final FrameLayout overlay = new FrameLayout(this);
        overlay.setBackgroundColor(Color.parseColor("#B3000000"));
        overlay.setClickable(true);
        overlay.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                rootView.removeView(overlay);
            }
        });

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackgroundResource(R.drawable.widget_card);
        panel.setPadding(dp(18), dp(18), dp(18), dp(18));
        FrameLayout.LayoutParams panelLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        panelLp.gravity = Gravity.BOTTOM;
        panelLp.leftMargin = dp(10);
        panelLp.rightMargin = dp(10);
        panelLp.bottomMargin = dp(20);
        panel.setLayoutParams(panelLp);

        TextView title = new TextView(this);
        title.setText("Control Center");
        title.setTextColor(Color.WHITE);
        title.setTextSize(15f);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        title.setPadding(dp(4), 0, 0, dp(14));
        panel.addView(title);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);

        final TextView flash = controlTile("🔦", "Flashlight", torchOn);
        flash.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleTorch();
                v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                styleTile(flash, "🔦", "Flashlight", torchOn);
            }
        });
        row.addView(flash);

        TextView wifi = controlTile("📶", "Wi-Fi", false);
        wifi.setOnClickListener(openSettingsTile(Settings.ACTION_WIFI_SETTINGS, overlay));
        row.addView(wifi);

        TextView bt = controlTile("🔵", "Bluetooth", false);
        bt.setOnClickListener(openSettingsTile(Settings.ACTION_BLUETOOTH_SETTINGS, overlay));
        row.addView(bt);

        TextView air = controlTile("✈", "Airplane", false);
        air.setOnClickListener(openSettingsTile(Settings.ACTION_AIRPLANE_MODE_SETTINGS, overlay));
        row.addView(air);

        panel.addView(row);

        LinearLayout row2 = new LinearLayout(this);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        row2.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams r2lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        r2lp.topMargin = dp(14);
        row2.setLayoutParams(r2lp);

        TextView calc = controlTile("🧮", "Calculator", false);
        calc.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                rootView.removeView(overlay);
                launchByCategory(Intent.CATEGORY_APP_CALCULATOR);
            }
        });
        row2.addView(calc);

        TextView cam = controlTile("📷", "Camera", false);
        cam.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                rootView.removeView(overlay);
                try {
                    startActivity(new Intent(android.provider.MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                } catch (Exception ignored) {
                }
            }
        });
        row2.addView(cam);

        TextView settings = controlTile("⚙", "Settings", false);
        settings.setOnClickListener(openSettingsTile(Settings.ACTION_SETTINGS, overlay));
        row2.addView(settings);

        TextView lock = controlTile("🔒", "Lock", false);
        lock.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                rootView.removeView(overlay);
                lockScreen();
            }
        });
        row2.addView(lock);

        panel.addView(row2);

        overlay.addView(panel);
        rootView.addView(overlay, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        TranslateAnimation anim = new TranslateAnimation(0, 0, dp(260), 0);
        anim.setDuration(220);
        anim.setInterpolator(new DecelerateInterpolator());
        panel.startAnimation(anim);
    }

    private View.OnClickListener openSettingsTile(final String action, final View overlay) {
        return new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                rootView.removeView(overlay);
                openSettings(action);
            }
        };
    }

    private TextView controlTile(String glyph, String label, boolean on) {
        TextView t = new TextView(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp.leftMargin = dp(6);
        lp.rightMargin = dp(6);
        t.setLayoutParams(lp);
        styleTile(t, glyph, label, on);
        return t;
    }

    private void styleTile(TextView t, String glyph, String label, boolean on) {
        t.setText(glyph + "\n" + label);
        t.setGravity(Gravity.CENTER);
        t.setTextColor(on ? Color.parseColor("#FF9500") : Color.WHITE);
        t.setTextSize(13f);
        t.setLineSpacing(dp(4), 1f);
        int v = dp(12);
        t.setPadding(0, v, 0, v);
        t.setBackgroundResource(on ? R.drawable.control_tile_on : R.drawable.control_tile);
        LinearLayout.LayoutParams existing = (LinearLayout.LayoutParams) t.getLayoutParams();
        if (existing != null) {
            existing.leftMargin = dp(6);
            existing.rightMargin = dp(6);
        }
    }

    private void toggleTorch() {
        if (cameraManager == null) {
            return;
        }
        try {
            String camId = null;
            String[] ids = cameraManager.getCameraIdList();
            for (int i = 0; i < ids.length; i++) {
                Boolean hasFlash = cameraManager.getCameraCharacteristics(ids[i])
                        .get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE);
                Integer facing = cameraManager.getCameraCharacteristics(ids[i])
                        .get(android.hardware.camera2.CameraCharacteristics.LENS_FACING);
                if (Boolean.TRUE.equals(hasFlash)) {
                    camId = ids[i];
                    if (facing != null && facing.intValue()
                            == android.hardware.camera2.CameraMetadata.LENS_FACING_BACK) {
                        break;
                    }
                }
            }
            if (camId != null) {
                torchOn = !torchOn;
                cameraManager.setTorchMode(camId, torchOn);
            }
        } catch (Exception ignored) {
        }
    }

    private void launchByCategory(String category) {
        try {
            Intent i = new Intent(Intent.ACTION_MAIN);
            i.addCategory(category);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (Exception ignored) {
        }
    }

    // ------------------------------------------------------------------
    // Spotlight search
    // ------------------------------------------------------------------

    private void showSpotlight() {
        if (spotlightOverlay != null) {
            return;
        }
        FrameLayout overlay = new FrameLayout(this);
        overlay.setBackgroundColor(Color.parseColor("#D9000000"));
        overlay.setClickable(true);
        overlay.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hideSpotlight();
            }
        });

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(16), getStatusBarHeight() + dp(18), dp(16), dp(16));

        final EditText field = new EditText(this);
        field.setHint("Search");
        field.setSingleLine(true);
        field.setTextColor(Color.WHITE);
        field.setHintTextColor(Color.parseColor("#99FFFFFF"));
        field.setTextSize(17f);
        field.setBackgroundResource(R.drawable.search_pill);
        field.setPadding(dp(16), dp(11), dp(16), dp(11));

        final LinearLayout results = new LinearLayout(this);
        results.setOrientation(LinearLayout.VERTICAL);
        ScrollView scroll = new ScrollView(this);
        scroll.addView(results);

        populateResults(results, "");
        field.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {
                populateResults(results, s.toString());
            }
            @Override
            public void afterTextChanged(Editable s) { }
        });

        panel.addView(field, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams scrollLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        scrollLp.topMargin = dp(12);
        panel.addView(scroll, scrollLp);

        overlay.addView(panel, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        rootView.addView(overlay, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        spotlightOverlay = overlay;

        field.requestFocus();
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.showSoftInput(field, InputMethodManager.SHOW_IMPLICIT);
        }
    }

    private void hideSpotlight() {
        if (spotlightOverlay == null) {
            return;
        }
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(spotlightOverlay.getWindowToken(), 0);
        }
        rootView.removeView(spotlightOverlay);
        spotlightOverlay = null;
    }

    private void populateResults(LinearLayout container, String query) {
        container.removeAllViews();
        String raw = query.trim();
        String q = raw.toLowerCase(Locale.getDefault());

        // Inline calculator.
        if (raw.length() > 0) {
            Double val = MathEval.eval(raw);
            if (val != null) {
                container.addView(buildCalcRow(raw, val));
            }
        }

        int shown = 0;
        for (int i = 0; i < allApps.size() && shown < 50; i++) {
            final AppInfo app = allApps.get(i);
            String label = String.valueOf(app.label).toLowerCase(Locale.getDefault());
            if (q.length() > 0 && !label.contains(q)) {
                continue;
            }
            container.addView(buildResultRow(app));
            shown++;
        }

        // Always offer a web search for the typed text.
        if (raw.length() > 0) {
            container.addView(buildWebRow(raw));
        }
    }

    private View buildCalcRow(final String expr, double value) {
        final String result = formatNumber(value);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(6), dp(10), dp(6), dp(10));

        TextView equals = new TextView(this);
        equals.setText("=");
        equals.setTextColor(Color.parseColor("#0A84FF"));
        equals.setTextSize(26f);
        equals.setGravity(Gravity.CENTER);
        equals.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        row.addView(equals, new LinearLayout.LayoutParams(dp(42), ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        TextView res = new TextView(this);
        res.setText(result);
        res.setTextColor(Color.WHITE);
        res.setTextSize(24f);
        res.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        res.setSingleLine(true);
        res.setEllipsize(android.text.TextUtils.TruncateAt.END);
        col.addView(res);
        TextView sub = new TextView(this);
        sub.setText(expr + "   ·   tap to copy");
        sub.setTextColor(Color.parseColor("#99FFFFFF"));
        sub.setTextSize(13f);
        col.addView(sub);
        LinearLayout.LayoutParams colLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        colLp.leftMargin = dp(10);
        row.addView(col, colLp);

        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    ClipboardManager cm =
                            (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                    cm.setPrimaryClip(ClipData.newPlainText("result", result));
                } catch (Exception ignored) {
                }
            }
        });
        return row;
    }

    private View buildWebRow(final String query) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(6), dp(12), dp(6), dp(12));

        TextView glyph = new TextView(this);
        glyph.setText("🔍");
        glyph.setTextSize(18f);
        glyph.setGravity(Gravity.CENTER);
        row.addView(glyph, new LinearLayout.LayoutParams(dp(42), ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView label = new TextView(this);
        label.setText("Search the web for “" + query + "”");
        label.setTextColor(Color.WHITE);
        label.setTextSize(16f);
        label.setSingleLine(true);
        label.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp.leftMargin = dp(10);
        row.addView(label, lp);

        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hideSpotlight();
                webSearch(query);
            }
        });
        return row;
    }

    private void webSearch(String query) {
        try {
            Intent i = new Intent(Intent.ACTION_WEB_SEARCH);
            i.putExtra(SearchManager.QUERY, query);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (Exception e) {
            try {
                Intent v = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(
                        "https://www.google.com/search?q=" + android.net.Uri.encode(query)));
                v.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(v);
            } catch (Exception ignored) {
            }
        }
    }

    private String formatNumber(double value) {
        if (value == Math.floor(value) && !Double.isInfinite(value)
                && Math.abs(value) < 1e15) {
            return String.valueOf((long) value);
        }
        String s = String.valueOf(value);
        return s;
    }

    private View buildResultRow(final AppInfo app) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(6), dp(8), dp(6), dp(8));

        ImageView icon = new ImageView(this);
        int s = dp(42);
        icon.setImageDrawable(IconUtils.makeIosIcon(app.icon, s));
        row.addView(icon, new LinearLayout.LayoutParams(s, s));

        TextView label = new TextView(this);
        label.setText(app.label);
        label.setTextColor(Color.WHITE);
        label.setTextSize(17f);
        label.setSingleLine(true);
        label.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp.leftMargin = dp(14);
        row.addView(label, lp);

        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hideSpotlight();
                launchApp(app);
            }
        });
        return row;
    }

    // ------------------------------------------------------------------
    // Data / persistence
    // ------------------------------------------------------------------

    private List<AppInfo> loadApps() {
        PackageManager pm = getPackageManager();
        Intent main = new Intent(Intent.ACTION_MAIN, null);
        main.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> resolved = pm.queryIntentActivities(main, 0);

        List<AppInfo> apps = new ArrayList<AppInfo>();
        String self = getPackageName();
        for (int i = 0; i < resolved.size(); i++) {
            ResolveInfo ri = resolved.get(i);
            String pkg = ri.activityInfo.packageName;
            if (self.equals(pkg)) {
                continue;
            }
            apps.add(new AppInfo(ri.loadLabel(pm), pkg, ri.activityInfo.name, ri.loadIcon(pm)));
        }
        Collections.sort(apps, new Comparator<AppInfo>() {
            @Override
            public int compare(AppInfo a, AppInfo b) {
                return String.valueOf(a.label).compareToIgnoreCase(String.valueOf(b.label));
            }
        });
        return apps;
    }

    private String key(AppInfo a) {
        return a.packageName + "/" + a.activityName;
    }

    /** Chosen home apps; seeds a sensible default on first run. */
    private Set<String> loadChosenKeys(Set<String> dockKeys) {
        Set<String> stored = prefs.getStringSet(KEY_HOME_APPS, null);
        if (stored != null) {
            return new LinkedHashSet<String>(stored);
        }
        // Default: the apps right after the dock, up to 12 of them.
        Set<String> def = new LinkedHashSet<String>();
        int count = 0;
        for (int i = 0; i < allApps.size() && count < 12; i++) {
            String k = key(allApps.get(i));
            if (dockKeys.contains(k)) {
                continue;
            }
            def.add(k);
            count++;
        }
        prefs.edit().putStringSet(KEY_HOME_APPS, def).apply();
        return def;
    }

    private List<Integer> loadWidgetIds() {
        List<Integer> ids = new ArrayList<Integer>();
        String raw = prefs.getString(KEY_WIDGETS, "");
        if (raw == null || raw.length() == 0) {
            return ids;
        }
        String[] parts = raw.split(",");
        for (int i = 0; i < parts.length; i++) {
            try {
                ids.add(Integer.valueOf(parts[i].trim()));
            } catch (NumberFormatException ignored) {
            }
        }
        return ids;
    }

    private void saveWidgetIds(List<Integer> ids) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) {
                sb.append(",");
            }
            sb.append(ids.get(i));
        }
        prefs.edit().putString(KEY_WIDGETS, sb.toString()).apply();
    }

    private void launchApp(AppInfo app) {
        try {
            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.addCategory(Intent.CATEGORY_LAUNCHER);
            intent.setClassName(app.packageName, app.activityName);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            startActivity(intent);
        } catch (Exception e) {
            Intent fallback = getPackageManager().getLaunchIntentForPackage(app.packageName);
            if (fallback != null) {
                fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(fallback);
            }
        }
    }

    private void updateClock() {
        if (statusBar != null) {
            statusBar.setTime(clockFmt.format(new Date()));
        }
    }

    private int getStatusBarHeight() {
        int id = getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (id > 0) {
            return getResources().getDimensionPixelSize(id);
        }
        return dp(24);
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
