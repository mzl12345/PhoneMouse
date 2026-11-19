package com.mzl.phonemouse;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;

/**
 * 主界面：像遥控器一样，只放按键和语音。
 * 空中鼠标与参数都在设置页。
 */
public class MainActivity extends Activity implements SensorEventListener {

    private static final String PREFS = "phonemouse";
    private static final int SENSOR_RATE = 10;

    private SharedPreferences prefs;

    private BoardView board;

    // 状态区
    private View statusDot;
    private TextView statusText;
    private TextView mouseChip;
    private LinearLayout statusStrip;

    // 语音
    private EditText textField;
    private EditText fullEdit;
    private LinearLayout inputBar;
    private LinearLayout fullPanel;

    // 传感器
    private SensorManager sensorManager;
    private Sensor gyro, gravSensor, accel;
    private HandlerThread sensorThread;
    private final float[] gravity = new float[3];
    private volatile boolean mouseOn = false;
    private boolean invertX = false, invertY = false;
    private volatile float smoothAlpha = 0.25f;
    private int sensProgress = 40;
    private float accX = 0f, accY = 0f;
    private float sH = 0f, sV = 0f;
    private float poseW = 0.5f;
    private boolean useYaw = true;
    private volatile boolean clutch = false;
    private float biasX = 0f, biasY = 0f, biasZ = 0f;
    private float biasSumX = 0f, biasSumY = 0f, biasSumZ = 0f;
    private int biasCount = 0;
    private static final int HIST = 200;
    private final float[][] hist = new float[3][HIST];
    private final float[][] m3 = new float[3][3];
    private int m3Idx = 0;
    private int m3Fill = 0;
    private int frameCount = 0;
    private int histIdx = 0;
    private int histFill = 0;
    private long lastGyroTs = 0;
    private long lastMoveSent = 0;
    private long lastLogTs = 0;
    private long lastBeatTs = 0;

    // 网络
    private DatagramSocket socket;
    private volatile InetAddress target;
    private volatile int targetPort = 51234;

    private Vibrator vibrator;

    // 自动重连：连不上就每 5 秒再找一次。
    private final Handler retryLoop = new Handler(Looper.getMainLooper());
    private volatile boolean stopped = false;

    /** 连续失败次数。到 3 次（约 15 秒）就把引导面板亮出来。 */
    private int failCount = 0;
    private LinearLayout connPanel;
    /** 用户手动叉掉引导面板后，不要每次重试失败又弹回来。 */
    private boolean connPanelDismissed = false;
    /** 手动连接对话框；连上之后要主动关掉，别留一个孤儿窗口。 */
    private android.app.AlertDialog manualDialog;

    private final Runnable retryTick = new Runnable() {
        @Override
        public void run() {
            if (stopped || target != null) return;
            discoverPc(1);
            retryLoop.postDelayed(this, 5000);
        }
    };

