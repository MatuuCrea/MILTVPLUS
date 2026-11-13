#define AppName "MILTVPLUS"
#define AppVersion "22.34.9"
#define Publisher "MILTVPLUS"
#define AppExe "MILTVPLUS.exe"
[Setup]
AppId={{A81D2C60-2F5A-4B72-AB30-9D1A22F7C511}
AppName={#AppName}
AppVersion={#AppVersion}
AppPublisher={#Publisher}
DefaultDirName={autopf}\MILTVPLUS
DefaultGroupName=MILTVPLUS
OutputDir=..\..\dist
OutputBaseFilename=MILTVPLUS-Setup-x64
ArchitecturesInstallIn64BitMode=x64
ArchitecturesAllowed=x64
PrivilegesRequired=lowest
Compression=lzma2
SolidCompression=yes
WizardStyle=modern
SetupIconFile=..\\MILTVPLUS.Desktop\\miltvplus.ico
UninstallDisplayIcon={app}\{#AppExe}
[Files]
Source: "..\publish\*"; DestDir: "{app}"; Flags: recursesubdirs ignoreversion
[Icons]
Name: "{group}\MILTVPLUS"; Filename: "{app}\{#AppExe}"
Name: "{autodesktop}\MILTVPLUS"; Filename: "{app}\{#AppExe}"; Tasks: desktopicon
[Tasks]
Name: "desktopicon"; Description: "Crear acceso directo en el escritorio"; Flags: unchecked
[Run]
Filename: "{app}\{#AppExe}"; Description: "Iniciar MILTVPLUS"; Flags: postinstall nowait skipifsilent
