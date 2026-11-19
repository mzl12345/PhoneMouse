package phonemouse.input;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import phonemouse.AppLog;
import phonemouse.HelperPaths;

/**
 * 主音量控制。
 *
 * <p><b>必须走 Core Audio API</b>（{@code VolumeSet.exe}），不能退回按键注入 ——
 * 这台机器上合成多媒体按键被封了，按键方式调不动系统音量。
 */
public final class VolumeControl {

    private static final String STEP = "4";

    private final AppLog log;

    public VolumeControl(AppLog log) {
        this.log = log;
    }

    /** @param up true 加、false 减 */
    public void step(boolean up) {
        String cmd = up ? "up" : "down";
        try {
            String exe = HelperPaths.of("VolumeSet.exe");
            Process p = new ProcessBuilder(exe, cmd, STEP)
                    .redirectErrorStream(true).start();
            BufferedReader r = new BufferedReader(
                    new InputStreamReader(p.getInputStream(), "GBK"));
            String line = r.readLine();
            r.close();
            log.log("音量" + cmd + " → " + (line == null ? "?" : line.trim()));
        } catch (Exception e) {
            log.log("音量控制失败：" + e.getMessage());
        }
    }
}
