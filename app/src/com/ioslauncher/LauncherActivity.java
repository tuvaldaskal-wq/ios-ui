package com.ioslauncher;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.BatteryManager;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
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
import java.util.List;
import java.util.Locale;

/**
 * The iOS-style home screen. Acts as a HOME launcher: paged grid of installed
 * apps, a translucent dock, an iOS status bar, page dots and a home indicator.
 */
public class LauncherActivity extends Activity {

    private static final int COLUMNS = 4;
    private static final int ROWS = 6;
    private static final int DOCK_COUNT = 4;

    private StatusBarView statusBar;
    private PagedScrollView pager;
    private LinearLayout dotsRow;
    private FrameLayout rootView;
    private View spotlightOverlay;
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
        applyImmersiveFlags();
        setContentView(buildUi());
        updateClock();
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

    /** As a HOME app, pressing back should stay on the home screen. */
    @Override
    public void onBackPressed() {
        if (spotlightOverlay != null) {
            hideSpotlight();
            return;
        }
        if (pager != null && pager.getCurrentPage() != 0) {
            pager.snapToPage(0);
        }
        // Otherwise swallow: do not exit the launcher.
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

        int topInset = getStatusBarHeight();
        column.setPadding(0, topInset, 0, 0);

        // 1. Status bar.
        statusBar = new StatusBarView(this);
        column.addView(statusBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(26)));

        List<AppInfo> apps = loadApps();
        allApps = apps;
        List<AppInfo> dockApps = new ArrayList<AppInfo>();
        List<AppInfo> gridApps = new ArrayList<AppInfo>();
        for (int i = 0; i < apps.size(); i++) {
            if (i < DOCK_COUNT) {
                dockApps.add(apps.get(i));
            } else {
                gridApps.add(apps.get(i));
            }
        }

        // 2. Paged app grid (fills remaining space).
        pager = new PagedScrollView(this);
        LinearLayout.LayoutParams pagerLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        pager.setLayoutParams(pagerLp);

        LinearLayout pagesRow = new LinearLayout(this);
        pagesRow.setOrientation(LinearLayout.HORIZONTAL);

        int screenW = getResources().getDisplayMetrics().widthPixels;
        int perPage = COLUMNS * ROWS;
        int pageCount = Math.max(1, (int) Math.ceil(gridApps.size() / (double) perPage));
        for (int p = 0; p < pageCount; p++) {
            int start = p * perPage;
            int end = Math.min(start + perPage, gridApps.size());
            View page = buildPage(gridApps.subList(start, end), screenW);
            pagesRow.addView(page);
        }
        pager.addView(pagesRow);
        column.addView(pager);

        // 3. Page dots.
        dotsRow = new LinearLayout(this);
        dotsRow.setOrientation(LinearLayout.HORIZONTAL);
        dotsRow.setGravity(Gravity.CENTER);
        for (int p = 0; p < pageCount; p++) {
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

        // 4. Search pill (decorative, iOS-style).
        column.addView(buildSearchPill());

        // 5. Dock.
        column.addView(buildDock(dockApps));

        // 6. Home indicator pill.
        column.addView(buildHomeIndicator());

        root.addView(column);
        return root;
    }

    private View buildPage(List<AppInfo> pageApps, int pageWidth) {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setLayoutParams(new LinearLayout.LayoutParams(
                pageWidth, ViewGroup.LayoutParams.MATCH_PARENT));
        page.setPadding(dp(8), dp(10), dp(8), dp(4));

        int index = 0;
        for (int r = 0; r < ROWS; r++) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
            for (int c = 0; c < COLUMNS; c++) {
                LinearLayout.LayoutParams cellLp = new LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
                if (index < pageApps.size()) {
                    row.addView(makeAppCell(pageApps.get(index), true), cellLp);
                } else {
                    View spacer = new View(this);
                    row.addView(spacer, cellLp);
                }
                index++;
            }
            page.addView(row, rowLp);
        }
        return page;
    }

    private View makeAppCell(final AppInfo app, boolean showLabel) {
        LinearLayout cell = new LinearLayout(this);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setGravity(Gravity.CENTER_HORIZONTAL);
        cell.setPadding(dp(4), dp(6), dp(4), dp(6));

        ImageView icon = new ImageView(this);
        int iconSize = dp(60);
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(iconSize, iconSize);
        icon.setImageDrawable(IconUtils.makeIosIcon(app.icon, iconSize));
        cell.addView(icon, iconLp);

        if (showLabel) {
            TextView label = new TextView(this);
            label.setText(app.label);
            label.setTextColor(Color.WHITE);
            label.setTextSize(11.5f);
            label.setMaxLines(1);
            label.setEllipsize(android.text.TextUtils.TruncateAt.END);
            label.setGravity(Gravity.CENTER);
            label.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
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
        cell.setBackground(null);
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
            LinearLayout.LayoutParams cellLp = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            dock.addView(makeAppCell(dockApps.get(i), false), cellLp);
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

        LinearLayout.LayoutParams pillLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        holder.addView(pill, pillLp);
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
    // Spotlight search
    // ------------------------------------------------------------------

    private void showSpotlight() {
        if (spotlightOverlay != null) {
            return;
        }
        FrameLayout overlay = new FrameLayout(this);
        overlay.setBackgroundColor(android.graphics.Color.parseColor("#D9000000"));
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
        field.setText("");
        field.setTextColor(android.graphics.Color.WHITE);
        field.setHintTextColor(android.graphics.Color.parseColor("#99FFFFFF"));
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
        label.setTextColor(android.graphics.Color.WHITE);
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
    // Data / actions
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
            CharSequence label = ri.loadLabel(pm);
            Drawable icon = ri.loadIcon(pm);
            apps.add(new AppInfo(label, pkg, ri.activityInfo.name, icon));
        }
        Collections.sort(apps, new Comparator<AppInfo>() {
            @Override
            public int compare(AppInfo a, AppInfo b) {
                return String.valueOf(a.label).compareToIgnoreCase(String.valueOf(b.label));
            }
        });
        return apps;
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
