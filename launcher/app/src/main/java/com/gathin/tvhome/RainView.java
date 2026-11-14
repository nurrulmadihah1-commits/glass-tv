package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;

import java.util.Random;

/**
 * A light rain over the wallpaper, behind the tiles: thin slanted streaks that fall and start again
 * at the top. Theme key {@code "rain"} (config.xml {@code rain}) turns it on or off.
 *
 * Kept light for 1 GB TVs: a fixed set of drops in plain arrays (nothing allocated per frame), one
 * Paint, lines only. It draws only while Home is in front: {@link #setRunning} is called from
 * onResume and onPause, so nothing runs while another app is playing. With reduced motion it uses
 * half the drops and about 30 frames a second.
 */
public class RainView extends View {

    /** Drops on a 1920-pixel-wide screen; scaled by the width so 720p and 4K look the same. */
    private static final int DROPS_1080P = 70;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Random random = new Random();
    private final float density;

    private float[] x, y, len, speed;
    private int[] alpha;
    private int count;
    private boolean enabled;
    private boolean running;
    private boolean reduced;
    private long lastFrame;

    public RainView(Context context, AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        paint.setColor(0xFFFFFFFF);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(Math.max(1f, 1.1f * density));
        setWillNotDraw(false);
        setFocusable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    /** Turns the rain on (from the config) and picks the calmer version for weak GPUs. */
    void configure(boolean on, boolean reducedMotion) {
        enabled = on;
        reduced = reducedMotion;
        setVisibility(on ? VISIBLE : GONE);
        count = 0;   // rebuilt on the next frame with the right size
    }

    /** Draw while Home is in front, stop while it is behind another app. */
    void setRunning(boolean run) {
        boolean go = run && enabled;
        if (go == running) return;
        running = go;
        lastFrame = 0;
        if (go) postInvalidateOnAnimation();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        count = 0;
    }

    @Override
    protected void onDetachedFromWindow() {
        running = false;
        super.onDetachedFromWindow();
    }

    private void build(int w, int h) {
        int n = Math.round(DROPS_1080P * (w / 1920f) * (reduced ? 0.5f : 1f));
        count = Math.max(12, Math.min(n, 160));
        x = new float[count];
        y = new float[count];
        len = new float[count];
        speed = new float[count];
        alpha = new int[count];
        for (int i = 0; i < count; i++) reset(i, w, h, true);
    }

    /** A new drop: near drops are longer, faster and brighter, far ones short and faint. */
    private void reset(int i, int w, int h, boolean anywhere) {
        float depth = random.nextFloat();
        len[i] = (14f + 30f * depth) * density;
        speed[i] = (380f + 520f * depth) * density;   // pixels a second
        alpha[i] = 28 + (int) (62 * depth);
        // Start a little to the left so the slant still covers the left edge.
        x[i] = random.nextFloat() * (w + h * SLANT) - h * SLANT;
        y[i] = anywhere ? random.nextFloat() * h : -len[i] - random.nextFloat() * h * 0.3f;
    }

    /** Sideways drift per pixel of fall: a gentle wind from the left. */
    private static final float SLANT = 0.16f;

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth(), h = getHeight();
        if (!enabled || w == 0 || h == 0) return;
        if (count == 0) build(w, h);

        long now = SystemClock.uptimeMillis();
        // Capped, so a long pause (or the first frame) doesn't make every drop jump.
        float dt = lastFrame == 0 ? 0f : Math.min(now - lastFrame, 50) / 1000f;
        lastFrame = now;

        for (int i = 0; i < count; i++) {
            float fall = speed[i] * dt;
            y[i] += fall;
            x[i] += fall * SLANT;
            if (y[i] - len[i] > h) reset(i, w, h, false);
            float x0 = x[i] - len[i] * SLANT, y0 = y[i] - len[i];
            // The tail is fainter than the head, which reads as motion without a gradient shader.
            float mx = (x0 + x[i]) / 2f, my = (y0 + y[i]) / 2f;
            paint.setAlpha(alpha[i] / 3);
            canvas.drawLine(x0, y0, mx, my, paint);
            paint.setAlpha(alpha[i]);
            canvas.drawLine(mx, my, x[i], y[i], paint);
        }

        if (running) {
            if (reduced) postInvalidateDelayed(33);
            else postInvalidateOnAnimation();
        }
    }
}
