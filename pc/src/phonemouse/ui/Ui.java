package phonemouse.ui;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/**
 * 视觉 token 的唯一来源。
 *
 * <p>方向「仪器面板」：把窗口当一台硬件来画 —— 连续机身、刻字标签、一颗状态灯。
 * 深色不是口味，是使用场景：这个软件绝大多数时候在**关灯的卧室**里被用，
 * 亮底界面等于手电筒。但也保留浅色，白天/投屏时更合适。
 *
 * <p>规则（改代码前先读）：
 * <ul>
 *   <li>只有一个强调色 {@link #LIVE}，只表示「活着 / 刚被按下」，不做装饰</li>
 *   <li>层次只声明一次：要么描边、要么明度差，不叠假投影</li>
 *   <li>等宽字体只给读数（IP / 计数 / 日志），不给正文</li>
 * </ul>
 */
public final class Ui {

    private Ui() {
    }

    // ---- 深色（默认）----
    public static Color BG         = new Color(0x1A, 0x1D, 0x22);
    public static Color SURFACE    = new Color(0x22, 0x26, 0x2D);
    public static Color KEY        = new Color(0x2A, 0x2F, 0x38);
    public static Color KEY_BOTTOM = new Color(0x1B, 0x1F, 0x25);
    public static Color KEY_DOWN   = new Color(0x36, 0x3C, 0x48);
    public static Color INK        = new Color(0xED, 0xEF, 0xF3);
    public static Color INK_DIM    = new Color(0x9A, 0xA2, 0xB0);
    public static Color LIVE       = new Color(0xFF, 0xB3, 0x24);
    public static Color ON_LIVE    = new Color(0x1A, 0x1D, 0x22);
    public static Color ALERT      = new Color(0xE5, 0x54, 0x4B);
    public static Color LINE       = new Color(0x33, 0x39, 0x45);

    public static boolean dark = true;

    public static Font LABEL  = new Font("Microsoft YaHei UI", Font.PLAIN, 13);
    public static Font STRONG = new Font("Microsoft YaHei UI", Font.BOLD, 13);
    public static Font SMALL  = new Font("Microsoft YaHei UI", Font.PLAIN, 11);
    public static Font TITLE  = new Font("Microsoft YaHei UI", Font.BOLD, 15);
    // 逻辑等宽字体：拉丁字符等宽，中文靠复合字体回退（用 Consolas 会显示成方块）
    public static Font MONO   = new Font(Font.MONOSPACED, Font.PLAIN, 12);

    /** 切换主题。必须在构建界面之前调用。 */
    public static void useDark() {
        dark = true;
        BG        = new Color(0x1A, 0x1D, 0x22);
        SURFACE   = new Color(0x22, 0x26, 0x2D);
        KEY       = new Color(0x2A, 0x2F, 0x38);
        KEY_BOTTOM= new Color(0x1B, 0x1F, 0x25);
        KEY_DOWN  = new Color(0x36, 0x3C, 0x48);
        INK       = new Color(0xED, 0xEF, 0xF3);
        INK_DIM   = new Color(0x9A, 0xA2, 0xB0);
        LIVE      = new Color(0xFF, 0xB3, 0x24);
        ON_LIVE   = new Color(0x1A, 0x1D, 0x22);
        ALERT     = new Color(0xE5, 0x54, 0x4B);
        LINE      = new Color(0x33, 0x39, 0x45);
    }

    /** 浅色：磨砂白瓷。同一个语汇，白天的样子。 */
    public static void useLight() {
        dark = false;
        BG        = new Color(0xEC, 0xEC, 0xE7);
        SURFACE   = new Color(0xFF, 0xFF, 0xFF);
        KEY       = new Color(0xFF, 0xFF, 0xFF);
        KEY_DOWN  = new Color(0xE2, 0xE2, 0xDC);
        INK       = new Color(0x16, 0x18, 0x1B);
        INK_DIM   = new Color(0x5F, 0x66, 0x72);
        LIVE      = new Color(0xB4, 0x6A, 0x00);
        ON_LIVE   = new Color(0xFF, 0xFF, 0xFF);
        ALERT     = new Color(0xB3, 0x2D, 0x22);
        LINE      = new Color(0xD3, 0xD3, 0xCB);
    }

    public static void antialias(Graphics2D g2) {
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,
                RenderingHints.VALUE_STROKE_PURE);
    }
}
