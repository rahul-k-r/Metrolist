@echo off
setlocal enabledelayedexpansion

echo [1/3] Cleaning previous builds...
if exist bin rmdir /s /q bin
if exist obj rmdir /s /q obj

set DEST=..\desktopApp\src\desktopMain\resources\windows
set STAGE=bin\publish

echo [2/3] Publishing Metro WebView2 Helper (Standalone)...
dotnet publish -c Release -r win-x64 --self-contained true --output "%STAGE%" /p:PublishSingleFile=true /p:IncludeNativeLibrariesForSelfExtract=true /p:DebugType=None /p:DebugSymbols=false /p:PublishReadyToRun=true

if %errorlevel% neq 0 (
    echo.
    echo ERROR: Build failed!
    exit /b %errorlevel%
)

echo [3/3] Deploying to Desktop App resources...
if not exist "%DEST%" mkdir "%DEST%"
if exist "%DEST%\metro-wv2-helper.exe" del /q "%DEST%\metro-wv2-helper.exe"
copy /Y "%STAGE%\metro-wv2-helper.exe" "%DEST%\"
if %errorlevel% neq 0 (
    echo.
    echo ERROR: Could not copy the helper to %DEST%.
    exit /b %errorlevel%
)

echo.
echo SUCCESS: Helper deployed to %DEST%\metro-wv2-helper.exe
pause
