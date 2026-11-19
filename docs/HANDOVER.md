# 交接文档（给下一个会话 / 下一个开发者）

> 本文件是「手机空中鼠标」项目的**完整交接**。读完这一份，就能接着干活，不需要翻聊天记录。
>
> 生成时间：2026-11-19
> 当前版本：v1.6（最终锁定版）
> 锁定快照：`D:\Projects\PhoneMouse\_releases\v1.6-final-locked-2026-11-19\`

---

## 一、这个项目是什么

用手机控制电脑的鼠标和键盘，走**局域网 UDP**，不经过任何服务器。

- **电脑端**：Java + Swing，打包成免安装 exe（自带精简 JRE）
- **手机端**：原生 Android（无 AndroidX，纯 Activity + 代码布局 + Canvas 自绘）

真实用途：躺床上/连电视/做演示时，手机当空中鼠标。

---

## 二、目录结构

```
D:\Projects\PhoneMouse\
├─ android\                        手机端工程（Gradle）
│   └─ app\src\main\
│       ├─ java\com\mzl\phonemouse\
│       │   ├─ MainActivity.java        主界面 + 网络 + 传感器（被锁）
│       │   ├─ BoardView.java           整屏自绘：所有按键的绘制与命中
│       │   ├─ SettingsActivity.java    设置页（手感/连接/关于/导出日志）
│       │   ├─ CalibrationActivity.java 拇指区域校准（画一圈记录舒适区）
│       │   ├─ Diag.java                本地日志（写 Android/data/.../diag.log）
│       │   ├─ ThumbPadView.java        旧弧形键盘（已被 BoardView 取代，保留备用）
│       │   ├─ DPadView.java            旧圆盘（同上）
│       │   └─ ScrollBarView.java       旧滚动条（同上）
│       ├─ res\                         图标（自适应）、少量 drawable
│       └─ AndroidManifest.xml
├─ pc\                             电脑端
│   ├─ src\PcReceiver.java             主程序（Swing）
│   ├─ TypeText.cs / .exe              Unicode 文字注入（中文安全）
│   ├─ KeySend.cs  / .exe              SendInput 按键注入（Esc 用）
│   ├─ VolumeSet.cs/ .exe              Core Audio API 直调音量
│   ├─ MediaKey.cs / .exe              WM_APPCOMMAND 广播（切歌/播放暂停）
│   ├─ PathTest.java                   跟随测试（测延迟/抖动）
│   └─ TraceTest.java                  全屏描线测试（测精度）
├─ docs\                           文档（USAGE/ARCHITECTURE/PROTOCOL/BUILD/LAYOUT/SENSOR_LOCK/CHANGELOG/HANDOVER）
├─ _releases\                      版本节点 v1.0 → v1.6
├─ _简洁包\手机空中鼠标-简洁版.zip     发朋友的包
├─ _发布包\手机空中鼠标-v1.0.zip       完整包
├─ _dist\                           jpackage 产物
└─ _keystore\phonemouse.jks        签名密钥（密码 (见本地 keystore.properties)，alias phonemouse）
```

---

## 三、通信协议（重要，重写时必须保持兼容或同步改两端）

UDP，端口 **51234**，UTF-8 文本，一行一条：

| 消息 | 方向 | 说明 |
|---|---|---|
| `ping` / `pong` | 双向 | 发现与心跳 |
| `hello <base64>` | 手机→电脑 | 上报机型 |
| `mv <dx> <dy>` | 手机→电脑 | 相对移动（像素） |
| `btn <id> <down\|up>` | 手机→电脑 | 按键按下/抬起 |
| `txt <base64>` | 手机→电脑 | 文字（电脑端用 TypeText.exe 注入） |
| `whl <±1>` | 手机→电脑 | 滚轮 |
| `clk L\|R\|M` | 手机→电脑 | 点击（旧协议，仍兼容） |

### 按键编号表

| id | 功能 | id | 功能 |
|---|---|---|---|
| 1 | 左键 | 13 | 确定（Enter） |
| 2 | 右键 | 16 | 定中（手机本地，不发电脑） |
| 3 | 拖拽（切换按住） | 17 | 音量+ |
| 7 | Esc | 18 | 音量− |
| 8 | 复制 Ctrl+C | 19 | 上一曲 |
| 9/10/11/12 | 上/下/左/右方向键 | 20 | 下一曲 |
| 21/22/23/24 | 自定1~4（电脑端可配） | 25 | 播放/暂停 |

---

## 四、传感器算法（这段是"焊死"的核心，重写时建议原样移植）

文件：`MainActivity.java` 中 `// SENSOR-CORE-BEGIN` 到 `// SENSOR-CORE-END`（约 130 行）

