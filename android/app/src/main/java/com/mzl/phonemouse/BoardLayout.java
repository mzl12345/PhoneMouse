package com.mzl.phonemouse;

import java.util.ArrayList;
import java.util.List;

/**
 * 界面坐标表（设计基准 1440 × 3168）。
 *
 * <p><b>v2.3 重排</b>：v2.0 的元素是半径各异（150/129/176）的圆，斜着撒在屏幕上，
 * 没有对齐关系，所以看起来"别扭"。现在改成四条横带 + 4 列栅格：
 *
 * <pre>
 *   栏距 M=50, 沟槽 G=40, 列宽 W=305
 *   C1 50..355   C2 395..700   C3 740..1045   C4 1085..1390
 *
 *   ① 状态条        y   0..240    开 · 名称/状态 · 主题 · 设置
 *   ② 主控          y 260..1460   方向/媒体盘（占 C1–C3）· 切换 · 滚动条（占 C4）
 *   ③ 鼠标键        y1500..1720   左 | 右（一根宽条，像笔记本触控板的按键）
 *   ④ 键盘          y1780..2700   4 列 × 3 行，键宽 305 高 280
 *   ⑤ 输入条        由 MainActivity 贴底
 * </pre>
 *
 * <p>所有元素都落在栅格上 —— 这是"看起来像是设计过"和"看起来像是摆上去"的区别。
 */
public final class BoardLayout {

    public static final float DESIGN_W = 1440f;
    public static final float DESIGN_H = 3168f;

    // 形状
    public static final int CIRCLE = 0;
    public static final int PILL   = 1;
    public static final int RECT   = 2;
    public static final int SPLIT  = 3;
    public static final int DPAD   = 4;
    public static final int SCROLL = 5;

    public static final class El {
        public final int id;
        public final int type;
        public final float cx, cy, w, h;
        public final String label;

        El(int id, int type, float cx, float cy, float w, float h, String label) {
            this.id = id;
            this.type = type;
            this.cx = cx;
            this.cy = cy;
            this.w = w;
            this.h = h;
            this.label = label;
        }
    }

    // 栅格
    public static final float MARGIN = 50f;
    public static final float GUTTER = 40f;
    private static final float[] COL = {202f, 547f, 892f, 1237f};   // 4 列中心
    private static final float KEY_W = 305f;
    private static final float KEY_H = 280f;
    private static final float[] ROW = {1920f, 2240f, 2560f};       // 3 行中心

    private static final List<El> ELS = new ArrayList<>();

    static {
        // ---------------- ① 状态条 ----------------
        ELS.add(new El(100, CIRCLE, 130, 120, 160, 160, "开"));
        ELS.add(new El(101, PILL, 640, 120, 660, 180, ""));
        ELS.add(new El(200, CIRCLE, 1160, 120, 120, 120, "主题"));   // 本地：切深浅色
        ELS.add(new El(102, CIRCLE, 1330, 120, 120, 120, "设置"));

        // ---------------- ② 主控 ----------------
        ELS.add(new El(103, RECT, 1237, 330, 220, 140, "切换"));     // 方向 / 媒体
        ELS.add(new El(104, DPAD, 547, 870, 995, 995, ""));
        ELS.add(new El(105, SCROLL, 1237, 940, 220, 1000, ""));

        // ---------------- ③ 鼠标键（一根宽条） ----------------
        ELS.add(new El(0, SPLIT, 720, 1610, 1340, 220, ""));

        // ---------------- ④ 键盘 4×3 ----------------
        ELS.add(new El(7,  RECT, COL[0], ROW[0], KEY_W, KEY_H, "Esc"));
        ELS.add(new El(13, RECT, COL[1], ROW[0], KEY_W, KEY_H, "回车"));
        ELS.add(new El(8,  RECT, COL[2], ROW[0], KEY_W, KEY_H, "复制"));
        ELS.add(new El(27, RECT, COL[3], ROW[0], KEY_W, KEY_H, "粘贴"));

        ELS.add(new El(26, RECT, COL[0], ROW[1], KEY_W, KEY_H, "中键"));
        ELS.add(new El(3,  RECT, COL[1], ROW[1], KEY_W, KEY_H, "拖拽"));
        ELS.add(new El(28, RECT, COL[2], ROW[1], KEY_W, KEY_H, "撤销"));
        ELS.add(new El(16, RECT, COL[3], ROW[1], KEY_W, KEY_H, "定中"));

        ELS.add(new El(21, RECT, COL[0], ROW[2], KEY_W, KEY_H, "自定1"));
        ELS.add(new El(22, RECT, COL[1], ROW[2], KEY_W, KEY_H, "自定2"));
        ELS.add(new El(23, RECT, COL[2], ROW[2], KEY_W, KEY_H, "自定3"));
        ELS.add(new El(24, RECT, COL[3], ROW[2], KEY_W, KEY_H, "自定4"));

        // ---------------- ⑤ 单手开关 ----------------
        // 放在右下角（右手拇指根部）：放在状态条上的话，正常握持根本够不到，
        // 「单手模式」就要用另一只手才能开，等于白做。
        ELS.add(new El(201, CIRCLE, 1320, 2790, 180, 180, "单手"));
    }

