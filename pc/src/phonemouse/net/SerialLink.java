package phonemouse.net;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import phonemouse.AppLog;

/**
 * 蓝牙 SPP 备用链路（当前版本的备份方案，默认自动探测、连不上就走 UDP）。
 *
 * <p>行为与 v1.6 一致：扫注册表找 COM 口 → {@code mode COMx} 设 115200 8N1 →
 * 用 {@link RandomAccessFile} 直接读写 → 按 {@code \n} 切行交给上层。
 */
public final class SerialLink {

    private final AppLog log;
    private final LineSink sink;

    private volatile OutputStream out;
    private volatile boolean connected;
    private RandomAccessFile raf;

    public interface LineSink {
        void onLine(String line);
    }

    public SerialLink(AppLog log, LineSink sink) {
        this.log = log;
        this.sink = sink;
    }

    public boolean isConnected() {
        return connected;
    }

    /** 写一行；未连接返回 false，调用方回退到 UDP。 */
    public boolean send(String msg) {
        OutputStream so = out;
        if (so == null) return false;
        try {
            so.write((msg + "\n").getBytes(StandardCharsets.UTF_8));
            so.flush();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** 后台自动连接蓝牙串口（若存在）。 */
    public void start() {
        if (connected) return;
        new Thread(() -> {
            List<String> ports = listComPorts();
            if (ports.isEmpty()) {
                log.log("没有蓝牙串口（走 Wi-Fi / 蓝牙共享网络也完全可以）");
                return;
            }
            for (String p : ports) {
                if (connected) return;
                try {
                    Runtime.getRuntime().exec(new String[]{"cmd", "/c",
                            "mode " + p + ": BAUD=115200 PARITY=n DATA=8 STOP=1"}).waitFor();
                    raf = new RandomAccessFile("\\\\.\\" + p, "rw");
                    out = new RafOutputStream(raf);
                    connected = true;
                    log.log("蓝牙串口已连接：" + p);
                    read(raf);
                } catch (Exception ignored) {
                }
            }
        }, "bt-serial").start();
    }

    private void read(RandomAccessFile file) {
        byte[] buf = new byte[4096];
        StringBuilder sb = new StringBuilder();
        try {
            int n;
            while ((n = file.read(buf)) >= 0) {
                sb.append(new String(buf, 0, n, StandardCharsets.UTF_8));
                int idx;
                while ((idx = sb.indexOf("\n")) >= 0) {
                    String line = sb.substring(0, idx).trim();
                    sb.delete(0, idx + 1);
                    if (!line.isEmpty()) sink.onLine(line);
                }
            }
        } catch (Exception ex) {
            log.log("蓝牙串口断开：" + ex.getMessage());
        } finally {
            connected = false;
            out = null;
        }
    }

    private static List<String> listComPorts() {
        List<String> ports = new ArrayList<>();
        try {
            Process p = new ProcessBuilder("reg", "query", "HKLM\\HARDWARE\\DEVICEMAP\\SERIALCOMM")
                    .redirectErrorStream(true).start();
            BufferedReader r = new BufferedReader(
                    new InputStreamReader(p.getInputStream(), Charset.defaultCharset()));
            String line;
            Pattern pat = Pattern.compile("(COM\\d+)");
            while ((line = r.readLine()) != null) {
                Matcher m = pat.matcher(line);
                if (m.find() && !ports.contains(m.group(1))) ports.add(m.group(1));
            }
            r.close();
        } catch (Exception ignored) {
        }
        return ports;
    }

    /** 把 RandomAccessFile 包成 OutputStream，用于串口写回 */
    private static final class RafOutputStream extends OutputStream {
        private final RandomAccessFile raf;

        RafOutputStream(RandomAccessFile raf) {
            this.raf = raf;
        }

        @Override
        public void write(int b) throws java.io.IOException {
            raf.write(b);
        }

        @Override
        public void write(byte[] b, int off, int len) throws java.io.IOException {
            raf.write(b, off, len);
        }
    }
}
