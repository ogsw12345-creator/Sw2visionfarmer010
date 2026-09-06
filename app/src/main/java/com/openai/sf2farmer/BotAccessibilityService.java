package com.openai.sf2farmer;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.view.accessibility.AccessibilityEvent;

import java.util.ArrayList;
import java.util.List;

public class BotAccessibilityService extends AccessibilityService {
    public static volatile BotAccessibilityService instance;

    @Override public void onServiceConnected() { instance = this; }
    @Override public void onAccessibilityEvent(AccessibilityEvent event) {}
    @Override public void onInterrupt() {}
    @Override public void onDestroy() { if (instance == this) instance = null; super.onDestroy(); }

    public boolean tap(float x, float y, long ms) {
        return multi(new Stroke(x, y, 0, ms));
    }

    public boolean multi(Stroke... strokes) {
        if (strokes == null || strokes.length == 0) return false;
        GestureDescription.Builder b = new GestureDescription.Builder();
        for (Stroke s : strokes) {
            Path p = new Path(); p.moveTo(s.x, s.y);
            b.addStroke(new GestureDescription.StrokeDescription(p, s.start, Math.max(1, s.duration)));
        }
        return dispatchGesture(b.build(), null, null);
    }

    public boolean sequence(List<Stroke> strokes) {
        GestureDescription.Builder b = new GestureDescription.Builder();
        for (Stroke s : strokes) {
            Path p = new Path(); p.moveTo(s.x, s.y);
            b.addStroke(new GestureDescription.StrokeDescription(p, s.start, Math.max(1, s.duration)));
        }
        return dispatchGesture(b.build(), null, null);
    }

    public static final class Stroke {
        public final float x, y;
        public final long start, duration;
        public Stroke(float x, float y, long start, long duration) {
            this.x=x; this.y=y; this.start=start; this.duration=duration;
        }
    }
}
