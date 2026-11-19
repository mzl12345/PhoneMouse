package phonemouse.keymap;

import java.util.function.Consumer;
import phonemouse.AppLog;
import phonemouse.input.InputTarget;

/**
 * 把「按键编号」翻译成「输入动作」并执行。
 *
 * <p>注意：所有动作都<b>只在按下时</b>触发（up 不做事），这是 v1.6 的既有语义 ——
 * 滚轮、媒体、音量都靠这个避免重复触发。
 */
public final class ButtonDispatcher {

    private final InputTarget target;
    private final AppLog log;
    private final Consumer<String> flash;
    private final CustomKeys custom;

    /** 拖拽（id 3）的按住状态。 */
    private volatile boolean dragOn = false;

    public ButtonDispatcher(InputTarget target, AppLog log,
                            Consumer<String> flash, CustomKeys custom) {
        this.target = target;
        this.log = log;
        this.flash = flash;
        this.custom = custom;
    }

    public void press(int id, boolean down) {
        if (down) log.log("按键 " + id);
        Action a = ButtonMap.of(id);
        if (a == null) {
            log.log("未配置的按键：" + id);
            return;
        }
        if (!down) return;

        if (a.flash() != null && flash != null) flash.accept(a.flash());

        switch (a.kind()) {
            case CLICK -> target.click(a.arg());
            case DRAG_TOGGLE -> {
                dragOn = !dragOn;
                target.hold("L", dragOn);
                log.log(dragOn ? "拖拽开始" : "拖拽结束");
            }
            case WHEEL -> target.wheel(Integer.parseInt(a.arg()));
            case COMBO -> target.combo(a.arg());
            case VOLUME -> target.volume("up".equals(a.arg()));
            case MEDIA -> target.media(a.arg());
            case CUSTOM -> runCustom(Integer.parseInt(a.arg()));
        }
    }

    private void runCustom(int id) {
        String act = custom.get(String.valueOf(id), "");
        if (act.trim().isEmpty()) {
            log.log("自定义键 " + (id - 20) + " 还没设置（点上方「自定义键」配置）");
            return;
        }
        execAction(act);
    }

    /** 执行一个自定义动作，如 {@code key CTRL+C} / {@code whl -1} / {@code clk L} */
    private void execAction(String act) {
        String a = act.trim();
        String lower = a.toLowerCase();
        if (lower.startsWith("whl")) {
            String[] parts = a.split("\\s+");
            int n = parts.length > 1 ? parseIntSafe(parts[1]) : 1;
            target.wheel(n >= 0 ? 1 : -1);
            log.log("自定义 → 滚轮 " + n);
            return;
        }
        if (lower.startsWith("clk")) {
            String[] parts = a.split("\\s+");
            String which = parts.length > 1 ? parts[1] : "L";
            target.click(which);
            log.log("自定义 → 点击 " + which);
            return;
        }
        if (lower.startsWith("key ")) {
            String combo = a.substring(4).trim();
            log.log("自定义 → 按键 " + combo);
            target.combo(combo);
            return;
        }
        log.log("自定义动作无法识别：" + act);
    }

    private static int parseIntSafe(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            return 1;
        }
    }
}
