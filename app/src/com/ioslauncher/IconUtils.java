package com.ioslauncher;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;

/**
 * Renders any app icon into the iOS "squircle" style: a rounded square with a
 * subtle inner background so transparent legacy icons still read as tiles.
 */
public final class IconUtils {

    private IconUtils() {}

    public static BitmapDrawable makeIosIcon(Drawable src, int sizePx) {
        int size = sizePx;
        float radius = size * 0.225f; // iOS continuous-corner ratio approximation

        // 1. Flatten the source drawable onto a tile-sized bitmap.
        Bitmap raw = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas rawCanvas = new Canvas(raw);
        if (src != null) {
            // Inset slightly so adaptive/legacy icons fill the tile nicely.
            int inset = Math.round(size * 0.06f);
            src.setBounds(inset, inset, size - inset, size - inset);
            // Fill behind transparent icons with a soft light tile.
            if (hasTransparentEdges(src)) {
                Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
                bg.setColor(Color.parseColor("#F2F2F7"));
                rawCanvas.drawRect(0, 0, size, size, bg);
            }
            src.draw(rawCanvas);
        }

        // 2. Clip into a rounded-rect output.
        Bitmap out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(out);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setShader(new BitmapShader(raw, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
        RectF rect = new RectF(0, 0, size, size);
        canvas.drawRoundRect(rect, radius, radius, paint);

        // 3. Thin highlight stroke for depth.
        Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setColor(Color.parseColor("#1AFFFFFF"));
        stroke.setStrokeWidth(Math.max(1f, size * 0.012f));
        float h = stroke.getStrokeWidth() / 2f;
        canvas.drawRoundRect(new RectF(h, h, size - h, size - h), radius, radius, stroke);

        raw.recycle();
        return new BitmapDrawable(out);
    }

    private static boolean hasTransparentEdges(Drawable d) {
        // Heuristic: many legacy launcher icons are non-square / have transparent
        // margins. Treat anything that isn't an opaque bitmap as needing a tile.
        return d.getOpacity() != android.graphics.PixelFormat.OPAQUE;
    }

    /** Convert dp to device pixels. */
    public static int dp(android.content.Context ctx, float dp) {
        float density = ctx.getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }
}
