#define MyAppName "M-INBODY"
#define MyAppVersion "1.0.0"
#define MyAppPublisher "M-INBODY"
#define MyAppURL "https://discord.gg/kDXpuEd3Em"
#define MyAppExeName "M-INBODY.exe"

[Setup]
AppId={{A7B5C123-4567-8901-2345-678901234567}
AppName={#MyAppName}
AppVersion={#MyAppVersion}
AppVerName={#MyAppName} {#MyAppVersion}
AppPublisher={#MyAppPublisher}
AppPublisherURL={#MyAppURL}
AppSupportURL={#MyAppURL}
AppUpdatesURL={#MyAppURL}
DefaultDirName={autopf}\M-INBODY
DisableProgramGroupPage=yes
OutputDir=output
OutputBaseFilename=M-INBODY-Setup
SetupIconFile=icon.ico
Compression=lzma
SolidCompression=yes
WizardStyle=modern
PrivilegesRequired=admin
ArchitecturesInstallIn64BitMode=x64
UninstallDisplayIcon={app}\{#MyAppExeName}
DisableWelcomePage=no
DisableDirPage=no
DisableReadyPage=no

[Languages]
Name: "korean"; MessagesFile: "compiler:Languages\Korean.isl"
Name: "english"; MessagesFile: "compiler:Default.isl"

[Tasks]
Name: "desktopicon"; Description: "{cm:CreateDesktopIcon}"; GroupDescription: "{cm:AdditionalIcons}"; Flags: unchecked

[Files]
; Main executable
Source: "M-INBODY.exe"; DestDir: "{app}"; Flags: ignoreversion
; JRE bundle
Source: "jre\*"; DestDir: "{app}\jre"; Flags: ignoreversion recursesubdirs createallsubdirs
; Npcap installer (will be downloaded)
Source: "npcap-1.79.exe"; DestDir: "{tmp}"; Flags: deleteafterinstall; Check: not IsNpcapInstalled

[Icons]
Name: "{autoprograms}\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}"
Name: "{autodesktop}\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}"; Tasks: desktopicon
Name: "{app}\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}"

[Run]
; Install Npcap silently if not already installed
Filename: "{tmp}\npcap-1.79.exe"; Parameters: "/S"; StatusMsg: "Installing network capture driver (Npcap)..."; Flags: runhidden; Check: not IsNpcapInstalled

; Launch application after install
Filename: "{app}\{#MyAppExeName}"; Description: "{cm:LaunchProgram,{#StringChange(MyAppName, '&', '&&')}}"; Flags: nowait postinstall skipifsilent

[Code]
var
  DownloadPage: TDownloadWizardPage;

function IsNpcapInstalled: Boolean;
begin
  Result := FileExists(ExpandConstant('{sys}\Npcap\npcap.sys')) or 
            FileExists(ExpandConstant('{pf}\Npcap\npcap.sys'));
end;

function OnDownloadProgress(const Url, FileName: String; const Progress, ProgressMax: Int64): Boolean;
begin
  if Progress = ProgressMax then
    Log(Format('Successfully downloaded file to {tmp}: %s', [FileName]));
  Result := True;
end;

procedure InitializeWizard;
begin
  DownloadPage := CreateDownloadPage(SetupMessage(msgWizardPreparing), SetupMessage(msgPreparingDesc), @OnDownloadProgress);
end;

function NextButtonClick(CurPageID: Integer): Boolean;
begin
  if CurPageID = wpReady then begin
    if not IsNpcapInstalled then begin
      DownloadPage.Clear;
      DownloadPage.Add('https://npcap.com/dist/npcap-1.79.exe', 'npcap-1.79.exe', '');
      DownloadPage.Show;
      try
        try
          DownloadPage.Download;
          Result := True;
        except
          MsgBox('Failed to download Npcap. Please install it manually from https://npcap.com/#download', mbError, MB_OK);
          Result := True;
        end;
      finally
        DownloadPage.Hide;
      end;
    end else
      Result := True;
  end else
    Result := True;
end;

procedure CurPageChanged(CurPageID: Integer);
begin
  if CurPageID = wpReady then
  begin
    if not IsNpcapInstalled then
    begin
      WizardForm.ReadyMemo.Lines.Add('');
      WizardForm.ReadyMemo.Lines.Add('Additional components to be installed:');
      WizardForm.ReadyMemo.Lines.Add('  - Npcap (Network capture driver) - Required for packet capture');
    end
    else
    begin
      WizardForm.ReadyMemo.Lines.Add('');
      WizardForm.ReadyMemo.Lines.Add('Npcap is already installed.');
    end;
  end;
end;

function PrepareToInstall(var NeedsRestart: Boolean): String;
begin
  Result := '';
  
  // Check if application is already running
  if CheckForMutexes('M-INBODY-Mutex') then
  begin
    Result := 'M-INBODY is currently running. Please close it before continuing.';
  end;
end;

procedure CurUninstallStepChanged(CurUninstallStep: TUninstallStep);
begin
  if CurUninstallStep = usUninstall then
  begin
    if MsgBox('Do you want to remove Npcap as well?' + #13#10 + 
              'Note: Other applications may be using Npcap.', mbConfirmation, MB_YESNO) = IDYES then
    begin
      // Uninstall Npcap
      Exec(ExpandConstant('{sys}\sc.exe'), 'stop npcap', '', SW_HIDE, ewWaitUntilTerminated, ResultCode);
      Exec(ExpandConstant('{pf}\Npcap\uninstall.exe'), '/S', '', SW_HIDE, ewWaitUntilTerminated, ResultCode);
    end;
  end;
end;

[UninstallDelete]
Type: filesandordirs; Name: "{app}"