package com.mzl.phonemouse;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.MotionEvent;
import android.view.View;

import java.util.List;

/**
 * 整屏自绘：按 1440×3168 设计基准等比缩放。
 *
 * <p>只做三件事：遍历元素表 → 画 → 分发触摸。坐标在 {@link BoardLayout}，
 * 外观在 {@link Theme}，这里不出现魔法颜色，也不出现魔法尺寸。
 *
 * <p>视觉：键帽 = 一个底缘 + 一条顶高光 + 一圈结构线。按下时底缘收进去、
 * 键面提亮、边缘点亮唯一强调色。没有多层投影，没有渐变背景。
 */
public class BoardView extends View {

    public interface Listener {
        void onKey(int id, boolean down);
    }

    private List<BoardLayout.El> els = BoardLayout.all();

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shine = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final Path tmp = new Path();
    private final RectF rect = new RectF();
    private final RectF rect2 = new RectF();

    private final Typeface faceLabel = Typeface.create("sans-serif-medium", Typeface.NORMAL);
    private final Typeface faceNum = Typeface.create("sans-serif", Typeface.BOLD);

    private Listener listener;
    private final Vibrator vibrator;

    private float scale = 1f, offX = 0f, offY = 0f;
    private boolean oneHand = false;
    private boolean clutchOn = false;
    private int pressedId = -1;
    private int splitSide = -1;
    private float knobCy = -1f;
    private boolean dragging = false;
    private float lastKnobY = 0f;
    private boolean mediaMode = false;
    private boolean connected = false;
    private boolean mouseOn = false;
    private String statusText = "未连接";
    private String ipText = "";

    public BoardView(Context c) {
        super(c);
        vibrator = (Vibrator) c.getSystemService(Context.VIBRATOR_SERVICE);
        line.setStyle(Paint.Style.STROKE);
        text.setTextAlign(Paint.Align.CENTER);
        text.setTypeface(faceLabel);
        shine.setStyle(Paint.Style.STROKE);
    }

    // ---------------------------------------------------------------- 接口

    public void setListener(Listener l) {
        listener = l;
    }

    public void setConnected(boolean ok, String ip) {
        connected = ok;
        statusText = ok ? "已连接" : "未连接";
        ipText = ok && ip != null ? ip : "";
        invalidate();
    }

    public void setMouseOn(boolean on) {
        mouseOn = on;
        invalidate();
    }

    public boolean isMediaMode() {
        return mediaMode;
    }

    public void toggleMedia() {
        mediaMode = !mediaMode;
        invalidate();
    }

    /** v1.6 遗留：元素表里没有「收起」，这条分支点不到。保留为不动 MainActivity 的兼容口。 */
    public void toggleCluster() {
    }

    // ---------------------------------------------------------------- 几何

    private float dp(float v) {
        return v * getResources().getDisplayMetrics().density;
    }

    private float s(float v) {
        return v * scale;
    }

    private float px(float designX) {
        return offX + designX * scale;
    }

    private float py(float designY) {
        return offY + designY * scale;
    }

    /** 单手模式：整套面板按比例缩小、贴到右下角；给底部输入条留出位置。 */
    public void setOneHand(boolean on) {
        if (oneHand == on) return;
        oneHand = on;
        els = on ? BoardLayout.compact() : BoardLayout.all();
        knobCy = -1f;
        computeTransform(getWidth(), getHeight());
        invalidate();
    }

    public boolean isOneHand() {
        return oneHand;
    }

    /** 定中改成「按一下锁定、再按一下解锁」，锁定期间那颗键保持点亮。 */
    public void setClutch(boolean on) {
        clutchOn = on;
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        computeTransform(w, h);
    }

    private void computeTransform(int w, int h) {
        if (w == 0 || h == 0) return;
        if (oneHand) {
            float maxW = w * 0.72f;
            // 面板往上长一截（0.50 → 0.56），把标题和圆盘顶上去，给下面那条弧让位置
            float maxH = h * 0.56f;
            scale = Math.min(maxW / BoardLayout.COMPACT_W, maxH / BoardLayout.COMPACT_H);
            offX = w - BoardLayout.COMPACT_W * scale - w * 0.035f;
            offY = h - BoardLayout.COMPACT_H * scale - h * 0.10f;
        } else {
            scale = Math.min(w / BoardLayout.DESIGN_W, h / BoardLayout.DESIGN_H);
            offX = (w - BoardLayout.DESIGN_W * scale) / 2f;
            offY = 0f;
        }
    }