    private BoardLayout() {
    }

    // ---------------------------------------------------------------- 单手模式
    //
    // 系统级单手模式是把整屏等比缩到角落，那是系统做不到重排的妥协。
    // 我们自己在软件里，直接给一套「右下角面板」：只留单手够得着、又常用的键，
    // 其余（Esc / 回车 / 复制 / 粘贴 / 撤销 / 自定1-4）不参与布局。
    //
    // 这是一套独立坐标系（1000 × 1250），由 BoardView 整体缩放并贴到右下角。

    public static final float COMPACT_W = 1000f;
    public static final float COMPACT_H = 1720f;

    private static final List<El> COMPACT = new ArrayList<>();

    static {
        // 顶行：低频操作放上面（那里最难够，反正也不用常点）
        COMPACT.add(new El(100, CIRCLE, 100, 90, 110, 110, "开"));
        COMPACT.add(new El(101, PILL, 545, 90, 480, 140, ""));
        COMPACT.add(new El(102, CIRCLE, 895, 90, 110, 110, "设置"));

        // 主控：整体往上让出下面那条弧
        COMPACT.add(new El(104, DPAD, 430, 560, 640, 640, ""));
        COMPACT.add(new El(105, SCROLL, 880, 560, 160, 600, ""));

        // 弧：以右下角「单手」键为圆心，半径 520，从竖直方向 75° 扫到 15°。
        //   定中 (75°，最靠左最低) → 右键 (45°) → 左键 (15°，最高)
        // 键取 250×170：屏幕换算下来约 70×48dp，刚好落在触摸下限之上。
        COMPACT.add(new El(16, RECT, 413, 1485, 250, 170, "定中"));
        COMPACT.add(new El(2, RECT, 547, 1252, 250, 170, "右键"));
        COMPACT.add(new El(1, RECT, 780, 1118, 250, 170, "左键"));

        // 圆心本体：单手开关，压在最够得着的右下角
        COMPACT.add(new El(201, CIRCLE, 915, 1620, 150, 150, "单手"));
    }

    /** 单手模式的面板元素（坐标系 1000 × 1250）。 */
    public static List<El> compact() {
        return COMPACT;
    }

    public static List<El> all() {
        return ELS;
    }

    public static El scroll() {
        for (El e : ELS) if (e.type == SCROLL) return e;
        return null;
    }

    /** 会随状态换字的标签（都显示「当前状态」，不显示「点了会变成什么」）。 */
    public static String labelOf(El e, boolean mouseOn, boolean mediaMode) {
        if (e.id == 100) return mouseOn ? "开" : "关";
        if (e.id == 103) return mediaMode ? "媒体" : "方向";
        if (e.id == 200) return Theme.dark ? "深色" : "浅色";
        if (e.id == 201) return "单手";
        if (e.id == 16) return "定中";
        return e.label;
    }

    /** 手机本地事件，不上网。 */
    public static boolean isLocal(int id) {
        return (id >= 100 && id <= 105) || id == 200 || id == 201
                || id == 204 || id == 205 || id == 16;
    }
}
