package phonemouse.input;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import phonemouse.AppLog;
import phonemouse.HelperPaths;

/**
 * 媒体控制：{@code WM_APPCOMMAND} 单次广播（{@code MediaKey.exe}）+ 400ms 防抖。
 *
 * <p><b>只能发一次。</b>早期同时走 4 条通道，播放器收到 4 遍，
 * 结果切一首变切 4 首、暂停抽风。防抖是硬约束，不要去掉。
 */
public final class MediaControl {

    private static final long DEBOUNCE_MS = 400;

    private final AppLog log;
    private long lastTs = 0;
    private String lastCmd = "";

    public MediaControl(AppLog log) {
        this.log = log;
    }

    /** @param cmd {@code prev} / {@code next} / {@code play} */
    public void send(String cmd) {
        long now = System.currentTimeMillis();
        if (cmd.equals(lastCmd) && now - lastTs < DEBOUNCE_MS) {
            log.log("媒体 " + cmd + " 已忽略（防抖）");
            return;
        }
        lastCmd = cmd;
        lastTs = now;
        try {
            String exe = HelperPaths.of("MediaKey.exe");
            Process p = new ProcessBuilder(exe, cmd).redirectErrorStream(true).start();
            BufferedReader r = new BufferedReader(
                    new InputStreamReader(p.getInputStream(), "GBK"));
            String line = r.readLine();
            r.close();
            log.log("媒体 " + cmd + " → " + (line == null ? "?" : line.trim()));
        } catch (Exception e) {
            log.log("媒体控制失败：" + e.getMessage());
        }
    }
}