    /** 立刻重新开始查找（会先把当前连接状态清掉）。 */
    private void restartDiscovery() {
        target = null;
        failCount = 0;
        connPanelDismissed = false;
        setStatus(false, null);
        if (connPanel != null) connPanel.setVisibility(View.GONE);
        retryLoop.removeCallbacks(retryTick);
        retryLoop.post(retryTick);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        applyTheme();
        Diag.init(this);
        vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);

        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        gyro = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        gravSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY);
        accel = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

        sensorThread = new HandlerThread("sensor");
        sensorThread.start();

        setContentView(buildUi());
        board.setOneHand(prefs.getBoolean("oneHand", false));
        setStatus(false, null);
        restartDiscovery();
    }

    // ---------------------------------------------------------------- 基础

    /** 套用主题：深色是默认（夜里用），设置里可切浅色。 */
    private void applyTheme() {
        boolean wantDark = prefs.getBoolean("darkTheme", true);
        if (wantDark) Theme.useDark();
        else Theme.useLight();
        getWindow().setStatusBarColor(Theme.RAIL);
        getWindow().setNavigationBarColor(Theme.BG);
        int flags = getWindow().getDecorView().getSystemUiVisibility();
        if (wantDark) {
            flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        } else {
            flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        }
        getWindow().getDecorView().setSystemUiVisibility(flags);
    }

    private int dp(float v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }

    private void tick() {
        if (vibrator == null || !vibrator.hasVibrator()) return;
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                vibrator.vibrate(VibrationEffect.createOneShot(18, 90));
            } else {
                vibrator.vibrate(18);
            }
        } catch (Exception ignored) {
        }
    }

    // ------------------------------------------------------------------ UI

    private View buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFFF6F7FB);

        board = new BoardView(this);
        board.setListener(this::onBoardKey);
        board.setMouseOn(mouseOn);
        root.addView(board, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // ---------- 底部输入条 ----------
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(28));
        bg.setColor(Theme.KEY);
        bg.setStroke(dp(1), Theme.LINE);
        bar.setBackground(bg);
        // 左内边距要够：圆角会吃掉一段，字贴着弧线看起来像被裁掉
        bar.setPadding(dp(26), dp(7), dp(8), dp(7));

        textField = new EditText(this);
        textField.setHint("打字，发送到电脑");
        textField.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        textField.setSingleLine(false);
        textField.setGravity(Gravity.CENTER_VERTICAL);
        textField.setIncludeFontPadding(false);
        textField.setPadding(dp(6), 0, dp(6), 0);
        textField.setBackground(null);
        textField.setTextColor(Theme.INK);
        textField.setHintTextColor(Theme.INK_DIM);
        textField.setFocusable(false);
        textField.setFocusableInTouchMode(false);
        textField.setClickable(false);
        textField.setOnTouchListener((v, ev) -> {
            openFullEditor();
            return true;
        });
        bar.addView(textField, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.MATCH_PARENT, 1f));

        // 原来这里有个 🎤，但它做的事和点输入框完全一样（只是打开键盘），
        // 而语音输入并没有实现 —— 一个不兑现的图标比少一个按钮更糟，删掉。

        TextView send = new TextView(this);
        send.setText("发送");
        send.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        send.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        send.setGravity(Gravity.CENTER);
        send.setTextColor(Theme.ON_LIVE);
        GradientDrawable sendBg = new GradientDrawable();
        sendBg.setCornerRadius(dp(24));
        sendBg.setColor(Theme.LIVE);
        send.setBackground(sendBg);
        send.setOnClickListener(v -> sendTextNow());
        bar.addView(send, new LinearLayout.LayoutParams(dp(96), dp(48)));

        // 点空白条也能进入全屏输入
        bar.setOnClickListener(v -> openFullEditor());
        root.addView(bar);
        inputBar = bar;

        // ---------- 连不上时的引导面板（盖住按键区，只留顶部状态条） ----------
        connPanel = buildConnectPanel();
        root.addView(connPanel);

        final int screenH = getResources().getDisplayMetrics().heightPixels;
        final float s0 = Math.min(1f, screenH / 3168f);
        final int barW = Math.round(1250 * s0);
        final int barH = Math.round(220 * s0);
        final int barLeft = Math.round(95 * s0);
        final int gap = dp(14);
        // 引导面板：从状态条下沿一直盖到底，右边不留缝
        final int connTop = Math.round(240 * s0);
        root.addOnLayoutChangeListener((v, l, tp, r2, b2, oL, oT, oR, oB) -> {
            int visibleH = b2 - tp;
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(barW, barH);
            lp.leftMargin = barLeft;
            lp.topMargin = Math.max(0, visibleH - barH - gap);
            inputBar.setLayoutParams(lp);

            FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT);
            plp.topMargin = connTop;
            connPanel.setLayoutParams(plp);
        });

        // ---------- 全屏输入面板（学微信） ----------
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackgroundColor(Theme.BG);
        panel.setPadding(dp(20), dp(20), dp(20), dp(14));
        panel.setVisibility(View.GONE);
        panel.setClickable(true);

        LinearLayout panelTop = new LinearLayout(this);
        panelTop.setOrientation(LinearLayout.HORIZONTAL);
        panelTop.setGravity(Gravity.CENTER_VERTICAL);

        TextView cancel = new TextView(this);
        cancel.setText("取消");
        cancel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        cancel.setTextColor(Theme.INK_DIM);
        cancel.setPadding(dp(10), dp(10), dp(10), dp(10));
        cancel.setOnClickListener(v -> closeFullEditor(false));
        panelTop.addView(cancel);

        View spacer = new View(this);
        panelTop.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1f));

        TextView done = new TextView(this);
        done.setText("发送");
        done.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        done.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        done.setTextColor(Theme.ON_LIVE);
        done.setGravity(Gravity.CENTER);
        GradientDrawable doneBg = new GradientDrawable();
        doneBg.setCornerRadius(dp(20));
        doneBg.setColor(Theme.LIVE);
        done.setBackground(doneBg);
        done.setPadding(dp(22), dp(9), dp(22), dp(9));
        done.setOnClickListener(v -> closeFullEditor(true));
        panelTop.addView(done);
        panel.addView(panelTop);

        fullEdit = new EditText(this);
        fullEdit.setHint("输入要发送的文字…");
        fullEdit.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        fullEdit.setTextColor(Theme.INK);
        fullEdit.setHintTextColor(Theme.INK_DIM);
        fullEdit.setBackground(null);
        fullEdit.setGravity(Gravity.TOP | Gravity.START);
        fullEdit.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        fullEdit.setPadding(dp(4), dp(16), dp(4), dp(8));
        LinearLayout.LayoutParams feLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        panel.addView(fullEdit, feLp);

        fullPanel = panel;
        root.addView(panel, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        return root;
    }

    /** 进入全屏输入：隐藏所有按键，只留大文字框 */
    private void openFullEditor() {
        if (fullPanel == null) return;
        fullEdit.setText(textField.getText());
        fullEdit.setSelection(fullEdit.getText().length());
        board.setVisibility(View.GONE);
        inputBar.setVisibility(View.GONE);
        fullPanel.setVisibility(View.VISIBLE);
        fullEdit.requestFocus();
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) imm.showSoftInput(fullEdit, InputMethodManager.SHOW_IMPLICIT);
    }

    /** 退出全屏输入；send=true 时发送内容 */
    private void closeFullEditor(boolean send) {
        String txt = fullEdit.getText().toString();
        textField.setText(txt);
        fullPanel.setVisibility(View.GONE);
        board.setVisibility(View.VISIBLE);
        inputBar.setVisibility(View.VISIBLE);
        fullEdit.clearFocus();
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(fullEdit.getWindowToken(), 0);
        if (send && !txt.trim().isEmpty()) {
            sendCmd("txt " + Base64.getEncoder()
                    .encodeToString(txt.getBytes(StandardCharsets.UTF_8)));
            textField.setText("");
            fullEdit.setText("");
        }
    }

    /** BoardView 的按键回调 */
    private void onBoardKey(int id, boolean down) {
        if (down) Diag.log("KEY", "按下 " + id + (board != null && board.isMediaMode() ? " (媒体模式)" : ""));
        switch (id) {
            case 100: {   // 空中鼠标开关
                if (!down) return;
                boolean on = !prefs.getBoolean("mouseOn", false);
                prefs.edit().putBoolean("mouseOn", on).apply();
                mouseOn = on;
                if (on) startSensors(); else stopSensors();
                updateMouseChip();
                return;
            }
            case 101:     // 名称/状态：点一下立刻重新查找电脑
                if (down) restartDiscovery();
                return;
            case 102: {   // 设置
                if (down) startActivity(new Intent(this, SettingsActivity.class));
                return;
            }
            case 200: {   // 主题：一点就切，不用进设置
                if (!down) return;
                boolean dark = prefs.getBoolean("darkTheme", true);
                prefs.edit().putBoolean("darkTheme", !dark).apply();
                recreate();
                return;
            }
            case 201: {   // 单手模式：整套面板缩小贴到右下角，用不到的键先收起来
                if (!down) return;
                boolean on = !prefs.getBoolean("oneHand", false);
                prefs.edit().putBoolean("oneHand", on).apply();
                if (board != null) board.setOneHand(on);
                Diag.log("CTRL", on ? "单手模式" : "全屏模式");
                return;
            }
            case 103: {   // 切换圆盘功能
                if (down) board.toggleMedia();
                return;
            }
            case 106: {   // 收起/展开
                if (down) board.toggleCluster();
                return;
            }
            case 105:     // 滚轮区域（具体方向由 204/205 给出）
                return;
            case 204:
                if (down) sendCmd("whl -1");
                return;
            case 205:
                if (down) sendCmd("whl 1");
                return;
            case 16: {    // 定中：按一下锁定（放开鼠标），再按一下解除
                if (!down) return;
                clutch = !clutch;
                sH = sV = 0f;
                accX = accY = 0f;
                if (board != null) board.setClutch(clutch);
                Diag.log("CTRL", clutch ? "定中锁定" : "定中解除");
                return;
            }
            default:
                sendCmd("btn " + id + (down ? " down" : " up"));
        }
    }

    private void updateMouseChip() {
        boolean on = prefs.getBoolean("mouseOn", false);
        mouseOn = on;
        if (board != null) board.setMouseOn(on);
    }

    private void setStatus(boolean ok, String ip) {
        if (board != null) board.setConnected(ok, ip);
    }
    // ---------------------------------------------------------------- 网络

    private void discoverPc(int attempts) {
        final int port = prefs.getInt("port", 51234);
        targetPort = port;
        new Thread(() -> {
            DatagramSocket ds = null;
            try {
                ds = new DatagramSocket();
                ds.setBroadcast(true);
                ds.setSoTimeout(1500);
                byte[] b = "ping".getBytes(StandardCharsets.UTF_8);
                InetAddress limited = InetAddress.getByName("255.255.255.255");
                ds.send(new DatagramPacket(b, b.length, limited, port));

                // 有些路由/AP 不转发 255.255.255.255，再补一发子网定向广播
                InetAddress directed = directedBroadcast();
                if (directed != null && !directed.equals(limited)) {
                    try {
                        ds.send(new DatagramPacket(b, b.length, directed, port));
                    } catch (Exception ignored) {
                    }
                }

                byte[] buf = new byte[128];
                DatagramPacket in = new DatagramPacket(buf, buf.length);
                ds.receive(in);
                String s = new String(in.getData(), 0, in.getLength(), StandardCharsets.UTF_8).trim();
                if (!s.startsWith("pong")) return;

                InetAddress addr = in.getAddress();
                target = addr;
                String ip = addr.getHostAddress();
                prefs.edit().putString("ip", ip).putInt("port", port).apply();
                try {
                    sendRaw("hello " + Base64.getEncoder()
                            .encodeToString(Build.MODEL.getBytes(StandardCharsets.UTF_8)));
                } catch (Exception ignored) {
                }
                Diag.log("NET", "发现电脑 " + ip + ":" + port);
                runOnUiThread(() -> onConnectedUi(ip));
            } catch (Exception ex) {
                if (attempts > 1) {
                    try {
                        Thread.sleep(3000);
                    } catch (InterruptedException ignored) {
                    }
                    discoverPc(attempts - 1);
                } else {
                    Diag.log("NET", "这一轮没找到电脑");
                    runOnUiThread(() -> {
                        setStatus(false, null);
                        if (target != null) return;
                        failCount++;
                        // 连着失败 3 次（约 15 秒）才盖面板，避免刚启动就吓人；
                        // 用户自己叉掉之后就不再自动弹回来
                        if (failCount >= 3 && !connPanelDismissed && connPanel != null) {
                            connPanel.setVisibility(View.VISIBLE);
                        }
                    });
                }
            } finally {
                if (ds != null) ds.close();
            }
        }).start();
    }

    /** 本机所在网段的定向广播地址（如 192.168.1.255），拿不到返回 null。 */
    private static InetAddress directedBroadcast() {
        try {
            for (java.net.NetworkInterface nif : java.util.Collections.list(
                    java.net.NetworkInterface.getNetworkInterfaces())) {
                if (!nif.isUp() || nif.isLoopback()) continue;
                for (java.net.InterfaceAddress ia : nif.getInterfaceAddresses()) {
                    if (ia.getAddress() instanceof java.net.Inet4Address && ia.getBroadcast() != null) {
                        return ia.getBroadcast();
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    // ---------------------------------------------------------------- 连不上时怎么办

    /** 连上之后统一收尾：清失败计数、收引导面板、关掉还开着的手动对话框。 */
    private void onConnectedUi(String ip) {
        failCount = 0;
        connPanelDismissed = false;
        if (connPanel != null) connPanel.setVisibility(View.GONE);
        if (manualDialog != null && manualDialog.isShowing()) {
            manualDialog.dismiss();
            manualDialog = null;
        }
        setStatus(true, ip);
    }

    /**
     * 连不上时的引导面板：盖住整个按键区（没连上时它们本来就都是死的），
     * 只留顶部状态条 —— 这样还能去「设置」改端口或换主题。
     */
    private LinearLayout buildConnectPanel() {
        LinearLayout p = new LinearLayout(this);
        p.setOrientation(LinearLayout.VERTICAL);
        p.setGravity(Gravity.CENTER);
        p.setBackgroundColor(Theme.BG);
        p.setClickable(true);           // 吃掉触摸，别漏到下面的按键
        p.setPadding(dp(30), dp(20), dp(30), dp(20));
        p.setVisibility(View.GONE);

        // 标题行：左边标题，右边关闭叉
        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = new TextView(this);
        title.setText("无法找到我的电脑");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        title.setTextColor(Theme.INK);
        title.setGravity(Gravity.START);
        titleRow.addView(title, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView close = new TextView(this);
        close.setText("✕");
        close.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        close.setTextColor(Theme.INK_DIM);
        close.setGravity(Gravity.CENTER);
        close.setPadding(dp(14), dp(10), dp(6), dp(10));
        close.setOnClickListener(v -> {
            connPanelDismissed = true;
            if (connPanel != null) connPanel.setVisibility(View.GONE);
        });
        titleRow.addView(close);

        p.addView(titleRow);

        final TextView sub = new TextView(this);
        sub.setText("按下面的顺序看一眼，多半就是其中之一：");
        sub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        sub.setTextColor(Theme.INK_DIM);
        sub.setLineSpacing(dp(4), 1f);
        sub.setGravity(Gravity.START);
        sub.setPadding(0, dp(10), 0, dp(18));
        p.addView(sub);

        String[][] reasons = {
                {"1", "手机的 Wi-Fi 没打开"},
                {"2", "手机和电脑连的不是同一个 Wi-Fi"},
                {"3", "电脑上的「手机空中鼠标」没打开"},
                {"4", "电脑的安全软件把它拦住了"},
        };
        for (String[] r : reasons) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(4), dp(7), dp(4), dp(7));

            TextView num = new TextView(this);
            num.setText(r[0]);
            num.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            num.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
            num.setTextColor(Theme.LIVE);
            row.addView(num, new LinearLayout.LayoutParams(dp(24),
                    ViewGroup.LayoutParams.WRAP_CONTENT));

            TextView txt = new TextView(this);
            txt.setText(r[1]);
            txt.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            txt.setTextColor(Theme.INK);
            row.addView(txt);
            p.addView(row);
        }

        TextView mark = new TextView(this);
        mark.setText("连上之后，电脑窗口左上角的灯会变成琥珀色");
        mark.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        mark.setTextColor(Theme.INK_DIM);
        mark.setGravity(Gravity.START);
        mark.setPadding(0, dp(16), 0, dp(20));
        p.addView(mark);

        TextView retry = actionButton("重新查找", true);
        retry.setOnClickListener(v -> {
            sub.setText("正在查找…每 5 秒自动重试一次");
            restartDiscovery();
        });
        p.addView(retry);

        TextView manual = actionButton("手动填 IP", false);
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(dp(220), dp(46));
        mlp.topMargin = dp(10);
        mlp.gravity = Gravity.CENTER_HORIZONTAL;
        manual.setLayoutParams(mlp);
        manual.setOnClickListener(v -> showManualConnect());
        p.addView(manual);

        return p;
    }

    private TextView actionButton(String text, boolean primary) {
        TextView b = new TextView(this);
        b.setText(text);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        b.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        b.setGravity(Gravity.CENTER);
        b.setTextColor(primary ? Theme.ON_LIVE : Theme.INK);
        GradientDrawable d = new GradientDrawable();
        d.setCornerRadius(dp(14));
        d.setColor(primary ? Theme.LIVE : Theme.KEY);
        if (!primary) d.setStroke(dp(1), Theme.LINE);
        b.setBackground(d);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(220), dp(46));
        lp.gravity = Gravity.CENTER_HORIZONTAL;
        b.setLayoutParams(lp);
        return b;
    }

    /** 手动填 IP / 端口直连，不依赖广播发现。 */
    private void showManualConnect() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int p = dp(20);
        box.setPadding(p, dp(10), p, 0);

        final EditText ipIn = new EditText(this);
        ipIn.setHint("电脑 IP，例如 192.168.1.100");
        ipIn.setText(prefs.getString("ip", ""));
        ipIn.setInputType(InputType.TYPE_CLASS_TEXT);
        box.addView(ipIn);

        final EditText portIn = new EditText(this);
        portIn.setHint("端口");
        portIn.setText(String.valueOf(prefs.getInt("port", 51234)));
        portIn.setInputType(InputType.TYPE_CLASS_NUMBER);
        box.addView(portIn);

        manualDialog = new android.app.AlertDialog.Builder(this)
                .setTitle("手动连接")
                .setMessage("电脑端窗口右上角显示着端口号")
                .setView(box)
                .setPositiveButton("连接", (d, w) -> {
                    String ip = ipIn.getText().toString().trim();
                    if (ip.isEmpty()) {
                        toast("先填电脑的 IP");
                        return;
                    }
                    int port;
                    try {
                        port = Integer.parseInt(portIn.getText().toString().trim());
                    } catch (Exception e) {
                        toast("端口要填数字");
                        return;
                    }
                    manualConnect(ip, port);
                })
                .setNegativeButton("取消", null)
                .show();
    }

    /** 直连一个地址：先单播 ping 验一下，通了才认。 */
    private void manualConnect(String ip, int port) {
        new Thread(() -> {
            DatagramSocket ds = null;
            try {
                InetAddress addr = InetAddress.getByName(ip);
                ds = new DatagramSocket();
                ds.setSoTimeout(1800);
                byte[] b = "ping manual".getBytes(StandardCharsets.UTF_8);
                ds.send(new DatagramPacket(b, b.length, addr, port));

                byte[] buf = new byte[128];
                DatagramPacket in = new DatagramPacket(buf, buf.length);
                ds.receive(in);
                String s = new String(in.getData(), 0, in.getLength(), StandardCharsets.UTF_8).trim();
                if (!s.startsWith("pong")) throw new Exception("回包不对");

                target = addr;
                targetPort = port;
                prefs.edit().putString("ip", ip).putInt("port", port).apply();
                try {
                    sendRaw("hello " + Base64.getEncoder()
                            .encodeToString(Build.MODEL.getBytes(StandardCharsets.UTF_8)));
                } catch (Exception ignored) {
                }
                Diag.log("NET", "手动连上 " + ip + ":" + port);
                runOnUiThread(() -> {
                    onConnectedUi(ip);
                    toast("连上了 " + ip);
                });
            } catch (Exception e) {
                Diag.log("NET", "手动连接失败 " + ip + ":" + port + " " + e.getMessage());
                runOnUiThread(() -> toast("连不上 " + ip + ":" + port + "\n确认电脑端在跑、IP 没填错"));
            } finally {
                if (ds != null) ds.close();
            }
        }).start();
    }

    private void toast(String msg) {
        android.widget.Toast.makeText(this, msg, android.widget.Toast.LENGTH_LONG).show();
    }

    private void sendCmd(String msg) {
        new Thread(() -> {
            try {
                sendRaw(msg);
            } catch (Exception ignored) {
            }
        }).start();
    }

    private void sendRaw(String msg) throws Exception {
        InetAddress addr = target;
        if (addr == null) return;
        if (socket == null || socket.isClosed()) socket = new DatagramSocket();
        byte[] data = msg.getBytes(StandardCharsets.UTF_8);
        socket.send(new DatagramPacket(data, data.length, addr, targetPort));
    }

    private void sendTextNow() {
        String s = textField.getText().toString().trim();
        if (s.isEmpty()) return;
        tick();
        sendCmd("txt " + Base64.getEncoder().encodeToString(s.getBytes(StandardCharsets.UTF_8)));
        textField.setText("");
    }

    private void openKeyboardForVoice() {
        textField.requestFocus();
        textField.setSelection(textField.getText().length());
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) imm.showSoftInput(textField, InputMethodManager.SHOW_IMPLICIT);
    }

    // ---------------------------------------------------------------- 传感器

    private void loadSensorPrefs() {
        boolean enabled = prefs.getBoolean("mouseOn", false);
        sensProgress = prefs.getInt("sens", 40);
        smoothAlpha = prefs.getInt("smooth", 30) / 100f * 0.85f;
        invertX = prefs.getBoolean("invX", false);
        invertY = prefs.getBoolean("invY", false);
        if (enabled != mouseOn) {
            mouseOn = enabled;
            if (enabled) startSensors(); else stopSensors();
        }
    }

    // ================= SENSOR-CORE-BEGIN =================
    // ⚠ 传感器核心（零偏补偿 / 死区 / 轴选择）已验证稳定：
    //   静止 12 秒 0 位移。改动前请先备份，并跑一次静止测试。
    //   详见 docs\SENSOR_LOCK.md
    private void startSensors() {
        Handler h = new Handler(sensorThread.getLooper());
        if (gyro != null) sensorManager.registerListener(this, gyro, SENSOR_RATE * 1000, h);
        Sensor pose = gravSensor != null ? gravSensor : accel;
        if (pose != null) sensorManager.registerListener(this, pose, SensorManager.SENSOR_DELAY_UI, h);
        lastGyroTs = 0;
        poseW = 0.5f;
    }

    private void stopSensors() {
        sensorManager.unregisterListener(this);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        int type = event.sensor.getType();
        if (type == Sensor.TYPE_GYROSCOPE) {
            handleGyro(event);
            return;
        }
        float gx = event.values[0], gy = event.values[1], gz = event.values[2];
        if (type == Sensor.TYPE_ACCELEROMETER) {
            final float a = 0.85f;
            gravity[0] = a * gravity[0] + (1 - a) * gx;
            gravity[1] = a * gravity[1] + (1 - a) * gy;
            gravity[2] = a * gravity[2] + (1 - a) * gz;
            gx = gravity[0];
            gy = gravity[1];
            gz = gravity[2];
        }
        float n = (float) Math.sqrt(gx * gx + gy * gy + gz * gz);
        if (n < 1f) return;
        gx /= n;
        gy /= n;
        gz /= n;
        float w = Math.abs(gy) / (Math.abs(gy) + Math.abs(gz) + 1e-6f);
        poseW = poseW * 0.92f + w * 0.08f;
    }

    private void handleGyro(SensorEvent e) {
        long ts = e.timestamp;
        if (lastGyroTs == 0) {
            lastGyroTs = ts;
            return;
        }
        float dt = (ts - lastGyroTs) / 1e9f;
        lastGyroTs = ts;
        if (dt <= 0 || dt > 0.1f) return;
        if (!mouseOn) return;
        // 心跳日志（每 5 秒一行，便于事后诊断）
        if (ts - lastBeatTs > 5_000_000_000L) {
            lastBeatTs = ts;
            Diag.log("GYRO", String.format(java.util.Locale.US,
                    "bias=%.2f,%.2f,%.2f pose=%.2f mouse=%s",
                    biasX, biasY, biasZ, poseW, mouseOn ? "on" : "off"));
        }
        if (clutch) {
            sH = sV = 0f;
            accX = accY = 0f;
            return;
        }

        float rx = (float) Math.toDegrees(e.values[0]);
        float ry = (float) Math.toDegrees(e.values[1]);
        float rz = (float) Math.toDegrees(e.values[2]);

        // ---- 零偏：长窗口(约2秒)中位数 + 分位差判定 ----
        // 手抖是零均值的，中位数依然能抓到真实零偏；分位差用来区分“真在转”
        hist[0][histIdx] = rx;
        hist[1][histIdx] = ry;
        hist[2][histIdx] = rz;
        histIdx = (histIdx + 1) % HIST;
        if (histFill < HIST) histFill++;
        frameCount++;
        if (histFill >= 60 && frameCount % 8 == 0) {
            float[] med = new float[3];
            boolean quiet = true;
            for (int ax = 0; ax < 3; ax++) {
                int n = histFill;
                float[] tmp = new float[n];
                System.arraycopy(hist[ax], 0, tmp, 0, n);
                java.util.Arrays.sort(tmp);
                med[ax] = tmp[n / 2];
                if (tmp[(int) (n * 0.9f)] - tmp[(int) (n * 0.1f)] > 8f) quiet = false;
            }
            if (quiet) {
                biasX += 0.15f * (med[0] - biasX);
                biasY += 0.15f * (med[1] - biasY);
                biasZ += 0.15f * (med[2] - biasZ);
            }
        }

        float wx = rx - biasX;
        float wy = ry - biasY;
        float wz = rz - biasZ;

        // ---- 3 点中值滤波：干掉单帧尖峰（快速抖动）----
        m3[0][m3Idx] = wx;
        m3[1][m3Idx] = wy;
        m3[2][m3Idx] = wz;
        m3Idx = (m3Idx + 1) % 3;
        if (m3Fill < 3) m3Fill++;
        if (m3Fill == 3) {
            wx = median3(m3[0]);
            wy = median3(m3[1]);
            wz = median3(m3[2]);
        }

        float mag = Math.max(Math.abs(wx), Math.max(Math.abs(wy), Math.abs(wz)));
        if (mag < 2.5f) {
            sH = sV = 0f;
            accX = accY = 0f;
            return;
        }

        float gate;
        if (mag < 7f) {
            gate = (mag - 2.5f) / 4.5f;
        } else {
            gate = 1f;
        }

        if (poseW > 0.6f) {
            useYaw = true;
        } else if (poseW < 0.4f) {
            useYaw = false;
        }

        float dVert = wx * dt * gate;
        float dHoriz = (useYaw ? wy : wz) * dt * gate;
        if (invertX) dHoriz = -dHoriz;
        if (invertY) dVert = -dVert;

        float a = smoothAlpha;
        sH = a * sH + (1 - a) * dHoriz;
        sV = a * sV + (1 - a) * dVert;

        float pxPerDeg = 8f + sensProgress / 100f * 52f;
        accX += sH * pxPerDeg;
        accY += sV * pxPerDeg;
        accX = Math.max(-60f, Math.min(60f, accX));
        accY = Math.max(-60f, Math.min(60f, accY));

        long now = SystemClock.uptimeMillis();
        if (now - lastMoveSent < 16) return;
        int dx = (int) accX;
        int dy = (int) accY;
        if (dx == 0 && dy == 0) return;
        accX -= dx;
        accY -= dy;
        lastMoveSent = now;
        try {
            sendRaw("mv " + dx + " " + dy);
        } catch (Exception ignored) {
        }
    }

    private static float median3(float[] a) {
        float x = a[0], y = a[1], z = a[2];
        if ((x <= y && y <= z) || (z <= y && y <= x)) return y;
        if ((y <= x && x <= z) || (z <= x && x <= y)) return x;
        return z;
    }
    // ================= SENSOR-CORE-END =================

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    // ---------------------------------------------------------------- 生命周期

    @Override
    protected void onResume() {
        super.onResume();
        // 设置页可能改了外观，回到主界面时整屏重建
        if (prefs.getBoolean("darkTheme", true) != Theme.dark) {
            recreate();
            return;
        }
        loadSensorPrefs();
        // 回到前台先解除定中锁定，否则会以为「鼠标坏了」
        if (clutch) {
            clutch = false;
            if (board != null) board.setClutch(false);
        }
        updateMouseChip();
        if (prefs.getBoolean("calibrate", false)) {
            prefs.edit().putBoolean("calibrate", false).apply();
            biasX = biasY = biasZ = 0f;
            biasSumX = biasSumY = biasSumZ = 0f;
            biasCount = 0;
            histIdx = 0;
            histFill = 0;
            m3Idx = 0;
            m3Fill = 0;
            frameCount = 0;
            sH = sV = 0f;
            accX = accY = 0f;
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopSensors();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopped = true;
        retryLoop.removeCallbacks(retryTick);
        stopSensors();
        if (socket != null) socket.close();
        sensorThread.quitSafely();
    }
}