### 演进过程（为什么现在这么写）

| 版本 | 做法 | 结果 |
|---|---|---|
| 初版 | 倾角映射速度 | 放平就狂飘 |
| v1 | 增量式 + 慢 EMA，阈值 2°/s | 零偏 >2°/s 时永远不校准 |
| v2 | 30 帧平均 | 平均值混入动作 → 假零偏 |
| v3 | 48 帧中位数，极差 <3° | 手抖让极差超标 → 停止更新 → 温漂 |
| **v4（现在）** | **200 帧中位数 + P90-P10 <8° 判定 + 3 点中值滤波 + 软门限 2.5~7°/s** | 静止 20 秒 0 位移，长时间使用不漂 |

关键点：
1. 手抖是**零均值**的，中位数能抓到真实零偏
2. 用**分位差**而非极差判断"是否在动"，一两个尖峰不影响判定
3. 窗口 200 帧（约 2 秒），热漂移能被持续跟踪
4. **定中键（id 16）**：按住期间丢弃陀螺仪数据，让用户把手机转回舒服姿势，松手从新姿势继续（等于抬起鼠标）

### 绝对不要做的改动

- 不要把零偏估计改回"固定阈值 + 简单平均"
- 不要去掉中值滤波（单帧尖峰会甩飞光标）
- **任何改动后必须跑静止测试：手机放桌上 20 秒，`mv` 包应为 0**

---

## 五、电脑端的几个坑（都踩过了，别再踩）

### 1. 合成按键在这台机器上被拦截

`Robot` / `SendInput` / `keybd_event` 全部无效（命令行直接跑 KeySend.exe 有效，但由 javaw 启动的子进程无效）。

因此：

| 功能 | 现在的做法 |
|---|---|
| 音量 | **Core Audio API**（VolumeSet.exe）直接读写主音量 |
| 切歌 / 播放暂停 | **WM_APPCOMMAND 单次广播**（MediaKey.exe）+ 400ms 防抖 |
| Esc | SendInput（这条还能用，按住 150ms） |

**教训**：媒体键**只能发一次**。早期同时用 4 条通道（前台/广播/桌面/keybd_event），播放器收到 4 遍 → 切一首变切 4 首、暂停抽风。

### 2. 鼠标移动必须"合并刷新"

手机以 60Hz 发 `mv`，电脑端 `Robot.mouseMove` 每次 1–5ms，直接排队会积压 → 延迟越来越大、"往左按了它还在补往右"。

现在做法：`pendX/pendY` 累加，每 8ms 一次性应用（单次限幅 ±200px）。

### 3. 窗口不能抢焦点

`setFocusableWindowState(false)` + `setAutoRequestFocus(false)`，否则 Ctrl+V 会粘到自己窗口里。

### 4. 启动时释放卡住的鼠标键

`releaseStuckButtons()`，防止拖拽状态残留导致光标一直拖窗口。

---

## 六、手机端 UI 架构（重写的重点）

`BoardView` 是**整屏自绘**：所有按键都是 `els` 列表里的数据（id/类型/中心/宽高/文字），按 1440×3168 的设计坐标等比缩放到实际屏幕。

