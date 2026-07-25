package com.aiassistant;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;

/**
 * A visual "look" for Aria — a named color scheme applied to the orb wherever
 * it's shown (the talk screen, chat header/avatars, paywall). Purely a color
 * palette so it works with the existing procedurally-drawn orb (OrbView) and
 * needs no bundled image assets.
 */
public final class Avatar {

    public final String id;
    public final String label;
    public final int glowPrimary;
    public final int glowSecondary;
    public final int blobHighlight;
    public final int blobMid;
    public final int blobDark;

    private Avatar(String id, String label, int glowPrimary, int glowSecondary,
                    int blobHighlight, int blobMid, int blobDark) {
        this.id = id;
        this.label = label;
        this.glowPrimary = glowPrimary;
        this.glowSecondary = glowSecondary;
        this.blobHighlight = blobHighlight;
        this.blobMid = blobMid;
        this.blobDark = blobDark;
    }

    public static final Avatar AURORA = new Avatar("aurora", "Aurora",
            0xFF7DD3FC, 0xFF6D5DF6, 0xFFBFE3FF, 0xFF5B8DEF, 0xFF7C3AED);
    public static final Avatar SUNSET = new Avatar("sunset", "Sunset",
            0xFFFFC48C, 0xFFFF6B6B, 0xFFFFE1B3, 0xFFFF9D6C, 0xFFEE4266);
    public static final Avatar OCEAN = new Avatar("ocean", "Ocean",
            0xFF7DF9FF, 0xFF2196F3, 0xFFBFFCF6, 0xFF34C1D9, 0xFF1565C0);
    public static final Avatar FOREST = new Avatar("forest", "Forest",
            0xFFB8FFB0, 0xFF3CB371, 0xFFE3FFDD, 0xFF7BC96F, 0xFF1B5E3A);
    public static final Avatar ROSE = new Avatar("rose", "Rose",
            0xFFFFC1E3, 0xFFEC4899, 0xFFFFE1F0, 0xFFF472B6, 0xFFBE185D);
    public static final Avatar MONO = new Avatar("mono", "Mono",
            0xFFFFFFFF, 0xFFB0B0B0, 0xFFFFFFFF, 0xFFD0D0D0, 0xFF6B7280);

    public static final Avatar[] ALL = {AURORA, SUNSET, OCEAN, FOREST, ROSE, MONO};

    public static Avatar byId(String id) {
        if (id != null) {
            for (int i = 0; i < ALL.length; i++) {
                if (ALL[i].id.equals(id)) {
                    return ALL[i];
                }
            }
        }
        return AURORA;
    }

    /** A small static radial-gradient version of this look for non-animated spots. */
    public GradientDrawable makeDrawable(Context ctx, int sizeDp) {
        float density = ctx.getResources().getDisplayMetrics().density;
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        d.setGradientRadius(sizeDp * density);
        d.setGradientCenter(0.35f, 0.3f);
        d.setColors(new int[]{blobHighlight, blobMid, blobDark});
        return d;
    }
}
