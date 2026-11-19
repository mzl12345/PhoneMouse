package phonemouse.keymap;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 按键编号 → 动作的<b>唯一真值表</b>。
 *
 * <p>v1.6 时这段是 {@code PcReceiver.handleButton()} 里一个 120 行的 {@code switch}，
 * 加一个键要动控制流。现在加键 = 加一行。
 *
 * <p>表内容与 v1.6 <b>逐条一致</b>，见 {@code docs/GOLDEN_BUTTONS.tsv}；
 * 改动这里的任何一行都必须同步改协议文档与黄金表。
 */
public final class ButtonMap {

    private static final Map<Integer, Action> MAP = new LinkedHashMap<>();

    private static void def(int id, Action.Kind kind, String arg, String flash) {
        MAP.put(id, new Action(kind, arg, flash));
    }

    static {
        // 鼠标
        def(1,  Action.Kind.CLICK, "L", "clk L");
        def(2,  Action.Kind.CLICK, "R", "clk R");
        def(3,  Action.Kind.DRAG_TOGGLE, "", "clk L");
        // 旧编号：滚轮（已弃用，兼容保留）
        def(4,  Action.Kind.WHEEL, "-1", "whl -1");
        def(5,  Action.Kind.WHEEL, "1", "whl 1");
        // 旧编号：回车（与 13 等价）
        def(6,  Action.Kind.COMBO, "ENTER", "key ENTER");
        // 键盘
        def(7,  Action.Kind.COMBO, "ESC", "key ESC");
        def(8,  Action.Kind.COMBO, "CTRL+C", "key CTRL+C");
        def(9,  Action.Kind.COMBO, "UP", "key UP");
        def(10, Action.Kind.COMBO, "DOWN", "key DOWN");
        def(11, Action.Kind.COMBO, "LEFT", "key LEFT");
        def(12, Action.Kind.COMBO, "RIGHT", "key RIGHT");
        def(13, Action.Kind.COMBO, "ENTER", "key ENTER");
        // 旧编号：音量（与 17/18 等价；这两条不点亮方块）
        def(14, Action.Kind.VOLUME, "up", null);
        def(15, Action.Kind.VOLUME, "down", null);
        // 17/18 是主协议音量
        def(17, Action.Kind.VOLUME, "up", null);
        def(18, Action.Kind.VOLUME, "down", null);
        // 媒体（400ms 防抖在 MediaControl 内部）
        def(19, Action.Kind.MEDIA, "prev", null);
        def(20, Action.Kind.MEDIA, "next", null);
        def(25, Action.Kind.MEDIA, "play", "key PLAY");
        // 自定义
        def(21, Action.Kind.CUSTOM, "21", null);
        def(22, Action.Kind.CUSTOM, "22", null);
        def(23, Action.Kind.CUSTOM, "23", null);
        def(24, Action.Kind.CUSTOM, "24", null);
        // v2.3 新增：手机上补了三颗常用键。电脑端方块本来就有，只是以前没有按键能触发它们。
        def(26, Action.Kind.CLICK, "M", "clk M");
        def(27, Action.Kind.COMBO, "CTRL+V", "key CTRL+V");
        def(28, Action.Kind.COMBO, "CTRL+Z", "key CTRL+Z");
        // 注意：16（定中）由手机端就地消费，从不上网，故此处无条目。
    }

    private ButtonMap() {
    }

    /** @return 该编号的动作；未定义返回 {@code null} */
    public static Action of(int id) {
        return MAP.get(id);
    }

    /** 所有已定义的编号（升序），供自检与文档生成用。 */
    public static Set<Integer> ids() {
        return Collections.unmodifiableSet(MAP.keySet());
    }
}
