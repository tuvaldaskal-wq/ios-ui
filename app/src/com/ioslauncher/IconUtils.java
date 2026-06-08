package com.ioslauncher;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;

/**
 * Renders any Android app icon into a flat iOS "squircle" tile:
 *   - Adaptive icons are drawn full-bleed and masked to the rounded square,
 *     which is exactly how iOS presents its icons.
 *   - Legacy icons (with transparent padding) are centred on a white tile so
 *     they still read as solid iOS-style tiles instead of floating glyphs.
 * No strokes, no drop shadow - iOS home-screen icons are perfectly flat.
 */
public final class IconUtils {

    private IconUtils() {}

    // iOS continuous-corner radius approximated with a circular corner.
    private static final float CORNER_RATIO = 0.2237f;

    public static BitmapDrawable makeIosIcon(Drawable src, int sizePx) {
        int size = Math.max(1, sizePx);
        float radius = size * CORNER_RATIO;

        Bitmap raw = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas rawCanvas = new Canvas(raw);

        boolean adaptive = src != null
                && src.getClass().getName().equals("android.graphics.drawable.AdaptiveIconDrawable");

        if (src == null) {
            rawCanvas.drawColor(Color.parseColor("#E5E5EA"));
        } else if (adaptive) {
            // Full-bleed: the adaptive layers are designed to be clipped by the mask.
            src.setBounds(0, 0, size, size);
            src.draw(rawCanvas);
        } else {
            // Legacy icon: white tile + centred artwork.
            rawCanvas.drawColor(Color.WHITE);
            int iw = src.getIntrinsicWidth();
            int ih = src.getIntrinsicHeight();
            float scale;
            if (iw > 0 && ih > 0) {
                scale = (size * 0.86f) / Math.max(iw, ih);
            } else {
                scale = 0.86f;
                iw = size;
                ih = size;
            }
            int dw = Math.round(iw * scale);
            int dh = Math.round(ih * scale);
            int left = (size - dw) / 2;
            int top = (size - dh) / 2;
            src.setBounds(left, top, left + dw, top + dh);
            src.draw(rawCanvas);
        }

        // Mask into an anti-aliased rounded square.
        Bitmap out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(out);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setShader(new BitmapShader(raw, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
        canvas.drawRoundRect(new RectF(0, 0, size, size), radius, radius, paint);

        raw.recycle();
        return new BitmapDrawable(out);
    }

    public static int dp(android.content.Context ctx, float dp) {
        return Math.round(dp * ctx.getResources().getDisplayMetrics().density);
    }
}
