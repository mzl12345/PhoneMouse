package phonemouse.input;

import java.awt.Robot;
import java.awt.event.InputEvent;
import java.util.concurrent.ExecutorService;
import phonemouse.AppLog;

/**
 * {@link InputTarget} 的真实实现：把语义动作落到 {@code java.awt.Robot} 与三个辅助 exe 上。
 *
 * <p>所有「会阻塞或需要顺序」的动作都丢进单线程 {@code inputExec}，
 * 保证按键交替不会错序（v1.6 就是这么做的）。
 */
public final class RobotInput implements InputTarget {

    private final Robot robot;
    private final ExecutorService exec;
    private final MouseMover mover;
    private final KeyInjector keys;
    private final VolumeControl volume;
    private final MediaControl media;

    public RobotInput(Robot robot, AppLog log, ExecutorService exec) {
        this.robot = robot;
        this.exec = exec;
        this.mover = new MouseMover(robot);
        this.keys = new KeyInjector(robot, log, exec);
        this.volume = new VolumeControl(log);
        this.media = new MediaControl(log);
    }

    public MouseMover mover() {
        return mover;
    }

    public KeyInjector keys() {
        return keys;
    }

    @Override
    public void moveBy(int dx, int dy) {
        mover.add(dx, dy);
    }

    @Override
    public void click(String which) {
        int mask = mask(which);
        exec.execute(() -> {
            robot.mousePress(mask);
            robot.mouseRelease(mask);
        });
    }

    @Override
    public void hold(String which, boolean down) {
        int mask = mask(which);
        exec.execute(() -> {
            if (down) robot.mousePress(mask);
            else robot.mouseRelease(mask);
        });
    }

    @Override
    public void wheel(int amount) {
        exec.execute(() -> robot.mouseWheel(amount));
    }

    @Override
    public void combo(String combo) {
        keys.combo(combo);
    }

    @Override
    public void text(String text) {
        keys.text(text);
    }

    @Override
    public void volume(boolean up) {
        volume.step(up);
    }

    @Override
    public void media(String cmd) {
        media.send(cmd);
    }

    private static int mask(String which) {
        switch (which == null ? "L" : which.trim().toUpperCase()) {
            case "R": return InputEvent.BUTTON3_DOWN_MASK;
            case "M": return InputEvent.BUTTON2_DOWN_MASK;
            default:  return InputEvent.BUTTON1_DOWN_MASK;
        }
    }
}
