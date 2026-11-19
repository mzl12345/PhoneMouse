package phonemouse.input;

/**
 * 输入层对外接口：协议层只认这套语义方法，不碰 {@code java.awt.Robot}。
 *
 * <p>这样按键映射表可以在没有真实鼠标键盘的环境里被测试与比对
 * （见 {@code docs/GOLDEN_BUTTONS.tsv} 的回归方式）。
 */
public interface InputTarget {

    /** 相对移动，累加后由 {@link MouseMover} 合并刷新。 */
    void moveBy(int dx, int dy);

    /** 完整点击（按下并抬起）。{@code which} 取 {@code L} / {@code R} / {@code M}。 */
    void click(String which);

    /** 只按下或只抬起，不配对。{@code which} 同上。拖拽开关也走这里。 */
    void hold(String which, boolean down);

    /** 滚轮，正数向下、负数向上。 */
    void wheel(int amount);

    /** 组合键，如 {@code CTRL+C}、{@code ENTER}。 */
    void combo(String combo);

    /** 文字输入（走 Unicode 注入，失败回退粘贴）。 */
    void text(String text);

    /** 主音量增减一步。 */
    void volume(boolean up);

    /** 媒体控制：{@code prev} / {@code next} / {@code play}，内部带 400ms 防抖。 */
    void media(String cmd);
}