    private BoardLayout.El findScroll() {
        for (BoardLayout.El e : els) {
            if (e.type == BoardLayout.SCROLL) return e;
        }
        return null;
    }

    // ---------------------------------------------------------------- 绘制

    @Override
    protected void onDraw(Canvas c) {
        c.drawColor(Theme.BG);

        if (oneHand) {
            // 面板外的区域压暗：一眼看出「只有这一小块是能按的」
            fill.setColor(0xAA000000);
            c.drawRect(0, 0, getWidth(), getHeight(), fill);
            rect.set(offX - dp(10), offY - dp(10),
                    offX + BoardLayout.COMPACT_W * scale + dp(10),
                    offY + BoardLayout.COMPACT_H * scale + dp(10));
            fill.setColor(Theme.RAIL);
            c.drawRoundRect(rect, s(44), s(44), fill);
            line.setStrokeWidth(dp(1.2f));
            line.setColor(Theme.LINE);
            c.drawRoundRect(rect, s(44), s(44), line);
        } else {
            // 顶部状态区：比机身再暗一档，一条结构线收边
            float railBottom = py(240);
            fill.setColor(Theme.RAIL);
            c.drawRect(0, 0, getWidth(), railBottom, fill);
            line.setStrokeWidth(dp(1));
            line.setColor(Theme.LINE);
            c.drawLine(0, railBottom, getWidth(), railBottom, line);
        }

        for (BoardLayout.El e : els) {
            switch (e.type) {
                case BoardLayout.CIRCLE: drawCircleKey(c, e); break;
                case BoardLayout.PILL:   drawNameplate(c, e); break;
                case BoardLayout.RECT:   drawKey(c, e); break;
                case BoardLayout.SPLIT:  drawMouseBar(c, e); break;
                case BoardLayout.DPAD:   drawDpad(c, e); break;
                case BoardLayout.SCROLL: drawScroll(c, e); break;
            }
        }
    }

    /** 键帽底缘：厚度只用这一层表达。 */
    private void lip(Canvas c, float cx, float cy, float r, boolean down) {
        fill.setColor(Theme.KEY_LIP);
        c.drawCircle(cx, cy + s(down ? 1f : 6f), r, fill);
    }

    // ---- 圆形键：开 / 主题 / 设置 ----
    private void drawCircleKey(Canvas c, BoardLayout.El e) {
        boolean down = pressedId == e.id;
        float cx = px(e.cx), cy = py(e.cy), r = s(e.w) / 2f;

        lip(c, cx, cy, r, down);
        fill.setColor(down ? Theme.KEY_DOWN : Theme.KEY);
        c.drawCircle(cx, cy, r, fill);

        shine.setColor(Theme.dark ? 0x14FFFFFF : 0x77FFFFFF);
        shine.setStrokeWidth(dp(1.4f));
        rect.set(cx - r + dp(1), cy - r + dp(1), cx + r - dp(1), cy + r - dp(1));
        c.drawArc(rect, 200, 140, false, shine);

        boolean armed = (e.id == 100 && mouseOn) || (e.id == 201 && oneHand);
        line.setStrokeWidth(dp(1.2f));
        line.setColor(armed ? Theme.LIVE : Theme.LINE);
        c.drawCircle(cx, cy, r, line);

        text.setTypeface(faceLabel);
        text.setTextSize(s(e.id == 100 ? 52 : 38));
        text.setColor(armed ? Theme.LIVE : (down ? Theme.LIVE : Theme.INK));
        c.drawText(BoardLayout.labelOf(e, mouseOn, mediaMode), cx, cy + s(16), text);
    }

    // ---- 圆角矩形键：切换 / 键盘区 / 单手模式底排 ----
    private void drawKey(Canvas c, BoardLayout.El e) {
        boolean down = pressedId == e.id;
        // 定中是「锁定式」：锁住期间保持点亮，不然不知道自己把鼠标冻住了
        boolean active = down || (e.id == 16 && clutchOn);
        float cx = px(e.cx), cy = py(e.cy);
        float hw = s(e.w) / 2f, hh = s(e.h) / 2f;
        float r = Math.min(hw, hh) * 0.42f;

        fill.setColor(Theme.KEY_LIP);
        rect.set(cx - hw, cy - hh + s(active ? 1f : 6f), cx + hw, cy + hh + s(active ? 1f : 6f));
        c.drawRoundRect(rect, r, r, fill);

        fill.setColor(active ? Theme.KEY_DOWN : Theme.KEY);
        rect.set(cx - hw, cy - hh, cx + hw, cy + hh);
        c.drawRoundRect(rect, r, r, fill);

        // 顶高光：一条内侧短弧线，不是整圈描边
        shine.setColor(Theme.dark ? 0x12FFFFFF : 0x88FFFFFF);
        shine.setStrokeWidth(dp(1.2f));
        rect2.set(cx - hw + dp(2), cy - hh + dp(2), cx + hw - dp(2), cy + hh - dp(2));
        c.drawRoundRect(rect2, r, r, shine);

        line.setStrokeWidth(dp(1.2f));
        line.setColor(active ? Theme.LIVE : Theme.LINE);
        c.drawRoundRect(rect, r, r, line);

        text.setTypeface(faceLabel);
        text.setTextSize(s(e.w > 260 ? 52 : 40));
        text.setColor(active ? Theme.LIVE : Theme.INK);
        c.drawText(BoardLayout.labelOf(e, mouseOn, mediaMode), cx, cy + s(e.w > 260 ? 18 : 14), text);
    }

