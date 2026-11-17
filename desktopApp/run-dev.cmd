@echo off
rem Launches the built dev exe with an isolated profile so it runs beside an installed release.
rem Usage: run-dev.cmd [profile]   (default: dev; a different name gives a separate parallel instance)
set "PROFILE=%~1"
if "%PROFILE%"=="" set "PROFILE=dev"
set "ROOT=%~dp0build\dev-profiles\%PROFILE%"
set "XDG_DATA_HOME=%ROOT%\data"
set "XDG_CONFIG_HOME=%ROOT%\config"
set "XDG_STATE_HOME=%ROOT%\state"
set "XDG_CACHE_HOME=%ROOT%\cache"
set "LOCALAPPDATA=%ROOT%\localappdata"
rem JavaCPP unpacks FFmpeg DLLs into one per-user cache; a running release has them locked, so give dev its own.
set "JAVA_TOOL_OPTIONS=-Dorg.bytedeco.javacpp.cachedir=\"%ROOT%\javacpp-cache\""
start "" "%~dp0build\compose\binaries\main\app\Metrolist\Metrolist.exe"
