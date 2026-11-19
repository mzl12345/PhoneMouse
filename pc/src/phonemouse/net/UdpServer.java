package phonemouse.net;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;

/**
 * UDP 监听层：只负责「收字节 → 切行 → 交给上层」与「回 pong」。
 *
 * <p>不解析协议语义、不碰输入，协议路由在 {@code PcReceiver.handle} 里。
 */
public final class UdpServer {

    public interface Listener {
        /** 收到一行非空文本。 */
        void onLine(String line);

        /** 首次看到某个客户端 IP（界面状态用）。 */
        void onNewClient(String ip);

        /** 接收异常（不致命，仅记录）。 */
        void onError(String message);
    }

    private final int port;
    private final Listener listener;

    private volatile DatagramSocket socket;
    private volatile boolean running;
    private volatile InetAddress lastAddr;
    private volatile int lastPort;

    public UdpServer(int port, Listener listener) {
        this.port = port;
        this.listener = listener;
    }

    /** 绑定端口并起监听线程。绑定失败抛 {@link SocketException}，由调用方显示原因。 */
    public void start() throws IOException {
        DatagramSocket s = new DatagramSocket(port);
        s.setReceiveBufferSize(256 * 1024);
        socket = s;
        running = true;
        Thread t = new Thread(this::loop, "udp-listener");
        t.setDaemon(true);
        t.start();
    }

    public void stop() {
        running = false;
        DatagramSocket s = socket;
        socket = null;
        if (s != null) s.close();
    }

    public boolean isRunning() {
        return running;
    }

    /** 上一次来包的客户端 IP；还没有包时返回 {@code "?"}。 */
    public String lastClientIp() {
        InetAddress a = lastAddr;
        return a == null ? "?" : a.getHostAddress();
    }

    /** 向上一次来包的地址回一条文本（{@code pong} 用）。 */
    public void reply(String msg) {
        DatagramSocket s = socket;
        InetAddress addr = lastAddr;
        if (s == null || addr == null) return;
        try {
            byte[] out = msg.getBytes(StandardCharsets.UTF_8);
            s.send(new DatagramPacket(out, out.length, addr, lastPort));
        } catch (Exception ignored) {
        }
    }

    private void loop() {
        byte[] buf = new byte[8192];
        String lastClient = "";
        while (running) {
            try {
                DatagramSocket s = socket;
                if (s == null) break;
                DatagramPacket p = new DatagramPacket(buf, buf.length);
                s.receive(p);
                String line = new String(p.getData(), 0, p.getLength(), StandardCharsets.UTF_8).trim();
                if (line.isEmpty()) continue;
                lastAddr = p.getAddress();
                lastPort = p.getPort();
                String from = p.getAddress().getHostAddress();
                if (!from.equals(lastClient)) {
                    lastClient = from;
                    listener.onNewClient(from);
                }
                listener.onLine(line);
            } catch (SocketException ex) {
                break;
            } catch (IOException ex) {
                if (running) listener.onError("接收异常：" + ex.getMessage());
            }
        }
    }
}
