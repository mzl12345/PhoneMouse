import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 跟随测试：屏幕上有个目标点在水平线上来回移动，
 * 用手机控制光标跟着它跑，记录轨迹并输出指标。
 *
 * 输出指标：
 *   平均横向误差 / 平均纵向误差 / 停顿抖动 / 超调次数
 * 同时把逐帧数据写到 _track.csv，便于进一步分析。
 */
public class PathTest extends JFrame {

    private static final int W = 1100, H = 620;
    private static final long DURATION_MS = 20000;

    private final List<float[]> samples = new ArrayList<>();  // t, cx, cy, tx
    private final long startNs = System.nanoTime();
    private float targetX, targetY;
    private boolean running = true;
    private final Timer timer;
    private final JLabel status = new JLabel("目标点出现后，用手机让光标跟着它跑");
    private final long[] pauseWindows = new long[0];

    public PathTest() {
        super("跟随测试 · 用手机控制光标");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout());
        setFocusableWindowState(false);

        CanvasPanel canvas = new CanvasPanel();
        add(canvas, BorderLayout.CENTER);

        status.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 16));
        status.setBorder(BorderFactory.createEmptyBorder(10, 16, 10, 16));
        add(status, BorderLayout.SOUTH);

        setSize(W, H);
        setLocationRelativeTo(null);

        timer = new Timer(16, e -> tick());
        timer.start();
    }

    private void tick() {
        long t = (System.nanoTime() - startNs) / 1_000_000L;
        if (t > DURATION_MS) {
            if (running) finish();
            return;
        }
        // 目标点：水平来回，速度约 260px/s，两端各停 400ms
        double period = 4000.0;
        double phase = (t % period) / period;
        double ease;
        if (phase < 0.4) {
            ease = phase / 0.4;          // 去程
        } else if (phase < 0.5) {
            ease = 1;                     // 右端停
        } else if (phase < 0.9) {
            ease = 1 - (phase - 0.5) / 0.4; // 回程
        } else {
            ease = 0;                     // 左端停
        }
        float margin = 90;
        targetX = (float) (margin + ease * (W - 2 * margin));
        targetY = H / 2f;

        PointerInfo pi = MouseInfo.getPointerInfo();
        if (pi != null) {
            Point p = pi.getLocation();
            Point local = new Point(p.x - getX() - getInsets().left,
                    p.y - getY() - getInsets().top);
            samples.add(new float[]{t, local.x, local.y, targetX});
        }
        repaint();
    }

    private void finish() {
        running = false;
        timer.stop();

        double sumEx = 0, sumEy = 0, sumJitter = 0;
        int n = samples.size();
        int jitterN = 0;
        int overshoot = 0;
        float lastSign = 0;
        for (float[] s : samples) {
            sumEx += Math.abs(s[1] - s[3]);
            sumEy += Math.abs(s[2] - targetY);
        }
        // 抖动：相邻帧移动量的绝对值的平均（越小越顺）
        for (int i = 1; i < n; i++) {
            float dx = samples.get(i)[1] - samples.get(i - 1)[1];
            float dy = samples.get(i)[2] - samples.get(i - 1)[2];
            sumJitter += Math.hypot(dx, dy);
            jitterN++;
        }
        for (int i = 1; i < n; i++) {
            float d = samples.get(i)[1] - samples.get(i)[3];
            float sign = Math.signum(d);
            if (Math.abs(d) > 40 && sign != 0 && lastSign != 0 && sign != lastSign) overshoot++;
            if (sign != 0) lastSign = sign;
        }

        double ex = n == 0 ? 0 : sumEx / n;
        double ey = n == 0 ? 0 : sumEy / n;
        double jit = jitterN == 0 ? 0 : sumJitter / jitterN;

        String summary = String.format(Locale.US,
                "横向平均误差 %.1f px ｜ 纵向平均误差 %.1f px ｜ 平均帧间位移 %.2f px ｜ 超调 %d 次",
                ex, ey, jit, overshoot);

        try (PrintWriter w = new PrintWriter(new File("_track.csv"), "UTF-8")) {
            w.println("t_ms,cursor_x,cursor_y,target_x");
            for (float[] s : samples) {
                w.printf(Locale.US, "%.0f,%.1f,%.1f,%.1f%n", s[0], s[1], s[2], s[3]);
            }
        } catch (Exception ignored) {
        }

        status.setText(summary);
        JOptionPane.showMessageDialog(this, summary + "\n\n数据已保存到 _track.csv",
                "测试完成", JOptionPane.INFORMATION_MESSAGE);
    }

    private class CanvasPanel extends JPanel {
        CanvasPanel() {
            setBackground(new Color(0x0E1116));
        }

        @Override
        protected void paintComponent(Graphics g0) {
            super.paintComponent(g0);
            Graphics2D g = (Graphics2D) g0;
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            // 轨道
            g.setColor(new Color(0x2A3340));
            g.setStroke(new BasicStroke(3));
            g.drawLine(90, getHeight() / 2, getWidth() - 90, getHeight() / 2);

            // 目标
            g.setColor(new Color(0x4C6FFF));
            g.fillOval((int) targetX - 14, (int) targetY - 14, 28, 28);

            // 光标轨迹
            g.setColor(new Color(0x33FFFFFF));
            g.setStroke(new BasicStroke(1.5f));
            int prevX = -1, prevY = -1;
            for (int i = 0; i < samples.size(); i += 2) {
                float[] s = samples.get(i);
                int x = (int) s[1], y = (int) s[2];
                if (prevX >= 0) g.drawLine(prevX, prevY, x, y);
                prevX = x;
                prevY = y;
            }

            // 当前光标
            PointerInfo pi = MouseInfo.getPointerInfo();
            if (pi != null) {
                Point p = pi.getLocation();
                g.setColor(new Color(0xFF5C7A));
                g.fillOval(p.x - getX() - 8, p.y - getY() - 8, 16, 16);
            }

            long left = Math.max(0, DURATION_MS - (System.nanoTime() - startNs) / 1_000_000L);
            g.setColor(Color.WHITE);
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 20));
            g.drawString(String.format(Locale.US, "剩余 %.1f 秒", left / 1000.0), 24, 40);
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new PathTest().setVisible(true));
    }
}
