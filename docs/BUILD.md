# 构建说明

## 环境

- JDK 17：`D:\Tools\jdk-17`
- Android SDK：`D:\Android\Sdk`
- Gradle 8.7：`D:\Android\gradle-8.7`
- C# 编译器：`C:\Windows\Microsoft.NET\Framework64\v4.0.30319\csc.exe`

## 手机端

```powershell
$env:JAVA_HOME='D:\Tools\jdk-17'
$env:ANDROID_HOME='D:\Android\Sdk'
D:\Android\gradle-8.7\bin\gradle.bat -p D:\Projects\PhoneMouse\android assembleRelease --no-daemon --console=plain
```

产物：`android\app\build\outputs\apk\release\app-release.apk`

安装（用同一个 keystore 签名，可以覆盖安装）：

```powershell
D:\Android\Sdk\platform-tools\adb.exe install -r <apk路径>
```

## 电脑端

**v2.x 起主类在包里了**：`phonemouse.PcReceiver`（原来是默认包的 `PcReceiver`）。
直接跑 `pc\build.bat` 即可，它递归编译 `src\**\*.java`：

```powershell
cd D:\Projects\PhoneMouse\pc
build.bat
```

等价的手工命令：

```powershell
$env:JAVA_HOME='D:\Tools\jdk-17'
cd D:\Projects\PhoneMouse\pc
dir /s /b src\*.java > %TEMP%\sources.txt
javac -encoding UTF-8 -d out @%TEMP%\sources.txt
jar --create --file PhoneMouseReceiver.jar --main-class phonemouse.PcReceiver -C out .
```

辅助 exe（4 个，改过一次就重新编）：

```bat
cd /d D:\Projects\PhoneMouse\pc
set CSC=C:\Windows\Microsoft.NET\Framework64\v4.0.30319\csc.exe
%CSC% /nologo /optimize /out:TypeText.exe  TypeText.cs
%CSC% /nologo /optimize /out:KeySend.exe   KeySend.cs
%CSC% /nologo /optimize /out:VolumeSet.exe VolumeSet.cs
%CSC% /nologo /optimize /out:MediaKey.exe  MediaKey.cs
```

运行：双击 `启动接收端.bat`（用 javaw 无控制台）。

## 回归测试（改按键逻辑必跑）

```powershell
pwsh -File D:\Projects\PhoneMouse\pc\test\compare-golden.ps1
```

必须输出 `GOLDEN OK`。它比对的是 `docs/GOLDEN_BUTTONS.tsv`：
24 个按键编号 + 4 条自定义键路由，逐行比对「按下时的输入调用 / 抬起时的调用 / 点亮哪个方块」。

## 调试图标

电脑端支持两个只读的调试开关（不写配置、不影响正常使用）：

| 参数 | 作用 |
|---|---|
| `-Dphonemouse.theme=light\|dark` | 强制主题（截图/试色用） |
| `-Dphonemouse.devlog=true` | 启动就展开开发者日志 |
| `-Dphonemouse.demo=true` | 伪造「已连接 + 两个键被按过」的界面，不注入任何真实输入 |

## 免安装包

```bat
jlink --add-modules java.base,java.desktop,java.logging --output _rt ^
      --strip-debug --no-man-pages --no-header-files --compress=2

jpackage --type app-image --input _pkg\input --main-jar PhoneMouseReceiver.jar ^
         --main-class phonemouse.PcReceiver --name PhoneMouse --dest _dist --runtime-image _rt
```

注意：

- `--main-class` 必须写成 `phonemouse.PcReceiver`（v2.x 起带包名）
- jar 与 4 个 exe 都要放进 `_pkg\input`

## 注意

- 打包 jar 前必须先结束正在运行的接收端，否则文件被占用会失败
- `local.properties` 里写死了 SDK 路径