```java
els.add(el(100, CIRCLE, 163, 152, 172, 172, "开"));
els.add(el(104, DPAD,   697, 1047, 700, 700, ""));   // 圆盘
els.add(el(0,   SPLIT,  1027, 2291, 352, 352, ""));  // 左右半圆
```

- 改布局 = 改这张表里的数字（完整坐标见 `docs/LAYOUT.md`）
- 绘制有统一的「浮起」效果：多层半透明投影 + 面层高光
- 背景：三段渐变（蓝→紫→粉）+ 5 个彩色光斑
- 圆盘是四层堆叠 + 投影

### 已实现的其他 UI 特性

- 圆盘双模式：方向 / 媒体（右上「切换」），中心键在媒体模式下变「播放/暂停」
- 左/右是两个半圆，中间留缝
- 滚轮条：滑块跟手拖动，**松手弹回中间**，每划 46px 触发一格
- 全屏输入：点输入框后隐藏所有按键，只留大文本框（学微信）
- 底部输入条用 `addOnLayoutChangeListener` 贴住可见区底部，键盘弹出时自动上移（`adjustResize`）

---


---

## 六点五、辅助程序清单（4 个 exe，路径与用途）

电脑端有四件事没法用纯 Java 做（或做了不可靠），所以拆成 4 个独立的小程序，由主程序按需调用。

### 文件绝对路径

| 程序 | 源码 | 编译产物 | 用途 |
|---|---|---|---|
| `TypeText.exe` | `D:\Projects\PhoneMouse\pc\TypeText.cs` | `D:\Projects\PhoneMouse\pc\TypeText.exe` | Unicode 文字注入，中文不撞输入法 |
| `KeySend.exe` | `D:\Projects\PhoneMouse\pc\KeySend.cs` | `D:\Projects\PhoneMouse\pc\KeySend.exe` | SendInput 注入按键（Esc 专用） |
| `VolumeSet.exe` | `D:\Projects\PhoneMouse\pc\VolumeSet.cs` | `D:\Projects\PhoneMouse\pc\VolumeSet.exe` | Core Audio API 直接读写主音量 |
| `MediaKey.exe` | `D:\Projects\PhoneMouse\pc\MediaKey.cs` | `D:\Projects\PhoneMouse\pc\MediaKey.exe` | WM_APPCOMMAND 广播（切歌/播放暂停） |

> 另有 `BtBridge.exe`（`pc\BtBridge.cs`），是蓝牙 SPP 的备份方案，**当前版本没用到**，可以不打包。

### 编译命令（都用这台机器自带的 csc）

```bat
cd /d D:\Projects\PhoneMouse\pc
set CSC=C:\Windows\Microsoft.NET\Framework64\v4.0.30319\csc.exe

%CSC% /nologo /optimize /out:TypeText.exe  TypeText.cs
%CSC% /nologo /optimize /out:KeySend.exe   KeySend.cs
%CSC% /nologo /optimize /out:VolumeSet.exe VolumeSet.cs
%CSC% /nologo /optimize /out:MediaKey.exe  MediaKey.cs
```

### 运行时怎么找到它们（helperPath）

`PcReceiver.helperPath(String name)` 按顺序找：

