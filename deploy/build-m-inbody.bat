@echo off
setlocal enabledelayedexpansion

:: Move to script directory
cd /d "%~dp0"
set "SCRIPT_DIR=%CD%"

echo =====================================
echo M-INBODY Setup Builder
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

:: Step 2: Find JAR file
echo [2/5] Looking for JAR file...
set "JAR_FILE="
for %%f in ("%SCRIPT_DIR%\*.jar") do (
    set "JAR_FILE=%%~nxf"
    set "JAR_PATH=%%f"
)
if not defined JAR_FILE (
    echo Error: No JAR file found in %SCRIPT_DIR%
    pause
    exit /b 1
)
echo Found JAR: %JAR_FILE%

:: Check icon
if not exist "%SCRIPT_DIR%\icon.ico" (
    echo Warning: icon.ico not found - using default icon
)

:: Step 3: Create dynamic Launch4j config
echo.
echo [3/5] Creating Launch4j configuration...
(
echo ^<?xml version="1.0" encoding="UTF-8"?^>
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
echo   ^<restartOnCrash^>false^</restartOnCrash^>
echo   ^<manifest^>%SCRIPT_DIR%\m-inbody.manifest^</manifest^>
if exist "%SCRIPT_DIR%\icon.ico" echo   ^<icon^>%SCRIPT_DIR%\icon.ico^</icon^>
echo   ^<jre^>
echo     ^<path^>jre^</path^>
echo     ^<bundledJre64Bit^>true^</bundledJre64Bit^>
echo     ^<bundledJreAsFallback^>false^</bundledJreAsFallback^>
echo     ^<minVersion^>^</minVersion^>
echo     ^<maxVersion^>^</maxVersion^>
echo     ^<jdkPreference^>jreOnly^</jdkPreference^>
echo     ^<runtimeBits^>64^</runtimeBits^>
echo     ^<opt^>-Dfile.encoding=UTF-8 -Dconsole.encoding=UTF-8 -Dsun.stdout.encoding=UTF-8 -Dsun.stderr.encoding=UTF-8 -Duser.language=ko -Duser.country=KR -Djava.net.preferIPv4Stack=true^</opt^>
echo     ^<initialHeapSize^>512^</initialHeapSize^>
echo     ^<maxHeapSize^>2048^</maxHeapSize^>
echo   ^</jre^>
echo ^</launch4jConfig^>
) > "%SCRIPT_DIR%\m-inbody-launch4j-temp.xml"

:: Create manifest if not exists
if not exist "%SCRIPT_DIR%\m-inbody.manifest" (
    echo Creating manifest file...
    (
    echo ^<?xml version="1.0" encoding="UTF-8" standalone="yes"?^>
    echo ^<assembly xmlns="urn:schemas-microsoft-com:asm.v1" manifestVersion="1.0"^>
    echo   ^<assemblyIdentity version="1.0.0.0" processorArchitecture="amd64" name="M-INBODY" type="win32"/^>
    echo   ^<description^>M-INBODY Damage Meter^</description^>
    echo   ^<trustInfo xmlns="urn:schemas-microsoft-com:asm.v3"^>
    echo     ^<security^>
    echo       ^<requestedPrivileges^>
    echo         ^<requestedExecutionLevel level="requireAdministrator" uiAccess="false"/^>
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

:: Step 5: Create Inno Setup script dynamically
echo.
echo [5/5] Creating installer with Inno Setup...
if not exist "%SCRIPT_DIR%\output" mkdir "%SCRIPT_DIR%\output"

:: Download Npcap if not exists
if not exist "%SCRIPT_DIR%\npcap-1.79.exe" (
    echo Downloading Npcap...
    powershell -Command "try { Invoke-WebRequest -Uri 'https://npcap.com/dist/npcap-1.79.exe' -OutFile '%SCRIPT_DIR%\npcap-1.79.exe' } catch { Write-Host 'Failed to download Npcap' -ForegroundColor Red }"
)

:: Create Inno Setup script
(
echo #define MyAppName "M-INBODY"
echo #define MyAppVersion "1.0.0"
echo #define MyAppPublisher "M-INBODY"
echo #define MyAppURL "https://discord.gg/kDXpuEd3Em"
echo #define MyAppExeName "M-INBODY.exe"
echo.
echo [Setup]
echo AppId={{A7B5C123-4567-8901-2345-678901234567}
echo AppName={#MyAppName}
echo AppVersion={#MyAppVersion}
echo AppVerName={#MyAppName} {#MyAppVersion}
echo AppPublisher={#MyAppPublisher}
echo AppPublisherURL={#MyAppURL}
echo AppSupportURL={#MyAppURL}
echo AppUpdatesURL={#MyAppURL}
echo DefaultDirName={autopf}\M-INBODY
echo DisableProgramGroupPage=yes
echo OutputDir=%SCRIPT_DIR%\output
echo OutputBaseFilename=M-INBODY-Setup
if exist "%SCRIPT_DIR%\icon.ico" echo SetupIconFile=%SCRIPT_DIR%\icon.ico
echo Compression=lzma
echo SolidCompression=yes
echo WizardStyle=modern
echo PrivilegesRequired=admin
echo ArchitecturesInstallIn64BitMode=x64
echo UninstallDisplayIcon={app}\{#MyAppExeName}
echo.
echo [Languages]
echo Name: "korean"; MessagesFile: "compiler:Languages\Korean.isl"
echo Name: "english"; MessagesFile: "compiler:Default.isl"
echo.
echo [Tasks]
echo Name: "desktopicon"; Description: "{cm:CreateDesktopIcon}"; GroupDescription: "{cm:AdditionalIcons}"; Flags: unchecked
echo.
echo [Files]
echo Source: "%SCRIPT_DIR%\M-INBODY.exe"; DestDir: "{app}"; Flags: ignoreversion
echo Source: "%SCRIPT_DIR%\jre\*"; DestDir: "{app}\jre"; Flags: ignoreversion recursesubdirs createallsubdirs
if exist "%SCRIPT_DIR%\npcap-1.79.exe" echo Source: "%SCRIPT_DIR%\npcap-1.79.exe"; DestDir: "{tmp}"; Flags: deleteafterinstall
echo.
echo [Icons]
echo Name: "{autoprograms}\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}"
echo Name: "{autodesktop}\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}"; Tasks: desktopicon
echo.
echo [Run]
if exist "%SCRIPT_DIR%\npcap-1.79.exe" echo Filename: "{tmp}\npcap-1.79.exe"; Parameters: "/S"; StatusMsg: "Installing network capture driver..."; Flags: runhidden
echo Filename: "{app}\{#MyAppExeName}"; Description: "{cm:LaunchProgram,{#StringChange(MyAppName, '&', '&&')}}"; Flags: nowait postinstall skipifsilent
echo.
echo [Code]
echo function IsNpcapInstalled: Boolean;
echo begin
echo   Result := FileExists(ExpandConstant('{sys}\Npcap\npcap.sys'^)^) or FileExists(ExpandConstant('{pf}\Npcap\npcap.sys'^)^);
echo end;
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
echo - Install M-INBODY with bundled Java (no JDK required)
echo - Automatically install Npcap if included
echo - Create desktop shortcut (optional)
echo - Run with administrator privileges
echo - Show console window for user to close
echo.
pause