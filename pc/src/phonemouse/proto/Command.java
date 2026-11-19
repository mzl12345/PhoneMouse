package phonemouse.proto;

/**
 * 一条已解析的协议指令：{@code kind} 为指令名，{@code arg} 为其后全部参数（已 trim）。
 *
 * <p>例：{@code "btn 21 down"} → {@code Command("btn", "21 down")}。
 */
public record Command(String kind, String arg) {
}
