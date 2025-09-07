@echo off
setlocal enabledelayedexpansion

:: Prevent window from closing on error
if "%1"=="" (
    cmd /k "%~f0" nested
    exit /b
)

:: Move to script directory
cd /d "%~dp0"
set "SCRIPT_DIR=%CD%"

echo =====================================
echo M-INBODY Setup Builder (Simplified)
echo =====================================
echo Working directory: %SCRIPT_DIR%
echo.

:: Step 1: Create JRE
echo [1/5] Creating JRE bundle...
call "%SCRIPT_DIR%\create-jre.bat"
if not exist "%SCRIPT_DIR%\jre\bin\java.exe" (
    echo Failed to create JRE - jre\bin\java.exe not found
    pause
    exit /b 1
)
echo JRE bundle verified.
echo.

:: Step 2: Find JAR file
echo [2/5] Looking for JAR file...
echo Current directory: %SCRIPT_DIR%
echo.
echo Searching for JAR files:
dir "%SCRIPT_DIR%\*.jar" 2>nul
echo.

set "JAR_FILE="
for %%f in ("%SCRIPT_DIR%\*.jar") do (
    set "JAR_FILE=%%~nxf"
    set "JAR_PATH=%%f"
    echo Found: %%f
)

if not defined JAR_FILE (
    echo Error: No JAR file found in %SCRIPT_DIR%
    echo.
    echo Please make sure you have a .jar file in the same directory as this script.
    echo Expected files like: m-inbody-0.0.1-SNAPSHOT.jar
    echo.
    pause
    exit /b 1
)
echo Using JAR: %JAR_FILE%

:: Check icon
if not exist "%SCRIPT_DIR%\icon.ico" (
    echo Warning: icon.ico not found - using default icon
)

:: Step 3: Create simplified Launch4j config
echo.
echo [3/5] Creating Launch4j configuration...
(
echo ^<launch4jConfig^>
echo   ^<dontWrapJar^>false^</dontWrapJar^>
echo   ^<headerType^>console^</headerType^>
echo   ^<jar^>%JAR_FILE%^</jar^>
echo   ^<outfile^>%SCRIPT_DIR%\M-INBODY.exe^</outfile^>
echo   ^<errTitle^>M-INBODY Error^</errTitle^>
echo   ^<cmdLine^>--spring.profiles.active=prod --logging.level.com.gamboo.minbody=DEBUG --logging.level.org.pcap4j=DEBUG^</cmdLine^>
echo   ^<chdir^>.^</chdir^>
echo   ^<priority^>normal^</priority^>
echo   ^<downloadUrl^>^</downloadUrl^>
echo   ^<supportUrl^>https://discord.gg/kDXpuEd3Em^</supportUrl^>
echo   ^<stayAlive^>true^</stayAlive^>
echo   ^<manifest^>%SCRIPT_DIR%\m-inbody.manifest^</manifest^>
if exist "%SCRIPT_DIR%\icon.ico" echo   ^<icon^>%SCRIPT_DIR%\icon.ico^</icon^>
echo   ^<jre^>
echo     ^<path^>jre^</path^>
echo     ^<minVersion^>^</minVersion^>
echo     ^<jdkPreference^>jreOnly^</jdkPreference^>
echo     ^<opt^>-Dfile.encoding=UTF-8 -Dconsole.encoding=UTF-8 -Dsun.stdout.encoding=UTF-8 -Dsun.stderr.encoding=UTF-8 -Duser.language=ko -Duser.country=KR^</opt^>
echo   ^</jre^>
echo ^</launch4jConfig^>
) > "%SCRIPT_DIR%\m-inbody-launch4j-temp.xml"

:: Create manifest if not exists
if not exist "%SCRIPT_DIR%\m-inbody.manifest" (
    echo Creating manifest file...
    (
    echo ^<?xml version="1.0" encoding="UTF-8" standalone="yes"?^>
    echo ^<assembly xmlns="urn:schemas-microsoft-com:asm.v1" manifestVersion="1.0"^>
    echo   ^<assemblyIdentity version="1.0.0.0" processorArchitecture="*" name="M-INBODY" type="win32"/^>
    echo   ^<description^>M-INBODY Damage Meter^</description^>
    echo   ^<trustInfo xmlns="urn:schemas-microsoft-com:asm.v2"^>
    echo     ^<security^>
    echo       ^<requestedPrivileges^>
    echo         ^<requestedExecutionLevel level="requireAdministrator"/^>
    echo       ^</requestedPrivileges^>
    echo     ^</security^>
    echo   ^</trustInfo^>
    echo ^</assembly^>
    ) > "%SCRIPT_DIR%\m-inbody.manifest"
)

