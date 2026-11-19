package phonemouse.input;

import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.ExecutorService;
import phonemouse.AppLog;
import phonemouse.HelperPaths;

/**
 * 键盘注入：组合键走 {@code Robot}，文字走 {@code TypeText.exe}（Unicode 注入，不撞输入法）。
 *
 * <p>注意：本机 {@code SendInput} / {@code keybd_event} 被拦截，
 * 但由 javaw 启动的 {@code Robot} 组合键是有效的（Esc / 复制 / 方向键都靠它）。
 */
public final class KeyInjector {

    private final Robot robot;
    private final AppLog log;
    private final ExecutorService exec;

    public KeyInjector(Robot robot, AppLog log, ExecutorService exec) {
        this.robot = robot;
        this.log = log;
        this.exec = exec;
    }

    /** 按下并抬起一组修饰键 + 主键，顺序为先按后放（放的时候反序）。 */
    public void combo(String combo) {
        String[] parts = combo.trim().toUpperCase().split("\\+");
        int[] codes = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            int c = vkOf(parts[i].trim());
            if (c < 0) return;
            codes[i] = c;
        }
        exec.execute(() -> {
            for (int c : codes) robot.keyPress(c);
            for (int i = codes.length - 1; i >= 0; i--) robot.keyRelease(codes[i]);
        });
    }

    /** 单键按下并抬起。 */
    public void pressVk(int vk) {
        exec.execute(() -> {
            robot.keyPress(vk);
            robot.keyRelease(vk);
        });
    }

    /** 文字走 SendInput Unicode（TypeText.exe），不会撞输入法候选词 */
    public void text(String text) {
        if (text == null || text.isEmpty()) return;
        exec.execute(() -> {
            String b64 = Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));
            File exe = new File(HelperPaths.of("TypeText.exe"));
            if (exe.exists()) {
                try {
                    new ProcessBuilder(exe.getAbsolutePath(), b64).start();
                    log.log("键入：" + preview(text));
                    return;
                } catch (IOException e) {
                    log.log("TypeText 调用失败，改走粘贴：" + e.getMessage());
                }
            }
            pasteText(text);
        });
    }

    private void pasteText(String text) {
        Clipboard cb = Toolkit.getDefaultToolkit().getSystemClipboard();
        Transferable old = null;
        try {
            old = cb.getContents(null);
        } catch (IllegalStateException ignored) {
        }
        try {
            cb.setContents(new StringSelection(text), null);
            robot.keyPress(KeyEvent.VK_CONTROL);
            robot.keyPress(KeyEvent.VK_V);
            robot.keyRelease(KeyEvent.VK_V);
            robot.keyRelease(KeyEvent.VK_CONTROL);
        } finally {
            if (old != null) {
                try {
                    cb.setContents(old, null);
                } catch (Exception ignored) {
                }
            }
        }
    }

    private static String preview(String s) {
        return s.length() > 40 ? s.substring(0, 40) + "…" : s;
    }

    // ---------------------------------------------------------------- 备用注入路径
    // v1.6 引入了 KeySend.exe 但没有调用它（Esc 实际走 Robot）。
    // 这两条保留成能力，方便将来 Robot 失效时切换，不影响当前行为。

    /** 用扫描码注入（最接近真实键盘，媒体键首选） */
    public void sendKeyScan(int scanCode, int holdMs) {
        try {
            String exe = HelperPaths.of("KeySend.exe");
            new ProcessBuilder(exe, "scan",
                    Integer.toHexString(scanCode), String.valueOf(holdMs)).start();
        } catch (Exception e) {
            log.log("扫描码注入失败：" + e.getMessage());
        }
    }

    /** 用 SendInput 注入按键（对全屏程序/浏览器更可靠） */
    public void sendKeyExternal(int vk, boolean extended, int holdMs) {
        try {
            String exe = HelperPaths.of("KeySend.exe");
            new ProcessBuilder(exe, String.valueOf(vk),
                    extended ? "1" : "0", String.valueOf(holdMs)).start();
        } catch (Exception e) {
            log.log("外部按键失败：" + e.getMessage());
        }
    }

    /** 键名 → AWT 虚拟键码，未知返回 -1。 */
    public static int vkOf(String name) {
        switch (name) {
            case "CTRL": case "CONTROL": return KeyEvent.VK_CONTROL;
            case "ALT":                 return KeyEvent.VK_ALT;
            case "SHIFT":               return KeyEvent.VK_SHIFT;
            case "WIN":                 return KeyEvent.VK_WINDOWS;
            case "ENTER": case "RETURN":return KeyEvent.VK_ENTER;
            case "ESC": case "ESCAPE":  return KeyEvent.VK_ESCAPE;
            case "TAB":                 return KeyEvent.VK_TAB;
            case "UP":                  return KeyEvent.VK_UP;
            case "DOWN":                return KeyEvent.VK_DOWN;
            case "LEFT":                return KeyEvent.VK_LEFT;
            case "RIGHT":               return KeyEvent.VK_RIGHT;
            case "HOME":                return KeyEvent.VK_HOME;
            case "END":                 return KeyEvent.VK_END;
            case "PAGEUP":              return KeyEvent.VK_PAGE_UP;
            case "PAGEDOWN":            return KeyEvent.VK_PAGE_DOWN;
            case "PLAYPAUSE":           return 0xB3;   // VK_MEDIA_PLAY_PAUSE
            case "VOLUP": case "VOLUMEUP": return 0xAF;   // VK_VOLUME_UP
            case "VOLDOWN": case "VOLUMEDOWN": return 0xAE; // VK_VOLUME_DOWN
            case "SPACE":               return KeyEvent.VK_SPACE;
            case "BACKSPACE":           return KeyEvent.VK_BACK_SPACE;
            case "DELETE": case "DEL":  return KeyEvent.VK_DELETE;
            case "F1": case "F2": case "F3": case "F4": case "F5": case "F6":
            case "F7": case "F8": case "F9": case "F10": case "F11": case "F12":
                return KeyEvent.VK_F1 + Integer.parseInt(name.substring(1)) - 1;
            default:
                if (name.length() == 1) return KeyEvent.getExtendedKeyCodeForChar(name.charAt(0));
                return -1;
        }
    }
}
