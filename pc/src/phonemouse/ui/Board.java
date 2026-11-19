package phonemouse.ui;

import java.util.LinkedHashMap;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.GridLayout;

/**
 * 按键自检板：14 个键的矩阵。
 *
 * <p>它不是「卡片墙」，是一块键盘 —— 等宽等高是这块板的物理属性，
 * 用来一眼看出**哪个键还没被摸过**。点亮只有一次短促的强调，不做循环动画。
 */
public final class Board extends JPanel {

    private final Map<String, KeyCell> cells = new LinkedHashMap<>();
    private final JPanel grid = new JPanel(new GridLayout(3, 5, 8, 8));

    public Board() {
        setOpaque(false);
        setLayout(new BorderLayout(0, 12));

        JLabel heading = new JLabel("按键自检");
        heading.setFont(Ui.TITLE);
        heading.setForeground(Ui.INK);
        heading.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
        add(heading, BorderLayout.NORTH);

        grid.setOpaque(false);
        add(grid, BorderLayout.CENTER);

        add("左键", "clk L");
        add("右键", "clk R");
        add("中键", "clk M");
        add("滚轮上", "whl -1");
        add("滚轮下", "whl 1");
        add("回车", "key ENTER");
        add("Esc", "key ESC");
        add("复制", "key CTRL+C");
        add("粘贴", "key CTRL+V");
        add("撤销", "key CTRL+Z");
        add("自定义1", "key CUSTOM1");
        add("自定义2", "key CUSTOM2");
        add("自定义3", "key CUSTOM3");
        add("自定义4", "key CUSTOM4");

        // 第 15 格：清零计数，让这块板有收尾而不是留个空洞
        FlatButton reset = new FlatButton("清零", FlatButton.Kind.GHOST);
        reset.setPreferredSize(new java.awt.Dimension(104, 58));
        reset.addActionListener(e -> resetAll());
        grid.add(reset);
    }

    private void resetAll() {
        for (KeyCell c : cells.values()) c.reset();
    }

    private void add(String label, String code) {
        KeyCell cell = new KeyCell(label, code);
        cells.put(code, cell);
        grid.add(cell);
    }

    /** 点亮一个键并在 200ms 后回落（可安全地从任意线程调用）。 */
    public void flash(String code) {
        javax.swing.SwingUtilities.invokeLater(() -> {
            KeyCell cell = cells.get(code);
            if (cell == null) return;
            cell.hit();
            Timer t = new Timer(200, e -> cell.unlight());
            t.setRepeats(false);
            t.start();
        });
    }

    /** 只给截图/演示用：让某个键保持点亮，不自动回落。 */
    public void forceLit(String code) {
        javax.swing.SwingUtilities.invokeLater(() -> {
            KeyCell cell = cells.get(code);
            if (cell == null) return;
            cell.hit();
        });
    }
}
