@echo off
setlocal enabledelayedexpansion

:: Move to script directory
cd /d "%~dp0"
set "SCRIPT_DIR=%CD%"

echo Creating custom JRE for M-INBODY...
echo Working directory: %SCRIPT_DIR%

:: Check JAVA_HOME or auto-detect
if not defined JAVA_HOME (
    echo JAVA_HOME not set. Trying to auto-detect...
    for /f "tokens=*" %%i in ('where java 2^>nul') do (
        set "JAVA_PATH=%%i"
        goto :found_java
    )
    :found_java
    if defined JAVA_PATH (
        for %%i in ("!JAVA_PATH!") do set "JAVA_BIN_DIR=%%~dpi"
        for %%i in ("!JAVA_BIN_DIR:~0,-1!") do set "JAVA_HOME=%%~dpi"
        set "JAVA_HOME=!JAVA_HOME:~0,-1!"
        echo Found Java at: !JAVA_HOME!
    ) else (
        echo Error: Java not found. Please install JDK 17 or set JAVA_HOME
        pause
        exit /b 1
    )
)

:: Clean previous JRE
if exist "%SCRIPT_DIR%\jre" rmdir /s /q "%SCRIPT_DIR%\jre"

:: Create minimal JRE with required modules for Spring Boot
"%JAVA_HOME%\bin\jlink" ^
    --add-modules java.base,java.desktop,java.logging,java.management,java.naming,java.net.http,java.scripting,java.sql,java.xml,jdk.crypto.ec,jdk.httpserver,jdk.unsupported,java.instrument,java.prefs,java.security.jgss,java.security.sasl,jdk.zipfs,jdk.management ^
    --output "%SCRIPT_DIR%\jre" ^
    --strip-debug ^
    --no-man-pages ^
    --no-header-files ^
    --compress=2

if %errorlevel% neq 0 (
    echo Error: Failed to create JRE
    pause
    exit /b 1
)

echo JRE created successfully!
echo Size: 
dir "%SCRIPT_DIR%\jre" | findstr "File(s)"
echo.
echo Press any key to continue...
pause >nul
exit /b 0