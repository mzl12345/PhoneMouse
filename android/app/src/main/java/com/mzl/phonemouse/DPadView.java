package com.mzl.phonemouse;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.MotionEvent;
import android.view.View;

/**
 * 圆盘：上下左右 + 中间确定，下方两侧音量加减。
 * 发送编号：9 上 / 10 下 / 11 左 / 12 右 / 13 确定 / 14 音量+ / 15 音量-
 */
public class DPadView extends View {

    public interface Listener {
        void onKeyDown(int id);

        void onKeyUp(int id);
    }

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arrow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();

    private Listener listener;
    private Vibrator vibrator;

    private float cx, cy, radius;
    private float volY, volR, volLeftX, volRightX;

    private int pressedId = -1;
    private boolean mediaMode = false;

    public DPadView(Context c) {
        super(c);
        vibrator = (Vibrator) c.getSystemService(Context.VIBRATOR_SERVICE);
        fill.setColor(0xFFF7F9FF);
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeWidth(dp(1.5f));
        ring.setColor(0xFFE3E7F2);
        text.setColor(0xFF1F2430);
        text.setTextAlign(Paint.Align.CENTER);
        text.setTypeface(Typeface.DEFAULT_BOLD);
        text.setTextSize(dp(15));
        arrow.setColor(0xFF9AA3B2);
        arrow.setStyle(Paint.Style.FILL);
    }

    public boolean isMediaMode() {
        return mediaMode;
    }

    public void setMediaMode(boolean media) {
        mediaMode = media;
        invalidate();
    }

    public void setListener(Listener l) {
        this.listener = l;
    }

    private float dp(float v) {
        return v * getResources().getDisplayMetrics().density;
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        cx = w / 2f;
        cy = h / 2f - dp(6);
        radius = Math.min(w, h) / 2f - dp(28);
        // 音量放到圆盘左右两侧，避免和下方弧形键重叠
        volY = cy;
        volR = dp(20);
        volLeftX = cx - radius - dp(30);
        volRightX = cx + radius + dp(30);
    }

    @Override
    protected void onDraw(Canvas c) {
        // 外圈
        c.drawCircle(cx, cy, radius, fill);
        c.drawCircle(cx, cy, radius, ring);

        if (mediaMode) {
            // 媒体模式：上=音量+，下=音量-，左=上一曲，右=下一曲
            text.setTextSize(dp(15));
            text.setColor(0xFF5A6172);
            c.drawText("音量+", cx, cy - radius * 0.55f + dp(6), text);
            c.drawText("音量-", cx, cy + radius * 0.55f + dp(6), text);
            c.drawText("上一曲", cx - radius * 0.52f, cy + dp(6), text);
            c.drawText("下一曲", cx + radius * 0.52f, cy + dp(6), text);
            text.setTextSize(dp(15));
        } else {
            drawArrow(c, cx, cy - radius * 0.55f, 0);
            drawArrow(c, cx + radius * 0.55f, cy, 90);
            drawArrow(c, cx, cy + radius * 0.55f, 180);
            drawArrow(c, cx - radius * 0.55f, cy, 270);
        }

        // 中间确定
        float centerR = radius * 0.30f;
        fill.setColor(pressedId == 13 ? 0xFF4C6FFF : 0xFFFFFFFF);
        c.drawCircle(cx, cy, centerR, fill);
        c.drawCircle(cx, cy, centerR, ring);
        text.setColor(pressedId == 13 ? Color.WHITE : 0xFF1F2430);
        c.drawText("确定", cx, cy + dp(5), text);
        fill.setColor(0xFFF7F9FF);


    }

    private void drawPill(Canvas c, float x, float y, String label, int id) {
        float w = dp(52), h = dp(30);
        float l = x - w / 2, t = y - h / 2;
        android.graphics.RectF r = new android.graphics.RectF(l, t, l + w, t + h);
        fill.setColor(pressedId == id ? 0xFF4C6FFF : 0xFFF1F4FA);
        c.drawRoundRect(r, dp(15), dp(15), fill);
        text.setColor(pressedId == id ? Color.WHITE : 0xFF5A6172);
        text.setTextSize(dp(12));
        c.drawText(label, x, y + dp(4), text);
        text.setTextSize(dp(15));
    }

    private void drawArrow(Canvas c, float x, float y, float rotate) {
        float s = dp(9);
        path.reset();
        path.moveTo(0, -s);
        path.lineTo(s * 0.85f, s * 0.6f);
        path.lineTo(-s * 0.85f, s * 0.6f);
        path.close();
        c.save();
        c.rotate(rotate, x, y);
        c.translate(x, y);
        c.drawPath(path, arrow);
        c.restore();
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                int id = hit(e.getX(), e.getY());
                if (id < 0) return false;
                pressedId = id;
                invalidate();
                buzz();
                if (listener != null) listener.onKeyDown(id);
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                if (pressedId >= 0 && listener != null) listener.onKeyUp(pressedId);
                pressedId = -1;
                invalidate();
                return true;
            }
        }
        return super.onTouchEvent(e);
    }

    private int hit(float x, float y) {

        float dx = x - cx, dy = y - cy;
        float d = (float) Math.hypot(dx, dy);
        if (d > radius) return -1;
        if (d < radius * 0.34f) return 13;
        boolean up = Math.abs(dy) > Math.abs(dx) ? dy < 0 : false;
        if (mediaMode) {
            if (Math.abs(dy) > Math.abs(dx)) return dy < 0 ? 17 : 18;   // 音量+ / 音量-
            return dx > 0 ? 20 : 19;                                     // 下一曲 / 上一曲
        }
        if (Math.abs(dy) > Math.abs(dx)) return dy < 0 ? 9 : 10;
        return dx > 0 ? 12 : 11;
    }

    private void buzz() {
        if (vibrator == null || !vibrator.hasVibrator()) return;
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                vibrator.vibrate(VibrationEffect.createOneShot(14, 80));
            } else {
                vibrator.vibrate(14);
            }
        } catch (Exception ignored) {
        }
    }
}
