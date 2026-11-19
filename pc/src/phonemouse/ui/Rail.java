package phonemouse.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

/**
 * 顶部状态轨：一颗灯、一句话、一个端口、一个开关。
 *
 * <p>替代原来那条蓝紫渐变横幅 —— 状态不需要装饰来强调，
 * 一颗会变色的灯比一整条渐变更快被读到。
 */
public final class Rail extends JPanel {

    private final Led led = new Led();
    private final JLabel nameplate = new JLabel("手机空中鼠标");
    private final JLabel status = new JLabel("等待手机连接…");
    private final JLabel device = new JLabel("还没连上过任何设备");
    private final JLabel localIpLabel = new JLabel("本机 IP —");
    private final JTextField portField = new JTextField(5);
    private final FlatButton serverBtn = new FlatButton("停止", FlatButton.Kind.SECONDARY);
    private final FlatButton themeBtn = new FlatButton("浅色", FlatButton.Kind.GHOST);

    private String localIp = "—";
    private boolean connected = false;
    private String phone = "";

    public Rail() {
        setOpaque(false);
        setLayout(new BorderLayout(16, 0));

        // ---- 左：灯 + 状态 + 设备 ----
        JPanel left = new JPanel();
        left.setOpaque(false);
        left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));

        JPanel topLine = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        topLine.setOpaque(false);

        // 铭牌：像设备外壳上印的型号，同时是彩蛋的点击目标（连点 7 次）
        nameplate.setFont(new java.awt.Font("Microsoft YaHei UI", java.awt.Font.BOLD, 14));
        nameplate.setForeground(Ui.INK_DIM);
        nameplate.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 6));
        nameplate.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));

        status.setFont(new java.awt.Font("Microsoft YaHei UI", java.awt.Font.BOLD, 14));
        status.setForeground(Ui.INK);
        topLine.add(nameplate);
        topLine.add(led);
        topLine.add(status);

        device.setFont(Ui.SMALL);
        device.setForeground(Ui.INK_DIM);
        device.setBorder(BorderFactory.createEmptyBorder(2, 18, 0, 0));

        localIpLabel.setFont(Ui.MONO);
        localIpLabel.setForeground(Ui.INK_DIM);
        localIpLabel.setBorder(BorderFactory.createEmptyBorder(0, 18, 0, 0));

        left.add(topLine);
        left.add(localIpLabel);
        left.add(device);

        // ---- 右：端口 + 开关 + 主题 ----
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        right.setOpaque(false);

        JLabel portLabel = new JLabel("端口");
        portLabel.setFont(Ui.SMALL);
        portLabel.setForeground(Ui.INK_DIM);
        right.add(portLabel);

        portField.setText("51234");
        portField.setFont(Ui.MONO);
        portField.setOpaque(true);
        portField.setBackground(Ui.SURFACE);
        portField.setForeground(Ui.INK);
        portField.setCaretColor(Ui.INK);
        portField.setSelectionColor(new Color(Ui.LIVE.getRed(), Ui.LIVE.getGreen(), Ui.LIVE.getBlue(), 90));
        portField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Ui.LINE),
                BorderFactory.createEmptyBorder(4, 7, 4, 7)));
        portField.setPreferredSize(new Dimension(66, 28));
        right.add(portField);

        serverBtn.setPreferredSize(new Dimension(72, 28));
        right.add(serverBtn);

        themeBtn.setPreferredSize(new Dimension(64, 28));
        right.add(themeBtn);

        add(left, BorderLayout.WEST);
        add(Box.createHorizontalGlue(), BorderLayout.CENTER);
        add(right, BorderLayout.EAST);
    }

    public JTextField portField() {
        return portField;
    }

    /** 铭牌标签（彩蛋点击目标）。 */
    public JLabel nameplate() {
        return nameplate;
    }

    public FlatButton serverButton() {
        return serverBtn;
    }

    public FlatButton themeButton() {
        return themeBtn;
    }

    /** 本机在局域网里的地址。新手要在手机上「手动填 IP」，填的就是这个。 */
    public void setLocalIp(String ip) {
        localIp = ip;
        localIpLabel.setText("本机 IP  " + ip);
        if (!connected) {
            device.setText("没连上手机时，手机上「手动填 IP」就填这个地址");
        }
    }

    public void setConnected(String deviceName, String ip) {
        connected = true;
        led.setColor(Ui.LIVE);
        status.setForeground(Ui.INK);
        status.setText("已连接，可以用了");
        String model = deviceName == null || deviceName.isBlank() ? "未知机型" : deviceName;
        phone = model + "  ·  " + ip;
        localIpLabel.setText("本机 IP  " + localIp);
        device.setText("已连接手机  " + phone);
    }

    public void setDisconnected() {
        connected = false;
        led.setColor(Ui.INK_DIM);
        status.setText("还没连上手机");
        localIpLabel.setText("本机 IP  " + localIp);
        device.setText("没连上时，手机上「手动填 IP」就填这个地址");
    }

    public void setAlert(String text) {
        led.setColor(Ui.ALERT);
        status.setText(text);
    }

    public void setRunning(boolean running) {
        serverBtn.setText(running ? "停止" : "启动");
    }

    public void setThemeLabel(boolean darkNow) {
        themeBtn.setText(darkNow ? "浅色" : "深色");
    }
}
