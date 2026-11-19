package phonemouse.proto;

/**
 * 单行协议解析。行为与 v1.6 的 {@code PcReceiver.handle()} 开头完全一致：
 * 以第一个空格切分，指令名不转小写，参数整体 trim。
 */
public final class Protocol {

    private Protocol() {
    }

    /** @return 解析结果；空行返回 {@code null}（调用方跳过） */
    public static Command parse(String line) {
        if (line == null) return null;
        String s = line.trim();
        if (s.isEmpty()) return null;
        int sp = s.indexOf(' ');
        if (sp < 0) return new Command(s, "");
        return new Command(s.substring(0, sp), s.substring(sp + 1).trim());
    }
}