1. 当前工作目录下同名文件
2. **jar 所在目录**（`out\` 或打包后的 `app\`）
3. jar 目录的上一级（jpackage 的 `PhoneMouse\` 根目录）
4. 都没有 → 返回当前目录路径（调用时会报错并写日志）

### 打包时必须放的位置

```
_pkg\input\          ← jpackage 的 --input 目录
├─ PhoneMouseReceiver.jar
├─ TypeText.exe
├─ KeySend.exe
├─ VolumeSet.exe
└─ MediaKey.exe
```

jpackage 会把这些文件一起复制到 `_dist\PhoneMouse\app\`，程序运行时靠 `helperPath()` 找到它们。

### 单独验证某个 exe 是否正常

```bat
cd /d D:\Projects\PhoneMouse\pc
VolumeSet.exe info            :: 打印当前音量，如 level=42 mute=False
VolumeSet.exe up 5            :: 加 5%
MediaKey.exe play             :: 播放/暂停
KeySend.exe vk 27 0 150       :: 按一下 Esc
KeySend.exe scan 30 40        :: 音量+（扫描码方式）
```
## 七、构建与打包

### 手机端

```bat
set JAVA_HOME=D:\Tools\jdk-17
set ANDROID_HOME=D:\Android\Sdk
D:\Android\gradle-8.7\bin\gradle.bat -p D:\Projects\PhoneMouse\android assembleRelease --no-daemon --console=plain
```

产物：`android\app\build\outputs\apk\release\app-release.apk`

签名：`_keystore\phonemouse.jks`（密码 `(见本地 keystore.properties)`，alias `phonemouse`）
**以后的版本必须用同一个 keystore，否则无法覆盖安装。**

### 电脑端

```bat
D:\Tools\jdk-17\bin\javac.exe -encoding UTF-8 -d out src\PcReceiver.java
D:\Tools\jdk-17\bin\jar.exe --create --file PhoneMouseReceiver.jar --main-class PcReceiver -C out .
```

### 免安装包（jpackage + jlink）

```bat
jlink --add-modules java.base,java.desktop,java.logging --output _rt ^
      --strip-debug --no-man-pages --no-header-files --compress=2

jpackage --type app-image --input _pkg\input --main-jar PhoneMouseReceiver.jar ^
         --main-class PcReceiver --name PhoneMouse --dest _dist --runtime-image _rt
```

注意：jar 必须放在 `--input` 目录里，否则 jpackage 报错。
`_pkg\input` 里要同时放 4 个 exe（KeySend/VolumeSet/MediaKey/TypeText）。

---

## 八、日志（排查问题的唯一手段）

| 端 | 位置 | 入口 |
|---|---|---|
| 电脑 | `%LOCALAPPDATA%\PhoneMouse\receiver.log` | 界面「一键导出日志」→ 打包到桌面 |
| 手机 | `Android/data/com.mzl.phonemouse/files/diag.log` | 设置 → 关于 → 「导出运行日志」→ 存到「下载」 |

手机日志标签：`APP` / `NET` / `KEY` / `GYRO`（每 5 秒记零偏与姿态）/ `CTRL`

---

## 九、环境信息（这台机器）

| 项 | 值 |
|---|---|
| JDK | `D:\Tools\jdk-17` |
| Android SDK | `D:\Android\Sdk`（platform-tools、build-tools 34.0.0） |
| Gradle | `D:\Android\gradle-8.7` |
| csc（C# 编译） | `C:\Windows\Microsoft.NET\Framework64\v4.0.30319\csc.exe` |
| 手机 | OnePlus PJD110，Android 17，serial `<手机序列号>` |
| 电脑 IP | 192.168.1.100（以太网） |
| UDP 端口 | 51234 |

**工具约束**：用户要求软件装到 D 盘，不要装 C 盘。

---

## 十、重写时要守住的底线

1. **协议不能变**（除非两端同步改）
2. **传感器算法原样移植**，并跑静止测试（20 秒 0 包）
3. **鼠标移动必须合并刷新**，不能逐包执行
4. **媒体键只发一次**，必须带防抖
5. **音量走 API**，不要退回按键注入
6. **签名不变**，否则无法覆盖安装
7. **日志位置不变**，否则用户不知道怎么导
8. 电脑端的 `helperPath()` 逻辑要保留（打包后要能找到那 4 个 exe）

---

## 十一、还可以做的事（下一阶段）

- 前端重写（推荐用 `frontend-design` / `impeccable` skill）：现在的 UI 是自绘 Canvas，改起来是改坐标表；如果想换成声明式布局 + 动效，是合理的重写理由
- 自定义键的用户界面（现在只有 PC 端一个对话框填命令）
- 蓝牙 SPP 链路（v2 计划，备份在 `_backup\v2-bluetooth-spp\`）
- 多设备记忆（记住多台电脑，一键切换）
- 语音输入（当前只有文字）
