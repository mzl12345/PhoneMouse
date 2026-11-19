package phonemouse;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.prefs.Preferences;

import phonemouse.input.MouseMover;
import phonemouse.input.RobotInput;
import phonemouse.keymap.ButtonDispatcher;
import phonemouse.keymap.CustomKeys;
import phonemouse.net.SerialLink;
import phonemouse.net.UdpServer;
import phonemouse.proto.Command;
import phonemouse.proto.Protocol;
import phonemouse.ui.Board;
import phonemouse.ui.FlatButton;
import phonemouse.ui.Rail;
import phonemouse.ui.Ui;

/**
 * 手机空中鼠标 —— 电脑接收端（窗口与装配）。
 *
 * <p>分层：
 * <ul>
 *   <li>{@code proto} 单行协议解析</li>
 *   <li>{@code net} UDP / 蓝牙串口收包</li>
 *   <li>{@code keymap} 按键编号 → 动作（表驱动）</li>
 *   <li>{@code input} Robot 与 4 个辅助 exe</li>
 *   <li>{@code ui} 视觉 token 与自绘组件</li>
 *   <li>本类：装配、协议路由、生命周期</li>
 * </ul>
 */
public class PcReceiver extends JFrame {

    private static final int DEFAULT_PORT = 51234;
    private static final Preferences PREFS = Preferences.userNodeForPackage(PcReceiver.class);

    // ---- 界面 ----
    private Rail rail;
    private Board board;
    private final JTextArea logArea = new JTextArea();
    private JScrollPane logScroll;
    private JCheckBox devMode;

    private final AppLog appLog = new AppLog(this::appendUiLog);

