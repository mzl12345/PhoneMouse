package phonemouse.keymap;

/**
 * 一个按键编号对应的动作描述。
 *
 * @param kind  动作类型
 * @param arg   动作参数（语义值，不是 AWT 常量，便于比对与测试）
 * @param flash 触发时要点亮哪个测试方块；{@code null} 表示没有对应方块
 */
public record Action(Kind kind, String arg, String flash) {

    public enum Kind {
        /** 完整点击，arg = L / R / M */
        CLICK,
        /** 拖拽开关 */
        DRAG_TOGGLE,
        /** 滚轮，arg = -1 / 1 */
        WHEEL,
        /** 组合键，arg = 如 CTRL+C */
        COMBO,
        /** 音量，arg = up / down */
        VOLUME,
        /** 媒体，arg = prev / next / play */
        MEDIA,
        /** 自定义键，arg = 按键 id */
        CUSTOM
    }
}
