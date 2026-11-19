# 任务：重写「手机空中鼠标」的前端与后端

## 一、先读这些文件（必读，按顺序）

1. `D:\Projects\PhoneMouse\docs\HANDOVER.md`  ← 完整交接文档，先读这个
2. `D:\Projects\PhoneMouse\docs\PROTOCOL.md`   ← 通信协议与按键编号
3. `D:\Projects\PhoneMouse\docs\SENSOR_LOCK.md`← 传感器算法（焊死，勿改）
4. `D:\Projects\PhoneMouse\docs\LAYOUT.md`     ← 现有界面坐标表
5. `D:\Projects\PhoneMouse\_releases\v1.6-final-locked-2026-11-19\LOCK.md`

## 二、项目现状

这是我自己在用、也准备发给朋友测试的小工具：
- 手机当电脑的空中鼠标（陀螺仪控光标）+ 按键面板 + 文字输入
- 手机端：原生 Android（com.mzl.phonemouse），无 AndroidX，纯 Activity + 代码布局 + 一个 Canvas 自绘的 BoardView
- 电脑端：Java Swing（PcReceiver），打包成免安装 exe
- 通信：局域网 UDP 51234，不经服务器
- 全部功能已跑通并通过实机测试，代码冻结在 v1.6 快照

## 三、要你做什么

**重写前端和后端代码**，同时保住下面这些已经验证过的东西。

### 前端（手机端）
- 现在所有按键是 BoardView 里一张坐标表（1440×3168 设计基准等比缩放），改样式要改绘制代码，扩展性差
- 想重做成更好维护、更好看的结构（可以用 frontend-design / impeccable 这类 skill 定视觉方向）
- 现有视觉：渐变背景（蓝→紫→粉）+ 彩色光斑、按键"浮起"质感（多层投影 + 高光面层）、圆盘四层堆叠
- 交互要保持：圆盘双模式（方向/媒体）、左右半圆、滚轮条跟手+松手回弹、全屏输入、定中键

### 后端（电脑端）
- 现在是一个 1000+ 行的 PcReceiver.java，UI、网络、按键映射、媒体控制全在一起
- 想拆分：网络层 / 输入层（鼠标键盘媒体）/ UI 层
- UI 也要重做（现在是很朴素的 Swing 面板）

## 四、绝对不能破坏的（十条底线）

1. **协议不能变**（`docs/PROTOCOL.md`），除非手机端同步改
2. **传感器算法原样移植**（`SENSOR_LOCK.md` 那段），改完必须跑：手机放桌上、鼠标开、电脑端收 20 秒 = 0 个 mv 包
3. **鼠标移动必须合并刷新**（现在每 8ms 应用一次累计位移），不能逐包调 Robot，否则会积压
4. **媒体键只发一次 + 400ms 防抖**（发多次会切 4 首歌、暂停抽风）
5. **音量必须走 Core Audio API**（VolumeSet.exe），不能退回按键注入 —— 这台机器上合成按键被封了
6. **APK 签名不能换**（`_keystore\phonemouse.jks`，密码 (见本地 keystore.properties)，alias phonemouse），否则无法覆盖安装
7. **日志位置不能变**（电脑 `%LOCALAPPDATA%\PhoneMouse\receiver.log`，手机 `Android/data/com.mzl.phonemouse/files/diag.log`）
8. 电脑端 `helperPath()` 逻辑要保留：打包后要能在程序目录找到 4 个 exe
   （TypeText / KeySend / VolumeSet / MediaKey，路径与编译命令见 HANDOVER.md 第六点五节）
9. 电脑窗口必须 `setFocusableWindowState(false)`，不抢焦点
10. 所有软件装 D 盘，不要装 C 盘

## 五、构建环境（这台机器）

```
JDK:      D:\Tools\jdk-17
SDK:      D:\Android\Sdk
Gradle:   D:\Android\gradle-8.7\bin\gradle.bat
csc:      C:\Windows\Microsoft.NET\Framework64\v4.0.30319\csc.exe
手机:     OnePlus PJD110 (serial <手机序列号>)，adb 可用
```

手机编译：
```
D:\Android\gradle-8.7\bin\gradle.bat -p D:\Projects\PhoneMouse\android assembleRelease --no-daemon --console=plain
```

电脑端打包：见 `docs/BUILD.md`（jlink 裁运行时 + jpackage 出免安装包）

## 六、工作方式要求

1. **先看代码再动手**，不要凭想象重写
2. 每完成一块，先编译通过再往下做
3. 改传感器相关代码前，先告诉我，并跑静止测试
4. 每完成一个可用的阶段，打一个版本节点（`_releases\vX.Y-说明-日期\`）
5. 保留 v1.6 快照不动，作为随时可回退的基线
6. 有不确定的地方直接问我，不要猜

## 七、当前状态

- 功能完整、实机验证通过、已打包成给朋友的发布包
- 传感器静止 20 秒 0 漂移（经过多轮修复才稳定，详见 HANDOVER.md 第四节）
- 电脑端音量/切歌/播放暂停/日志导出/自定义键/关于页 全部可用
- 手机端有彩蛋（连点标题 7 次），电脑端也有（连点左上角标题 7 次）

先读文档，读完告诉我你打算怎么拆分和重写，我们确认后再动手。