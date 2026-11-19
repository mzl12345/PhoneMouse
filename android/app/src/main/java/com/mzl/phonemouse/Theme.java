package com.mzl.phonemouse;

/**
 * 视觉 token 的唯一来源。
 *
 * <p>方向「仪器面板」：把手机屏幕当一台硬件的面板来画 —— 连续机身、键帽、
 * 刻字、一颗灯。深色不是口味：这软件的真实场景是**关灯的卧室**，
 * 亮底界面等于拿手电照眼睛。
 *
 * <p>三条规则：
 * <ol>
 *   <li>强调色只有一个 {@link #LIVE}，只表示「通着 / 刚按下」，不做装饰</li>
 *   <li>层次只声明一次：键帽用一个底缘 + 一条顶高光，不叠多层投影</li>
 *   <li>文字靠位置和形状识别，句子越少越好</li>
 * </ol>
 */
public final class Theme {

    private Theme() {
    }

    public static boolean dark = true;

    // ---- 深色（默认）----
    public static int BG        = 0xFF171A1F;   // 机身
    public static int RAIL      = 0xFF14171B;   // 顶部状态区（比机身再暗一档）
    public static int KEY       = 0xFF262B33;   // 键面
    public static int KEY_LIP   = 0xFF1A1E24;   // 键帽底缘（厚度的错觉来源）
    public static int KEY_DOWN  = 0xFF323841;   // 按下
    public static int INK       = 0xFFEDEFF3;   // 主文字
    public static int INK_DIM   = 0xFF98A0AE;   // 说明/未激活
    public static int LIVE      = 0xFFFFB324;   // 唯一强调：通着 / 刚按下
    public static int ON_LIVE   = 0xFF171A1F;   // 压在强调色上的字
    public static int LINE      = 0xFF39404B;   // 结构线
    public static int TRACK     = 0xFF1F232A;   // 滚轮轨道

    public static void useDark() {
        dark = true;
        BG       = 0xFF171A1F;
        RAIL     = 0xFF14171B;
        KEY      = 0xFF262B33;
        KEY_LIP  = 0xFF1A1E24;
        KEY_DOWN = 0xFF323841;
        INK      = 0xFFEDEFF3;
        INK_DIM  = 0xFF98A0AE;
        LIVE     = 0xFFFFB324;
        ON_LIVE  = 0xFF171A1F;
        LINE     = 0xFF39404B;
        TRACK    = 0xFF1F232A;
    }

    /** 浅色：磨砂白瓷。同一个语汇，白天的样子。 */
    public static void useLight() {
        dark = false;
        BG       = 0xFFECECE7;
        RAIL     = 0xFFE4E4DE;
        KEY      = 0xFFFFFFFF;
        KEY_LIP  = 0xFFD9D9D2;
        KEY_DOWN = 0xFFF0F0EB;
        INK      = 0xFF16181B;
        INK_DIM  = 0xFF5F6672;
        LIVE     = 0xFFB46A00;
        ON_LIVE  = 0xFFFFFFFF;
        LINE     = 0xFFD3D3CB;
        TRACK    = 0xFFE4E4DE;
    }
}
