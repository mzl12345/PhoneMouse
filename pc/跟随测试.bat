@echo off
set "JAVA_HOME=D:\Tools\jdk-17"
start "" "%JAVA_HOME%\bin\javaw.exe" -cp "%~dp0out" PathTest