:: Step 4: Create EXE with Launch4j
echo.
echo [4/5] Creating M-INBODY.exe with Launch4j...
set "LAUNCH4J_FOUND=0"
if exist "C:\Program Files (x86)\Launch4j\launch4jc.exe" (
    "C:\Program Files (x86)\Launch4j\launch4jc.exe" "%SCRIPT_DIR%\m-inbody-launch4j-temp.xml"
    set "LAUNCH4J_FOUND=1"
) else if exist "C:\Program Files\Launch4j\launch4jc.exe" (
    "C:\Program Files\Launch4j\launch4jc.exe" "%SCRIPT_DIR%\m-inbody-launch4j-temp.xml"
    set "LAUNCH4J_FOUND=1"
)

if "%LAUNCH4J_FOUND%"=="0" (
    echo Error: Launch4j not found
    echo Please install Launch4j from https://launch4j.sourceforge.net/
    del "%SCRIPT_DIR%\m-inbody-launch4j-temp.xml" 2>nul
    pause
    exit /b 1
)

del "%SCRIPT_DIR%\m-inbody-launch4j-temp.xml" 2>nul

if not exist "%SCRIPT_DIR%\M-INBODY.exe" (
    echo Failed to create M-INBODY.exe
    pause
    exit /b 1
)
echo M-INBODY.exe created successfully.

:: Copy JAR file
echo Copying JAR file...
copy "%JAR_PATH%" "%SCRIPT_DIR%\M-INBODY.jar" >nul

:: Create launcher script
echo Creating launcher script...
(
echo @echo off
echo setlocal enabledelayedexpansion
echo.
echo :: Get script directory
echo cd /d "%%~dp0"
echo set "APP_DIR=%%CD%%"
echo.
echo :: Check if Npcap is installed
echo set "NPCAP_INSTALLED=0"
echo if exist "%%ProgramFiles%%\Npcap\npcap.sys" set "NPCAP_INSTALLED=1"
echo if exist "%%SystemRoot%%\System32\Npcap\npcap.sys" set "NPCAP_INSTALLED=1"
echo.
echo if "%%NPCAP_INSTALLED%%"=="0" ^(
echo     echo =====================================
echo     echo M-INBODY - Npcap Required
echo     echo =====================================
echo     echo.
echo     echo M-INBODY requires Npcap for network packet capture.
echo     echo.
echo     
echo     :: Check if Npcap installer exists
echo     if exist "%%APP_DIR%%\npcap-1.79.exe" ^(
echo         echo Npcap installer will now open.
echo         echo Please complete the installation to continue.
echo         echo.
echo         echo IMPORTANT: Just click "Next" through all steps to install.
echo         echo.
echo         pause
echo         
echo         :: Run Npcap installer
echo         start /wait "" "%%APP_DIR%%\npcap-1.79.exe"
echo         
echo         :: Re-check if installed
echo         set "NPCAP_NOW_INSTALLED=0"
echo         if exist "%%ProgramFiles%%\Npcap\npcap.sys" set "NPCAP_NOW_INSTALLED=1"
echo         if exist "%%SystemRoot%%\System32\Npcap\npcap.sys" set "NPCAP_NOW_INSTALLED=1"
echo         
echo         if "!NPCAP_NOW_INSTALLED!"=="0" ^(
echo             echo.
echo             echo Npcap installation was not completed.
echo             echo M-INBODY cannot run without Npcap.
echo             echo.
echo             pause
echo             exit /b 1
echo         ^)
echo         echo.
echo         echo Npcap installed successfully!
echo     ^) else ^(
echo         echo Error: Npcap installer not found.
echo         echo Please reinstall M-INBODY.
echo         pause
echo         exit /b 1
echo     ^)
echo ^)
echo.
echo :: Run M-INBODY with bundled JRE
echo echo Starting M-INBODY...
echo start "" "%%APP_DIR%%\jre\bin\java.exe" -Dfile.encoding=UTF-8 -Dconsole.encoding=UTF-8 -Dsun.stdout.encoding=UTF-8 -Dsun.stderr.encoding=UTF-8 -Duser.language=ko -Duser.country=KR -jar "%%APP_DIR%%\M-INBODY.jar" --spring.profiles.active=prod --logging.level.com.gamboo.minbody=DEBUG --logging.level.org.pcap4j=DEBUG
) > "%SCRIPT_DIR%\M-INBODY-Launcher.bat"

