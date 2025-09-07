@echo off
chcp 65001 > nul
setlocal enabledelayedexpansion
set "CONSOLE_FONT=Consolas"
set JAVA_TOOL_OPTIONS=-Dfile.encoding=UTF-8 -Dsun.stdout.encoding=UTF-8 -Dsun.stderr.encoding=UTF-8
title M-Inbody Damage Meter

echo ===============================================
echo       M-Inbody Damage Meter Running...
echo ===============================================
echo.

REM Check if Java 17 or higher is installed
echo [INFO] Checking Java version...
java -version 2>&1 | findstr /i "version" > temp_java_check.txt
if %errorlevel% neq 0 (
    echo [ERROR] Java is not installed.
    echo Please install Java 17 or higher.
    echo Download: https://adoptium.net/
    echo.
    del temp_java_check.txt 2>nul
    pause
    exit /b 1
)
del temp_java_check.txt 2>nul

REM Move to current directory
cd /d "%~dp0"

REM Check for JAR file (prefer latest version)
set JAR_FILE=
if exist "m-inbody-0.0.1-SNAPSHOT.jar" (
    set JAR_FILE=m-inbody-0.0.1-SNAPSHOT.jar
) else if exist "m-inbody.jar" (
    set JAR_FILE=m-inbody.jar
) else (
    echo [ERROR] Cannot find JAR file.
    echo Please check if m-inbody-0.0.1-SNAPSHOT.jar or m-inbody.jar exists in current directory.
    echo.
    pause
    exit /b 1
)

echo [INFO] System diagnostics in progress...
echo [DEBUG] Current directory: %cd%
echo [DEBUG] Script location: %~dp0

REM Check network adapter
echo [INFO] Checking network adapter...
ipconfig /all | findstr "Adapter" >nul 2>&1
if %errorlevel% neq 0 (
    echo [WARNING] Cannot get network adapter information.
)

REM Check Npcap service
echo [INFO] Checking Npcap service...
sc query npcap >nul 2>&1
if %errorlevel% neq 0 (
    echo [WARNING] Npcap service is not running.
    echo Please check if Npcap is properly installed.
)

echo [INFO] Starting application...
echo [INFO] JAR file to run: %JAR_FILE%
echo [INFO] Access http://localhost:5000 in your web browser.
echo [INFO] Press Ctrl+C to exit.
echo.

REM Run with administrator privileges (required for packet capture)
echo [INFO] Checking administrator privileges...
net session >nul 2>&1
if %errorlevel% neq 0 (
    echo [WARNING] Administrator privileges required.
    echo Run as administrator for packet capture.
    echo.
    echo Press any key to restart with administrator privileges...
    pause > nul
    powershell -Command "Start-Process cmd -ArgumentList '/c cd /d \"\"%~dp0\"\" && \"\"%~nx0\"\"' -Verb runAs -WorkingDirectory '\"%~dp0\"'"
    exit /b
)

REM Run application (with debug mode for detailed logs)
java -Dfile.encoding=UTF-8 -Dconsole.encoding=UTF-8 -Dsun.stdout.encoding=UTF-8 -Dsun.stderr.encoding=UTF-8 -Duser.language=ko -Duser.country=KR -jar %JAR_FILE% --spring.profiles.active=prod --logging.level.com.gamboo.minbody=DEBUG --logging.level.org.pcap4j=DEBUG

echo.
echo [INFO] Application terminated.
pause