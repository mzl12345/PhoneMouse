package phonemouse.ui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import javax.swing.JComponent;

/** 一颗状态灯：唯一用颜色说话的地方。 */
public final class Led extends JComponent {

    private Color color = Ui.INK_DIM;

    public Led() {
        setPreferredSize(new Dimension(10, 10));
        setMinimumSize(new Dimension(10, 10));
        setMaximumSize(new Dimension(10, 10));
    }

    public void setColor(Color c) {
        color = c;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        Ui.antialias(g2);
        g2.setColor(color);
        g2.fillOval(0, 0, getWidth() - 1, getHeight() - 1);
        g2.dispose();
    }
}