:: Step 5: Create Inno Setup script dynamically
echo.
echo [5/5] Creating installer with Inno Setup...
if not exist "%SCRIPT_DIR%\output" mkdir "%SCRIPT_DIR%\output"

:: Download Npcap if not exists
if not exist "%SCRIPT_DIR%\npcap-1.79.exe" (
    echo Downloading Npcap...
    powershell -Command "try { Invoke-WebRequest -Uri 'https://npcap.com/dist/npcap-1.79.exe' -OutFile '%SCRIPT_DIR%\npcap-1.79.exe' } catch { Write-Host 'Failed to download Npcap' -ForegroundColor Red }"
)

:: Create simple Inno Setup script
(
echo [Setup]
echo AppName=M-INBODY
echo AppVersion=1.0.0
echo AppPublisher=M-INBODY
echo AppPublisherURL=https://discord.gg/kDXpuEd3Em
echo DefaultDirName={autopf}\M-INBODY
echo DisableProgramGroupPage=yes
echo OutputDir=%SCRIPT_DIR%\output
echo OutputBaseFilename=M-INBODY-Setup
echo Compression=lzma
echo SolidCompression=yes
echo PrivilegesRequired=admin
echo ArchitecturesInstallIn64BitMode=x64
echo.
echo [Languages]
echo Name: "korean"; MessagesFile: "compiler:Languages\Korean.isl"
echo.
echo [Tasks]
echo Name: "desktopicon"; Description: "{cm:CreateDesktopIcon}"; GroupDescription: "{cm:AdditionalIcons}"
echo.
echo [Files]
echo Source: "%SCRIPT_DIR%\M-INBODY.exe"; DestDir: "{app}"; Flags: ignoreversion
echo Source: "%SCRIPT_DIR%\M-INBODY.jar"; DestDir: "{app}"; Flags: ignoreversion
echo Source: "%SCRIPT_DIR%\M-INBODY-Launcher.bat"; DestDir: "{app}"; Flags: ignoreversion
echo Source: "%SCRIPT_DIR%\jre\*"; DestDir: "{app}\jre"; Flags: ignoreversion recursesubdirs createallsubdirs
if exist "%SCRIPT_DIR%\npcap-1.79.exe" echo Source: "%SCRIPT_DIR%\npcap-1.79.exe"; DestDir: "{app}"; Flags: ignoreversion
echo.
echo [Icons]
echo Name: "{autoprograms}\M-INBODY"; Filename: "{app}\M-INBODY-Launcher.bat"; IconFilename: "{app}\M-INBODY.exe"
echo Name: "{autodesktop}\M-INBODY"; Filename: "{app}\M-INBODY-Launcher.bat"; IconFilename: "{app}\M-INBODY.exe"; Tasks: desktopicon
echo.
echo [Run]
echo Filename: "{app}\M-INBODY-Launcher.bat"; Description: "{cm:LaunchProgram,M-INBODY}"; Flags: nowait postinstall skipifsilent
) > "%SCRIPT_DIR%\m-inbody-installer-temp.iss"

:: Run Inno Setup
set "INNO_FOUND=0"
if exist "C:\Program Files (x86)\Inno Setup 6\ISCC.exe" (
    "C:\Program Files (x86)\Inno Setup 6\ISCC.exe" "%SCRIPT_DIR%\m-inbody-installer-temp.iss"
    set "INNO_FOUND=1"
) else if exist "C:\Program Files\Inno Setup 6\ISCC.exe" (
    "C:\Program Files\Inno Setup 6\ISCC.exe" "%SCRIPT_DIR%\m-inbody-installer-temp.iss"
    set "INNO_FOUND=1"
)

if "%INNO_FOUND%"=="0" (
    echo Error: Inno Setup not found
    echo Please install Inno Setup from https://jrsoftware.org/isinfo.php
    del "%SCRIPT_DIR%\m-inbody-installer-temp.iss" 2>nul
    pause
    exit /b 1
)

del "%SCRIPT_DIR%\m-inbody-installer-temp.iss" 2>nul

echo.
echo =====================================
echo Build Complete!
echo =====================================
echo.
echo Output file: %SCRIPT_DIR%\output\M-INBODY-Setup.exe
echo.
echo This installer will:
echo - Install M-INBODY with bundled Java (no JDK needed)
echo - Include Npcap installer for first-time setup
echo - Guide users through Npcap installation
echo - Show console window for monitoring
echo - Create desktop shortcut (optional)
echo.
pause