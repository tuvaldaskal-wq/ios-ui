package com.aiassistant;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.view.View;

/**
 * A glowing orb on black that breathes when idle and wiggles/morphs to the
 * rhythm of speech. Amplitude can be fed externally (e.g. mic RMS while
 * listening); while speaking it generates an organic, speech-like envelope.
 */
public class OrbView extends View {

    public static final int IDLE = 0;
    public static final int LISTENING = 1;
    public static final int THINKING = 2;
    public static final int SPEAKING = 3;

    private final Paint blobPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();

    private int state = IDLE;
    private float extLevel = 0f;       // external amplitude (listening)
    private float displayed = 0.12f;   // smoothed level actually drawn
    private double phase = 0;
    private long startTime = System.currentTimeMillis();

    public OrbView(Context context) {
        super(context);
    }

    public void setState(int s) {
        this.state = s;
        invalidate();
    }

    /** Feed external amplitude 0..1 (used while listening to the mic). */
    public void setLevel(float level) {
        if (level < 0) {
            level = 0;
        }
        if (level > 1) {
            level = 1;
        }
        this.extLevel = level;
    }

    private float targetLevel(double t) {
        switch (state) {
            case LISTENING:
                return Math.max(0.15f, extLevel);
            case THINKING:
                return 0.22f + 0.10f * (float) Math.sin(t * 5.0);
            case SPEAKING:
                // Organic, speech-like envelope from layered sines + slow drift.
                double e = 0.55
                        + 0.30 * Math.sin(t * 11.0)
                        + 0.18 * Math.sin(t * 19.0 + 1.3)
                        + 0.12 * Math.sin(t * 31.0 + 2.1);
                return (float) Math.max(0.2, Math.min(1.0, e));
            default: // IDLE — slow breathing
                return 0.12f + 0.05f * (float) Math.sin(t * 1.6);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        double t = (System.currentTimeMillis() - startTime) / 1000.0;
        phase += 0.06;

        float target = targetLevel(t);
        displayed += (target - displayed) * 0.22f;

        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float base = Math.min(getWidth(), getHeight()) * 0.22f;
        float pulse = 1f + displayed * 0.18f;

        // --- outer glow ---
        float glowR = base * 2.6f * (1f + displayed * 0.25f);
        glowPaint.setShader(new RadialGradient(cx, cy, glowR,
                new int[]{
                        withAlpha(0xFF7DD3FC, (int) (120 + 120 * displayed)),
                        withAlpha(0xFF6D5DF6, 80),
                        0x00000000},
                new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawCircle(cx, cy, glowR, glowPaint);

        // --- morphing blob ---
        int pts = 48;
        path.reset();
        for (int i = 0; i <= pts; i++) {
            double a = (i % pts) / (double) pts * Math.PI * 2;
            double wobble = displayed * 0.30 * Math.sin(3 * a + phase)
                    + displayed * 0.18 * Math.sin(5 * a - phase * 1.3)
                    + displayed * 0.10 * Math.sin(8 * a + phase * 0.7);
            float r = (float) (base * pulse * (1 + wobble));
            float x = cx + (float) (Math.cos(a) * r);
            float y = cy + (float) (Math.sin(a) * r);
            if (i == 0) {
                path.moveTo(x, y);
            } else {
                path.lineTo(x, y);
            }
        }
        path.close();

        blobPaint.setShader(new RadialGradient(cx - base * 0.3f, cy - base * 0.3f, base * 1.8f,
                new int[]{0xFFBFE3FF, 0xFF5B8DEF, 0xFF7C3AED},
                new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawPath(path, blobPaint);

        postInvalidateDelayed(16);
    }

    private static int withAlpha(int color, int alpha) {
        if (alpha > 255) {
            alpha = 255;
        }
        return (alpha << 24) | (color & 0x00FFFFFF);
    }
}
