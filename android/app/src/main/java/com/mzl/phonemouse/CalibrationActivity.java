package com.mzl.phonemouse;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * 拇指可及区域校准：让用户在屏幕上扫一遍，记录扫到的范围。
 * 结果会存进 prefs，主界面的弧形按键据此适配。
 */
public class CalibrationActivity extends Activity {

    /** 记录到的区域（归一化 0..1） */
    public static final String KEY_CAL = "thumbBox";

    private CalibrationView calView;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("phonemouse", MODE_PRIVATE);

        FrameLayout root = new FrameLayout(this);

        calView = new CalibrationView();
        root.addView(calView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // 顶部说明
        TextView tip = new TextView(this);
        tip.setText("用拇指在屏幕上尽量扫一圈\n扫到哪就算到哪，松手后点「完成」");
        tip.setTextSize(15);
        tip.setTextColor(Color.WHITE);
        tip.setGravity(Gravity.CENTER);
        tip.setPadding(0, 60, 0, 0);
        FrameLayout.LayoutParams tipLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tipLp.gravity = Gravity.TOP;
        root.addView(tip, tipLp);

        // 底部按钮
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER);
        bar.setPadding(40, 0, 40, 48);

        Button reset = new Button(this);
        reset.setText("重来");
        reset.setAllCaps(false);
        reset.setOnClickListener(v -> calView.clear());
        bar.addView(reset, new LinearLayout.LayoutParams(0, 110, 1f));

        Button done = new Button(this);
        done.setText("完成");
        done.setAllCaps(false);
        done.setOnClickListener(v -> {
            float[] box = calView.box();
            if (box == null) {
                finish();
                return;
            }
            prefs.edit().putString(KEY_CAL, box[0] + "," + box[1] + "," + box[2] + "," + box[3]).apply();
            finish();
        });
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(0, 110, 1f);
        dlp.leftMargin = 30;
        bar.addView(done, dlp);

        FrameLayout.LayoutParams barLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        barLp.gravity = Gravity.BOTTOM;
        root.addView(bar, barLp);

        setContentView(root);
    }

    /** 记录触摸点并画出轨迹 */
    private class CalibrationView extends View {
        private final List<float[]> pts = new ArrayList<>();
        private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);

        CalibrationView() {
            super(CalibrationActivity.this);
            setBackgroundColor(0xFF10131A);
            dot.setColor(0x664C6FFF);
            line.setColor(0x334C6FFF);
            line.setStrokeWidth(40);
            line.setStrokeCap(Paint.Cap.ROUND);
        }

        void clear() {
            pts.clear();
            invalidate();
        }

        /** 返回归一化包围盒 l,t,r,b；点太少返回 null */
        float[] box() {
            if (pts.size() < 5) return null;
            float l = 1f, t = 1f, r = 0f, b = 0f;
            for (float[] p : pts) {
                if (p[0] < l) l = p[0];
                if (p[1] < t) t = p[1];
                if (p[0] > r) r = p[0];
                if (p[1] > b) b = p[1];
            }
            float pad = 0.02f;
            return new float[]{
                    Math.max(0f, l - pad), Math.max(0f, t - pad),
                    Math.min(1f, r + pad), Math.min(1f, b + pad)};
        }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth();
            float h = getHeight();
            float prevX = -1, prevY = -1;
            for (float[] p : pts) {
                float x = p[0] * w;
                float y = p[1] * h;
                if (prevX >= 0) c.drawLine(prevX, prevY, x, y, line);
                c.drawCircle(x, y, 14, dot);
                prevX = x;
                prevY = y;
            }
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                case MotionEvent.ACTION_MOVE:
                    pts.add(new float[]{e.getX() / getWidth(), e.getY() / getHeight()});
                    invalidate();
                    return true;
            }
            return true;
        }
    }
}
