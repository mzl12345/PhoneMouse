package com.mzl.phonemouse;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;

/**
 * 右侧滚动条：按住中间的圆点上下拖，页面就上下滚。
 * 松手后圆点回到中间。
 */
public class ScrollBarView extends View {

    public interface Listener {
        /** direction: -1 向上滚，1 向下滚 */
        void onScroll(int direction);
    }

    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint knobPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Listener listener;

    private float knobY;        // 当前圆点中心 y
    private float centerY;      // 轨道中点
    private float maxOffset;    // 最大偏移
    private float trackTop, trackBottom, trackLen;
    private long lastMoveTs;
    private float lastY;
    private boolean dragging;

    private final Runnable scroller = new Runnable() {
        @Override
        public void run() {
            if (!dragging) return;
            float offset = knobY - centerY;
            float depth = maxOffset <= 0 ? 0 : Math.abs(offset) / maxOffset;
            if (depth > 0.12f && listener != null) {
                listener.onScroll(offset > 0 ? 1 : -1);
            }
            handler.postDelayed(this, (long) (60 - depth * 30));
        }
    };

    public ScrollBarView(Context c) {
        super(c);
        trackPaint.setColor(0x1A000000);
        knobPaint.setColor(0xFF4C6FFF);
        setClickable(true);
    }

    public void setListener(Listener l) {
        this.listener = l;
    }

    private float dp(float v) {
        return v * getResources().getDisplayMetrics().density;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        centerY = h / 2f;
        knobY = centerY;
        trackLen = Math.min(h - dp(80), dp(240));
        trackTop = centerY - trackLen / 2f;
        trackBottom = centerY + trackLen / 2f;
        maxOffset = Math.max(0, trackLen / 2f - dp(24));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth();
        float trackW = dp(8);
        float left = (w - trackW) / 2f;
        float r = trackW / 2f;
        canvas.drawRoundRect(left, trackTop, left + trackW, trackBottom, r, r, trackPaint);

        float knobR = dp(19);
        canvas.drawCircle(w / 2f, knobY, knobR, knobPaint);
        knobPaint.setColor(0x33FFFFFF);
        canvas.drawCircle(w / 2f, knobY - knobR * 0.25f, knobR * 0.45f, knobPaint);
        knobPaint.setColor(0xFF4C6FFF);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                float dy = Math.abs(e.getY() - knobY);
                if (dy > dp(44)) {
                    knobY = Math.min(trackBottom, Math.max(trackTop, e.getY()));
                }
                dragging = true;
                lastY = e.getY();
                lastMoveTs = android.os.SystemClock.uptimeMillis();
                vibrate();
                handler.removeCallbacks(scroller);
                invalidate();
                return true;
            }
            case MotionEvent.ACTION_MOVE: {
                if (!dragging) return true;
                knobY = Math.min(trackBottom, Math.max(trackTop, e.getY()));
                long now = android.os.SystemClock.uptimeMillis();
                float dt = Math.max(8f, now - lastMoveTs);
                float dy = e.getY() - lastY;
                // 拖得越快，一次发越多步（加速）
                float speed = Math.abs(dy) / dt;
                int steps = (int) (Math.abs(dy) / dp(14) + speed * 7);
                steps = Math.max(1, Math.min(6, steps));
                if (Math.abs(dy) > dp(2) && listener != null) {
                    int dir = dy > 0 ? 1 : -1;
                    for (int i = 0; i < steps; i++) listener.onScroll(dir);
                }
                lastY = e.getY();
                lastMoveTs = now;
                invalidate();
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                dragging = false;
                handler.removeCallbacks(scroller);
                knobY = centerY;
                invalidate();
                return true;
            }
        }
        return super.onTouchEvent(e);
    }

    private void vibrate() {
        try {
            android.os.Vibrator v = (android.os.Vibrator) getContext().getSystemService(Context.VIBRATOR_SERVICE);
            if (v == null || !v.hasVibrator()) return;
            if (Build.VERSION.SDK_INT >= 26) {
                v.vibrate(android.os.VibrationEffect.createOneShot(12, 70));
            } else {
                v.vibrate(12);
            }
        } catch (Exception ignored) {
        }
    }
}
