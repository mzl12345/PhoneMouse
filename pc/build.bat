@echo off
chcp 65001 >nul
setlocal
set "JAVA_HOME=D:\Tools\jdk-17"
set "PATH=%JAVA_HOME%\bin;%PATH%"
cd /d "%~dp0"

if not exist out mkdir out

rem 编译全部源码（含子包），用文件清单避免通配符层级问题
dir /s /b "src\*.java" > "%TEMP%\phonemouse-sources.txt"
javac -encoding UTF-8 -d out @"%TEMP%\phonemouse-sources.txt"
if errorlevel 1 (
  echo [FAIL] javac
  pause
  exit /b 1
)
del "%TEMP%\phonemouse-sources.txt" >nul 2>nul

rem 主类在包里了：phonemouse.PcReceiver
jar --create --file PhoneMouseReceiver.jar --main-class phonemouse.PcReceiver -C out .
if errorlevel 1 (
  echo [FAIL] jar
  pause
  exit /b 1
)

echo.
echo OK: PhoneMouseReceiver.jar  ^(main = phonemouse.PcReceiver^)
pause
