package com.aiassistant;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;

/** Three softly pulsing dots — the "Aria is thinking" indicator. */
public class TypingDotsView extends View {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float density;
    private long start;

    public TypingDotsView(Context context) {
        super(context);
        density = getResources().getDisplayMetrics().density;
        paint.setColor(Color.WHITE);
        start = System.currentTimeMillis();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(Math.round(34 * density), Math.round(14 * density));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float r = 3f * density;
        float gap = 9f * density;
        float cy = getHeight() / 2f;
        float startX = r + 2 * density;
        long t = System.currentTimeMillis() - start;
        for (int i = 0; i < 3; i++) {
            double phase = (t / 1000.0 * 2 * Math.PI) - i * 0.6;
            float a = (float) (0.35 + 0.65 * (0.5 + 0.5 * Math.sin(phase)));
            paint.setAlpha(Math.round(255 * a));
            canvas.drawCircle(startX + i * gap, cy, r, paint);
        }
        postInvalidateDelayed(50);
    }
}
