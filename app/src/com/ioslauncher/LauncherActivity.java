package com.ioslauncher;

import android.app.Activity;
import android.app.AlertDialog;
import android.appwidget.AppWidgetHost;
import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.BatteryManager;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
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
                if (statusBar != null) {
                    statusBar.setBattery(pct, charging);
                }
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
    }

    @Override
    protected void onPause() {
        super.onPause();
        try {
            unregisterReceiver(systemReceiver);
        } catch (IllegalArgumentException ignored) {
        }
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
        FrameLayout root = new FrameLayout(this);
        rootView = root;
        root.setBackgroundResource(R.drawable.ios_wallpaper);

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
        return root;
    }

    private View buildHomePage(List<AppInfo> homeApps, int pageWidth) {
        ScrollView scroll = new ScrollView(this);
        scroll.setLayoutParams(new LinearLayout.LayoutParams(
                pageWidth, ViewGroup.LayoutParams.MATCH_PARENT));
        scroll.setVerticalScrollBarEnabled(false);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(12), dp(10), dp(12), dp(8));
        content.setMinimumHeight(getResources().getDisplayMetrics().heightPixels);

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

        // Long-press anywhere empty on the home screen opens the edit menu.
        content.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                showHomeMenu();
                return true;
            }
        });

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

        FrameLayout card = new FrameLayout(this);
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

        card.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                confirmRemoveWidget(widgetId, String.valueOf(info.loadLabel(getPackageManager())));
                return true;
            }
        });
        return card;
    }

    private View makeAppCell(final AppInfo app, boolean showLabel, boolean home) {
        LinearLayout cell = new LinearLayout(this);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setGravity(Gravity.CENTER_HORIZONTAL);
        cell.setPadding(dp(4), dp(6), dp(4), dp(6));

        ImageView icon = new ImageView(this);
        int iconSize = dp(60);
        icon.setImageDrawable(IconUtils.makeIosIcon(app.icon, iconSize));
        cell.addView(icon, new LinearLayout.LayoutParams(iconSize, iconSize));

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
        holder.addView(bar);
        holder.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
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

    private void showHomeMenu() {
        final CharSequence[] items = {"Add Widget", "Choose Home Apps"};
        new AlertDialog.Builder(this)
                .setTitle("Edit Home Screen")
                .setItems(items, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (which == 0) {
                            showWidgetPicker();
                        } else {
                            showAppChooser();
                        }
                    }
                })
                .show();
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

    private void showWidgetPicker() {
        final List<AppWidgetProviderInfo> providers = appWidgetManager.getInstalledProviders();
        if (providers == null || providers.isEmpty()) {
            new AlertDialog.Builder(this)
                    .setMessage("No widgets are available on this device.")
                    .setPositiveButton("OK", null)
                    .show();
            return;
        }
        Collections.sort(providers, new Comparator<AppWidgetProviderInfo>() {
            @Override
            public int compare(AppWidgetProviderInfo a, AppWidgetProviderInfo b) {
                return providerLabel(a).compareToIgnoreCase(providerLabel(b));
            }
        });
        final String[] labels = new String[providers.size()];
        for (int i = 0; i < providers.size(); i++) {
            labels[i] = providerLabel(providers.get(i));
        }
        new AlertDialog.Builder(this)
                .setTitle("Add Widget")
                .setItems(labels, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        addWidget(providers.get(which));
                    }
                })
                .show();
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

    private void confirmRemoveWidget(final int widgetId, String label) {
        new AlertDialog.Builder(this)
                .setTitle("Remove Widget")
                .setMessage("Remove the " + label + " widget?")
                .setPositiveButton("Remove", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        List<Integer> ids = loadWidgetIds();
                        ids.remove(Integer.valueOf(widgetId));
                        saveWidgetIds(ids);
                        appWidgetHost.deleteAppWidgetId(widgetId);
                        rebuildUi();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void confirmRemoveApp(final AppInfo app) {
        new AlertDialog.Builder(this)
                .setTitle(String.valueOf(app.label))
                .setItems(new CharSequence[]{"Remove from Home", "App Info"},
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                if (which == 0) {
                                    Set<String> chosen = loadChosenKeys(new LinkedHashSet<String>());
                                    chosen.remove(key(app));
                                    prefs.edit().putStringSet(KEY_HOME_APPS, chosen).apply();
                                    rebuildUi();
                                } else {
                                    openAppInfo(app.packageName);
                                }
                            }
                        })
                .show();
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
        String q = query.trim().toLowerCase(Locale.getDefault());
        int shown = 0;
        for (int i = 0; i < allApps.size() && shown < 60; i++) {
            final AppInfo app = allApps.get(i);
            String label = String.valueOf(app.label).toLowerCase(Locale.getDefault());
            if (q.length() > 0 && !label.contains(q)) {
                continue;
            }
            container.addView(buildResultRow(app));
            shown++;
        }
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
