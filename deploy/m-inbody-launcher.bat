@echo off
setlocal enabledelayedexpansion

:: Check if running as admin
net session >nul 2>&1
if %errorlevel% neq 0 (
    echo Requesting administrator privileges...
    powershell -Command "Start-Process '%~f0' -Verb runAs"
    exit /b
)

:: Check if Npcap is installed
if not exist "%ProgramFiles%\Npcap\npcap.sys" (
    if not exist "%SystemRoot%\System32\Npcap\npcap.sys" (
        echo =====================================
        echo Npcap is not installed
        echo =====================================
        echo.
        echo M-INBODY requires Npcap for packet capture.
        echo.
        
        :: Check if Npcap installer exists
        if exist "%~dp0\npcap-1.79.exe" (
            echo Installing Npcap now...
            echo Please follow the installation wizard.
            echo.
            start /wait "%~dp0\npcap-1.79.exe"
            
            :: Check if installation was successful
            if not exist "%ProgramFiles%\Npcap\npcap.sys" (
                if not exist "%SystemRoot%\System32\Npcap\npcap.sys" (
                    echo.
                    echo Npcap installation failed or was cancelled.
                    echo Please install Npcap manually from: https://npcap.com/#download
                    pause
                    exit /b 1
                )
            )
            echo Npcap installed successfully!
        ) else (
            echo Npcap installer not found.
            echo Please download and install from: https://npcap.com/#download
            pause
            exit /b 1
        )
    )
)

:: Run M-INBODY
echo Starting M-INBODY...
cd /d "%~dp0"
start "" "jre\bin\java.exe" -Dfile.encoding=UTF-8 -Dconsole.encoding=UTF-8 -Dsun.stdout.encoding=UTF-8 -Dsun.stderr.encoding=UTF-8 -Duser.language=ko -Duser.country=KR -jar "%~dp0\m-inbody.jar" --spring.profiles.active=prod --logging.level.com.gamboo.minbody=DEBUG --logging.level.org.pcap4j=DEBUG