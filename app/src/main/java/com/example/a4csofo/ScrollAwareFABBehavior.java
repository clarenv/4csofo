package com.example.a4csofo;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.view.ViewCompat;

import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

public class ScrollAwareFABBehavior extends CoordinatorLayout.Behavior<ExtendedFloatingActionButton> {

    public ScrollAwareFABBehavior(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public boolean onStartNestedScroll(@NonNull CoordinatorLayout coordinatorLayout,
                                       @NonNull ExtendedFloatingActionButton child,
                                       @NonNull View directTargetChild,
                                       @NonNull View target, int axes, int type) {
        return axes == ViewCompat.SCROLL_AXIS_VERTICAL;
    }

    @Override
    public void onNestedScroll(@NonNull CoordinatorLayout coordinatorLayout,
                               @NonNull ExtendedFloatingActionButton child,
                               @NonNull View target, int dxConsumed, int dyConsumed,
                               int dxUnconsumed, int dyUnconsumed, int type) {
        super.onNestedScroll(coordinatorLayout, child, target, dxConsumed, dyConsumed,
                dxUnconsumed, dyUnconsumed, type);

        if (dyConsumed > 10 && child.isExtended()) {
            // Scrolling down - hide FAB
            child.shrink();
        } else if (dyConsumed < -10 && !child.isExtended()) {
            // Scrolling up - show FAB
            child.extend();
        }
    }

    @Override
    public boolean onNestedFling(@NonNull CoordinatorLayout coordinatorLayout,
                                 @NonNull ExtendedFloatingActionButton child,
                                 @NonNull View target, float velocityX, float velocityY,
                                 boolean consumed) {
        if (velocityY > 0 && child.isExtended()) {
            // Fling down - hide FAB
            child.shrink();
        } else if (velocityY < 0 && !child.isExtended()) {
            // Fling up - show FAB
            child.extend();
        }
        return super.onNestedFling(coordinatorLayout, child, target, velocityX, velocityY, consumed);
    }
}