    // ---- 状态条中间：铭牌 + 灯 + 地址 ----
    private void drawNameplate(Canvas c, BoardLayout.El e) {
        float cx = px(e.cx), cy = py(e.cy);
        boolean small = e.w < 600;   // 单手模式下这块更窄

        text.setTypeface(faceLabel);
        text.setTextSize(s(small ? 38 : 62));
        text.setColor(Theme.INK);
        c.drawText("手机空中鼠标", cx, cy - s(small ? 2 : 8), text);

        text.setTypeface(faceNum);
        text.setTextSize(s(small ? 22 : 30));
        text.setColor(Theme.INK_DIM);
        String sub;
        if (connected) {
            sub = ipText.isEmpty() ? statusText : statusText + "   " + ipText;
        } else {
            sub = "未连接 · 点这里重新查找";
        }
        c.drawText(sub, cx, cy + s(small ? 30 : 52), text);

        fill.setColor(connected ? Theme.LIVE : Theme.INK_DIM);
        float half = text.measureText(sub) / 2f;
        c.drawCircle(cx - half - s(small ? 16 : 26), cy + s(small ? 22 : 34), s(small ? 5 : 7), fill);
    }

    // ---- 左 / 右：一根宽条，像笔记本触控板的按键 ----
    private void drawMouseBar(Canvas c, BoardLayout.El e) {
        float left = px(e.cx - e.w / 2f), right = px(e.cx + e.w / 2f);
        float top = py(e.cy - e.h / 2f), bottom = py(e.cy + e.h / 2f);
        float gap = s(40) / 2f;
        float r = s(44);

        // 底缘
        fill.setColor(Theme.KEY_LIP);
        rect.set(left, top + s(6), px(e.cx) - gap, bottom + s(6));
        c.drawRoundRect(rect, r, r, fill);
        rect.set(px(e.cx) + gap, top + s(6), right, bottom + s(6));
        c.drawRoundRect(rect, r, r, fill);

        // 键面
        RectF L = new RectF(left, top, px(e.cx) - gap, bottom);
        RectF R = new RectF(px(e.cx) + gap, top, right, bottom);
        fill.setColor(splitSide == 1 ? Theme.LIVE : Theme.KEY);
        c.drawRoundRect(L, r, r, fill);
        fill.setColor(splitSide == 2 ? Theme.LIVE : Theme.KEY);
        c.drawRoundRect(R, r, r, fill);

        line.setStrokeWidth(dp(1.2f));
        line.setColor(Theme.LINE);
        c.drawRoundRect(L, r, r, line);
        c.drawRoundRect(R, r, r, line);

        text.setTypeface(faceLabel);
        text.setTextSize(s(46));
        text.setColor(splitSide == 1 ? Theme.ON_LIVE : Theme.INK);
        c.drawText("左键", L.centerX(), L.centerY() + s(16), text);
        text.setColor(splitSide == 2 ? Theme.ON_LIVE : Theme.INK);
        c.drawText("右键", R.centerX(), R.centerY() + s(16), text);
    }

