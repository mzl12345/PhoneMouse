package golden;

import java.util.ArrayList;
import java.util.List;
import phonemouse.AppLog;
import phonemouse.input.InputTarget;
import phonemouse.keymap.ButtonDispatcher;
import phonemouse.keymap.ButtonMap;
import phonemouse.keymap.CustomKeys;

/**
 * 黄金表导出器：把 {@link ButtonMap} + {@link ButtonDispatcher} 的实际行为打印成机器可读的表，
 * 供 {@code pc/test/compare-golden.ps1} 与 {@code docs/GOLDEN_BUTTONS.tsv} 逐行比对。
 *
 * <p>不碰真实鼠标键盘，不读真实配置，不写日志文件 —— 输出必须完全确定。
 *
 * <p>输出格式（TAB 分隔）：
 * <pre>
 * T &lt;id&gt; &lt;down 调用&gt; &lt;up 调用&gt; &lt;flash&gt;     -- 按键表
 * C &lt;id&gt; &lt;调用&gt;                        -- 自定义键路由（固定桩配置）
 * </pre>
 */
public final class GoldenDump {

    /** 记录所有输入调用的假设备。 */
    private static final class Fake implements InputTarget {
        final List<String> calls = new ArrayList<>();

        @Override public void moveBy(int dx, int dy) { calls.add("move:" + dx + "," + dy); }
        @Override public void click(String which) { calls.add("click:" + which); }
        @Override public void hold(String which, boolean down) { calls.add("hold:" + which + ":" + down); }
        @Override public void wheel(int amount) { calls.add("wheel:" + amount); }
        @Override public void combo(String combo) { calls.add("combo:" + combo); }
        @Override public void text(String text) { calls.add("text:" + text); }
        @Override public void volume(boolean up) { calls.add("volume:" + (up ? "up" : "down")); }
        @Override public void media(String cmd) { calls.add("media:" + cmd); }
    }

    private static final AppLog LOG = new AppLog(s -> { }, false);

    public static void main(String[] args) {
        // ---- 1. 按键表：空的配置，保证确定 ----
        List<String> flashes = new ArrayList<>();
        for (int id : ButtonMap.ids()) {
            Fake fake = new Fake();
            flashes.clear();
            ButtonDispatcher d = new ButtonDispatcher(fake, LOG, flashes::add, new CustomKeys());

            d.press(id, true);
            String down = String.join(";", fake.calls);
            fake.calls.clear();

            d.press(id, false);
            String up = String.join(";", fake.calls);

            System.out.println("T\t" + id + "\t" + down + "\t" + up + "\t" + String.join(";", flashes));
        }

        // ---- 2. 自定义键路由：固定桩，覆盖 whl / clk / key / 无效 ----
        CustomKeys stub = new CustomKeys();
        stub.set("21", "key CTRL+V");
        stub.set("22", "whl -1");
        stub.set("23", "clk R");
        stub.set("24", "nonsense");
        for (int id = 21; id <= 24; id++) {
            Fake fake = new Fake();
            ButtonDispatcher d = new ButtonDispatcher(fake, LOG, c -> { }, stub);
            d.press(id, true);
            System.out.println("C\t" + id + "\t" + String.join(";", fake.calls));
        }
    }
}
