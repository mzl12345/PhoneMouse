package phonemouse.keymap;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.util.Properties;
import phonemouse.AppLog;

/**
 * 自定义键（手机「自定 1–4」↔ id 21–24）的读写。
 *
 * <p><b>位置变更说明</b>：v1.6 把 {@code buttons.properties} 写在进程的当前工作目录，
 * 而 jpackage 打包后工作目录由快捷方式决定，配置大概率存不住。
 * 现在固定写到 {@code %LOCALAPPDATA%\PhoneMouse\}（与日志同目录，日志位置本身未变），
 * 并**兼容读取**旧的工作目录文件：首次读到就自动迁移过来。
 */
public final class CustomKeys {

    private final Properties props = new Properties();

    /** 新位置。 */
    public static File file() {
        return new File(AppLog.dir(), "buttons.properties");
    }

    /** 旧位置（v1.6 的工作目录写入点），只读。 */
    public static File legacyFile() {
        return new File("buttons.properties");
    }

    public void load(AppLog log) {
        File f = file();
        boolean migrating = false;
        if (!f.exists()) {
            File legacy = legacyFile();
            if (!legacy.exists()) return;
            f = legacy;
            migrating = true;
        }
        try (Reader r = new InputStreamReader(new FileInputStream(f), "UTF-8")) {
            props.load(r);
            if (migrating) {
                log.log("自定义键配置已从旧位置迁移到 " + file());
                save(log);
            }
        } catch (Exception e) {
            log.log("读取自定义键失败：" + e.getMessage());
        }
    }

    public void save(AppLog log) {
        try (Writer w = new OutputStreamWriter(new FileOutputStream(file()), "UTF-8")) {
            props.store(w, "custom keys 21-24");
            log.log("自定义键已保存");
        } catch (Exception e) {
            log.log("保存失败：" + e.getMessage());
        }
    }

    public String get(String key, String def) {
        return props.getProperty(key, def);
    }

    public void set(String key, String value) {
        props.setProperty(key, value);
    }

    public void remove(String key) {
        props.remove(key);
    }
}