    // ---- 圆盘 ----
    private void drawDpad(Canvas c, BoardLayout.El e) {
        float cx = px(e.cx), cy = py(e.cy), r = s(e.w) / 2f;

        lip(c, cx, cy, r, false);
        fill.setColor(Theme.KEY);
        c.drawCircle(cx, cy, r, fill);

        // 一圈内圈告诉手指「这里可以按方向」
        line.setStrokeWidth(dp(1.2f));
        line.setColor(Theme.LINE);
        c.drawCircle(cx, cy, r * 0.82f, line);
        c.drawCircle(cx, cy, r, line);

        float cr = r * 0.26f;
        fill.setColor(Theme.KEY_LIP);
        c.drawCircle(cx, cy + s(pressedCenter() ? 1f : 6f), cr, fill);
        fill.setColor(pressedCenter() ? Theme.KEY_DOWN : Theme.KEY);
        c.drawCircle(cx, cy, cr, fill);
        line.setColor(pressedCenter() ? Theme.LIVE : Theme.LINE);
        c.drawCircle(cx, cy, cr, line);

        float off = r * 0.60f;
        if (mediaMode) {
            text.setTypeface(faceLabel);
            text.setTextSize(s(34));
            label(c, "音量+", cx, cy - off + s(12), pressedId == 17);
            label(c, "音量−", cx, cy + off + s(12), pressedId == 18);
            label(c, "上一曲", cx - off, cy + s(12), pressedId == 19);
            label(c, "下一曲", cx + off, cy + s(12), pressedId == 20);
        } else {
            arrow(c, cx, cy - off, 0, pressedId == 9);
            arrow(c, cx + off, cy, 90, pressedId == 12);
            arrow(c, cx, cy + off, 180, pressedId == 10);
            arrow(c, cx - off, cy, 270, pressedId == 11);
        }

        if (mediaMode) {
            icon(c, cx, cy, cr * 0.5f);
        } else {
            text.setTypeface(faceLabel);
            text.setTextSize(s(42));
            text.setColor(pressedCenter() ? Theme.LIVE : Theme.INK);
            c.drawText("确定", cx, cy + s(15), text);
        }
    }

    private boolean pressedCenter() {
        return pressedId == 13 || pressedId == 25;
    }

    private void label(Canvas c, String t, float x, float y, boolean active) {
        text.setColor(active ? Theme.LIVE : Theme.INK_DIM);
        c.drawText(t, x, y, text);
    }

    private void arrow(Canvas c, float x, float y, float rotate, boolean active) {
        float size = s(30);
        path.reset();
        path.moveTo(0, -size);
        path.lineTo(size * 0.82f, size * 0.58f);
        path.lineTo(-size * 0.82f, size * 0.58f);
        path.close();
        c.save();
        c.rotate(rotate, x, y);
        c.translate(x, y);
        fill.setColor(active ? Theme.LIVE : Theme.INK_DIM);
        c.drawPath(path, fill);
        c.restore();
    }

    /** 播放 / 暂停图标：自己画的，不用 emoji。 */
    private void icon(Canvas c, float cx, float cy, float r) {
        fill.setColor(pressedCenter() ? Theme.LIVE : Theme.INK);
        if (pressedId != 25) {
            path.reset();
            path.moveTo(cx - r * 0.65f, cy - r);
            path.lineTo(cx + r, cy);
            path.lineTo(cx - r * 0.65f, cy + r);
            path.close();
            c.drawPath(path, fill);
        } else {
            float w = r * 0.32f;
            c.drawRect(cx - r * 0.7f, cy - r, cx - r * 0.7f + w, cy + r, fill);
            c.drawRect(cx + r * 0.38f, cy - r, cx + r * 0.38f + w, cy + r, fill);
        }
    }

    // ---- 滚动条：加宽到 220，拇指更容易搭上 ----
    private void drawScroll(Canvas c, BoardLayout.El e) {
        float left = px(e.cx - e.w / 2f), right = px(e.cx + e.w / 2f);
        float top = py(e.cy - e.h / 2f), bottom = py(e.cy + e.h / 2f);
        float radius = (right - left) / 2f;

        rect.set(left, top, right, bottom);
        fill.setColor(Theme.TRACK);
        c.drawRoundRect(rect, radius, radius, fill);
        line.setStrokeWidth(dp(1.2f));
        line.setColor(Theme.LINE);
        c.drawRoundRect(rect, radius, radius, line);

        float kh = s(e.h) * 0.24f;
        if (knobCy < 0) knobCy = (top + bottom) / 2f;
        knobCy = Math.max(top + kh / 2f, Math.min(bottom - kh / 2f, knobCy));

        float pad = s(14);
        RectF knob = new RectF(left + pad, knobCy - kh / 2f, right - pad, knobCy + kh / 2f);
        fill.setColor(Theme.KEY_LIP);
        c.drawRoundRect(new RectF(knob.left, knob.top + s(5), knob.right, knob.bottom + s(5)),
                s(30), s(30), fill);
        fill.setColor(dragging ? Theme.LIVE : Theme.KEY);
        c.drawRoundRect(knob, s(30), s(30), fill);
        line.setColor(dragging ? Theme.LIVE : Theme.LINE);
        c.drawRoundRect(knob, s(30), s(30), line);

        // 握把：三条短横线，宽键更容易被识别成「可以拖」
        fill.setColor(dragging ? Theme.ON_LIVE : Theme.INK_DIM);
        float w = knob.width() * 0.42f;
        for (int i = -1; i <= 1; i++) {
            c.drawRect(knob.centerX() - w / 2f,
                    knobCy + i * s(20) - s(2f),
                    knob.centerX() + w / 2f,
                    knobCy + i * s(20) + s(2f), fill);
        }
    }

