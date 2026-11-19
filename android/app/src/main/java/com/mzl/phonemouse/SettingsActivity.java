package com.mzl.phonemouse;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

/**
 * 设置页：外观 / 空中鼠标 / 手感 / 连接 / 关于。
 *
 * <p>与 v1.6 的差别：
 * <ul>
 *   <li>配色改由 {@link Theme} 统一提供（深色/浅色都成立）</li>
 *   <li>新增「外观」卡片，深色可切</li>
 *   <li>删掉「左手模式」开关 —— 它只是把值写进 preferences，全项目没有任何代码读它，
 *       是个不兑现的开关。要做左手镜像就得真的镜像布局，那是另一个功能。</li>
 * </ul>
 */
public class SettingsActivity extends Activity {

    private SharedPreferences prefs;

    private TextView sensValue, smoothValue;
    private SeekBar sensBar, smoothBar;
    private Switch invXSw, invYSw;
    private EditText ipField, portField;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("phonemouse", MODE_PRIVATE);
        applyTheme();
        setContentView(buildUi());
    }

    private void applyTheme() {
        boolean dark = prefs.getBoolean("darkTheme", true);
        if (dark) Theme.useDark();
        else Theme.useLight();
        getWindow().setStatusBarColor(Theme.RAIL);
        getWindow().setNavigationBarColor(Theme.BG);
        int flags = getWindow().getDecorView().getSystemUiVisibility();
        if (dark) flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        else flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        getWindow().getDecorView().setSystemUiVisibility(flags);
    }

    // ---------------------------------------------------------------- 小工具

    private int dp(float v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }

    private GradientDrawable round(int fill, int stroke, float radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(dp(radiusDp));
        if (stroke != 0) d.setStroke(dp(1), stroke);
        return d;
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackground(round(Theme.KEY, Theme.LINE, 16));
        int p = dp(18);
        c.setPadding(p, p, p, p);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(12);
        c.setLayoutParams(lp);
        return c;
    }

    private TextView title(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        t.setTextColor(Theme.INK);
        return t;
    }

    private TextView caption(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        t.setTextColor(Theme.INK_DIM);
        t.setLineSpacing(dp(3), 1f);
        t.setPadding(0, dp(6), 0, dp(10));
        return t;
    }

    private Switch toggle(String text, boolean checked, android.widget.CompoundButton.OnCheckedChangeListener l) {
        Switch sw = new Switch(this);
        sw.setText(text);
        sw.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        sw.setTextColor(Theme.INK);

        // 拇指随状态变色：开=强调色，关=灰。只用一种颜色会让人误读「关」也是开着的。
        int[][] states = {{android.R.attr.state_checked}, {}};
        sw.setThumbTintList(new android.content.res.ColorStateList(
                states, new int[]{Theme.LIVE, Theme.INK_DIM}));
        int trackOn = (Theme.LIVE & 0x00FFFFFF) | 0x55000000;
        sw.setTrackTintList(new android.content.res.ColorStateList(
                states, new int[]{trackOn, Theme.LINE}));

        sw.setChecked(checked);
        sw.setOnCheckedChangeListener(l);
        return sw;
    }

    private TextView button(String text, boolean primary, int heightDp) {
        TextView b = new TextView(this);
        b.setText(text);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        b.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        b.setGravity(Gravity.CENTER);
        b.setTextColor(primary ? Theme.ON_LIVE : Theme.INK);
        b.setBackground(round(primary ? Theme.LIVE : Theme.KEY, primary ? 0 : Theme.LINE, 12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(heightDp));
        lp.topMargin = dp(10);
        b.setLayoutParams(lp);
        return b;
    }

    // ---------------------------------------------------------------- 界面

    private View buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Theme.BG);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(root);

        // ---- 头部 ----
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setBackgroundColor(Theme.RAIL);
        header.setPadding(dp(18), dp(18), dp(18), dp(18));

        LinearLayout topRow = new LinearLayout(this);
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView back = new TextView(this);
        back.setText("← 返回");
        back.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        back.setTextColor(Theme.INK_DIM);
        back.setPadding(dp(4), dp(8), dp(14), dp(8));
        back.setOnClickListener(v -> finish());
        topRow.addView(back);

        TextView name = new TextView(this);
        name.setText("设置");
        name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        name.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        name.setTextColor(Theme.INK);
        topRow.addView(name);

        header.addView(topRow);
        root.addView(header);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(14), dp(16), dp(14), dp(28));
        root.addView(content);

        // ---- 外观 ----
        LinearLayout look = card();
        look.addView(title("外观"));
        look.addView(caption("深色适合关灯后用；浅色在白天和投屏时更清楚"));
        Switch darkSw = toggle("深色界面", prefs.getBoolean("darkTheme", true), (v, on) -> {
            prefs.edit().putBoolean("darkTheme", on).apply();
            recreate();
        });
        look.addView(darkSw);
        content.addView(look);

        // ---- 手感 ----
        LinearLayout feel = card();
        feel.addView(title("鼠标手感"));
        feel.addView(caption("手腕转动的角度 → 光标移动的快慢"));

        feel.addView(rowLabel("灵敏度", true));
        sensBar = new SeekBar(this);
        sensBar.setMax(100);
        sensBar.setProgressTintList(android.content.res.ColorStateList.valueOf(Theme.LIVE));
        sensBar.setThumbTintList(android.content.res.ColorStateList.valueOf(Theme.LIVE));
        sensBar.setOnSeekBarChangeListener(simpleProgress(p -> {
            sensValue.setText(String.valueOf(p));
            prefs.edit().putInt("sens", p).apply();
        }));
        feel.addView(sensBar);

        feel.addView(rowLabel("平滑", false));
        feel.addView(caption("数值越大越稳，但反应更迟钝"));
        smoothBar = new SeekBar(this);
        smoothBar.setMax(100);
        smoothBar.setProgressTintList(android.content.res.ColorStateList.valueOf(Theme.LIVE));
        smoothBar.setThumbTintList(android.content.res.ColorStateList.valueOf(Theme.LIVE));
        smoothBar.setOnSeekBarChangeListener(simpleProgress(p -> {
            smoothValue.setText(String.valueOf(p));
            prefs.edit().putInt("smooth", p).apply();
        }));
        feel.addView(smoothBar);

        invXSw = toggle("反转横向", prefs.getBoolean("invX", false),
                (v, c) -> prefs.edit().putBoolean("invX", c).apply());
        invXSw.setPadding(0, dp(14), 0, 0);
        feel.addView(invXSw);

        invYSw = toggle("反转纵向", prefs.getBoolean("invY", false),
                (v, c) -> prefs.edit().putBoolean("invY", c).apply());
        feel.addView(invYSw);

        // 重新校准原来在「空中鼠标」卡片里，那张卡已删（开关在主界面），校准搬到这里
        TextView calib = button("重新校准零偏", false, 44);
        calib.setOnClickListener(v -> {
            prefs.edit().putBoolean("calibrate", true).apply();
            calib.setText("已校准 ✓");
        });
        feel.addView(calib);

        content.addView(feel);

        // ---- 连接 ----
        LinearLayout conn = card();
        conn.addView(title("连接"));
        conn.addView(caption("一般不用改：主界面点「查找电脑」会自动填好"));

        ipField = field("电脑 IP", InputType.TYPE_CLASS_TEXT);
        conn.addView(ipField);

        portField = field("端口", InputType.TYPE_CLASS_NUMBER);
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        plp.topMargin = dp(8);
        conn.addView(portField, plp);

        TextView save = button("保存", true, 48);
        save.setOnClickListener(v -> {
            String ip = ipField.getText().toString().trim();
            int port;
            try {
                port = Integer.parseInt(portField.getText().toString().trim());
            } catch (Exception e) {
                port = 51234;
            }
            SharedPreferences.Editor ed = prefs.edit().putInt("port", port);
            if (!ip.isEmpty()) ed.putString("ip", ip);
            ed.apply();
            finish();
        });
        conn.addView(save);
        content.addView(conn);

        // ---- 关于 ----
        LinearLayout about = card();
        about.addView(title("关于"));
        about.addView(caption("手机空中鼠标 v2.0\n\n"
                + "作者：MZL\n\n"
                + "手机与电脑走局域网 UDP，不经过任何服务器。\n"
                + "断网也能用，只要还在同一个 Wi-Fi 里。"));

        TextView exportLog = button("导出运行日志", true, 44);
        exportLog.setOnClickListener(v -> exportDiag());
        about.addView(exportLog);

        TextView reset = button("恢复默认手感", false, 44);
        reset.setOnClickListener(v -> {
            prefs.edit()
                    .putInt("sens", 40)
                    .putInt("smooth", 30)
                    .putBoolean("invX", false)
                    .putBoolean("invY", false)
                    .apply();
            loadValues();
        });
        about.addView(reset);
        content.addView(about);

        loadValues();
        return scroll;
    }

    /** 一行「名称 —— 数值」。数值只读展示，实际值靠 SeekBar 调。 */
    private LinearLayout rowLabel(String text, boolean first) {
        TextView label = new TextView(this);
        label.setText(text);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        label.setTextColor(Theme.INK);

        TextView value = new TextView(this);
        value.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        value.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        value.setTextColor(Theme.LIVE);
        if (first) sensValue = value;
        else smoothValue = value;

        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.HORIZONTAL);
        wrap.setGravity(Gravity.CENTER_VERTICAL);
        wrap.setPadding(0, first ? 0 : dp(16), 0, 0);
        wrap.addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        wrap.addView(value);
        return wrap;
    }

    private EditText field(String hint, int inputType) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        e.setHintTextColor(Theme.INK_DIM);
        e.setTextColor(Theme.INK);
        e.setBackground(round(Theme.BG, Theme.LINE, 10));
        e.setPadding(dp(12), dp(10), dp(12), dp(10));
        e.setInputType(inputType);
        return e;
    }

    private SeekBar.OnSeekBarChangeListener simpleProgress(java.util.function.IntConsumer onProgress) {
        return new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                onProgress.accept(progress);
            }

            @Override public void onStartTrackingTouch(SeekBar seekBar) { }

            @Override public void onStopTrackingTouch(SeekBar seekBar) { }
        };
    }

    /** 把 diag.log 复制到「下载」目录，方便拔线后取回 */
    private void exportDiag() {
        try {
            java.io.File src = new java.io.File(getExternalFilesDir(null), "diag.log");
            if (!src.exists()) {
                toast("还没有日志文件");
                return;
            }
            java.io.FileInputStream in = new java.io.FileInputStream(src);
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
            in.close();
            byte[] data = bos.toByteArray();

            if (Build.VERSION.SDK_INT >= 29) {
                android.content.ContentValues cv = new android.content.ContentValues();
                cv.put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME,
                        "PhoneMouse-diag-" + System.currentTimeMillis() + ".log");
                cv.put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "text/plain");
                cv.put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH,
                        android.os.Environment.DIRECTORY_DOWNLOADS);
                android.net.Uri uri = getContentResolver().insert(
                        android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
                if (uri == null) {
                    toast("导出失败");
                    return;
                }
                java.io.OutputStream os = getContentResolver().openOutputStream(uri);
                os.write(data);
                os.close();
                toast("已导出到「下载」目录");
            } else {
                java.io.File dst = new java.io.File(
                        android.os.Environment.getExternalStoragePublicDirectory(
                                android.os.Environment.DIRECTORY_DOWNLOADS),
                        "PhoneMouse-diag.log");
                java.io.FileOutputStream os = new java.io.FileOutputStream(dst);
                os.write(data);
                os.close();
                toast("已导出到 " + dst.getAbsolutePath());
            }
        } catch (Exception e) {
            toast("导出失败：" + e.getMessage());
        }
    }

    private void toast(String msg) {
        android.widget.Toast.makeText(this, msg, android.widget.Toast.LENGTH_LONG).show();
    }

    private void loadValues() {
        int sens = prefs.getInt("sens", 40);
        int smooth = prefs.getInt("smooth", 30);
        sensBar.setProgress(sens);
        sensValue.setText(String.valueOf(sens));
        smoothBar.setProgress(smooth);
        smoothValue.setText(String.valueOf(smooth));
        invXSw.setChecked(prefs.getBoolean("invX", false));
        invYSw.setChecked(prefs.getBoolean("invY", false));
        ipField.setText(prefs.getString("ip", ""));
        portField.setText(String.valueOf(prefs.getInt("port", 51234)));
    }
}
