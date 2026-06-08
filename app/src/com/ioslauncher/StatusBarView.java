package com.ioslauncher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.View;

/**
 * Custom-drawn iOS status bar: time on the left, cellular signal, Wi-Fi and a
 * battery glyph on the right. Everything is vector-drawn so it scales crisply
 * and needs no image assets.
 */
public class StatusBarView extends View {

    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float density;

    private String time = "9:41";
    private int batteryLevel = 100;
    private boolean charging = false;

    public StatusBarView(Context context) {
        super(context);
        density = getResources().getDisplayMetrics().density;

        textPaint.setColor(Color.WHITE);
        textPaint.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        textPaint.setTextSize(16 * density);

        fillPaint.setColor(Color.WHITE);
        fillPaint.setStyle(Paint.Style.FILL);

        strokePaint.setColor(Color.WHITE);
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(1.4f * density);
    }

    public void setTime(String t) {
        this.time = t;
        invalidate();
    }

    public void setBattery(int level, boolean isCharging) {
        this.batteryLevel = level;
        this.charging = isCharging;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth();
        int h = getHeight();
        float cy = h / 2f;
        float pad = 22 * density;

        // --- Time, left aligned and vertically centred ---
        Paint.FontMetrics fm = textPaint.getFontMetrics();
        float baseline = cy - (fm.ascent + fm.descent) / 2f;
        canvas.drawText(time, pad, baseline, textPaint);

        // --- Right cluster: signal | wifi | battery ---
        float right = w - pad;

        // Battery (rightmost).
        float batW = 24 * density;
        float batH = 12 * density;
        float batLeft = right - batW;
        float batTop = cy - batH / 2f;
        RectF body = new RectF(batLeft, batTop, batLeft + batW, batTop + batH);
        float r = 3 * density;
        canvas.drawRoundRect(body, r, r, strokePaint);
        // Terminal nub.
        RectF nub = new RectF(batLeft + batW + 0.8f * density, cy - 2.4f * density,
                batLeft + batW + 2.6f * density, cy + 2.4f * density);
        canvas.drawRoundRect(nub, 1 * density, 1 * density, fillPaint);
        // Fill proportional to level.
        float innerPad = 2f * density;
        float fillW = (batW - 2 * innerPad) * (batteryLevel / 100f);
        RectF fill = new RectF(batLeft + innerPad, batTop + innerPad,
                batLeft + innerPad + Math.max(0f, fillW), batTop + batH - innerPad);
        fillPaint.setColor(charging ? Color.parseColor("#34C759")
                : (batteryLevel <= 20 ? Color.parseColor("#FF3B30") : Color.WHITE));
        canvas.drawRoundRect(fill, 1.5f * density, 1.5f * density, fillPaint);
        fillPaint.setColor(Color.WHITE);

        // Wi-Fi glyph to the left of the battery.
        float wifiRight = batLeft - 9 * density;
        drawWifi(canvas, wifiRight, cy);

        // Cellular signal bars to the left of Wi-Fi.
        float wifiW = 18 * density;
        float sigRight = wifiRight - wifiW - 6 * density;
        drawSignal(canvas, sigRight, cy);
    }

    private void drawSignal(Canvas canvas, float right, float cy) {
        int bars = 4;
        float barW = 3.2f * density;
        float gap = 1.8f * density;
        float maxH = 11 * density;
        float bottom = cy + maxH / 2f;
        for (int i = 0; i < bars; i++) {
            float bh = maxH * (0.45f + 0.183f * i);
            float x = right - (bars - 1 - i) * (barW + gap);
            RectF bar = new RectF(x - barW, bottom - bh, x, bottom);
            canvas.drawRoundRect(bar, 1f * density, 1f * density, fillPaint);
        }
    }

    private void drawWifi(Canvas canvas, float right, float cy) {
        float size = 16 * density;
        float cx = right - size / 2f;
        float baseY = cy + size * 0.32f;
        strokePaint.setStrokeWidth(1.6f * density);
        for (int i = 2; i >= 1; i--) {
            float rad = size * 0.30f * i;
            RectF arc = new RectF(cx - rad, baseY - rad, cx + rad, baseY + rad);
            canvas.drawArc(arc, 225, 90, false, strokePaint);
        }
        // Centre dot.
        canvas.drawCircle(cx, baseY, 1.7f * density, fillPaint);
        strokePaint.setStrokeWidth(1.4f * density);
    }
}
