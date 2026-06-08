package com.ioslauncher;

import android.content.Context;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.widget.FrameLayout;

/**
 * Container for a hosted widget that reliably detects a long-press ("hold")
 * even though the embedded widget consumes touch events. Normal taps still
 * pass through to the widget; only a long-press is intercepted.
 */
public class WidgetCard extends FrameLayout {

    public interface OnHold {
        void onHold();
    }

    private final GestureDetector detector;
    private boolean intercept;

    public WidgetCard(Context context, final OnHold onHold) {
        super(context);
        detector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public void onLongPress(MotionEvent e) {
                intercept = true;
                if (onHold != null) {
                    onHold.onHold();
                }
            }
        });
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (ev.getActionMasked() == MotionEvent.ACTION_DOWN) {
            intercept = false;
        }
        detector.onTouchEvent(ev);
        return intercept;
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        detector.onTouchEvent(ev);
        return true;
    }
}
