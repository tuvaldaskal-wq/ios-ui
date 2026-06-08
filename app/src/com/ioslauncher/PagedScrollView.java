package com.ioslauncher;

import android.content.Context;
import android.view.MotionEvent;
import android.view.View;
import android.widget.HorizontalScrollView;

/**
 * A HorizontalScrollView that snaps to full-width pages on release, mimicking
 * the iOS home-screen paging behaviour. Each direct child of the inner content
 * row is one page sized to the viewport width.
 */
public class PagedScrollView extends HorizontalScrollView {

    public interface OnPageChangeListener {
        void onPageChanged(int page);
    }

    private OnPageChangeListener listener;
    private int currentPage = 0;
    private float downX;
    private boolean dragging;

    public PagedScrollView(Context context) {
        super(context);
        setHorizontalScrollBarEnabled(false);
        setOverScrollMode(OVER_SCROLL_NEVER);
    }

    public void setOnPageChangeListener(OnPageChangeListener l) {
        this.listener = l;
    }

    public int getCurrentPage() {
        return currentPage;
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = ev.getX();
                dragging = true;
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (dragging) {
                    dragging = false;
                    snapToNearestPage(ev.getX());
                    return true;
                }
                break;
            default:
                break;
        }
        return super.onTouchEvent(ev);
    }

    private void snapToNearestPage(float upX) {
        int width = getWidth();
        if (width == 0) {
            return;
        }
        int pageCount = getPageCount();
        float dx = downX - upX;
        int target = Math.round((float) getScrollX() / width);

        // Honour a deliberate swipe even if it didn't cross the half-page line.
        float threshold = width * 0.18f;
        if (dx > threshold) {
            target = currentPage + 1;
        } else if (dx < -threshold) {
            target = currentPage - 1;
        }
        if (target < 0) {
            target = 0;
        }
        if (target > pageCount - 1) {
            target = pageCount - 1;
        }
        smoothScrollTo(target * width, 0);
        if (target != currentPage) {
            currentPage = target;
            if (listener != null) {
                listener.onPageChanged(currentPage);
            }
        }
    }

    private int getPageCount() {
        if (getChildCount() == 0) {
            return 1;
        }
        View content = getChildAt(0);
        if (!(content instanceof android.view.ViewGroup)) {
            return 1;
        }
        return Math.max(1, ((android.view.ViewGroup) content).getChildCount());
    }

    public void snapToPage(int page) {
        int width = getWidth();
        smoothScrollTo(page * width, 0);
        currentPage = page;
        if (listener != null) {
            listener.onPageChanged(page);
        }
    }
}
