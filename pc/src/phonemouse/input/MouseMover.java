package phonemouse.input;

import java.awt.Dimension;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.PointerInfo;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Toolkit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.Timer;

/**
 * 光标位移的「合并刷新」层。
 *
 * <p>手机以约 60Hz 发 {@code mv}，而 {@code Robot.mouseMove} 每次要 1–5ms；
 * 逐包执行会排队积压，表现为「往左按了它还在补往右」。
 * 因此这里只做累加，每 8ms 把累计值一次性应用，单次限幅 ±200px。
 *
 * <p>定时器用 {@code javax.swing.Timer}，回调发生在 EDT —— 与 v1.6 行为一致。
 */
public final class MouseMover {

    private static final int FLUSH_MS = 8;
    private static final int MAX_STEP = 200;

    private final Robot robot;
    private final AtomicInteger pendX = new AtomicInteger();
    private final AtomicInteger pendY = new AtomicInteger();
    private Timer timer;

    public MouseMover(Robot robot) {
        this.robot = robot;
    }

    /** 起一个 8ms 的合并刷新定时器。 */
    public void start() {
        if (timer != null) return;
        timer = new Timer(FLUSH_MS, e -> flush());
        timer.start();
    }

    public void stop() {
        if (timer != null) {
            timer.stop();
            timer = null;
        }
    }

    /** 累加一个位移，不直接动鼠标。 */
    public void add(int dx, int dy) {
        if (dx == 0 && dy == 0) return;
        pendX.addAndGet(dx);
        pendY.addAndGet(dy);
    }

    /** 每 8ms 把累计位移一次性应用：只跟最新位置，永不积压 */
    private void flush() {
        int dx = pendX.getAndSet(0);
        int dy = pendY.getAndSet(0);
        if (dx == 0 && dy == 0) return;
        // 单次最多 200px，防止极端跳变
        dx = Math.max(-MAX_STEP, Math.min(MAX_STEP, dx));
        dy = Math.max(-MAX_STEP, Math.min(MAX_STEP, dy));
        moveRelative(dx, dy);
    }

    // 多显示器：按所有屏幕的并集钳制，否则副屏（尤其主屏左侧/上侧）上光标会被挡住。
    private void moveRelative(int dx, int dy) {
        PointerInfo pi = MouseInfo.getPointerInfo();
        if (pi == null) return;
        Point cur = pi.getLocation();
        Rectangle bounds = virtualBounds();
        int nx = Math.max(bounds.x, Math.min(bounds.x + bounds.width - 1, cur.x + dx));
        int ny = Math.max(bounds.y, Math.min(bounds.y + bounds.height - 1, cur.y + dy));
        robot.mouseMove(nx, ny);
    }

    /** 所有屏幕的并集；取不到时退回主屏尺寸。 */
    private static Rectangle virtualBounds() {
        Rectangle union = new Rectangle();
        for (java.awt.GraphicsDevice gd :
                java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices()) {
            union = union.union(gd.getDefaultConfiguration().getBounds());
        }
        if (union.isEmpty()) {
            Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
            union = new Rectangle(0, 0, screen.width, screen.height);
        }
        return union;
    }

    /** 启动时清掉可能卡住的鼠标按键，避免光标变成「拖拽」状态 */
    public static void releaseStuckButtons(Robot robot) {
        try {
            robot.mouseRelease(java.awt.event.InputEvent.BUTTON1_DOWN_MASK);
            robot.mouseRelease(java.awt.event.InputEvent.BUTTON2_DOWN_MASK);
            robot.mouseRelease(java.awt.event.InputEvent.BUTTON3_DOWN_MASK);
        } catch (Exception ignored) {
        }
    }
}
