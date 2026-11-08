@echo off
setlocal
rem builds build\portable\RustyClient.exe and build\installer\RustyClient-Setup.exe

call "%~dp0env.bat"
if errorlevel 1 exit /b 1

pushd "%~dp0.."
if not exist target\stage mkdir target\stage

rem installed build, it ships inside the installer next to the cef files
echo building the installed launcher
cargo build --release -p RustyClient
if errorlevel 1 goto failed
copy /y target\release\RustyClient.exe target\stage\RustyClient.exe >nul
if errorlevel 1 goto failed

rem portable build with the cef runtime packed inside
echo building the portable launcher
cargo build --release -p RustyClient --features portable
if errorlevel 1 goto failed
copy /y target\release\RustyClient.exe target\stage\RustyClient-portable.exe >nul
if errorlevel 1 goto failed

rem installer with the cef runtime and the installed build packed inside
echo building the installer
set "RUSTY_SETUP_PAYLOAD=%CD%\target\stage\RustyClient.exe"
cargo build --release -p RustyClient --features setup
if errorlevel 1 goto failed
copy /y target\release\RustyClient.exe target\stage\RustyClient-Setup.exe >nul
if errorlevel 1 goto failed

if exist build rmdir /s /q build
mkdir build\portable
mkdir build\installer
copy /y target\stage\RustyClient-portable.exe build\portable\RustyClient.exe >nul
if errorlevel 1 goto failed
copy /y target\stage\RustyClient-Setup.exe build\installer\RustyClient-Setup.exe >nul
if errorlevel 1 goto failed

popd
echo built build\portable\RustyClient.exe
echo built build\installer\RustyClient-Setup.exe
exit /b 0

:failed
popd
echo build failed
exit /b 1
