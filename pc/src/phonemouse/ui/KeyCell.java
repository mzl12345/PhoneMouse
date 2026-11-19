package phonemouse.ui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import javax.swing.JComponent;

/**
 * 自检板上的一个键。
 *
 * <p>刻字标签 + 一次点亮。点亮时用强调色，200ms 后回落到「已测 N 次」的暗态 ——
 * 让人一眼看出哪些键还没被摸过。
 */
public final class KeyCell extends JComponent {

    private final String label;
    private final String code;
    private int hits = 0;
    private boolean lit = false;

    public KeyCell(String label, String code) {
        this.label = label;
        this.code = code;
        setPreferredSize(new Dimension(104, 58));
    }

    public String code() {
        return code;
    }

    public void hit() {
        hits++;
        lit = true;
        repaint();
    }

    public void unlight() {
        lit = false;
        repaint();
    }

    public void reset() {
        hits = 0;
        lit = false;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        Ui.antialias(g2);
        int w = getWidth();
        int h = getHeight();
        int r = 9;

        Color fill = lit ? Ui.LIVE : Ui.KEY;
        Color border = lit ? Ui.LIVE : Ui.LINE;
        Color ink = lit ? Ui.ON_LIVE : Ui.INK;
        Color sub = lit ? Ui.ON_LIVE : Ui.INK_DIM;

        // 与手机端同一套键帽语汇：底缘一层 + 顶高光一条 + 一圈线
        int lip = 3;
        g2.setColor(lit ? Ui.LIVE : Ui.KEY_BOTTOM);
        g2.fillRoundRect(0, lip, w - 1, h - 1, r, r);

        g2.setColor(fill);
        g2.fillRoundRect(0, 0, w - 1, h - 1 - lip, r, r);

        g2.setColor(lit ? new Color(255, 255, 255, 60) : new Color(255, 255, 255, 16));
        g2.drawLine(r, 1, w - r, 1);

        g2.setColor(border);
        g2.drawRoundRect(0, 0, w - 1, h - 1 - lip, r, r);

        g2.setFont(Ui.LABEL);
        g2.setColor(ink);
        var fm = g2.getFontMetrics();
        int tx = (w - fm.stringWidth(label)) / 2;
        int baseline = (h - lip) / 2 + fm.getAscent() / 2 - 6;
        g2.drawString(label, tx, baseline);

        g2.setFont(Ui.MONO);
        g2.setColor(sub);
        String s = hits == 0 ? "未测" : (hits + " 次");
        var fm2 = g2.getFontMetrics();
        g2.drawString(s, (w - fm2.stringWidth(s)) / 2, baseline + 20);

        g2.dispose();
    }
}
