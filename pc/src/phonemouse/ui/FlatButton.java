package phonemouse.ui;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.JButton;

/**
 * 一个安静的按钮：圆角、描边、无投影。悬停时只提亮一点，按下时用强调色。
 *
 * <p>刻意不用 Swing 默认外观 —— 默认按钮的浮雕边和系统配色跟这套语汇不搭。
 */
public final class FlatButton extends JButton {

    public enum Kind { PRIMARY, SECONDARY, GHOST }

    private final Kind kind;
    private boolean hover;

    public FlatButton(String text, Kind kind) {
        super(text);
        this.kind = kind;
        setOpaque(false);
        setContentAreaFilled(false);
        setBorderPainted(false);
        setFocusPainted(false);
        setRolloverEnabled(true);
        setFont(Ui.STRONG);
        setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
        addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) {
                hover = true;
                repaint();
            }

            @Override public void mouseExited(MouseEvent e) {
                hover = false;
                repaint();
            }
        });
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        Ui.antialias(g2);
        int w = getWidth();
        int h = getHeight();
        int r = 8;

        boolean pressed = getModel().isPressed() && isEnabled();
        Color fill;
        Color border;
        Color fg;

        if (!isEnabled()) {
            fill = Ui.BG;
            border = Ui.LINE;
            fg = Ui.INK_DIM;
        } else if (kind == Kind.PRIMARY) {
            fill = pressed ? Ui.LIVE.darker() : (hover ? Ui.LIVE.brighter() : Ui.LIVE);
            border = fill;
            fg = Ui.ON_LIVE;
        } else if (kind == Kind.SECONDARY) {
            fill = pressed ? Ui.KEY_DOWN : (hover ? Ui.SURFACE : Ui.BG);
            border = hover ? Ui.INK_DIM : Ui.LINE;
            fg = Ui.INK;
        } else {
            fill = pressed ? Ui.KEY_DOWN : (hover ? Ui.SURFACE : Ui.BG);
            border = hover ? Ui.INK_DIM : Ui.LINE;
            fg = Ui.INK_DIM;
        }

        g2.setColor(fill);
        g2.fillRoundRect(0, 0, w - 1, h - 1, r, r);
        g2.setColor(border);
        g2.drawRoundRect(0, 0, w - 1, h - 1, r, r);

        g2.setFont(getFont());
        g2.setColor(fg);
        var fm = g2.getFontMetrics();
        String t = getText();
        int tx = (w - fm.stringWidth(t)) / 2;
        int ty = (h - fm.getHeight()) / 2 + fm.getAscent();
        g2.drawString(t, tx, ty);
        g2.dispose();
    }
}
