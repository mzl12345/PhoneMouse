package com.mzl.phonemouse;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.MotionEvent;
import android.view.View;

/**
 * 拇指区按键：左/右键是两个半圆（中间留缝），其余是圆形键。
 * 布局参照手绘稿：以右下角为锚点，按固定偏移摆放。
 *
 * 编号：1 左键 / 2 右键 / 3 拖拽 / 6 回车 / 7 Esc / 8 复制 / 16 定中
 */
public class ThumbPadView extends View {

    public interface Listener {
        void onKeyDown(int id);

        void onKeyUp(int id);
    }

    private static class Key {
        int id;
        String label;
        float ox;      // 相对锚点的偏移（dp，向左为负）
        float oy;      // 向上为负
        float r;       // 半径 dp
        float cx, cy, rr;
        boolean pressed;
    }

    private final java.util.List<Key> keys = new java.util.ArrayList<>();
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();

    private Listener listener;
    private Vibrator vibrator;
    private Key active;

    private float boxL = 0.30f, boxT = 0.40f, boxR = 0.92f, boxB = 0.94f;
    private boolean leftHanded = false;

    // 左右键：两个半圆
    private float splitCx, splitCy, splitR, splitGap;
    private boolean splitPressed;
    private int splitActive = -1;   // 1 左键 / 2 右键

    public ThumbPadView(Context c) {
        super(c);
        vibrator = (Vibrator) c.getSystemService(Context.VIBRATOR_SERVICE);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(dp(1.5f));
        stroke.setColor(0xFFE3E7F2);
        textPaint.setColor(0xFF1F2430);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTypeface(Typeface.DEFAULT_BOLD);
        textPaint.setTextSize(dp(14));

        add(3, "拖拽", -196, -96, 36);
        add(6, "回车", -96, -206, 34);
        add(7, "Esc", -196, -206, 31);
        add(8, "复制", -292, -96, 31);
        add(16, "定中", -292, -206, 29);
    }

    private void add(int id, String label, float ox, float oy, float r) {
        Key k = new Key();
        k.id = id;
        k.label = label;
        k.ox = ox;
        k.oy = oy;
        k.r = r;
        keys.add(k);
    }

    public void setListener(Listener l) {
        this.listener = l;
    }

    public void setLeftHanded(boolean left) {
        if (leftHanded == left) return;
        leftHanded = left;
        requestLayout();
        invalidate();
    }

    public void setComfortBox(float l, float t, float r, float b) {
        if (r - l < 0.2f || b - t < 0.2f) return;
        float padX = (r - l) * 0.06f;
        float padY = (b - t) * 0.06f;
        boxL = Math.max(0f, l - padX);
        boxT = Math.max(0f, t - padY);
        boxR = Math.min(1f, r + padX);
        boxB = Math.min(1f, b + padY);
        requestLayout();
        invalidate();
    }

    private float dp(float v) {
        return v * getResources().getDisplayMetrics().density;
    }

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        int w = MeasureSpec.getSize(wSpec);
        int h = MeasureSpec.getSize(hSpec);
        setMeasuredDimension(w, h);
        layoutKeys(w, h);
    }

    private void layoutKeys(int w, int h) {
        // 固定锚在可视区右下角，位置可预测，不再依赖校准框
        float anchorX = leftHanded ? dp(22) : w - dp(22);
        float anchorY = h - dp(22);
        float sign = leftHanded ? -1 : 1;
        float scale = 1f;

        splitR = dp(54) * scale;
        splitGap = dp(7) * scale;
        splitCx = anchorX + sign * dp(-96) * scale;
        splitCy = anchorY - dp(96) * scale;

        for (Key k : keys) {
            k.cx = anchorX + sign * dp(k.ox) * scale;
            k.cy = anchorY + dp(k.oy) * scale;
            k.rr = dp(k.r) * scale;
        }
    }

    @Override
    protected void onDraw(Canvas c) {
        // 左右半圆
        float gapHalf = splitGap / 2f;
        float left = splitCx - splitR, top = splitCy - splitR;
        RectF oval = new RectF(left, top, splitCx + splitR, splitCy + splitR);

        path.reset();
        path.addArc(oval, 90, 180);
        path.close();
        fill.setColor(splitActive == 1 ? 0xFF4C6FFF : 0xFFF7F9FF);
        c.drawPath(path, fill);
        c.drawPath(path, stroke);

        path.reset();
        path.addArc(oval, 270, 180);
        path.close();
        fill.setColor(splitActive == 2 ? 0xFF4C6FFF : 0xFFF7F9FF);
        c.drawPath(path, fill);
        c.drawPath(path, stroke);

        // 中间缝隙（用背景色盖出留缝效果）
        fill.setColor(0xFFF6F7FB);
        c.drawRect(splitCx - gapHalf, top, splitCx + gapHalf, top + splitR * 2, fill);

        textPaint.setTextSize(dp(15));
        textPaint.setColor(splitActive == 1 ? Color.WHITE : 0xFF1F2430);
        c.drawText("左", splitCx - splitR * 0.45f, splitCy + dp(6), textPaint);
        textPaint.setColor(splitActive == 2 ? Color.WHITE : 0xFF1F2430);
        c.drawText("右", splitCx + splitR * 0.45f, splitCy + dp(6), textPaint);

        // 其他圆键
        textPaint.setTextSize(dp(13));
        for (Key k : keys) {
            fill.setColor(k.pressed ? 0xFF4C6FFF : 0xFFF7F9FF);
            c.drawCircle(k.cx, k.cy, k.rr, fill);
            c.drawCircle(k.cx, k.cy, k.rr, stroke);
            textPaint.setColor(k.pressed ? Color.WHITE : 0xFF1F2430);
            float baseline = k.cy - (textPaint.descent() + textPaint.ascent()) / 2f;
            c.drawText(k.label, k.cx, baseline, textPaint);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                // 半圆优先
                float dx = e.getX() - splitCx, dy = e.getY() - splitCy;
                if (Math.hypot(dx, dy) <= splitR) {
                    splitActive = dx < 0 ? 1 : 2;
                    splitPressed = true;
                    buzz();
                    if (listener != null) listener.onKeyDown(splitActive);
                    invalidate();
                    return true;
                }
                Key hit = hitTest(e.getX(), e.getY());
                if (hit == null) return false;
                active = hit;
                hit.pressed = true;
                buzz();
                if (listener != null) listener.onKeyDown(hit.id);
                invalidate();
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                if (splitActive >= 0) {
                    if (listener != null) listener.onKeyUp(splitActive);
                    splitActive = -1;
                    splitPressed = false;
                }
                if (active != null) {
                    active.pressed = false;
                    if (listener != null) listener.onKeyUp(active.id);
                    active = null;
                }
                invalidate();
                return true;
            }
        }
        return true;
    }

    private Key hitTest(float x, float y) {
        Key best = null;
        float bestD = Float.MAX_VALUE;
        for (Key k : keys) {
            float d = (float) Math.hypot(x - k.cx, y - k.cy);
            if (d <= k.rr * 1.2f && d < bestD) {
                best = k;
                bestD = d;
            }
        }
        return best;
    }

    private void buzz() {
        if (vibrator == null || !vibrator.hasVibrator()) return;
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                vibrator.vibrate(VibrationEffect.createOneShot(15, 90));
            } else {
                vibrator.vibrate(15);
            }
        } catch (Exception ignored) {
        }
    }
}