    private final ExecutorService inputExec = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "input");
        t.setDaemon(true);
        return t;
    });

    private final CustomKeys custom = new CustomKeys();
    private Robot robot;
    private RobotInput target;
    private ButtonDispatcher dispatcher;
    private volatile UdpServer udp;
    private volatile SerialLink serial;

    private int eggClicks = 0;
    private String lastDevice = "";
    private String lastIp = "";
    private boolean everConnected = false;

    public PcReceiver() {
        super("手机空中鼠标");
        setFocusableWindowState(false);   // 绝不抢焦点，否则输入会打进自己
        setAutoRequestFocus(false);

        if (PREFS.getBoolean("dark", true)) Ui.useDark();
        else Ui.useLight();

        // 便于截图/临时试色：-Dphonemouse.theme=light|dark（不写回配置）
        String forced = System.getProperty("phonemouse.theme");
        if ("light".equalsIgnoreCase(forced)) Ui.useLight();
        else if ("dark".equalsIgnoreCase(forced)) Ui.useDark();

        try {
            robot = new Robot();
            robot.setAutoDelay(4);
            MouseMover.releaseStuckButtons(robot);
            custom.load(appLog);
            target = new RobotInput(robot, appLog, inputExec);
            target.mover().start();
            dispatcher = new ButtonDispatcher(target, appLog, this::flash, custom);
        } catch (AWTException e) {
            JOptionPane.showMessageDialog(null, "无法初始化输入模拟：" + e.getMessage());
            System.exit(1);
        }
        buildUi();
        SwingUtilities.invokeLater(this::startServer);
    }

    // ------------------------------------------------------------------ 日志

    /** 把一行追加到界面日志区，超过 300 行时丢掉前半段。 */
    private void appendUiLog(String line) {
        SwingUtilities.invokeLater(() -> {
            if (logArea == null) return;
            logArea.append(line + "\n");
            if (logArea.getLineCount() > 300) {
                try {
                    logArea.replaceRange("", 0, logArea.getLineOfOffset(logArea.getText().length() / 2));
                } catch (Exception ignored) {
                }
            }
        });
    }

    private void log(String msg) {
        appLog.log(msg);
    }

    // ------------------------------------------------------------------ 界面

    private void buildUi() {
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        JPanel root = new JPanel(new BorderLayout(0, 16));
        root.setBackground(Ui.BG);
        root.setBorder(new EmptyBorder(16, 20, 14, 20));

        // ---- 顶部状态轨 ----
        rail = new Rail();
        rail.setLocalIp(localIpv4());
        rail.portField().setText(String.valueOf(PREFS.getInt("port", DEFAULT_PORT)));
        rail.setThemeLabel(Ui.dark);
        rail.serverButton().addActionListener(e -> {
            if (udp != null && udp.isRunning()) stopServer();
            else startServer();
        });
        rail.themeButton().addActionListener(e -> toggleTheme());
        rail.nameplate().addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                eggClicks++;
                if (eggClicks == 7) showEasterEgg();
            }
        });
        root.add(rail, BorderLayout.NORTH);

        // ---- 中部：自检板 + 一行引导 ----
        JPanel center = new JPanel();
        center.setOpaque(false);
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));
        center.setBorder(new EmptyBorder(2, 0, 0, 0));

        board = new Board();
        board.setAlignmentX(Component.LEFT_ALIGNMENT);
        // 不自定最大高度：让自检板吃掉窗口多余的竖向空间，键跟着变大，也就没有空档
        center.add(board);
        center.add(Box.createRigidArea(new Dimension(0, 12)));

        JLabel hint = new JLabel("在手机上按任意键，这块板上对应的键会亮起来。");
        hint.setFont(Ui.SMALL);
        hint.setForeground(Ui.INK_DIM);
        hint.setAlignmentX(Component.LEFT_ALIGNMENT);
        center.add(hint);
        root.add(center, BorderLayout.CENTER);

        // ---- 底部：命令 + 日志抽屉 ----
        root.add(buildSouth(), BorderLayout.SOUTH);

        setContentPane(root);
        pack();
        setMinimumSize(new Dimension(860, 470));
        setLocationRelativeTo(null);

        if (Boolean.getBoolean("phonemouse.devlog")) {
            devMode.setSelected(true);
            setLogVisible(true);
        }

        // 只给截图/演示用：-Dphonemouse.demo=1 伪造一个「已连接 + 两个键刚被按过」的界面，
        // 不发送、不注入任何真实输入。
        if (Boolean.getBoolean("phonemouse.demo")) {
            rail.setConnected("PJD110", "192.168.1.100");
            board.forceLit("clk L");
            board.forceLit("key ENTER");
        }
    }

    private JComponent buildSouth() {
        JPanel south = new JPanel(new BorderLayout(0, 10));
        south.setOpaque(false);

        JPanel cmd = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        cmd.setOpaque(false);

        FlatButton customBtn = new FlatButton("自定义键…", FlatButton.Kind.SECONDARY);
        customBtn.setPreferredSize(new Dimension(104, 30));
        customBtn.addActionListener(e -> showCustomKeyDialog());

        FlatButton logBtn = new FlatButton("导出日志", FlatButton.Kind.SECONDARY);
        logBtn.setPreferredSize(new Dimension(92, 30));
        logBtn.setToolTipText("遇到问题点这里：日志会打包到桌面，直接发给作者即可");
        logBtn.addActionListener(e -> exportLogToDesktop());

        FlatButton aboutBtn = new FlatButton("关于", FlatButton.Kind.GHOST);
        aboutBtn.setPreferredSize(new Dimension(64, 30));
        aboutBtn.addActionListener(e -> showAbout());

        cmd.add(customBtn);
        cmd.add(logBtn);
        cmd.add(aboutBtn);

        devMode = new JCheckBox("开发者日志");
        devMode.setOpaque(false);
        devMode.setFont(Ui.SMALL);
        devMode.setForeground(Ui.INK_DIM);
        devMode.setFocusPainted(false);
        devMode.setBorder(new EmptyBorder(0, 8, 0, 0));
        devMode.addActionListener(e -> setLogVisible(devMode.isSelected()));
        cmd.add(devMode);

        south.add(cmd, BorderLayout.NORTH);
        southContainer = south;

        // ---- 日志抽屉（默认不参与布局，展开时才加进来）----
        logArea.setEditable(false);
        logArea.setFont(Ui.MONO);
        logArea.setBackground(Ui.SURFACE);
        logArea.setForeground(Ui.INK_DIM);
        logArea.setCaretColor(Ui.INK);
        logArea.setSelectionColor(new Color(Ui.LIVE.getRed(), Ui.LIVE.getGreen(), Ui.LIVE.getBlue(), 90));
        logArea.setBorder(new EmptyBorder(8, 10, 8, 10));

        logScroll = new JScrollPane(logArea);
        logScroll.setBorder(BorderFactory.createLineBorder(Ui.LINE));
        logScroll.getViewport().setBackground(Ui.SURFACE);
        logScroll.setVisible(false);
        logScroll.setPreferredSize(new Dimension(100, 150));

        return south;
    }

    private JPanel southContainer;

    /** 展开/收起日志抽屉，并让窗口跟着长高/缩回。 */
    private void setLogVisible(boolean on) {
        if (southContainer == null) return;
        southContainer.remove(logScroll);
        logScroll.setVisible(on);
        if (on) southContainer.add(logScroll, BorderLayout.CENTER);
        southContainer.revalidate();
        pack();
    }

    /** 切主题：重建界面，保住日志内容与连接状态。 */
    private void toggleTheme() {
        boolean darkNow = !Ui.dark;
        if (darkNow) Ui.useDark();
        else Ui.useLight();
        PREFS.putBoolean("dark", darkNow);

        String keepLog = logArea.getText();
        String portText = rail.portField().getText();
        boolean running = udp != null && udp.isRunning();
        boolean devOpen = devMode != null && devMode.isSelected();

        buildUi();
        rail.portField().setText(portText);
        rail.setRunning(running);
        if (everConnected) rail.setConnected(lastDevice, lastIp);
        logArea.setText(keepLog);
        if (devOpen) {
            devMode.setSelected(true);
            setLogVisible(true);
        }
        revalidate();
        repaint();
    }

    // ------------------------------------------------------------- 连接状态

    /**
     * 本机在局域网里的 IPv4，给新手照着填。
     *
     * <p>这台机器上同时有以太网 192.168.1.100、utun8 172.31.x、vgate0 172.30.x 等一堆地址，
     * 直接取「第一个非回环地址」很可能给出虚拟网卡的。按私网段优先级挑：
     * 192.168. &gt; 10. &gt; 172. &gt; 其它。
     */
    private static String localIpv4() {
        String best = null;
        int bestScore = -1;
        try {
            for (java.net.NetworkInterface nif : java.util.Collections.list(
                    java.net.NetworkInterface.getNetworkInterfaces())) {
                if (!nif.isUp() || nif.isLoopback()) continue;
                for (java.net.InterfaceAddress ia : nif.getInterfaceAddresses()) {
                    java.net.InetAddress a = ia.getAddress();
                    if (!(a instanceof java.net.Inet4Address)) continue;
                    if (a.isLoopbackAddress() || a.isLinkLocalAddress()) continue;
                    String ip = a.getHostAddress();
                    int score = 0;
                    if (ip.startsWith("192.168.")) score = 3;
                    else if (ip.startsWith("10.")) score = 2;
                    else if (ip.startsWith("172.")) score = 1;
                    if (score > bestScore) {
                        bestScore = score;
                        best = ip;
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return best == null ? "—" : best;
    }

    private void setConnected(String deviceName, String ip) {
        lastDevice = deviceName == null ? "" : deviceName;
        lastIp = ip == null ? "" : ip;
        everConnected = true;
        SwingUtilities.invokeLater(() -> rail.setConnected(lastDevice, lastIp));
    }

    private void setDisconnected() {
        SwingUtilities.invokeLater(() -> rail.setDisconnected());
    }

    // ------------------------------------------------------------- 服务端

    private void startServer() {
        int port;
        try {
            port = Integer.parseInt(rail.portField().getText().trim());
            if (port < 1 || port > 65535) throw new NumberFormatException();
        } catch (NumberFormatException ex) {
            rail.setAlert("端口不合法");
            return;
        }

        UdpServer server = new UdpServer(port, new UdpServer.Listener() {
            @Override public void onLine(String line) {
                handle(line, false);
            }

            @Override public void onNewClient(String ip) {
                setConnected("", ip);
            }

            @Override public void onError(String message) {
                log(message);
            }
        });

        try {
            server.start();
        } catch (SocketException ex) {
            rail.setAlert("端口 " + port + " 被占用");
            log("端口绑定失败：" + ex.getMessage());
            return;
        } catch (Exception ex) {
            rail.setAlert("端口 " + port + " 起不来");
            log("监听失败：" + ex.getMessage());
            return;
        }

        udp = server;
        PREFS.putInt("port", port);
        rail.setRunning(true);
        rail.portField().setEnabled(false);
        log("UDP 监听 " + port);

        serial = new SerialLink(appLog, line -> handle(line, true));
        serial.start();
    }

    private void stopServer() {
        UdpServer server = udp;
        udp = null;
        if (server != null) server.stop();
        rail.setRunning(false);
        rail.portField().setEnabled(true);
        setDisconnected();
    }

    // ------------------------------------------------------------- 协议

    private void handle(String line) {
        handle(line, false);
    }

    /**
     * @param viaSerial 这条指令是否从蓝牙串口来（决定 {@code pong} 从哪条链路回）
     */
    private void handle(String line, boolean viaSerial) {
        Command c = Protocol.parse(line);
        if (c == null) return;
        String cmd = c.kind();
        String arg = c.arg();

        switch (cmd) {
            case "mv": {
                String[] xy = arg.split("\\s+");
                if (xy.length < 2) return;
                int dx, dy;
                try {
                    dx = (int) Math.round(Double.parseDouble(xy[0]));
                    dy = (int) Math.round(Double.parseDouble(xy[1]));
                } catch (NumberFormatException e) {
                    return;
                }
                if (dx == 0 && dy == 0) return;
                target.moveBy(dx, dy);
                break;
            }
            case "clk": {
                String which = arg.equalsIgnoreCase("R") ? "R" : arg.equalsIgnoreCase("M") ? "M" : "L";
                flash(arg.equalsIgnoreCase("R") ? "clk R" : arg.equalsIgnoreCase("M") ? "clk M" : "clk L");
                target.click(which);
                break;
            }
            case "down": {
                log("按下（拖拽开始）");
                flash("clk L");
                target.hold(arg, true);
                break;
            }
            case "up": {
                log("抬起（拖拽结束）");
                target.hold(arg, false);
                break;
            }
            case "whl": {
                try {
                    int n = Integer.parseInt(arg.trim());
                    if (n == 0) return;
                    flash(n < 0 ? "whl -1" : "whl 1");
                    target.wheel(n);
                } catch (NumberFormatException ignored) {
                }
                break;
            }
            case "btn": {
                String[] parts = arg.split("\\s+");
                if (parts.length == 0) return;
                int id;
                try {
                    id = Integer.parseInt(parts[0].trim());
                } catch (NumberFormatException e) {
                    return;
                }
                boolean down = parts.length < 2 || "down".equalsIgnoreCase(parts[1]);
                dispatcher.press(id, down);
                break;
            }
            case "key": {
                flash("key " + arg.toUpperCase());
                target.combo(arg);
                break;
            }
            case "txt": {
                try {
                    target.text(new String(Base64.getDecoder().decode(arg), StandardCharsets.UTF_8));
                } catch (IllegalArgumentException e) {
                    log("文本解码失败");
                }
                break;
            }
            case "hello": {
                String name;
                try {
                    name = new String(Base64.getDecoder().decode(arg), StandardCharsets.UTF_8);
                } catch (IllegalArgumentException e) {
                    name = "";
                }
                UdpServer server = udp;
                String ip = server == null ? "?" : server.lastClientIp();
                setConnected(name, ip);
                log("手机上报：" + name);
                break;
            }
            case "ping": {
                String reply = arg.isEmpty() ? "pong" : ("pong " + arg);
                // 从哪条链路来、就从哪条链路回。v1.6 是「串口优先」，
                // 结果只要蓝牙 SPP 口能打开，UDP 发现就永远收不到 pong。
                boolean sent = false;
                if (viaSerial) {
                    SerialLink link = serial;
                    sent = link != null && link.send(reply);
                } else {
                    UdpServer server = udp;
                    if (server != null) {
                        server.reply(reply);
                        sent = true;
                    }
                }
                if (!sent) {
                    SerialLink link = serial;
                    if (link != null) link.send(reply);
                }
                break;
            }
            default:
                log("未知指令：" + cmd);
        }
    }

    private void flash(String code) {
        Board b = board;
        if (b != null) b.flash(code);
    }

    // ------------------------------------------------------------- 对话框

    /** 一键把日志打包到桌面，方便小白直接拖去发 */
    private void exportLogToDesktop() {
        File out = AppLog.exportZip();
        if (out == null) {
            JOptionPane.showMessageDialog(this,
                    "导出失败。\n请手动打开这个文件夹查找日志：\n" + AppLog.dir(),
                    "导出失败", JOptionPane.WARNING_MESSAGE);
            return;
        }
        log("日志已导出：" + out.getAbsolutePath());

        JOptionPane.showMessageDialog(this,
                "日志已经放到桌面了：\n\n  " + out.getName() + "\n\n"
                        + "把桌面上这个文件发给我就行，\n"
                        + "顺便说一下当时在做什么、出现了什么问题。",
                "导出成功", JOptionPane.INFORMATION_MESSAGE);

        try {
            new ProcessBuilder("explorer.exe", "/select,", out.getAbsolutePath()).start();
        } catch (Exception ignored) {
        }
    }

    /** 自定义键配置对话框 */
    private void showCustomKeyDialog() {
        JDialog dlg = new JDialog(this, "自定义键设置", true);
        dlg.getContentPane().setBackground(Ui.BG);
        dlg.setLayout(new BorderLayout(0, 12));

        JPanel body = new JPanel(new GridLayout(4, 2, 10, 10));
        body.setOpaque(false);
        body.setBorder(new EmptyBorder(16, 16, 4, 16));

        JTextField[] fields = new JTextField[4];
        for (int i = 0; i < 4; i++) {
            int id = 21 + i;
            JLabel lab = new JLabel("手机「自定" + (i + 1) + "」");
            lab.setFont(Ui.LABEL);
            lab.setForeground(Ui.INK);
            body.add(lab);

            JTextField tf = new JTextField(custom.get(String.valueOf(id), ""));
            tf.setFont(Ui.MONO);
            tf.setBackground(Ui.SURFACE);
            tf.setForeground(Ui.INK);
            tf.setCaretColor(Ui.INK);
            tf.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(Ui.LINE),
                    new EmptyBorder(6, 8, 6, 8)));
            tf.setToolTipText("示例：key CTRL+C ｜ whl -1 ｜ clk L");
            fields[i] = tf;
            body.add(tf);
        }

        JLabel tip = new JLabel("写法：key 键名（可组合，如 CTRL+V）｜whl -1 向上 ｜clk L/R/M 点击");
        tip.setBorder(new EmptyBorder(0, 16, 0, 16));
        tip.setFont(Ui.SMALL);
        tip.setForeground(Ui.INK_DIM);

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 10));
        bottom.setOpaque(false);
        FlatButton ok = new FlatButton("保存", FlatButton.Kind.PRIMARY);
        ok.setPreferredSize(new Dimension(84, 30));
        ok.addActionListener(e -> {
            for (int i = 0; i < 4; i++) {
                String v = fields[i].getText().trim();
                if (v.isEmpty()) custom.remove(String.valueOf(21 + i));
                else custom.set(String.valueOf(21 + i), v);
            }
            custom.save(appLog);
            dlg.dispose();
        });
        bottom.add(ok);

        JPanel south = new JPanel(new BorderLayout());
        south.setOpaque(false);
        south.add(tip, BorderLayout.NORTH);
        south.add(bottom, BorderLayout.SOUTH);

        dlg.add(body, BorderLayout.CENTER);
        dlg.add(south, BorderLayout.SOUTH);
        dlg.setSize(500, 280);
        dlg.setLocationRelativeTo(this);
        dlg.setVisible(true);
    }

    /** 关于：作者、版本、开发时间 */
    private void showAbout() {
        String msg =
                "手机空中鼠标  v2.0\n\n" +
                "开发时间：2026 年 11 月 18–19 日\n" +
                "开发方式：一个下午 + 一整个晚上，边调边改\n\n" +
                "作者：MZL\n\n" +
                "技术栈：Java + Swing（电脑端） / 原生 Android（手机端）\n" +
                "传输：局域网 UDP，不经过任何服务器\n\n" +
                "特别感谢：那颗总爱漂移的陀螺仪，\n" +
                "让我们学会了什么叫「零偏估计」。";
        JOptionPane.showMessageDialog(this, msg, "关于", JOptionPane.INFORMATION_MESSAGE);
    }

    /** 小彩蛋：连点铭牌 7 次 */
    private void showEasterEgg() {
        eggClicks = 0;
        String egg =
                "        ╭──────────────────╮\n" +
                "        │   嘀 —— 彩蛋！   │\n" +
                "        ╰──────────────────╯\n\n" +
                "既然你点到这里，说明你真的在用。\n\n" +
                "这个软件诞生于一个很朴素的念头：\n" +
                "躺在被窝里，也能顺手控制电脑。\n\n" +
                "—— 第 7 次点击，送你一个隐藏祝福：\n" +
                "   愿你永远不用再爬到桌子前拿鼠标。\n\n" +
                "（试试再点 7 次）";
        JOptionPane.showMessageDialog(this, egg, "🎉", JOptionPane.PLAIN_MESSAGE);
    }

    public static void main(String[] args) {
        if (args.length > 0 && args[0].equals("--export-log")) {
            File f = AppLog.exportZip();
            System.out.println(f == null ? "导出失败" : f.getAbsolutePath());
            return;
        }
        SwingUtilities.invokeLater(() -> new PcReceiver().setVisible(true));
    }
}