    // ---------------------------------------------------------------- 触摸

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                int hit = hitTest(ev.getX(), ev.getY());
                if (hit == Integer.MIN_VALUE) return false;
                pressedId = hit;
                if (hit == 105) {
                    dragging = true;
                    BoardLayout.El se = findScroll();
                    if (se != null) {
                        float top = py(se.cy - se.h / 2f);
                        float bottom = py(se.cy + se.h / 2f);
                        float kh = s(se.h) * 0.24f;
                        knobCy = Math.max(top + kh / 2f, Math.min(bottom - kh / 2f, ev.getY()));
                        lastKnobY = knobCy;
                    }
                }
                buzz();
                if (listener != null) listener.onKey(hit, true);
                invalidate();
                return true;
            }
            case MotionEvent.ACTION_MOVE: {
                if (pressedId == 105 && dragging) {
                    BoardLayout.El se = findScroll();
                    if (se != null) {
                        float top = py(se.cy - se.h / 2f);
                        float bottom = py(se.cy + se.h / 2f);
                        float kh = s(se.h) * 0.24f;
                        knobCy = Math.max(top + kh / 2f, Math.min(bottom - kh / 2f, ev.getY()));
                        float delta = knobCy - lastKnobY;
                        float step = s(46);
                        while (Math.abs(delta) >= step) {
                            int dir = delta > 0 ? 1 : -1;
                            if (listener != null) listener.onKey(dir > 0 ? 205 : 204, true);
                            delta -= dir * step;
                            lastKnobY += dir * step;
                        }
                        invalidate();
                    }
                }
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                if (pressedId != Integer.MIN_VALUE && pressedId >= 0 && listener != null) {
                    listener.onKey(pressedId, false);
                }
                if (pressedId == 105) knobCy = -1f;   // 松手回弹到中间
                pressedId = -1;
                splitSide = -1;
                dragging = false;
                invalidate();
                return true;
            }
        }
        return true;
    }

    /** 返回命中的 id；滚动条=105，圆盘方向=9–13/17–20/25，鼠标条=1/2。 */
    private int hitTest(float x, float y) {
        for (int i = els.size() - 1; i >= 0; i--) {
            BoardLayout.El e = els.get(i);
            float dx = x - px(e.cx);
            float dy = y - py(e.cy);
            if (e.type == BoardLayout.SCROLL) {
                // 轨道是长条，必须按矩形判定。按圆判定的话 1000 高的条只有中间
                // 220 的圆能点到 —— 这就是「滚动条有时候点不到」的真正原因。
                if (Math.abs(dx) <= s(e.w) / 2f && Math.abs(dy) <= s(e.h) / 2f) return e.id;
            } else if (e.type == BoardLayout.CIRCLE || e.type == BoardLayout.DPAD) {
                float r = s(e.w) / 2f;
                if (Math.hypot(dx, dy) <= r) {
                    if (e.type == BoardLayout.DPAD) return dpadHit(dx, dy, r);
                    return e.id;
                }
            } else {
                if (Math.abs(dx) <= s(e.w) / 2f && Math.abs(dy) <= s(e.h) / 2f) {
                    if (e.type == BoardLayout.SPLIT) {
                        splitSide = dx < 0 ? 1 : 2;
                        return splitSide;
                    }
                    return e.id;
                }
            }
        }
        return Integer.MIN_VALUE;
    }

    private int dpadHit(float dx, float dy, float r) {
        float d = (float) Math.hypot(dx, dy);
        if (d < r * 0.32f) return mediaMode ? 25 : 13;
        if (mediaMode) {
            if (Math.abs(dy) > Math.abs(dx)) return dy < 0 ? 17 : 18;
            return dx > 0 ? 20 : 19;
        }
        if (Math.abs(dy) > Math.abs(dx)) return dy < 0 ? 9 : 10;
        return dx > 0 ? 12 : 11;
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
