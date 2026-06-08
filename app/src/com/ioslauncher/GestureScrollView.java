package com.ioslauncher;

import android.content.Context;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.widget.ScrollView;

/**
 * ScrollView that feeds every touch event to an optional GestureDetector via
 * dispatchTouchEvent (so the detector always sees the full DOWN..UP stream and
 * never gets "stuck"), while leaving normal scrolling and child clicks intact.
 */
public class GestureScrollView extends ScrollView {

    private GestureDetector detector;

    public GestureScrollView(Context context) {
        super(context);
    }

    public void setDetector(GestureDetector d) {
        this.detector = d;
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (detector != null) {
            detector.onTouchEvent(ev);
        }
        return super.dispatchTouchEvent(ev);
    }
}
