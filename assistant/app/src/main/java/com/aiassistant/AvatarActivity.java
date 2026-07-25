package com.aiassistant;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** Lets the user pick Aria's "look" — a color scheme applied to the orb. */
public class AvatarActivity extends Activity {

    private Avatar selected;
    private final List<FrameLayout> rings = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        selected = AvatarPrefs.get(this);
        setContentView(buildUi());
    }

    private View buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundResource(R.drawable.app_bg);
        root.setFitsSystemWindows(true);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(20), dp(18), dp(8), dp(4));

        TextView title = new TextView(this);
        title.setText("Aria's look");
        title.setTextColor(Color.WHITE);
        title.setTextSize(22f);
        title.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        header.addView(title, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView close = new TextView(this);
        close.setText("✕");
        close.setTextColor(Color.WHITE);
        close.setTextSize(20f);
        close.setPadding(dp(12), dp(12), dp(12), dp(12));
        close.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        header.addView(close);
        root.addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView subtitle = new TextView(this);
        subtitle.setText("Pick a color scheme for the orb you talk to.");
        subtitle.setTextColor(Color.parseColor("#99FFFFFF"));
        subtitle.setTextSize(13.5f);
        subtitle.setPadding(dp(20), 0, dp(20), dp(18));
        root.addView(subtitle);

        ScrollView scroll = new ScrollView(this);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(10), 0, dp(10), dp(24));

        LinearLayout row = null;
        for (int i = 0; i < Avatar.ALL.length; i++) {
            if (i % 3 == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                list.addView(row, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            }
            LinearLayout.LayoutParams cellLp = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            row.addView(buildCell(Avatar.ALL[i]), cellLp);
        }
        scroll.addView(list);
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        return root;
    }

    private View buildCell(final Avatar avatar) {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER);
        col.setPadding(0, dp(10), 0, dp(10));

        final FrameLayout ring = new FrameLayout(this);
        int ringSize = dp(76);
        ring.setPadding(dp(6), dp(6), dp(6), dp(6));
        ring.setTag(avatar);

        OrbView preview = new OrbView(this);
        preview.setAvatar(avatar);
        ring.addView(preview, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        applyRing(ring, avatar.id.equals(selected.id));
        rings.add(ring);

        col.addView(ring, new LinearLayout.LayoutParams(ringSize, ringSize));

        TextView label = new TextView(this);
        label.setText(avatar.label);
        label.setTextColor(Color.parseColor("#E6FFFFFF"));
        label.setTextSize(13f);
        label.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        labelLp.topMargin = dp(8);
        col.addView(label, labelLp);

        col.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                selected = avatar;
                AvatarPrefs.set(AvatarActivity.this, avatar);
                for (int i = 0; i < rings.size(); i++) {
                    FrameLayout r = rings.get(i);
                    applyRing(r, ((Avatar) r.getTag()).id.equals(avatar.id));
                }
            }
        });
        return col;
    }

    private void applyRing(FrameLayout ring, boolean isSelected) {
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        if (isSelected) {
            bg.setStroke(dp(3), Color.WHITE);
        } else {
            bg.setStroke(dp(1), Color.parseColor("#33FFFFFF"));
        }
        ring.setBackground(bg);
    }

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
