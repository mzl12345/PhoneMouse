package phonemouse;

import java.io.File;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.function.Consumer;

/**
 * 双写日志：标准输出 + 日志文件 + 界面。
 *
 * <p>日志位置**不可更改**：{@code %LOCALAPPDATA%\PhoneMouse\receiver.log}，
 * 超过 2MB 自动轮转为 {@code receiver.1.log}。
 */
public final class AppLog {

    private static final SimpleDateFormat FILE_FMT = new SimpleDateFormat("MM-dd HH:mm:ss.SSS");
    private static final SimpleDateFormat UI_FMT = new SimpleDateFormat("HH:mm:ss");

    private final Consumer<String> ui;
    private final boolean persist;

    public AppLog(Consumer<String> ui) {
        this(ui, true);
    }

    /** @param persist false 时只打到标准输出与界面，不写日志文件（供离线自检用） */
    public AppLog(Consumer<String> ui, boolean persist) {
        this.ui = ui;
        this.persist = persist;
    }

    /** 日志目录：%LOCALAPPDATA%\PhoneMouse（取不到则退回用户目录） */
    public static File dir() {
        String base = System.getenv("LOCALAPPDATA");
        File d = base != null
                ? new File(base, "PhoneMouse")
                : new File(System.getProperty("user.home"), "PhoneMouse");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    public static File logFile() {
        return new File(dir(), "receiver.log");
    }

    public void log(String msg) {
        String t = UI_FMT.format(new Date());
        System.out.println("[" + t + "] " + msg);
        if (persist) appendFile(msg);
        if (ui != null) ui.accept(t + "  " + msg);
    }

    private void appendFile(String msg) {
        try {
            File f = logFile();
            if (f.length() > 2 * 1024 * 1024) {
                File old = new File(dir(), "receiver.1.log");
                if (old.exists()) old.delete();
                f.renameTo(old);
            }
            try (FileWriter w = new FileWriter(f, true)) {
                w.write(FILE_FMT.format(new Date()) + "  " + msg + "\n");
            }
        } catch (Exception ignored) {
        }
    }

    /** 把整个日志目录打包到桌面，返回产物；失败返回 null。 */
    public static File exportZip() {
        try {
            File dir = dir();
            File desktop = desktopDir();
            String name = "手机空中鼠标-日志-"
                    + new SimpleDateFormat("MMdd_HHmm").format(new Date()) + ".zip";
            File out = new File(desktop, name);

            try (java.util.zip.ZipOutputStream zos =
                         new java.util.zip.ZipOutputStream(new java.io.FileOutputStream(out))) {
                File[] files = dir.listFiles();
                if (files != null) {
                    for (File f : files) {
                        if (!f.isFile()) continue;
                        zos.putNextEntry(new java.util.zip.ZipEntry(f.getName()));
                        java.nio.file.Files.copy(f.toPath(), zos);
                        zos.closeEntry();
                    }
                }
            }
            return out;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 真实的桌面目录。
     *
     * <p>不能写死 {@code %USERPROFILE%\Desktop}：桌面被重定向到别的盘（本机是 {@code D:\Desktop}）
     * 或交给 OneDrive 托管时，用户会在「桌面」上看不到导出的文件。
     * 先读注册表的 User Shell Folders，失败再退回默认位置。
     */
    private static File desktopDir() {
        try {
            Process p = new ProcessBuilder("reg", "query",
                    "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\User Shell Folders",
                    "/v", "Desktop").redirectErrorStream(true).start();
            java.io.BufferedReader r = new java.io.BufferedReader(
                    new java.io.InputStreamReader(p.getInputStream(),
                            java.nio.charset.Charset.defaultCharset()));
            String line;
            java.util.regex.Pattern pat =
                    java.util.regex.Pattern.compile("Desktop\\s+REG_\\w+\\s+(.+)");
            while ((line = r.readLine()) != null) {
                java.util.regex.Matcher m = pat.matcher(line);
                if (m.find()) {
                    String v = expandEnv(m.group(1).trim());
                    File f = new File(v);
                    if (f.isDirectory()) {
                        r.close();
                        return f;
                    }
                }
            }
            r.close();
        } catch (Exception ignored) {
        }
        File fallback = new File(System.getProperty("user.home"), "Desktop");
        return fallback.isDirectory() ? fallback : new File(System.getProperty("user.home"));
    }

    /** 展开 {@code %VAR%} 形式的环境变量。 */
    private static String expandEnv(String s) {
        java.util.regex.Matcher m =
                java.util.regex.Pattern.compile("%([^%]+)%").matcher(s);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String v = System.getenv(m.group(1));
            m.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(v == null ? m.group(0) : v));
        }
        m.appendTail(sb);
        return sb.toString();
    }
}
