package phonemouse;

import java.io.File;

/**
 * 定位随程序分发的辅助 exe（TypeText / KeySend / VolumeSet / MediaKey）。
 *
 * <p>查找顺序（与 v1.6 完全一致，打包后也能找到）：
 * <ol>
 *   <li>当前工作目录同名文件</li>
 *   <li>classes/jar 所在目录（jpackage 后是 app\）</li>
 *   <li>上一级目录（jpackage 后是 PhoneMouse\ 根）</li>
 *   <li>都找不到 → 返回当前目录下的预期路径，调用方报错并写日志</li>
 * </ol>
 */
public final class HelperPaths {

    private HelperPaths() {
    }

    public static String of(String name) {
        File f = new File(name);
        if (f.exists()) return f.getAbsolutePath();
        try {
            File code = new File(HelperPaths.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI());
            File dir = code.isDirectory() ? code : code.getParentFile();
            File g = new File(dir, name);
            if (g.exists()) return g.getAbsolutePath();
            File parent = dir.getParentFile();
            if (parent != null) {
                File h = new File(parent, name);
                if (h.exists()) return h.getAbsolutePath();
            }
        } catch (Exception ignored) {
        }
        return f.getAbsolutePath();
    }
}
