@echo off
setlocal
rem builds build\portable\RustyClient.exe and build\installer\RustyClient-Setup.exe

call "%~dp0env.bat"
if errorlevel 1 exit /b 1

pushd "%~dp0.."
if not exist target\stage mkdir target\stage

rem in-game client jars, embedded into the installed and portable launcher
echo building the in-game client
if not defined RUSTY_CLIENT_JDK set "RUSTY_CLIENT_JDK=%ProgramFiles%\BellSoft\LibericaJDK-25"
if not exist "%RUSTY_CLIENT_JDK%\bin\java.exe" (
    echo could not find jdk 25 at %RUSTY_CLIENT_JDK%, set RUSTY_CLIENT_JDK
    goto failed
)
pushd client
set "JAVA_HOME=%RUSTY_CLIENT_JDK%"
call "%CD%\gradlew.bat" collectJars --no-daemon
set "GRADLE_RESULT=%ERRORLEVEL%"
popd
if not "%GRADLE_RESULT%"=="0" goto failed
if exist target\stage\client rmdir /s /q target\stage\client
mkdir target\stage\client
copy /y client\build\jars\rustyclient-*.jar target\stage\client\ >nul
if errorlevel 1 goto failed
set "RUSTY_CLIENT_JARS=%CD%\target\stage\client"

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
