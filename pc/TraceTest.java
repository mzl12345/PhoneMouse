import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 全屏描线测试：屏幕上有矩形边框、十字中线、两条对角线。
 * 用鼠标（手机控制）沿着线走，程序记录轨迹并统计偏差。
 * 按 ESC 结束并输出结果。
 */
public class TraceTest extends JFrame {

    private final List<int[]> lines = new ArrayList<>();   // x1,y1,x2,y2
    private final List<float[]> samples = new ArrayList<>(); // t, x, y
    private final long startNs = System.nanoTime();
    private boolean finished = false;

    public TraceTest() {
        super("描线测试");
        setUndecorated(true);
        setExtendedState(JFrame.MAXIMIZED_BOTH);
        setBackground(new Color(0x10141C));
        setAlwaysOnTop(false);

        add(new TracePanel(), BorderLayout.CENTER);

        Timer t = new Timer(16, e -> {
            if (finished) return;
            Point p = MouseInfo.getPointerInfo().getLocation();
            Point local = new Point(p.x - getX(), p.y - getY());
            samples.add(new float[]{(System.nanoTime() - startNs) / 1_000_000f, local.x, local.y});
            repaint();
        });
        t.start();

        // ESC 结束
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "finish");
        getRootPane().getActionMap().put("finish", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                finish();
            }
        });
    }

    /** 生成图形：矩形 + 十字 + 对角线 */
    private void buildShapes(int w, int h) {
        lines.clear();
        int m = Math.min(w, h) / 8;
        int x1 = m, y1 = m, x2 = w - m, y2 = h - m;
        // 矩形
        lines.add(new int[]{x1, y1, x2, y1});
        lines.add(new int[]{x2, y1, x2, y2});
        lines.add(new int[]{x2, y2, x1, y2});
        lines.add(new int[]{x1, y2, x1, y1});
        // 十字
        int cx = w / 2, cy = h / 2;
        lines.add(new int[]{cx, y1, cx, y2});
        lines.add(new int[]{x1, cy, x2, cy});
        // 对角线
        lines.add(new int[]{x1, y1, x2, y2});
        lines.add(new int[]{x1, y2, x2, y1});
    }

    private static double distToSeg(double px, double py, int[] s) {
        double x1 = s[0], y1 = s[1], x2 = s[2], y2 = s[3];
        double dx = x2 - x1, dy = y2 - y1;
        double len2 = dx * dx + dy * dy;
        double t = len2 == 0 ? 0 : ((px - x1) * dx + (py - y1) * dy) / len2;
        t = Math.max(0, Math.min(1, t));
        double qx = x1 + t * dx, qy = y1 + t * dy;
        return Math.hypot(px - qx, py - qy);
    }

    private void finish() {
        if (finished) return;
        finished = true;

        double sum = 0, max = 0, jitter = 0;
        int within20 = 0, n = samples.size();
        for (int i = 0; i < n; i++) {
            float[] p = samples.get(i);
            double best = Double.MAX_VALUE;
            for (int[] s : lines) {
                best = Math.min(best, distToSeg(p[1], p[2], s));
            }
            sum += best;
            max = Math.max(max, best);
            if (best <= 20) within20++;
            if (i > 0) {
                float[] q = samples.get(i - 1);
                jitter += Math.hypot(p[1] - q[1], p[2] - q[2]);
            }
        }
        double avg = n == 0 ? 0 : sum / n;
        double jit = n <= 1 ? 0 : jitter / (n - 1);
        double pct = n == 0 ? 0 : within20 * 100.0 / n;

        String summary = String.format(Locale.US,
                "平均偏差 %.1f px ｜ 最大偏差 %.0f px ｜ 20px 内占比 %.0f%% ｜ 帧间位移 %.2f px ｜ 采样 %d",
                avg, max, pct, jit, n);

        try (PrintWriter w = new PrintWriter(new File("_trace.csv"), "UTF-8")) {
            w.println("t_ms,x,y");
            for (float[] s : samples) w.printf(Locale.US, "%.0f,%.0f,%.0f%n", s[0], s[1], s[2]);
        } catch (Exception ignored) {
        }

        JOptionPane.showMessageDialog(this, summary + "\n\n数据已保存到 _trace.csv",
                "描线结果", JOptionPane.INFORMATION_MESSAGE);
        dispose();
        System.exit(0);
    }

    private class TracePanel extends JPanel {
        TracePanel() {
            setBackground(new Color(0x10141C));
        }

        @Override
        protected void paintComponent(Graphics g0) {
            super.paintComponent(g0);
            Graphics2D g = (Graphics2D) g0;
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            if (lines.isEmpty()) buildShapes(getWidth(), getHeight());

            // 目标线
            g.setStroke(new BasicStroke(3));
            g.setColor(new Color(0x3A4557));
            for (int[] s : lines) g.drawLine(s[0], s[1], s[2], s[3]);

            // 轨迹
            g.setColor(new Color(0x664C6FFF));
            g.setStroke(new BasicStroke(2f));
            int px = -1, py = -1;
            for (int i = 0; i < samples.size(); i += 2) {
                float[] s = samples.get(i);
                if (px >= 0) g.drawLine(px, py, (int) s[1], (int) s[2]);
                px = (int) s[1];
                py = (int) s[2];
            }

            // 提示
            g.setColor(new Color(0xFF, 0xFF, 0xFF, 200));
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 22));
            g.drawString("沿着灰色线走一圈，画完按 ESC 出结果", 40, 52);

            // 光标
            PointerInfo pi = MouseInfo.getPointerInfo();
            if (pi != null) {
                Point p = pi.getLocation();
                g.setColor(new Color(0xFF5C7A));
                g.fillOval(p.x - getX() - 7, p.y - getY() - 7, 14, 14);
            }
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new TraceTest().setVisible(true));
    }
}
